package com.nexuscipher.labyrinth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nexuscipher.labyrinth.core.Node;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Starting Nexus Cipher Labyrinth...");

        // Create a new node
        Node node = new Node();

        // Start the node
        node.start();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down node...");
            node.stop();
        }));

        logger.info("Node initialized successfully. Press Ctrl+C to stop.");
    }
}