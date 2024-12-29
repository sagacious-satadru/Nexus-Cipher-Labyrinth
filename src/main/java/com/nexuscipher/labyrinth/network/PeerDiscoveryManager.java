package com.nexuscipher.labyrinth.network;

import com.nexuscipher.labyrinth.network.protocol.PeerDiscoveryMessage;
import com.nexuscipher.labyrinth.util.SerializationUtil;  // For serialization utilities
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;  // For thread-safe boolean operations

public class PeerDiscoveryManager {
    private static final Logger logger = LoggerFactory.getLogger(PeerDiscoveryManager.class);

    private static final int DISCOVERY_PORT = 54321;  // Port for UDP discovery broadcasts
    private static final int DISCOVERY_INTERVAL = 30000;  // 30 seconds between discovery attempts
    private static final int PEER_EXPIRY_TIME = 300000;  // 5 minutes until peer is considered stale

    private final String nodeId;
    private final int servicePort;  // Port where our main service listens
    private final ConnectionManager connectionManager;
    private final Map<String, PeerDiscoveryMessage.PeerInfo> knownPeers;
    private final ScheduledExecutorService scheduler;
    private final DatagramSocket discoverySocket;
    private final AtomicBoolean isRunning;

    public PeerDiscoveryManager(String nodeId, int servicePort,
                                ConnectionManager connectionManager) throws SocketException {
        this.nodeId = nodeId;
        this.servicePort = servicePort;
        this.connectionManager = connectionManager;
        this.knownPeers = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.discoverySocket = new DatagramSocket(DISCOVERY_PORT);
        this.isRunning = new AtomicBoolean(false);

        // Allow broadcast packets
        discoverySocket.setBroadcast(true);
    }

    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            // Start listening for discovery messages
            scheduler.submit(this::listenForDiscovery);

            // Schedule periodic discovery broadcasts
            scheduler.scheduleAtFixedRate(
                    this::broadcastDiscovery,
                    0,
                    DISCOVERY_INTERVAL,
                    TimeUnit.MILLISECONDS
            );

            // Schedule periodic peer list cleanup
            scheduler.scheduleAtFixedRate(
                    this::cleanupStaleNodes,
                    PEER_EXPIRY_TIME,
                    PEER_EXPIRY_TIME,
                    TimeUnit.MILLISECONDS
            );

            logger.info("Peer discovery started on port {}", DISCOVERY_PORT);
        }
    }

    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            scheduler.shutdown();
            discoverySocket.close();
            logger.info("Peer discovery stopped");
        }
    }

    private void broadcastDiscovery() {
        try {
            // Create discovery message
            PeerDiscoveryMessage message = new PeerDiscoveryMessage(
                    nodeId,
                    PeerDiscoveryMessage.MessageSubType.DISCOVERY_REQUEST,
                    InetAddress.getLocalHost().getHostAddress(),
                    servicePort
            );

            // Serialize message
            byte[] data = SerializationUtil.serialize(message);

            // Create broadcast packet
            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName("255.255.255.255"),
                    DISCOVERY_PORT
            );

            // Send broadcast
            discoverySocket.send(packet);
            logger.debug("Sent discovery broadcast");

        } catch (Exception e) {
            logger.error("Failed to broadcast discovery message", e);
        }
    }

    private void listenForDiscovery() {
        byte[] buffer = new byte[8192];  // 8KB buffer

        while (isRunning.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                discoverySocket.receive(packet);

                // Deserialize message
                PeerDiscoveryMessage message = (PeerDiscoveryMessage)
                        SerializationUtil.deserialize(
                                Arrays.copyOf(packet.getData(), packet.getLength())
                        );

                // Handle message based on its type
                switch (message.getSubType()) {
                    case DISCOVERY_REQUEST:
                        handleDiscoveryRequest(message, packet.getAddress());
                        break;
                    case DISCOVERY_RESPONSE:
                        handleDiscoveryResponse(message);
                        break;
                    case PEER_LIST_REQUEST:
                        handlePeerListRequest(message, packet.getAddress());
                        break;
                    case PEER_LIST_RESPONSE:
                        handlePeerListResponse(message);
                        break;
                }

            } catch (Exception e) {
                if (isRunning.get()) {
                    logger.error("Error processing discovery message", e);
                }
            }
        }
    }

    private void handleDiscoveryRequest(PeerDiscoveryMessage message, InetAddress sender) {
        // Don't respond to our own broadcasts
        if (message.getSenderId().equals(nodeId)) return;

        try {
            // Create response message
            PeerDiscoveryMessage response = new PeerDiscoveryMessage(
                    nodeId,
                    PeerDiscoveryMessage.MessageSubType.DISCOVERY_RESPONSE,
                    InetAddress.getLocalHost().getHostAddress(),
                    servicePort
            );

            // Send response directly to the requesting node
            byte[] data = SerializationUtil.serialize(response);
            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    sender,
                    DISCOVERY_PORT
            );

            discoverySocket.send(packet);
            logger.debug("Sent discovery response to {}", sender.getHostAddress());

        } catch (Exception e) {
            logger.error("Failed to send discovery response", e);
        }
    }

    private void handleDiscoveryResponse(PeerDiscoveryMessage message) {
        // Don't process our own messages
        if (message.getSenderId().equals(nodeId)) return;

        // Add to known peers
        PeerDiscoveryMessage.PeerInfo peerInfo = new PeerDiscoveryMessage.PeerInfo(
                message.getSenderId(),
                message.getHost(),
                message.getPort()
        );

        knownPeers.put(message.getSenderId(), peerInfo);

        // Initiate connection if we're not already connected
        connectionManager.connectToPeer(message.getHost(), message.getPort());

        logger.info("Discovered new peer: {} at {}:{}",
                message.getSenderId(), message.getHost(), message.getPort());
    }

    private void handlePeerListRequest(PeerDiscoveryMessage message, InetAddress sender) {
        try {
            // Create response with our known peers
            PeerDiscoveryMessage response = new PeerDiscoveryMessage(
                    nodeId,
                    PeerDiscoveryMessage.MessageSubType.PEER_LIST_RESPONSE,
                    InetAddress.getLocalHost().getHostAddress(),
                    servicePort,
                    new ArrayList<>(knownPeers.values())
            );

            // Send response
            byte[] data = SerializationUtil.serialize(response);
            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    sender,
                    DISCOVERY_PORT
            );

            discoverySocket.send(packet);

        } catch (Exception e) {
            logger.error("Failed to send peer list response", e);
        }
    }

    private void handlePeerListResponse(PeerDiscoveryMessage message) {
        // Add all peers to our known peers list
        for (PeerDiscoveryMessage.PeerInfo peer : message.getKnownPeers()) {
            if (!peer.getNodeId().equals(nodeId) && !knownPeers.containsKey(peer.getNodeId())) {
                knownPeers.put(peer.getNodeId(), peer);
                connectionManager.connectToPeer(peer.getHost(), peer.getPort());
            }
        }
    }

    private void cleanupStaleNodes() {
        // Implement cleanup logic here
        // Remove peers that haven't been seen recently
        // This will be enhanced when we add heartbeat mechanism
    }
}