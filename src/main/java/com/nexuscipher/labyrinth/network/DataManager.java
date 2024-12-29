package com.nexuscipher.labyrinth.network;

import com.nexuscipher.labyrinth.crypto.QuantumResistantCrypto;
import com.nexuscipher.labyrinth.network.protocol.DataMessage;
import com.nexuscipher.labyrinth.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public class DataManager {
    private static final Logger logger = LoggerFactory.getLogger(DataManager.class);

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long CHUNK_TIMEOUT = 30000; // 30 seconds
    private static final int MAX_CHUNK_SIZE = 1024 * 1024; // 1MB

    private final String nodeId;
    private final QuantumResistantCrypto crypto;
    private final RoutingManager routingManager;
    private final Map<String, MessageAssembler> incomingMessages;
    private final Map<String, MessageTracker> outgoingMessages;
    private final ScheduledExecutorService timeoutChecker;

    public DataManager(String nodeId,
                       QuantumResistantCrypto crypto,
                       RoutingManager routingManager) {
        this.nodeId = nodeId;
        this.crypto = crypto;
        this.routingManager = routingManager;
        this.incomingMessages = new ConcurrentHashMap<>();
        this.outgoingMessages = new ConcurrentHashMap<>();
        this.timeoutChecker = Executors.newScheduledThreadPool(1);

        // Start timeout checker
        startTimeoutChecker();
    }

    /**
     * Sends data to a target node, fragmenting if necessary
     */
    public void sendData(String targetNodeId, byte[] data) {
        String messageGroupId = UUID.randomUUID().toString();

        // Calculate number of chunks needed
        int totalChunks = (int) Math.ceil((double) data.length / MAX_CHUNK_SIZE);

        // Create message tracker
        MessageTracker tracker = new MessageTracker(messageGroupId, totalChunks);
        outgoingMessages.put(messageGroupId, tracker);

        // Split and send data chunks
        for (int i = 0; i < totalChunks; i++) {
            int start = i * MAX_CHUNK_SIZE;
            int end = Math.min(start + MAX_CHUNK_SIZE, data.length);
            byte[] chunk = Arrays.copyOfRange(data, start, end);
            byte[] checksum = CryptoUtil.calculateChecksum(chunk);

            DataMessage message = new DataMessage(
                    nodeId,
                    messageGroupId,
                    totalChunks,
                    i,
                    chunk,
                    checksum,
                    DataMessage.MessageState.DATA_CHUNK
            );

            // Send through routing manager
            routingManager.routeMessage(targetNodeId, message);
        }

        logger.info("Started sending message {} in {} chunks to {}",
                messageGroupId, totalChunks, targetNodeId);
    }

    /**
     * Handles incoming data messages
     */
    public void handleDataMessage(DataMessage message) {
        switch (message.getState()) {
            case DATA_CHUNK:
                handleDataChunk(message);
                break;
            case ACKNOWLEDGMENT:
                handleAcknowledgment(message);
                break;
            case RETRANSMIT_REQUEST:
                handleRetransmitRequest(message);
                break;
            case COMPLETE:
                handleComplete(message);
                break;
        }
    }

    private void handleDataChunk(DataMessage message) {
        // Verify checksum
        byte[] calculatedChecksum = CryptoUtil.calculateChecksum(message.getData());
        if (!Arrays.equals(calculatedChecksum, message.getChecksum())) {
            logger.warn("Checksum mismatch for chunk {} of message {}",
                    message.getChunkNumber(), message.getMessageGroupId());
            requestRetransmission(message);
            return;
        }

        // Get or create message assembler
        MessageAssembler assembler = incomingMessages.computeIfAbsent(
                message.getMessageGroupId(),
                id -> new MessageAssembler(message.getTotalChunks())
        );

        // Add chunk to assembler
        assembler.addChunk(message.getChunkNumber(), message.getData());

        // Send acknowledgment
        sendAcknowledgment(message);

        // Check if message is complete
        if (assembler.isComplete()) {
            processCompleteMessage(message.getMessageGroupId(), assembler);
        }
    }

// ... continuing DataManager class

    private void handleAcknowledgment(DataMessage message) {
        MessageTracker tracker = outgoingMessages.get(message.getMessageGroupId());
        if (tracker != null) {
            tracker.acknowledgeChunk(message.getChunkNumber());

            if (tracker.isComplete()) {
                logger.info("Message {} fully acknowledged by recipient",
                        message.getMessageGroupId());
                outgoingMessages.remove(message.getMessageGroupId());
            }
        }
    }

    private void handleRetransmitRequest(DataMessage message) {
        MessageTracker tracker = outgoingMessages.get(message.getMessageGroupId());
        if (tracker != null) {
            if (tracker.incrementRetryCount() > MAX_RETRY_ATTEMPTS) {
                logger.error("Max retry attempts exceeded for message {}",
                        message.getMessageGroupId());
                outgoingMessages.remove(message.getMessageGroupId());
                return;
            }

            // Resend the requested chunk
            sendData(message.getSenderId(), message.getData());
            logger.debug("Retransmitted chunk {} of message {}",
                    message.getChunkNumber(), message.getMessageGroupId());
        }
    }

    private void handleComplete(DataMessage message) {
        MessageAssembler assembler = incomingMessages.remove(message.getMessageGroupId());
        if (assembler != null && assembler.isComplete()) {
            byte[] completeData = assembler.assembleMessage();
            processCompleteMessage(message.getMessageGroupId(), assembler);
        }
    }

    private void processCompleteMessage(String messageGroupId, MessageAssembler assembler) {
        try {
            byte[] completeData = assembler.assembleMessage();
            // Here we would typically pass the complete data to an application layer handler
            logger.info("Successfully assembled complete message {}", messageGroupId);

            // Clean up
            incomingMessages.remove(messageGroupId);

            // Send completion acknowledgment
            DataMessage completeMessage = new DataMessage(
                    nodeId,
                    messageGroupId,
                    0,  // Not relevant for completion message
                    0,  // Not relevant for completion message
                    new byte[0],  // No data needed
                    new byte[0],  // No checksum needed
                    DataMessage.MessageState.COMPLETE
            );

            routingManager.routeMessage(completeMessage.getSenderId(), completeMessage);
        } catch (Exception e) {
            logger.error("Failed to process complete message {}", messageGroupId, e);
        }
    }

    private void requestRetransmission(DataMessage message) {
        DataMessage retransmitRequest = new DataMessage(
                nodeId,
                message.getMessageGroupId(),
                message.getTotalChunks(),
                message.getChunkNumber(),
                new byte[0],  // No data needed
                new byte[0],  // No checksum needed
                DataMessage.MessageState.RETRANSMIT_REQUEST
        );

        routingManager.routeMessage(message.getSenderId(), retransmitRequest);
    }

    private void sendAcknowledgment(DataMessage message) {
        DataMessage ack = new DataMessage(
                nodeId,
                message.getMessageGroupId(),
                message.getTotalChunks(),
                message.getChunkNumber(),
                new byte[0],  // No data needed
                new byte[0],  // No checksum needed
                DataMessage.MessageState.ACKNOWLEDGMENT
        );

        routingManager.routeMessage(message.getSenderId(), ack);
    }

    private void startTimeoutChecker() {
        timeoutChecker.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();

            // Check outgoing messages for timeouts
            outgoingMessages.forEach((messageId, tracker) -> {
                if (currentTime - tracker.getCreationTime() > CHUNK_TIMEOUT) {
                    if (tracker.incrementRetryCount() > MAX_RETRY_ATTEMPTS) {
                        logger.error("Message {} timed out after max retries", messageId);
                        outgoingMessages.remove(messageId);
                    } else {
                        // Resend missing chunks
                        logger.warn("Message {} timed out, retrying...", messageId);
                        for (int chunkNumber : tracker.getMissingChunks()) {
                            // Implement resend logic here
                            logger.debug("Resending chunk {} of message {}",
                                    chunkNumber, messageId);
                        }
                    }
                }
            });

            // Check incoming messages for timeouts
            incomingMessages.entrySet().removeIf(entry ->
                    currentTime - entry.getValue().getCreationTime() > CHUNK_TIMEOUT);

        }, CHUNK_TIMEOUT, CHUNK_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        timeoutChecker.shutdown();
        try {
            timeoutChecker.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.warn("Timeout checker shutdown interrupted");
            Thread.currentThread().interrupt();
        }
    }

    // Add creation time to MessageAssembler
    private long getCreationTime() {
        return System.currentTimeMillis();
    }
}