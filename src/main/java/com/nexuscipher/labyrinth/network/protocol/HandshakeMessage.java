package com.nexuscipher.labyrinth.network.protocol;

public class HandshakeMessage extends Message {
    private final byte[] publicKey;      // Quantum-resistant public key
    private final byte[] signature;      // Cryptographic signature
    private final String challenge;      // Random challenge for verification
    private final byte[] challengeResponse;  // Response to previous challenge (if any)

    public HandshakeMessage(String senderId, MessageType type, byte[] publicKey,
                            byte[] signature, String challenge, byte[] challengeResponse) {
        super(senderId, type);
        this.publicKey = publicKey;
        this.signature = signature;
        this.challenge = challenge;
        this.challengeResponse = challengeResponse;
    }

    // Getters
    public byte[] getPublicKey() { return publicKey; }
    public byte[] getSignature() { return signature; }
    public String getChallenge() { return challenge; }
    public byte[] getChallengeResponse() { return challengeResponse; }
}