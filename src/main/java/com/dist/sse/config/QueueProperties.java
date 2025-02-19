package com.dist.sse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "queue")
public class QueueProperties {
    private long processingDelay = 1000;
    private long messageSendDelay = 500;
    private long unconfirmedMessageRetryInterval = 60000;
    private long unconfirmedMessageTimeout = 300000;
    private long keepAliveInterval = 30000;
    private long leaderElectionInterval = 5000;
    private int initialQueueCapacity = 11;
    private String haproxyUrl = "http://haproxy:8404";
    private String haproxyAdminPath = "/admin";
    private long emitterTimeout = 3600000;

    public long getEmitterTimeout() {
        return emitterTimeout;
    }

    public void setEmitterTimeout(long emitterTimeout) {
        this.emitterTimeout = emitterTimeout;
    }

    // Getters and setters
    public long getProcessingDelay() {
        return processingDelay;
    }

    public void setProcessingDelay(long processingDelay) {
        this.processingDelay = processingDelay;
    }

    public long getMessageSendDelay() {
        return messageSendDelay;
    }

    public void setMessageSendDelay(long messageSendDelay) {
        this.messageSendDelay = messageSendDelay;
    }

    public long getUnconfirmedMessageRetryInterval() {
        return unconfirmedMessageRetryInterval;
    }

    public void setUnconfirmedMessageRetryInterval(long unconfirmedMessageRetryInterval) {
        this.unconfirmedMessageRetryInterval = unconfirmedMessageRetryInterval;
    }

    public long getUnconfirmedMessageTimeout() {
        return unconfirmedMessageTimeout;
    }

    public void setUnconfirmedMessageTimeout(long unconfirmedMessageTimeout) {
        this.unconfirmedMessageTimeout = unconfirmedMessageTimeout;
    }

    public long getKeepAliveInterval() {
        return keepAliveInterval;
    }

    public void setKeepAliveInterval(long keepAliveInterval) {
        this.keepAliveInterval = keepAliveInterval;
    }

    public long getLeaderElectionInterval() {
        return leaderElectionInterval;
    }

    public void setLeaderElectionInterval(long leaderElectionInterval) {
        this.leaderElectionInterval = leaderElectionInterval;
    }

    public int getInitialQueueCapacity() {
        return initialQueueCapacity;
    }

    public void setInitialQueueCapacity(int initialQueueCapacity) {
        this.initialQueueCapacity = initialQueueCapacity;
    }

    public String getHaproxyUrl() {
        return haproxyUrl;
    }

    public void setHaproxyUrl(String haproxyUrl) {
        this.haproxyUrl = haproxyUrl;
    }

    public String getHaproxyAdminPath() {
        return haproxyAdminPath;
    }

    public void setHaproxyAdminPath(String haproxyAdminPath) {
        this.haproxyAdminPath = haproxyAdminPath;
    }
}