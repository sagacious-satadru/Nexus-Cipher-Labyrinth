package com.nexuscipher.labyrinth.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeerConnection {
    private static final Logger logger = LoggerFactory.getLogger(PeerConnection.class);

    private final String peerId;
    private final String address;
    private final int port;

    public PeerConnection(String peerId, String address, int port) {
        this.peerId = peerId;
        this.address = address;
        this.port = port;
        logger.debug("Created new peer connection to {}:{}", address, port);
    }

    // Getters
    public String getPeerId() { return peerId; }
    public String getAddress() { return address; }
    public int getPort() { return port; }
}