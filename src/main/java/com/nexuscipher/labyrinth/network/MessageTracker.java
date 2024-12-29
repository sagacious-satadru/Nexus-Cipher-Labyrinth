package com.nexuscipher.labyrinth.network;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageTracker {
    private final String messageGroupId;
    private final int totalChunks;
    private final BitSet acknowledgedChunks;
    private final AtomicInteger retryCount;
    private final long creationTime;

    public MessageTracker(String messageGroupId, int totalChunks) {
        this.messageGroupId = messageGroupId;
        this.totalChunks = totalChunks;
        this.acknowledgedChunks = new BitSet(totalChunks);
        this.retryCount = new AtomicInteger(0);
        this.creationTime = System.currentTimeMillis();
    }

    public synchronized void acknowledgeChunk(int chunkNumber) {
        acknowledgedChunks.set(chunkNumber);
    }

    public synchronized boolean isComplete() {
        return acknowledgedChunks.cardinality() == totalChunks;
    }

    public int incrementRetryCount() {
        return retryCount.incrementAndGet();
    }

    public long getCreationTime() {
        return creationTime;
    }

    public String getMessageGroupId() {
        return messageGroupId;
    }

    public synchronized int[] getMissingChunks() {
        BitSet missing = new BitSet(totalChunks);
        missing.set(0, totalChunks);
        missing.andNot(acknowledgedChunks);

        int[] missingChunks = new int[totalChunks - acknowledgedChunks.cardinality()];
        int j = 0;
        for (int i = missing.nextSetBit(0); i >= 0; i = missing.nextSetBit(i + 1)) {
            missingChunks[j++] = i;
        }
        return missingChunks;
    }
}