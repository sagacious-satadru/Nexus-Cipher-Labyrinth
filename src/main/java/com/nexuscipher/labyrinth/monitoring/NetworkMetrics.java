package com.nexuscipher.labyrinth.monitoring;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maintains real-time performance metrics for our network.
 * Think of it as a dashboard that tracks vital statistics
 * like a car's instrument panel.
 */
public class NetworkMetrics {
    private final AtomicLong averageLatency;
    private final AtomicInteger messageThroughput;
    private final AtomicLong errorRate;
    private final AtomicInteger activeConnections;

    public NetworkMetrics() {
        this.averageLatency = new AtomicLong(0);
        this.messageThroughput = new AtomicInteger(0);
        this.errorRate = new AtomicLong(0);
        this.activeConnections = new AtomicInteger(0);
    }

    public void updateMetrics(int connections, long latency, int throughput, long errors) {
        activeConnections.set(connections);
        averageLatency.set(latency);
        messageThroughput.set(throughput);
        errorRate.set(errors);
    }

    // Getters
    public long getAverageLatency() { return averageLatency.get(); }
    public int getMessageThroughput() { return messageThroughput.get(); }
    public long getErrorRate() { return errorRate.get(); }
    public int getActiveConnections() { return activeConnections.get(); }
}