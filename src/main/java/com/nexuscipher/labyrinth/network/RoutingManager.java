package com.nexuscipher.labyrinth.network;

import com.nexuscipher.labyrinth.core.PeerConnection;
import com.nexuscipher.labyrinth.crypto.QuantumResistantCrypto;
import com.nexuscipher.labyrinth.network.protocol.RoutingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RoutingManager {
    private static final Logger logger = LoggerFactory.getLogger(RoutingManager.class);
    private static final int MAX_HOPS = 10;  // Maximum number of hops for a message
    private static final int MAX_PATHS = 3;  // Maximum paths for multipath routing

    private final String nodeId;
    private final QuantumResistantCrypto crypto;
    private final ConnectionManager connectionManager;
    private final Map<String, Set<String>> routingTable;  // NodeId -> Set of next hop options
    private final Map<String, AtomicInteger> messageCount;  // Message ID -> Count (for multipath)

    public RoutingManager(String nodeId,
                          QuantumResistantCrypto crypto,
                          ConnectionManager connectionManager) {
        this.nodeId = nodeId;
        this.crypto = crypto;
        this.connectionManager = connectionManager;
        this.routingTable = new ConcurrentHashMap<>();
        this.messageCount = new ConcurrentHashMap<>();
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

    private void floodMessage(RoutingMessage message, MessageHandler sourceHandler) {
        // Forward to all peers except the one we received it from
        connectionManager.getAllPeers().forEach(peer -> {
            if (!message.hasVisited(peer.getPeerId()) &&
                    !peer.getPeerId().equals(sourceHandler.getSocket().getInetAddress().getHostAddress())) {
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
}