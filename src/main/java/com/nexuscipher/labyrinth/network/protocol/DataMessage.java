package com.nexuscipher.labyrinth.network.protocol;

import java.util.UUID;

public class DataMessage extends Message {
    // Maximum size of data chunk in bytes (1MB)
    private static final int MAX_CHUNK_SIZE = 1024 * 1024;

    private final String messageGroupId;    // Groups chunks of the same message
    private final int totalChunks;          // Total number of chunks in complete message
    private final int chunkNumber;          // Current chunk number
    private final byte[] data;              // Actual data chunk
    private final byte[] checksum;          // Integrity verification
    private final long timestamp;           // For ordering and timeout handling
    private final MessageState state;       // Current state of the message

    public enum MessageState {
        DATA_CHUNK,         // Regular data chunk
        ACKNOWLEDGMENT,     // Confirmation of receipt
        RETRANSMIT_REQUEST, // Request for missing chunks
        COMPLETE           // Final chunk received
    }

    public DataMessage(String senderId,
                       String messageGroupId,
                       int totalChunks,
                       int chunkNumber,
                       byte[] data,
                       byte[] checksum,
                       MessageState state) {
        super(senderId, MessageType.DATA);
        this.messageGroupId = messageGroupId;
        this.totalChunks = totalChunks;
        this.chunkNumber = chunkNumber;
        this.data = data;
        this.checksum = checksum;
        this.timestamp = System.currentTimeMillis();
        this.state = state;
    }

    // Getters
    public String getMessageGroupId() { return messageGroupId; }
    public int getTotalChunks() { return totalChunks; }
    public int getChunkNumber() { return chunkNumber; }
    public byte[] getData() { return data; }
    public byte[] getChecksum() { return checksum; }
    public long getTimestamp() { return timestamp; }
    public MessageState getState() { return state; }

    /**
     * Helper method to determine if this is the last chunk
     */
    public boolean isLastChunk() {
        return chunkNumber == totalChunks - 1;
    }
}