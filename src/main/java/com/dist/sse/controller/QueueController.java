package com.dist.sse.controller;

import com.dist.sse.services.QueueMessage;
import com.dist.sse.services.QueueService;
import com.dist.sse.services.DiscoveryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * REST Controller for managing distributed message queue operations.
 * Handles message queuing, SSE subscriptions, and instance discovery.
 */
@RestController
@RequestMapping("/queue")  // Base path for all endpoints in this controller
public class QueueController {
    // Logger for this class
    private static final Logger logger = LoggerFactory.getLogger(QueueController.class);

    // Service dependencies injected through constructor
    private final QueueService queueService;
    private final DiscoveryService discoveryService;

    // Constructor injection of required services
    public QueueController(QueueService queueService, DiscoveryService discoveryService) {
        this.queueService = queueService;
        this.discoveryService = discoveryService;
    }

    /**
     * Subscribe endpoint - Creates SSE connection for real-time message streaming
     * URL: GET /queue/subscribe
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam(required = false) String clientId) {
        // Generate UUID if clientId not provided
        if (clientId == null) {
            clientId = UUID.randomUUID().toString();
        }
        return queueService.subscribe(clientId);
    }

    /**
     * Message addition endpoint
     * URL: POST /queue/add
     */
    @PostMapping("/add")
    public ResponseEntity<String> addToQueue(@RequestBody QueueMessage message) {
        try {
            queueService.addToQueue(message);
            return ResponseEntity.ok("Message added to queue");
        } catch (Exception e) {
            logger.error("Error adding message to queue", e);
            return ResponseEntity.badRequest().body("Error adding message to queue: " + e.getMessage());
        }
    }

    /**
     * Message acknowledgment endpoint
     * URL: POST /queue/ack/{messageId}
     */
    @PostMapping(value = "/ack/{messageId}")
    public ResponseEntity<?> acknowledgeMessage(
            @PathVariable String messageId,
            @RequestParam String clientId) {
        try {
            QueueMessage message = queueService.acknowledgeMessage(messageId, clientId);
            if (message != null) {
                return ResponseEntity.ok(message);
            } else {
                return ResponseEntity.ok().build();
            }
        } catch (Exception e) {
            logger.error("Error acknowledging message: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Error acknowledging message: " + e.getMessage());
        }
    }

    /**
     * Notification endpoint for inter-instance communication
     * URL: POST /queue/notify
     */
    @PostMapping("/notify")
    public ResponseEntity<String> notify(@RequestBody QueueMessage message) {
        try {
            queueService.receiveNotification(message);
            return ResponseEntity.ok("Notification received");
        } catch (Exception e) {
            logger.error("Error processing notification", e);
            return ResponseEntity.badRequest().body("Error processing notification: " + e.getMessage());
        }
    }

    /**
     * Instance registration endpoint for service discovery
     * URL: POST /queue/register
     */
    @PostMapping("/register")
    public ResponseEntity<String> registerInstance(@RequestBody String instanceUrl) {
        try {
            discoveryService.addInstance(instanceUrl);
            return ResponseEntity.ok("Instance registered");
        } catch (Exception e) {
            logger.error("Error registering instance", e);
            return ResponseEntity.badRequest().body("Error registering instance: " + e.getMessage());
        }
    }

    /**
     * Health check endpoint
     * URL: GET /queue/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Healthy");
    }
}