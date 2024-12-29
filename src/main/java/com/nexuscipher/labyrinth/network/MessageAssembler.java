package com.nexuscipher.labyrinth.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.BitSet;

public class MessageAssembler {
    private static final Logger logger = LoggerFactory.getLogger(MessageAssembler.class);

    private final byte[][] chunks;
    private final BitSet receivedChunks;
    private final int totalChunks;
    // Add this field at the top of MessageAssembler class
    private final long creationTime;


    public long getCreationTime() {
        return creationTime;
    }

    public MessageAssembler(int totalChunks) {
        this.chunks = new byte[totalChunks][];
        this.receivedChunks = new BitSet(totalChunks);
        this.totalChunks = totalChunks;
        this.creationTime = System.currentTimeMillis();
    }

    public synchronized void addChunk(int chunkNumber, byte[] data) {
        if (chunkNumber >= totalChunks) {
            throw new IllegalArgumentException("Invalid chunk number");
        }
        chunks[chunkNumber] = data;
        receivedChunks.set(chunkNumber);
    }

    public synchronized boolean isComplete() {
        return receivedChunks.cardinality() == totalChunks;
    }

    public synchronized byte[] assembleMessage() {
        if (!isComplete()) {
            throw new IllegalStateException("Message is not complete");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            try {
                outputStream.write(chunk);
            } catch (Exception e) {
                logger.error("Failed to assemble message", e);
                throw new RuntimeException("Message assembly failed", e);
            }
        }
        return outputStream.toByteArray();
    }

    public synchronized int[] getMissingChunks() {
        BitSet missing = new BitSet(totalChunks);
        missing.set(0, totalChunks);
        missing.andNot(receivedChunks);

        int[] missingChunks = new int[totalChunks - receivedChunks.cardinality()];
        int j = 0;
        for (int i = missing.nextSetBit(0); i >= 0; i = missing.nextSetBit(i + 1)) {
            missingChunks[j++] = i;
        }
        return missingChunks;
    }
}