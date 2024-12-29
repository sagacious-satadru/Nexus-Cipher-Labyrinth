package com.nexuscipher.labyrinth.monitoring;

/**
 * Provides a snapshot of current network performance metrics.
 * Think of it as a health report that gives us quick insights
 * into how our quantum-resistant network is performing.
 */
public class NetworkStats {
    private final int activePeers;
    private final long averageLatency;
    private final int messageThroughput;
    private final long errorRate;

    public NetworkStats(int activePeers, long averageLatency,
                        int messageThroughput, long errorRate) {
        this.activePeers = activePeers;
        this.averageLatency = averageLatency;
        this.messageThroughput = messageThroughput;
        this.errorRate = errorRate;
    }

    // Getters
    public int getActivePeers() { return activePeers; }
    public long getAverageLatency() { return averageLatency; }
    public int getMessageThroughput() { return messageThroughput; }
    public long getErrorRate() { return errorRate; }
}