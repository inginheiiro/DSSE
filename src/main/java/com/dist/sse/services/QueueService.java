package com.dist.sse.services;

import com.dist.sse.config.QueueProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * QueueService - Manages a distributed message queue system with high availability.
 *
 * Key Features:
 * 1. Leader Election: Uses distributed leader election to manage queue operations
 * 2. Message Ordering: Maintains message sequence and timestamp-based ordering
 * 3. Real-time Updates: Uses Server-Sent Events (SSE) for client notifications
 * 4. High Availability: Supports failover with leader-follower pattern
 * 5. Message Tracking: Ensures guaranteed message delivery with acknowledgments
 * 6. Load Balancing: Integrates with HAProxy for load distribution
 */
@Service
public class QueueService {
    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);


    /**
     * Thread-safe priority queue that stores messages ordered by timestamp.
     * Used by leader instance to maintain message ordering before processing.
     */
    private final PriorityBlockingQueue<QueueMessage> localQueue;

    /**
     * Maps client IDs to their SSE emitters for real-time message delivery.
     * Thread-safe map to handle concurrent client connections/disconnections.
     */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Thread-safe boolean flag indicating if this instance is currently the leader.
     * Updated during leader election process.
     */
    private final AtomicBoolean isLeader = new AtomicBoolean(false);

    /**
     * Tracks messages that have been sent but not yet acknowledged by all clients.
     * Key: Message ID
     * Value: Message object
     */
    private final Map<String, QueueMessage> unconfirmedMessages = new ConcurrentHashMap<>();

    /**
     * Maintains strict time-based ordering of messages pending processing.
     * Messages are stored with their timestamps as keys for ordered processing.
     */
    private final ConcurrentSkipListMap<Instant, QueueMessage> orderedBuffer = new ConcurrentSkipListMap<>();

    /**
     * Set of message IDs that have been fully processed.
     * Used to prevent duplicate processing across instances.
     */
    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();

    /**
     * Thread-safe counter for generating sequential message numbers.
     * Ensures strict ordering of messages processed by leader.
     */
    private final AtomicLong sequenceGenerator = new AtomicLong(0);

    /**
     * Tracks which messages have been processed by each client.
     * Key: Client ID
     * Value: Set of processed message IDs for that client
     */
    private final Map<String, Set<String>> clientProcessedMessages = new ConcurrentHashMap<>();

    /**
     * Spring's RestTemplate for making HTTP requests to other instances
     * Used for leader forwarding and inter-instance communication.
     */
    private final RestTemplate restTemplate;

    /**
     * Service for discovering and tracking available queue instances.
     * Used during leader election and instance notification.
     */
    private final DiscoveryService discoveryService;

    /**
     * Configuration properties for queue behavior and timing.
     * Contains timeouts, intervals, and other operational parameters.
     */
    private final QueueProperties queueProperties;

    /**
     * Single-threaded executor for scheduling delayed message processing.
     * Used to maintain consistent timing in message handling.
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Value("${server.port}")
    private String serverPort;

    @Value("${app.host}")
    private String appHost;

    /**
     * Returns the URL of the current instance.
     */
    private String getSelfUrl() {
        return "http://" + appHost + ":" + serverPort;
    }

    /**
     * Constructs QueueService with required dependencies.
     * Initializes queue with configured capacity and timestamp-based ordering.
     */
    public QueueService(RestTemplate restTemplate,
                        DiscoveryService discoveryService,
                        QueueProperties queueProperties) {
        this.restTemplate = restTemplate;
        this.discoveryService = discoveryService;
        this.queueProperties = queueProperties;
        this.localQueue = new PriorityBlockingQueue<>(
                queueProperties.getInitialQueueCapacity(),
                Comparator.comparing(QueueMessage::getTimestamp)
        );
    }

    /**
     * Performs periodic leader election among service instances.
     *
     * Flow:
     * 1. Retrieves all available service instances from discovery service
     * 2. Determines leader by selecting instance with lexicographically lowest URL
     * 3. Updates local leader status
     * 4. If becoming new leader, notifies HAProxy to update routing
     *
     * Leader Responsibilities:
     * - Process incoming messages
     * - Maintain message ordering
     * - Distribute messages to followers
     * - Handle client notifications
     */
    @Scheduled(fixedRateString = "${queue.leader-election-interval}")
    public void electLeader() {
        List<String> instances = discoveryService.getInstances();
        String selfUrl = getSelfUrl();
        String currentLeader = instances.stream().min(String::compareTo).orElse(null);

        boolean wasLeader = isLeader.get();
        isLeader.set(selfUrl.equals(currentLeader));

        if (isLeader.get() && !wasLeader) {
            logger.info("This instance is now the leader: {}", selfUrl);
            notifyHAProxy(selfUrl);
        }
    }

    /**
     * Adds new message to the distributed queue.
     *
     * Flow:
     * 1. Validates and initializes message metadata (ID and timestamp)
     * 2. If instance is leader:
     *    - Assigns sequence number for ordering
     *    - Adds to local processing queue
     *    - Triggers queue processing
     * 3. If instance is follower:
     *    - Forwards message to current leader
     *    - Falls back to local processing if forward fails
     *
     * @param message The message to be added to the queue
     */
    public void addToQueue(QueueMessage message) {
        if (message.getId() == null) {
            message.setId(UUID.randomUUID().toString());
        }
        if (message.getTimestamp() == null) {
            message.setTimestamp(Instant.now());
        }

        if (isLeader.get()) {
            long seq = sequenceGenerator.incrementAndGet();
            message.setSequence(seq);
            localQueue.offer(message);
            processQueue();
        } else {
            forwardToLeader(message);
        }
    }

    /**
     * Creates SSE connection for client subscription and real-time updates.
     *
     * Flow:
     * 1. Creates new SSE emitter with configured timeout
     * 2. Registers cleanup handlers for completion and timeout
     * 3. Sends initial connection confirmation
     * 4. Adds emitter to active connections map
     *
     * Error Handling:
     * - Removes emitter if initial connection message fails
     * - Cleans up resources on timeout/completion
     *
     * @param clientId Unique identifier for the subscribing client
     * @return SseEmitter configured for the client connection
     */
    public SseEmitter subscribe(String clientId) {
        SseEmitter emitter = new SseEmitter(queueProperties.getEmitterTimeout());
        emitters.put(clientId, emitter);

        emitter.onCompletion(() -> removeEmitter(clientId));
        emitter.onTimeout(() -> removeEmitter(clientId));

        try {
            emitter.send(SseEmitter.event()
                    .id("0")
                    .name("connected")
                    .data("Connection established"));
            logger.info("Client {} subscribed successfully", clientId);
        } catch (IOException e) {
            logger.warn("Failed to send initial connection event to client {}: {}", clientId, e.getMessage());
            removeEmitter(clientId);
        }

        return emitter;
    }

    /**
     * Asynchronously processes the queue if this instance is the leader.
     * Orders messages by sequence number before processing.
     */
    @Async
    public void processQueue() {
        if (!isLeader.get()) {
            return;
        }

        List<QueueMessage> messagesToProcess = new ArrayList<>();
        localQueue.drainTo(messagesToProcess);

        if (messagesToProcess.isEmpty()) {
            return;
        }

        messagesToProcess.sort(Comparator.comparing(QueueMessage::getSequence));

        for (QueueMessage message : messagesToProcess) {
            if (!processedMessageIds.contains(message.getId())) {
                orderedBuffer.put(message.getTimestamp(), message);
            }
        }

        scheduler.schedule(
                this::processOrderedBuffer,
                queueProperties.getProcessingDelay(),
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Acknowledges message processing by a specific client.
     *
     * Flow:
     * 1. Validates message and client IDs
     * 2. Records message as processed for the client
     * 3. Checks if all subscribed clients have processed
     * 4. If fully processed:
     *    - Updates message status
     *    - Adds to processed messages set
     *    - Removes from unconfirmed tracking
     *
     * Message Lifecycle:
     * - Unconfirmed → Partially Confirmed → Fully Confirmed
     *
     * @param messageId ID of the message being acknowledged
     * @param clientId ID of the client acknowledging the message
     * @return The acknowledged message or null if not found
     * @throws IllegalArgumentException if messageId or clientId is null
     */
    public QueueMessage acknowledgeMessage(String messageId, String clientId) {
        if (messageId == null || clientId == null) {
            throw new IllegalArgumentException("Message ID and Client ID cannot be null");
        }

        Set<String> clientMessages = clientProcessedMessages
                .computeIfAbsent(clientId, k -> ConcurrentHashMap.newKeySet());

        clientMessages.add(messageId);
        QueueMessage message = unconfirmedMessages.get(messageId);

        logger.debug("Client {} requested acknowledgment for message - ID: {}",
                clientId, messageId);

        boolean allAcknowledged = emitters.keySet().stream()
                .allMatch(cId -> clientProcessedMessages
                        .getOrDefault(cId, Collections.emptySet())
                        .contains(messageId));

        if (allAcknowledged && message != null) {
            message.setProcessed(true);
            processedMessageIds.add(messageId);
            unconfirmedMessages.remove(messageId);

            logger.debug("Client {} completed processing - ID: {}, Content: {}, Processed: {}",
                    clientId, messageId, message.getContent(), message.isProcessed());
            return message;
        }

        return message;
    }

    /**
     * Handles notification of processed messages from other instances.
     *
     * Flow:
     * 1. Checks if message was already processed locally
     * 2. If new message:
     *    - Notifies local subscribers
     *    - Marks as processed to prevent duplicates
     * 3. Logs notification status for debugging
     *
     * Usage:
     * - Maintains consistency across instances
     * - Prevents duplicate message processing
     * - Ensures all instances are aware of processed messages
     *
     * @param message The processed message being notified about
     */
    public void receiveNotification(QueueMessage message) {
        if (processedMessageIds.contains(message.getId())) {
            logger.debug("Message {} already processed, ignoring notification", message.getId());
            return;
        }

        logger.debug("Received notification for message: {}", message.getId());
        notifySubscribers(message);
        processedMessageIds.add(message.getId());
    }

    /**
     * Scheduled cleanup of expired and processed messages.
     *
     * Flow:
     * 1. Calculates expiry threshold based on configuration
     * 2. Removes expired messages from:
     *    - Processed messages set
     *    - Unconfirmed messages map
     *    - Ordered buffer
     *
     * Cleanup Criteria:
     * - Message age exceeds configured timeout
     * - Message is fully processed
     * - Message is no longer in unconfirmed state
     */
    @Scheduled(fixedRateString = "${queue.unconfirmed-message-retry-interval}")
    public void cleanup() {
        Instant expiryTime = Instant.now()
                .minus(Duration.ofMillis(queueProperties.getUnconfirmedMessageTimeout()));

        processedMessageIds.removeIf(id -> {
            QueueMessage msg = unconfirmedMessages.get(id);
            return msg == null || msg.getTimestamp().isBefore(expiryTime);
        });

        unconfirmedMessages.entrySet().removeIf(entry ->
                entry.getValue().getTimestamp().isBefore(expiryTime));

        orderedBuffer.headMap(expiryTime).clear();
    }

    /**
     * Sends periodic keepalive messages to connected clients.
     */
    @Scheduled(fixedRateString = "${queue.keep-alive-interval}")
    public void sendKeepAlive() {
        if (emitters.isEmpty()) {
            return;
        }

        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .id(String.valueOf(System.currentTimeMillis()))
                .name("keepalive")
                .data("ping");

        List<String> deadClients = new ArrayList<>();

        emitters.forEach((clientId, emitter) -> {
            try {
                emitter.send(event);
                logger.debug("Sent keepalive to client {}", clientId);
            } catch (Exception e) {
                logger.warn("Failed to send keepalive to client {}", clientId);
                deadClients.add(clientId);
            }
        });

        deadClients.forEach(this::removeEmitter);
    }

    /**
     * Notifies all subscribed clients about a new message.
     * Handles failed notifications and client cleanup.
     */
    private void notifySubscribers(QueueMessage message) {
        if (emitters.isEmpty()) {
            return;
        }

        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .id(message.getId())
                .name("message")
                .data(message.toJson(), MediaType.APPLICATION_JSON);

        List<String> failedClients = new ArrayList<>();

        emitters.forEach((clientId, emitter) -> {
            Set<String> clientMessages = clientProcessedMessages
                    .computeIfAbsent(clientId, k -> ConcurrentHashMap.newKeySet());

            if (!clientMessages.contains(message.getId())) {
                try {
                    emitter.send(event);
                    logger.debug("Sent message {} to client {}", message.getId(), clientId);
                } catch (Exception e) {
                    logger.warn("Failed to send message to client {}: {}", clientId, e.getMessage());
                    failedClients.add(clientId);
                }
            }
        });

        failedClients.forEach(this::removeEmitter);
    }

    /**
     * Removes client emitter and cleans up resources.
     */
    private void removeEmitter(String clientId) {
        SseEmitter emitter = emitters.remove(clientId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                logger.warn("Error completing emitter for client {}", clientId);
            }
        }
    }

    /**
     * Forwards message to current leader instance for processing.
     *
     * Flow:
     * 1. Identifies current leader from instance list
     * 2. Attempts to forward message via HTTP POST
     * 3. On failure:
     *    - Logs error
     *    - Falls back to local processing
     *    - Adds to local queue
     *    - Triggers queue processing
     *
     * Error Handling:
     * - Network failures
     * - Leader unavailability
     * - Invalid leader state
     *
     * @param message Message to be forwarded to leader
     */
    private void forwardToLeader(QueueMessage message) {
        try {
            String leaderUrl = discoveryService.getInstances().stream()
                    .min(String::compareTo)
                    .orElseThrow(() -> new RuntimeException("No leader found"));

            restTemplate.postForObject(leaderUrl + "/queue/add", message, String.class);
            logger.debug("Forwarded message {} to leader at {}", message.getId(), leaderUrl);
        } catch (Exception e) {
            logger.error("Failed to forward message to leader: {}", e.getMessage());
            localQueue.offer(message);
            processQueue();
        }
    }


    /**
     * Notifies HAProxy about leader status change via Runtime API.
     *
     * Flow:
     * 1. Constructs HAProxy Runtime API URL with server identifier
     * 2. Executes GET request to update server state to READY
     * 3. Logs any communication failures without breaking operation
     *
     * HTTP Request Format:
     * GET http://[haproxyUrl][adminPath]?set-server app_servers/[serverUrl] state READY
     *
     * Example:
     * GET http://haproxy:8404/admin?set-server app_servers/server1 state READY
     *
     * @param serverUrl The URL of the server to be marked as ready
     */
    private void notifyHAProxy(String serverUrl) {
        try {
            String url = String.format("%s%s?set-server app_servers/%s state READY",
                    queueProperties.getHaproxyUrl(),
                    queueProperties.getHaproxyAdminPath(),
                    serverUrl);
            restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            logger.warn("Failed to notify HAProxy about new leader: {}", e.getMessage());
        }
    }

    /**
     * Processes messages from ordered buffer while maintaining sequence integrity.
     * Only executed by the current leader instance.
     *
     * Flow:
     * 1. Verifies instance is still leader
     * 2. Processes oldest message from ordered buffer
     * 3. Checks for already processed messages to avoid duplicates
     * 4. Updates processing status and notifies clients
     * 5. Handles failures without losing messages
     *
     * Message Processing Steps:
     * - Validate message hasn't been processed
     * - Add to unconfirmed messages tracking
     * - Notify subscribed clients
     * - Notify other instances
     * - Mark as processed
     *
     * Error Handling:
     * - Stops processing on first error to maintain order
     * - Keeps failed messages in buffer for retry
     * - Logs processing errors for monitoring
     */
    private void processOrderedBuffer() {
        if (!isLeader.get()) {
            return;
        }

        while (!orderedBuffer.isEmpty()) {
            Map.Entry<Instant, QueueMessage> entry = orderedBuffer.firstEntry();
            if (entry == null) continue;

            QueueMessage message = entry.getValue();

            if (processedMessageIds.contains(message.getId())) {
                orderedBuffer.remove(entry.getKey());
                continue;
            }

            try {
                processMessage(message);
                processedMessageIds.add(message.getId());
                orderedBuffer.remove(entry.getKey());
            } catch (Exception e) {
                logger.error("Error processing message {}: {}", message.getId(), e.getMessage());
                break;
            }
        }
    }

    /**
     * Processes individual messages while maintaining order and consistency.
     *
     * Flow:
     * 1. Logs processing attempt
     * 2. Adds to unconfirmed messages tracking
     * 3. Notifies all active subscribers
     * 4. Notifies other service instances
     * 5. Marks message as processed
     *
     * State Transitions:
     * - Queued → Processing → Processed
     * - Maintains message state across all instances
     *
     * @param message The message to be processed
     */
    private void processMessage(QueueMessage message) {
        logger.debug("Processing message: {}", message.getId());
        unconfirmedMessages.put(message.getId(), message);
        notifySubscribers(message);
        notifyOtherInstances(message);
        message.setProcessed(true);
    }

    /**
     * Notifies other service instances about processed messages.
     *
     * Flow:
     * 1. Gets current instance URL
     * 2. Filters out self from instance list
     * 3. Sends notification to each instance via HTTP POST
     * 4. Logs success/failure for each notification
     *
     * Error Handling:
     * - Individual instance failures don't stop notification process
     * - Failed notifications are logged for monitoring
     *
     * @param message The processed message to notify about
     */
    private void notifyOtherInstances(QueueMessage message) {
        String selfUrl = getSelfUrl();

        discoveryService.getInstances().stream()
                .filter(instance -> !instance.equals(selfUrl))
                .forEach(instance -> {
                    try {
                        restTemplate.postForObject(instance + "/queue/notify", message, String.class);
                        logger.debug("Notified instance {} about message {}", instance, message.getId());
                    } catch (Exception e) {
                        logger.warn("Failed to notify instance {} about message {}: {}",
                                instance, message.getId(), e.getMessage());
                    }
                });
    }
}