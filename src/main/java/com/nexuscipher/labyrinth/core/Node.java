package com.nexuscipher.labyrinth.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Node {
    private static final Logger logger = LoggerFactory.getLogger(Node.class);

    private final String nodeId;
    private final ConcurrentMap<String, PeerConnection> peers;
    private final String hostAddress;
    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService connectionExecutor;
    private final AtomicBoolean isRunning;

    public Node() {
        this(0); // 0 means random available port
    }

    public Node(int port) {
        this.nodeId = UUID.randomUUID().toString();
        this.peers = new ConcurrentHashMap<>();
        this.port = port;
        this.connectionExecutor = Executors.newCachedThreadPool();
        this.isRunning = new AtomicBoolean(false);

        try {
            this.hostAddress = InetAddress.getLocalHost().getHostAddress();
            this.serverSocket = new ServerSocket(port);
            this.port = serverSocket.getLocalPort(); // Get the actual port if we used 0
            logger.info("Created new node with ID: {} on {}:{}", nodeId, hostAddress, this.port);
        } catch (UnknownHostException e) {
            logger.error("Failed to get host address", e);
            throw new RuntimeException("Failed to initialize node", e);
        } catch (IOException e) {
            logger.error("Failed to create server socket", e);
            throw new RuntimeException("Failed to initialize node", e);
        }
    }

    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            // Start listening for connections in a separate thread
            connectionExecutor.submit(this::listenForConnections);
            logger.info("Node started and listening for connections on port {}", port);
        }
    }

    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            try {
                serverSocket.close();
                connectionExecutor.shutdown();
                logger.info("Node stopped");
            } catch (IOException e) {
                logger.error("Error while stopping node", e);
            }
        }
    }

    private void listenForConnections() {
        while (isRunning.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleNewConnection(clientSocket);
            } catch (IOException e) {
                if (isRunning.get()) {
                    logger.error("Error accepting connection", e);
                }
            }
        }
    }

    private void handleNewConnection(Socket clientSocket) {
        connectionExecutor.submit(() -> {
            try {
                // TODO: Implement handshake protocol
                logger.info("New connection from {}", clientSocket.getInetAddress());

                // For now, just close the connection
                clientSocket.close();
            } catch (IOException e) {
                logger.error("Error handling connection", e);
            }
        });
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public int getPort() {
        return port;
    }

    public boolean addPeer(PeerConnection peer) {
        if (peers.putIfAbsent(peer.getPeerId(), peer) == null) {
            logger.info("Added new peer: {} at {}:{}",
                    peer.getPeerId(), peer.getAddress(), peer.getPort());
            return true;
        }
        return false;
    }

    public boolean removePeer(String peerId) {
        PeerConnection removed = peers.remove(peerId);
        if (removed != null) {
            logger.info("Removed peer: {}", peerId);
            return true;
        }
        return false;
    }
}