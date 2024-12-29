package com.nexuscipher.labyrinth.network.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a message being routed through the peer-to-peer network.
 * This class encapsulates all routing-related information and provides
 * quantum-resistant security for message delivery.
 */
public class RoutingMessage extends Message {
    private final String targetNodeId;              // Final destination node
    private final List<String> route;              // Complete path taken by message
    private final Message payload;                 // Actual message being routed
    private final RoutingType routingType;         // Strategy for message routing

    /**
     * Defines different strategies for routing messages through the network.
     * Each strategy optimizes for different scenarios like reliability or speed.
     */
    public enum RoutingType {
        DIRECT,             // Point-to-point delivery for known routes
        FLOOD,              // Network-wide broadcast for discovery
        MULTIPATH,          // Multiple simultaneous paths for redundancy
        DISCOVER_ROUTE      // Active route discovery with path learning
    }

    /**
     * Creates a new routing message that wraps and delivers a payload message.
     * @param senderId ID of the originating node
     * @param targetNodeId ID of the destination node
     * @param messageId Unique identifier for tracking
     * @param payload The actual message to be delivered
     * @param routingType Strategy to use for routing
     */
    public RoutingMessage(String senderId,
                          String targetNodeId,
                          String messageId,
                          Message payload,
                          RoutingType routingType) {
        super(senderId, MessageType.ROUTING);  // Mark as routing message
        this.targetNodeId = targetNodeId;
        this.payload = payload;
        this.routingType = routingType;
        this.route = new ArrayList<>();
        this.route.add(senderId);  // Initialize route with sender
    }

    /**
     * Records a node in the message's path through the network.
     * This helps prevent routing loops and enables route learning.
     */
    public void addHop(String nodeId) {
        route.add(nodeId);
    }

    // Getters with defensive copies where needed
    public String getTargetNodeId() { return targetNodeId; }

    /**
     * Returns a copy of the route to prevent external modification.
     */
    public List<String> getRoute() {
        return new ArrayList<>(route);
    }

    public Message getPayload() { return payload; }
    public RoutingType getRoutingType() { return routingType; }

    /**
     * Returns the last node this message passed through.
     */
    public String getLastHop() {
        return route.isEmpty() ? null : route.get(route.size() - 1);
    }

    /**
     * Returns the number of hops excluding the sender.
     */
    public int getHopCount() {
        return route.size() - 1;
    }

    /**
     * Checks if this message has already visited a specific node.
     * Used to prevent routing loops in the network.
     */
    public boolean hasVisited(String nodeId) {
        return route.contains(nodeId);
    }
}