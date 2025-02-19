Distributed Server-Sent Events (SSE) Queue System
=================================================

A scalable and fault-tolerant distributed architecture for handling Server-Sent Events (SSE) with guaranteed message delivery and ordering. Built with Spring Boot, this system provides real-time message streaming with acknowledgment tracking and automatic retry mechanisms.

Core Features
-------------

### Message Processing

* Distributed leader-based processing
* Strict message ordering with sequence numbers
* Timestamp-based message handling
* Guaranteed message delivery with acknowledgments
* Automatic message retry for undelivered messages

### High Availability

* Automatic leader election
* Instance health monitoring
* Automatic failover handling
* Load balancing with HAProxy
* Session persistence for SSE connections

### Performance Optimizations

* Asynchronous message processing
* Concurrent message handling
* Connection pooling
* Response compression
* Configurable timeouts and intervals

Technical Architecture
----------------------

### Components

1. **QueueService**
   * Manages message queue operations
   * Handles message ordering and sequencing
   * Processes acknowledgments
   * Manages message retry logic
   * Maintains delivery tracking

2. **DiscoveryService**
   * Handles service instance registration
   * Manages leader election process
   * Monitors instance health
   * Maintains service registry

3. **HAProxy Layer**
   * Load balances incoming requests
   * Maintains SSE connection persistence
   * Performs health checks
   * Handles failover routing

### Message Flow

1. Client sends message to any instance
2. Non-leader instances forward to leader
3. Leader assigns sequence number
4. Message stored in ordered buffer
5. Message broadcast to subscribers
6. Clients acknowledge receipt
7. Message marked as processed
8. Unacknowledged messages retried

Usage
-----

### Prerequisites

* Java 11 or higher
* Maven 3.6+
* Docker and Docker Compose
* Python 3.x (for testing tools)

### Configuration

Key application properties (application.properties):

```properties
# Server
server.port=${SERVER_PORT:8080}
server.compression.enabled=true

# Queue Configuration
queue.leader-election-interval=5000
queue.unconfirmed-message-timeout=300000
queue.keep-alive-interval=30000

# Discovery
discovery.check-interval=30000
discovery.self-register-interval=10000
```

HAProxy configuration (haproxy.cfg):

```
frontend http-in
bind *:80
default_backend app_servers

backend app_servers
balance source
cookie SERVERID insert indirect nocache
server app1 app1:8080 check
server app2 app2:8080 check
```

### Running the System

1. Build the application:
```mvn clean package```

2. Start the system:
```docker compose up --build```

3. Stop and cleanup:
```docker compose down -v```

### API Endpoints

1. Subscribe to Events:
```
   # Using provided script
./subscribe.sh http://localhost:80

# Direct curl command
curl -N http://localhost:80/queue/subscribe
```

2. Send Messages:
```
   # Single message
curl -X POST http://localhost:80/queue/add \
-H "Content-Type: application/json" \
-d '{"content":"Test message"}'

# Load testing
python parallelGenerator.py http://localhost:80/queue/add 1000 50
```

3. Acknowledge Messages:
```
   curl -X POST http://localhost:80/queue/ack/{messageId}?clientId={clientId}
```

Testing and Monitoring
----------------------

### Health Checks


# Instance health
```
curl http://localhost:80/queue/health
```

# HAProxy stats
```
curl http://localhost:8404
```


### Load Testing

```# Generate parallel messages
python parallelGenerator.py [URL] [MESSAGE_COUNT] [PARALLEL_REQUESTS]

# Example
python parallelGenerator.py http://localhost:80/queue/add 1000 50
```

Implementation Details
----------------------

### Message Ordering

```
private final AtomicLong sequenceGenerator = new AtomicLong(0);
private final PriorityBlockingQueue<QueueMessage> localQueue;
```

### Leader Election

```
@Scheduled(fixedRateString = "${queue.leader-election-interval}")
public void electLeader() {
List<String> instances = discoveryService.getInstances();

String currentLeader = instances.stream()
.min(String::compareTo)
.orElse(null);

isLeader.set(selfUrl.equals(currentLeader));
}
```

Production Considerations
-------------------------

### Scaling

* Horizontal scaling with additional instances
* HAProxy configuration updates for new instances
* Memory requirements for message tracking
* Network capacity for SSE connections

