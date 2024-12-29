// Current NetworkMonitor.java

package com.nexuscipher.labyrinth.monitoring;

import com.nexuscipher.labyrinth.core.PeerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nexuscipher.labyrinth.network.ConnectionManager;

import java.util.*;
import java.util.concurrent.*;

/**
 * Acts as the central nervous system of our network, monitoring health,
 * tracking performance, and coordinating automatic recovery actions.
 * Think of it as a sophisticated diagnostics system that keeps our
 * quantum-resistant network running smoothly.
 */
public class NetworkMonitor {
    private static final Logger logger = LoggerFactory.getLogger(NetworkMonitor.class);

    // How often we check network health (in milliseconds)
    private static final long HEALTH_CHECK_INTERVAL = 5000;

    // How long before we consider a peer potentially disconnected
    private static final long PEER_TIMEOUT = 30000;

    private final String nodeId;
    private final Map<String, PeerHealth> peerHealth;
    private final ScheduledExecutorService scheduler;
    private final NetworkMetrics metrics;
    private final ConnectionManager connectionManager;
    private final Queue<NetworkEvent> eventHistory;

    public NetworkMonitor(String nodeId, ConnectionManager connectionManager) {
        this.nodeId = nodeId;
        this.connectionManager = connectionManager;
        this.peerHealth = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.metrics = new NetworkMetrics();
        this.eventHistory = new ConcurrentLinkedQueue<>();

        // Start health monitoring
        startMonitoring();
    }

    private void startMonitoring() {
        // Regular health checks
        scheduler.scheduleAtFixedRate(
                this::performHealthCheck,
                HEALTH_CHECK_INTERVAL,
                HEALTH_CHECK_INTERVAL,
                TimeUnit.MILLISECONDS
        );

        // Metrics collection
        scheduler.scheduleAtFixedRate(
                this::collectMetrics,
                1000,
                1000,
                TimeUnit.MILLISECONDS
        );

        logger.info("Network monitoring started for node: {}", nodeId);
    }

    private void performHealthCheck() {
        Collection<PeerConnection> peers = connectionManager.getAllPeers();

        peers.forEach(peer -> {
            PeerHealth health = peerHealth.computeIfAbsent(
                    peer.getPeerId(),
                    id -> new PeerHealth()
            );

            if (health.checkHealth()) {
                logger.debug("Peer {} is healthy", peer.getPeerId());
            } else {
                handleUnhealthyPeer(peer);
            }
        });
    }

    private void handleUnhealthyPeer(PeerConnection peer) {
        logger.warn("Detected unhealthy peer: {}", peer.getPeerId());

        // Record the event
        recordEvent(new NetworkEvent(
                NetworkEvent.EventType.PEER_UNHEALTHY,
                peer.getPeerId(),
                "Peer health check failed"
        ));

        // Attempt recovery
        attemptRecovery(peer);
    }

    private void attemptRecovery(PeerConnection peer) {
        logger.info("Attempting recovery for peer: {}", peer.getPeerId());

        // Implement exponential backoff for reconnection attempts
        PeerHealth health = peerHealth.get(peer.getPeerId());
        if (health.shouldAttemptReconnect()) {
            connectionManager.connectToPeer(peer.getAddress(), peer.getPort());
            health.recordReconnectionAttempt();
        } else {
            logger.error("Exceeded maximum reconnection attempts for peer: {}",
                    peer.getPeerId());
        }
    }

    public NetworkStats getNetworkStats() {
        return new NetworkStats(
                peerHealth.size(),
                metrics.getAverageLatency(),
                metrics.getMessageThroughput(),
                metrics.getErrorRate()
        );
    }

    private void collectMetrics() {
        // Update network metrics
        metrics.updateMetrics(
                connectionManager.getAllPeers().size(),
                calculateAverageLatency(),
                calculateMessageThroughput(),
                calculateErrorRate()
        );
    }

    public void recordEvent(NetworkEvent event) {
        eventHistory.offer(event);
        // Keep only recent history
        while (eventHistory.size() > 1000) {
            eventHistory.poll();
        }
    }

    public List<NetworkEvent> getRecentEvents() {
        return new ArrayList<>(eventHistory);
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.warn("Network monitor shutdown interrupted");
            Thread.currentThread().interrupt();
        }
    }

    private long calculateAverageLatency() {
        return Math.round(peerHealth.values().stream()
                .mapToLong(PeerHealth::getLatency)
                .average()
                .orElse(0));
    }

    private int calculateMessageThroughput() {
        return peerHealth.values().stream()
                .mapToInt(PeerHealth::getMessageCount)
                .sum();
    }

    private long calculateErrorRate() {
        int totalMessages = calculateMessageThroughput();
        int totalErrors = peerHealth.values().stream()
                .mapToInt(PeerHealth::getErrorCount)
                .sum();
        return totalMessages == 0 ? 0 : (totalErrors * 100L) / totalMessages;
    }
}