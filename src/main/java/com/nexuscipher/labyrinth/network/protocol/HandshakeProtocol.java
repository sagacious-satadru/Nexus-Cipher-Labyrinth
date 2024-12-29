package com.nexuscipher.labyrinth.network.protocol;

import com.nexuscipher.labyrinth.crypto.QuantumResistantCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HandshakeProtocol {
    private static final Logger logger = LoggerFactory.getLogger(HandshakeProtocol.class);
    private static final int CHALLENGE_LENGTH = 32;  // Length of challenge in bytes
    private final SecureRandom random = new SecureRandom();

    private final String nodeId;
    private final QuantumResistantCrypto crypto;

    // Store ongoing handshakes - Map<messageId, challenge>
    private final Map<String, String> pendingHandshakes = new HashMap<>();

    public HandshakeProtocol(String nodeId, QuantumResistantCrypto crypto) {
        this.nodeId = nodeId;
        this.crypto = crypto;
    }

    /**
     * Initiates a new handshake by creating an initial message.
     * This is step 1 of the 3-way handshake.
     */
    public HandshakeMessage createInitialHandshake() {
        // Generate a random challenge
        byte[] challengeBytes = new byte[CHALLENGE_LENGTH];
        random.nextBytes(challengeBytes);
        String challenge = Base64.getEncoder().encodeToString(challengeBytes);

        // Sign our node ID to prove it's really us
        byte[] signature;
        try {
            signature = crypto.sign(nodeId.getBytes());
        } catch (Exception e) {
            logger.error("Failed to sign handshake init message", e);
            throw new RuntimeException("Handshake initialization failed", e);
        }

        HandshakeMessage message = new HandshakeMessage(
                nodeId,
                Message.MessageType.HANDSHAKE_INIT,
                crypto.getPublicKey(),
                signature,
                challenge,
                null  // No challenge response in initial message
        );

        // Store the challenge we sent
        pendingHandshakes.put(message.getMessageId(), challenge);
        return message;
    }

    /**
     * Processes a received handshake initialization and creates a response.
     * This is step 2 of the 3-way handshake.
     */
    public HandshakeMessage handleInitialHandshake(HandshakeMessage initMessage) {
        // Verify the signature of the initiating node
        try {
            boolean validSignature = crypto.verify(
                    initMessage.getSenderId().getBytes(),
                    initMessage.getSignature(),
                    initMessage.getPublicKey()
            );

            if (!validSignature) {
                logger.error("Invalid signature in handshake init from {}", initMessage.getSenderId());
                throw new SecurityException("Invalid signature in handshake");
            }
        } catch (Exception e) {
            logger.error("Failed to verify handshake init signature", e);
            throw new SecurityException("Signature verification failed", e);
        }

        // Generate our own challenge
        byte[] challengeBytes = new byte[CHALLENGE_LENGTH];
        random.nextBytes(challengeBytes);
        String newChallenge = Base64.getEncoder().encodeToString(challengeBytes);

        // Sign our response
        byte[] signature;
        try {
            // Sign both our ID and our response to their challenge
            String dataToSign = nodeId + initMessage.getChallenge();
            signature = crypto.sign(dataToSign.getBytes());
        } catch (Exception e) {
            logger.error("Failed to sign handshake response", e);
            throw new RuntimeException("Handshake response creation failed", e);
        }

        // Create response message
        HandshakeMessage response = new HandshakeMessage(
                nodeId,
                Message.MessageType.HANDSHAKE_RESPONSE,
                crypto.getPublicKey(),
                signature,
                newChallenge,
                initMessage.getChallenge().getBytes()  // Echo back their challenge
        );

        // Store our challenge
        pendingHandshakes.put(response.getMessageId(), newChallenge);
        return response;
    }

    /**
     * Verifies a handshake response and creates the final confirmation.
     * This is step 3 of the 3-way handshake.
     */
    public HandshakeMessage handleHandshakeResponse(HandshakeMessage responseMessage) {
        // Verify their signature
        try {
            String expectedData = responseMessage.getSenderId() +
                    pendingHandshakes.get(responseMessage.getMessageId());
            boolean validSignature = crypto.verify(
                    expectedData.getBytes(),
                    responseMessage.getSignature(),
                    responseMessage.getPublicKey()
            );

            if (!validSignature) {
                logger.error("Invalid signature in handshake response from {}",
                        responseMessage.getSenderId());
                throw new SecurityException("Invalid signature in handshake response");
            }
        } catch (Exception e) {
            logger.error("Failed to verify handshake response signature", e);
            throw new SecurityException("Signature verification failed", e);
        }

        // Sign our final confirmation
        byte[] signature;
        try {
            signature = crypto.sign(responseMessage.getChallenge().getBytes());
        } catch (Exception e) {
            logger.error("Failed to sign handshake confirmation", e);
            throw new RuntimeException("Handshake confirmation failed", e);
        }

        // Create confirmation message
        return new HandshakeMessage(
                nodeId,
                Message.MessageType.HANDSHAKE_CONFIRM,
                crypto.getPublicKey(),
                signature,
                null,  // No new challenge needed
                responseMessage.getChallenge().getBytes()
        );
    }

    /**
     * Verifies the final handshake confirmation.
     * Returns true if the handshake is complete and valid.
     */
    public boolean verifyHandshakeConfirmation(HandshakeMessage confirmMessage) {
        try {
            boolean validSignature = crypto.verify(
                    pendingHandshakes.get(confirmMessage.getMessageId()).getBytes(),
                    confirmMessage.getSignature(),
                    confirmMessage.getPublicKey()
            );

            if (validSignature) {
                // Clean up stored challenge
                pendingHandshakes.remove(confirmMessage.getMessageId());
                return true;
            }

            logger.error("Invalid signature in handshake confirmation from {}",
                    confirmMessage.getSenderId());
            return false;
        } catch (Exception e) {
            logger.error("Failed to verify handshake confirmation", e);
            return false;
        }
    }
}