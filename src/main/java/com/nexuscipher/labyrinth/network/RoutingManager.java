package com.nexuscipher.labyrinth.network;

import com.nexuscipher.labyrinth.core.PeerConnection;
import com.nexuscipher.labyrinth.crypto.QuantumResistantCrypto;
import com.nexuscipher.labyrinth.network.protocol.Message;
import com.nexuscipher.labyrinth.network.protocol.RoutingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the routing of messages through the peer-to-peer network.
 * Implements multiple routing strategies and maintains routing tables
 * for efficient message delivery while ensuring quantum-resistant security.
 */
public class RoutingManager {
    private static final Logger logger = LoggerFactory.getLogger(RoutingManager.class);
    private static final int MAX_HOPS = 10;  // Maximum number of hops for a message
    private static final int MAX_PATHS = 3;  // Maximum paths for multipath routing

    private final String nodeId;
    private final QuantumResistantCrypto crypto;
    private final ConnectionManager connectionManager;
    private final Map<String, Set<String>> routingTable;  // NodeId -> Set of next hop options
    private final Map<String, AtomicInteger> messageCount;  // Message ID -> Count (for multipath)

    // Cache to prevent message loops and duplicates
    private final Map<String, Long> recentMessages;
    private static final long MESSAGE_CACHE_TIMEOUT = 300000; // 5 minutes

    public RoutingManager(String nodeId,
                          QuantumResistantCrypto crypto,
                          ConnectionManager connectionManager) {
        this.nodeId = nodeId;
        this.crypto = crypto;
        this.connectionManager = connectionManager;
        this.routingTable = new ConcurrentHashMap<>();
        this.messageCount = new ConcurrentHashMap<>();
        this.recentMessages = new ConcurrentHashMap<>();
    }

    /**
     * Routes a message to its target destination using the most appropriate strategy.
     * This is the main entry point for routing new messages through the network.
     */
    public void routeMessage(String targetNodeId, Message message) {
        // Prevent routing loops by checking recent message cache
        if (recentMessages.containsKey(message.getMessageId())) {
            return;
        }

        // Update recent messages cache
        recentMessages.put(message.getMessageId(), System.currentTimeMillis());
        cleanupMessageCache();

        // Handle local delivery
        if (targetNodeId.equals(nodeId)) {
            processLocalMessage(message);
            return;
        }

        // Create routing envelope for the message
        RoutingMessage routingMessage = new RoutingMessage(
                message.getSenderId(),
                targetNodeId,
                message.getMessageId(),
                message,
                determineRoutingType(targetNodeId)
        );

        routingMessage.addHop(nodeId);  // Record our node in the route

        // Route based on determined strategy
        switch (routingMessage.getRoutingType()) {
            case DIRECT:
                routeDirect(routingMessage);
                break;
            case FLOOD:
                floodMessage(routingMessage, null);
                break;
            case MULTIPATH:
                routeMultipath(routingMessage);
                break;
            case DISCOVER_ROUTE:
                handleRouteDiscovery(routingMessage);
                break;
        }
    }


    public void handleRoutingMessage(RoutingMessage message, MessageHandler sourceHandler) {
        // Don't process if we've seen this message too many times
        if (message.getHopCount() >= MAX_HOPS) {
            logger.warn("Message exceeded maximum hop count: {}", message.getMessageId());
            return;
        }

        // If we're the target, process the message
        if (message.getTargetNodeId().equals(nodeId)) {
            processIncomingMessage(message);
            return;
        }

        // Add ourselves to the route
        message.addHop(nodeId);

        // Handle based on routing type
        switch (message.getRoutingType()) {
            case DIRECT:
                routeDirect(message);
                break;
            case FLOOD:
                floodMessage(message, sourceHandler);
                break;
            case MULTIPATH:
                routeMultipath(message);
                break;
            case DISCOVER_ROUTE:
                handleRouteDiscovery(message);
                break;
        }
    }

    private void routeDirect(RoutingMessage message) {
        Set<String> nextHops = routingTable.get(message.getTargetNodeId());
        if (nextHops != null && !nextHops.isEmpty()) {
            // Get the first available next hop
            String nextHop = nextHops.iterator().next();
            forwardMessage(message, nextHop);
        } else {
            logger.warn("No route to target: {}", message.getTargetNodeId());
        }
    }

//    private void floodMessage(RoutingMessage message, MessageHandler sourceHandler) {
//        // Forward to all peers except the one we received it from
//        connectionManager.getAllPeers().forEach(peer -> {
//            if (!message.hasVisited(peer.getPeerId()) &&
//                    !peer.getPeerId().equals(sourceHandler.getSocket().getInetAddress().getHostAddress())) {
//                forwardMessage(message, peer.getPeerId());
//            }
//        });
//    }
    private void floodMessage(RoutingMessage message, MessageHandler sourceHandler) {
        // Forward to all peers except the source (if provided)
        connectionManager.getAllPeers().forEach(peer -> {
            if (!message.hasVisited(peer.getPeerId()) &&
                    (sourceHandler == null ||
                            !peer.getPeerId().equals(sourceHandler.getSocket().getInetAddress().getHostAddress()))) {
                forwardMessage(message, peer.getPeerId());
            }
        });
    }

    private void routeMultipath(RoutingMessage message) {
        Set<String> nextHops = routingTable.get(message.getTargetNodeId());
        if (nextHops != null && !nextHops.isEmpty()) {
            // Use up to MAX_PATHS different paths
            nextHops.stream()
                    .limit(MAX_PATHS)
                    .forEach(nextHop -> forwardMessage(message, nextHop));
        }
    }

    private void handleRouteDiscovery(RoutingMessage message) {
        // Update routing table with the path this message took
        List<String> route = message.getRoute();
        for (int i = 0; i < route.size() - 1; i++) {
            String currentNode = route.get(i);
            String nextHop = route.get(i + 1);
            routingTable.computeIfAbsent(currentNode, k -> new HashSet<>()).add(nextHop);
        }
    }

    private void forwardMessage(RoutingMessage message, String nextHopId) {
        try {
            connectionManager.sendMessage(message, nextHopId);
            logger.debug("Forwarded message {} to next hop {}",
                    message.getMessageId(), nextHopId);
        } catch (Exception e) {
            logger.error("Failed to forward message to {}", nextHopId, e);
            // Update routing table to remove failed route
            routingTable.getOrDefault(message.getTargetNodeId(), new HashSet<>())
                    .remove(nextHopId);
        }
    }

    private void processIncomingMessage(RoutingMessage message) {
        try {
            // Decrypt and process the message
            // This would integrate with your application layer
            logger.info("Received message {} via {} hops",
                    message.getMessageId(), message.getHopCount());
        } catch (Exception e) {
            logger.error("Failed to process incoming message", e);
        }
    }

    // Method to update routing table based on network changes
    public void updateRoute(String targetNodeId, String nextHopId) {
        routingTable.computeIfAbsent(targetNodeId, k -> new HashSet<>()).add(nextHopId);
        logger.debug("Updated route to {} via {}", targetNodeId, nextHopId);
    }

    public void removeRoute(String targetNodeId, String nextHopId) {
        Set<String> routes = routingTable.get(targetNodeId);
        if (routes != null) {
            routes.remove(nextHopId);
            if (routes.isEmpty()) {
                routingTable.remove(targetNodeId);
            }
            logger.debug("Removed route to {} via {}", targetNodeId, nextHopId);
        }
    }

    /**
     * Determines the best routing strategy based on network knowledge
     */
    private RoutingMessage.RoutingType determineRoutingType(String targetNodeId) {
        if (routingTable.containsKey(targetNodeId)) {
            return RoutingMessage.RoutingType.DIRECT;
        } else {
            return RoutingMessage.RoutingType.FLOOD;
        }
    }

    /**
     * Processes a message intended for this node
     */
    private void processLocalMessage(Message message) {
        logger.info("Processing local message: {}", message.getMessageId());
        // Implementation depends on message type and application needs
    }

    /**
     * Removes expired entries from the recent messages cache
     */
    private void cleanupMessageCache() {
        long currentTime = System.currentTimeMillis();
        recentMessages.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > MESSAGE_CACHE_TIMEOUT);
    }

    /**
     * Performs cleanup when shutting down the routing manager
     */
    public void shutdown() {
        routingTable.clear();
        messageCount.clear();
        recentMessages.clear();
        logger.info("RoutingManager shutdown complete");
    }
}