package com.nexuscipher.labyrinth.monitoring;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks the health status of a single peer, maintaining
 * connection statistics and managing reconnection attempts.
 */
public class PeerHealth {
    private static final int MAX_RECONNECTION_ATTEMPTS = 5;
    private static final long BACKOFF_BASE_MS = 1000;

    private final AtomicLong lastSeen;
    private final AtomicInteger reconnectionAttempts;
    private final AtomicLong latency;
    private final AtomicInteger messageCount;
    private final AtomicInteger errorCount;

    public PeerHealth() {
        this.lastSeen = new AtomicLong(System.currentTimeMillis());
        this.reconnectionAttempts = new AtomicInteger(0);
        this.latency = new AtomicLong(0);
        this.messageCount = new AtomicInteger(0);
        this.errorCount = new AtomicInteger(0);
    }

    public boolean checkHealth() {
        return System.currentTimeMillis() - lastSeen.get() < 30000;
    }

    public void updateLastSeen() {
        lastSeen.set(System.currentTimeMillis());
    }

    public boolean shouldAttemptReconnect() {
        return reconnectionAttempts.get() < MAX_RECONNECTION_ATTEMPTS;
    }

    public void recordReconnectionAttempt() {
        reconnectionAttempts.incrementAndGet();
    }

    public void resetReconnectionAttempts() {
        reconnectionAttempts.set(0);
    }

    public long getBackoffDelay() {
        return BACKOFF_BASE_MS * (1L << reconnectionAttempts.get());
    }

    // Metrics tracking
    public void recordLatency(long ms) {
        latency.set(ms);
    }

    public void incrementMessageCount() {
        messageCount.incrementAndGet();
    }

    public void incrementErrorCount() {
        errorCount.incrementAndGet();
    }

    // Getters for metrics
    public long getLatency() { return latency.get(); }
    public int getMessageCount() { return messageCount.get(); }
    public int getErrorCount() { return errorCount.get(); }
}