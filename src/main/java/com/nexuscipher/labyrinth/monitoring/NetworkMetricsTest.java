package com.nexuscipher.labyrinth.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NetworkMetrics - our network performance tracking system.
 * Each test verifies a specific aspect of our metrics collection and reporting.
 */
public class NetworkMetricsTest {
    private NetworkMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new NetworkMetrics();
    }

    @Test
    @DisplayName("Should initialize with zero values")
    void testInitialization() {
        assertEquals(0, metrics.getAverageLatency(),
                "Initial latency should be zero");
        assertEquals(0, metrics.getMessageThroughput(),
                "Initial throughput should be zero");
        assertEquals(0, metrics.getErrorRate(),
                "Initial error rate should be zero");
        assertEquals(0, metrics.getActiveConnections(),
                "Initial connections should be zero");
    }

    @Test
    @DisplayName("Should properly update all metrics")
    void testMetricsUpdate() {
        metrics.updateMetrics(5, 100L, 1000, 50L);

        assertEquals(5, metrics.getActiveConnections(),
                "Should update active connections");
        assertEquals(100L, metrics.getAverageLatency(),
                "Should update average latency");
        assertEquals(1000, metrics.getMessageThroughput(),
                "Should update message throughput");
        assertEquals(50L, metrics.getErrorRate(),
                "Should update error rate");
    }

    @Test
    @DisplayName("Should handle concurrent updates safely")
    void testConcurrentUpdates() {
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            final int value = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    metrics.updateMetrics(value, value, value, value);
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for completion
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                fail("Thread interrupted: " + e.getMessage());
            }
        }

        // Verify metrics remain valid after concurrent updates
        assertTrue(metrics.getActiveConnections() >= 0,
                "Connections should never be negative");
        assertTrue(metrics.getAverageLatency() >= 0,
                "Latency should never be negative");
        assertTrue(metrics.getMessageThroughput() >= 0,
                "Throughput should never be negative");
        assertTrue(metrics.getErrorRate() >= 0,
                "Error rate should never be negative");
    }
}