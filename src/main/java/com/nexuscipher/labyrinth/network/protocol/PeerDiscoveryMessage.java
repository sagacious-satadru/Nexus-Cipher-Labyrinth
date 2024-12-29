package com.nexuscipher.labyrinth.network.protocol;

public class PeerDiscoveryMessage extends Message {
    private final String host;
    private final int port;
    private final MessageSubType subType;

    public enum MessageSubType {
        DISCOVERY_REQUEST,    // "Hello, any nodes out there?"
        DISCOVERY_RESPONSE,   // "Yes, I'm here!"
        PEER_LIST_REQUEST,    // "Who else do you know?"
        PEER_LIST_RESPONSE    // "Here are the peers I know about"
    }

    private final java.util.List<PeerInfo> knownPeers;  // For PEER_LIST_RESPONSE

    public PeerDiscoveryMessage(String senderId, MessageSubType subType,
                                String host, int port) {
        super(senderId, MessageType.PEER_DISCOVERY);
        this.subType = subType;
        this.host = host;
        this.port = port;
        this.knownPeers = new java.util.ArrayList<>();
    }

    public PeerDiscoveryMessage(String senderId, MessageSubType subType,
                                String host, int port,
                                java.util.List<PeerInfo> knownPeers) {
        this(senderId, subType, host, port);
        this.knownPeers.addAll(knownPeers);
    }

    // Getters
    public String getHost() { return host; }
    public int getPort() { return port; }
    public MessageSubType getSubType() { return subType; }
    public java.util.List<PeerInfo> getKnownPeers() {
        return new java.util.ArrayList<>(knownPeers);
    }

    // Helper class for peer information
    public static class PeerInfo implements java.io.Serializable {
        private final String nodeId;
        private final String host;
        private final int port;

        public PeerInfo(String nodeId, String host, int port) {
            this.nodeId = nodeId;
            this.host = host;
            this.port = port;
        }

        // Getters
        public String getNodeId() { return nodeId; }
        public String getHost() { return host; }
        public int getPort() { return port; }
    }
}