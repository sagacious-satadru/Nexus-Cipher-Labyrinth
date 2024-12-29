package com.nexuscipher.labyrinth.network;

import com.nexuscipher.labyrinth.core.PeerConnection;
import com.nexuscipher.labyrinth.crypto.QuantumResistantCrypto;
import com.nexuscipher.labyrinth.network.protocol.HandshakeMessage;
import com.nexuscipher.labyrinth.network.protocol.HandshakeProtocol;
import com.nexuscipher.labyrinth.network.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.Collection;

public class ConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);

    private final String nodeId;
    private final QuantumResistantCrypto crypto;
    private final HandshakeProtocol handshakeProtocol;
    private final Map<String, MessageHandler> activeConnections;
    private final Map<String, PeerConnection> verifiedPeers;
    private final ExecutorService connectionExecutor;

    public ConnectionManager(String nodeId, QuantumResistantCrypto crypto) {
        this.nodeId = nodeId;
        this.crypto = crypto;
        this.handshakeProtocol = new HandshakeProtocol(nodeId, crypto);
        this.activeConnections = new ConcurrentHashMap<>();
        this.verifiedPeers = new ConcurrentHashMap<>();
        this.connectionExecutor = Executors.newCachedThreadPool();
    }

    /**
     * Initiates a connection to a new peer
     */
    public void connectToPeer(String address, int port) {
        connectionExecutor.submit(() -> {
            try {
                Socket socket = new Socket(address, port);
                logger.info("Connected to peer at {}:{}", address, port);

                // Create message handler for the new connection
                MessageHandler handler = new MessageHandler(socket, nodeId, crypto, this);

                // Start handshake process
                HandshakeMessage initMessage = handshakeProtocol.createInitialHandshake();
                handler.sendMessage(initMessage);

                // Start handling messages from this peer
                connectionExecutor.submit(handler);

                // Store the handler until peer is verified
                activeConnections.put(initMessage.getMessageId(), handler);

            } catch (IOException e) {
                logger.error("Failed to connect to peer at {}:{}", address, port, e);
            }
        });
    }

    /**
     * Handles a new incoming connection
     */
    public void handleIncomingConnection(Socket socket) {
        try {
            MessageHandler handler = new MessageHandler(socket, nodeId, crypto, this);
            connectionExecutor.submit(handler);

            // The message ID will be set when we receive their handshake init
            activeConnections.put(socket.getRemoteSocketAddress().toString(), handler);

        } catch (IOException e) {
            logger.error("Failed to handle incoming connection from {}",
                    socket.getRemoteSocketAddress(), e);
        }
    }

    /**
     * Processes a message received from a peer
     */
    public void handleMessage(Message message, MessageHandler handler) {
        switch (message.getType()) {
            case HANDSHAKE_INIT:
                handleHandshakeInit((HandshakeMessage) message, handler);
                break;
            case HANDSHAKE_RESPONSE:
                handleHandshakeResponse((HandshakeMessage) message, handler);
                break;
            case HANDSHAKE_CONFIRM:
                handleHandshakeConfirm((HandshakeMessage) message, handler);
                break;
            case DATA:
                // Only process data messages from verified peers
                if (verifiedPeers.containsKey(message.getSenderId())) {
                    processDataMessage(message);
                } else {
                    logger.warn("Received data message from unverified peer: {}",
                            message.getSenderId());
                }
                break;
            default:
                logger.warn("Received unknown message type: {}", message.getType());
        }
    }

    private void handleHandshakeInit(HandshakeMessage message, MessageHandler handler) {
        try {
            HandshakeMessage response = handshakeProtocol.handleInitialHandshake(message);
            handler.sendMessage(response);

            // Update connection mapping with the actual message ID
            activeConnections.remove(handler.getSocket().getRemoteSocketAddress().toString());
            activeConnections.put(message.getMessageId(), handler);

        } catch (SecurityException e) {
            logger.error("Handshake initialization failed", e);
            handler.close();
        }
    }

    private void handleHandshakeResponse(HandshakeMessage message, MessageHandler handler) {
        try {
            HandshakeMessage confirmation = handshakeProtocol.handleHandshakeResponse(message);
            handler.sendMessage(confirmation);

            // Add to verified peers
            PeerConnection peer = new PeerConnection(
                    message.getSenderId(),
                    handler.getSocket().getInetAddress().getHostAddress(),
                    handler.getSocket().getPort()
            );
            verifiedPeers.put(message.getSenderId(), peer);

        } catch (SecurityException e) {
            logger.error("Handshake response verification failed", e);
            handler.close();
            activeConnections.remove(message.getMessageId());
        }
    }

    private void handleHandshakeConfirm(HandshakeMessage message, MessageHandler handler) {
        boolean verified = handshakeProtocol.verifyHandshakeConfirmation(message);
        if (verified) {
            // Add to verified peers
            PeerConnection peer = new PeerConnection(
                    message.getSenderId(),
                    handler.getSocket().getInetAddress().getHostAddress(),
                    handler.getSocket().getPort()
            );
            verifiedPeers.put(message.getSenderId(), peer);
            logger.info("Peer verified and added: {}", message.getSenderId());
        } else {
            logger.error("Handshake confirmation verification failed for peer: {}",
                    message.getSenderId());
            handler.close();
            activeConnections.remove(message.getMessageId());
        }
    }

    private void processDataMessage(Message message) {
        // To be implemented based on application needs
        logger.info("Received data message from {}: {}", message.getSenderId(), message);
    }

    public void shutdown() {
        connectionExecutor.shutdown();
        activeConnections.values().forEach(MessageHandler::close);
        activeConnections.clear();
        verifiedPeers.clear();
    }

    /**
     * Gets all verified peers
     */
    public Collection<PeerConnection> getAllPeers() {
        return new ArrayList<>(verifiedPeers.values());
    }

    /**
     * Gets a peer by ID
     */
    public Optional<PeerConnection> getPeerById(String peerId) {
        return Optional.ofNullable(verifiedPeers.get(peerId));
    }

    /**
     * Sends a message to a specific peer
     */
    public void sendMessage(Message message, String peerId) throws IOException {
        MessageHandler handler = activeConnections.get(peerId);
        if (handler != null && handler.isActive()) {
            handler.sendMessage(message);
        } else {
            throw new IOException("No active connection to peer: " + peerId);
        }
    }
}