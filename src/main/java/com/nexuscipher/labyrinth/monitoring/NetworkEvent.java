package com.nexuscipher.labyrinth.monitoring;

import java.time.Instant;

/**
 * Represents significant events in our network's operation.
 * Like a ship's log, it helps us track and understand what's
 * happening in our quantum-resistant mesh.
 */
public class NetworkEvent {
    public enum EventType {
        PEER_CONNECTED,
        PEER_DISCONNECTED,
        PEER_UNHEALTHY,
        ROUTE_DISCOVERED,
        ROUTE_LOST,
        RECOVERY_ATTEMPTED,
        RECOVERY_SUCCEEDED,
        RECOVERY_FAILED
    }

    private final EventType type;
    private final String peerId;
    private final String description;
    private final Instant timestamp;

    public NetworkEvent(EventType type, String peerId, String description) {
        this.type = type;
        this.peerId = peerId;
        this.description = description;
        this.timestamp = Instant.now();
    }

    // Getters
    public EventType getType() { return type; }
    public String getPeerId() { return peerId; }
    public String getDescription() { return description; }
    public Instant getTimestamp() { return timestamp; }
}