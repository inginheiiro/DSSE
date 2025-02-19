package com.dist.sse.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service responsible for managing service discovery in a distributed system.
 * Implements a fire-and-forget approach without message confirmation.
 * Uses CopyOnWriteArrayList for thread-safe operations without explicit synchronization.
 */
@Service
public class DiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryService.class);

    /**
     * Thread-safe list to store active service instances
     */
    private final List<String> instances = new CopyOnWriteArrayList<>();

    @Value("${SERVER_PORT:8080}")
    private String serverPort;

    @Value("${app.host:localhost}")
    private String appHost;

    private final RestTemplate restTemplate;

    /**
     * Constructs the DiscoveryService with a RestTemplate for HTTP operations
     * @param restTemplate The REST template to use for health checks
     */
    public DiscoveryService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Registers the current instance in the service registry.
     * Runs at a fixed interval defined by discovery.self-register-interval property.
     */
    @Scheduled(fixedRateString = "${discovery.self-register-interval}")
    public void registerSelf() {
        String selfUrl = String.format("http://%s:%s", appHost, serverPort);
        if (!instances.contains(selfUrl)) {
            instances.add(selfUrl);
            logger.info("Registered self: {}", selfUrl);
        }
        logCurrentInstances();
    }

    /**
     * Performs health checks on all registered instances.
     * Removes unreachable instances from the registry.
     * Runs at a fixed interval defined by discovery.check-interval property.
     */
    @Scheduled(fixedRateString = "${discovery.check-interval}")
    public void checkInstances() {
        logger.info("Starting instance health check...");
        instances.removeIf(instance -> {
            try {
                restTemplate.getForEntity(instance + "/health", String.class);
                logger.debug("Instance {} is healthy", instance);
                return false;
            } catch (Exception e) {
                logger.warn("Instance {} is unreachable and will be removed: {}",
                        instance, e.getMessage());
                return true;
            }
        });
        logCurrentInstances();
    }

    /**
     * Returns all currently registered service instances
     * @return List of instance URLs
     */
    public List<String> getInstances() {
        return instances;
    }

    /**
     * Registers a new service instance
     * @param instance URL of the new instance to register
     */
    public void addInstance(String instance) {
        if (!instances.contains(instance)) {
            instances.add(instance);
            logger.info("New instance added: {}", instance);
            logCurrentInstances();
        }
    }

    /**
     * Returns the configured application host
     * @return The application host string
     */
    public String getAppHost() {
        return appHost;
    }

    /**
     * Logs the current state of registered instances
     */
    private void logCurrentInstances() {
        logger.info("Current active instances:");
        for (int i = 0; i < instances.size(); i++) {
            logger.info("  {}. {}", i + 1, instances.get(i));
        }
    }
}