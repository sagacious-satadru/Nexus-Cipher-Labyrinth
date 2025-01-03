package com.nexuscipher.labyrinth.network.protocol;

import java.io.Serializable;
import java.util.UUID;

public abstract class Message implements Serializable {
    private final String messageId;
    private final String senderId;
    private final MessageType type;
    private final long timestamp;

    public enum MessageType {
        // Core message types
        DATA,           // Regular data payload messages
        ROUTING,        // Network routing control messages

        // Handshake protocol messages
        HANDSHAKE_INIT,
        HANDSHAKE_RESPONSE,
        HANDSHAKE_CONFIRM,

        // Network discovery messages
        PEER_DISCOVERY,
        PEER_LIST
    }

    protected Message(String senderId, MessageType type) {
        this.messageId = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public String getMessageId() { return messageId; }
    public String getSenderId() { return senderId; }
    public MessageType getType() { return type; }
    public long getTimestamp() { return timestamp; }
}