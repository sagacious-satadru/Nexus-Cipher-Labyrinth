package com.nexuscipher.labyrinth.network.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RoutingMessage extends Message {
    private final String targetNodeId;              // Final destination
    private final List<String> route;              // Route taken so far
    private final byte[] encryptedPayload;         // Quantum-encrypted data
    private final RoutingType routingType;         // Type of routing

    public enum RoutingType {
        DIRECT,             // Point-to-point
        FLOOD,              // Flood to all neighbors
        MULTIPATH,          // Use multiple paths for redundancy
        DISCOVER_ROUTE      // Find route to target
    }

    public RoutingMessage(String senderId,
                          String targetNodeId,
                          byte[] encryptedPayload,
                          RoutingType routingType) {
        super(senderId, MessageType.DATA);
        this.targetNodeId = targetNodeId;
        this.encryptedPayload = encryptedPayload;
        this.routingType = routingType;
        this.route = new ArrayList<>();
        this.route.add(senderId);  // Add sender as first hop
    }

    public void addHop(String nodeId) {
        route.add(nodeId);
    }

    // Getters
    public String getTargetNodeId() { return targetNodeId; }
    public List<String> getRoute() { return new ArrayList<>(route); }
    public byte[] getEncryptedPayload() { return encryptedPayload; }
    public RoutingType getRoutingType() { return routingType; }
    public String getLastHop() { return route.get(route.size() - 1); }
    public int getHopCount() { return route.size() - 1; }  // Exclude sender

    // Check if message has been through a node
    public boolean hasVisited(String nodeId) {
        return route.contains(nodeId);
    }
}