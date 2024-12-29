package com.nexuscipher.labyrinth.network;

import com.nexuscipher.labyrinth.crypto.QuantumResistantCrypto;
import com.nexuscipher.labyrinth.network.protocol.Message;
import com.nexuscipher.labyrinth.network.protocol.HandshakeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class MessageHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MessageHandler.class);

    private final Socket socket;
    private final String nodeId;
    private final QuantumResistantCrypto crypto;
    private final ObjectInputStream in;
    private final ObjectOutputStream out;
    private final ConnectionManager connectionManager;
    private final AtomicBoolean isRunning;

    public MessageHandler(Socket socket, String nodeId, QuantumResistantCrypto crypto,
                          ConnectionManager connectionManager) throws IOException {
        this.socket = socket;
        this.nodeId = nodeId;
        this.crypto = crypto;
        this.connectionManager = connectionManager;
        // Create output stream first to prevent deadlock
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
        this.isRunning = new AtomicBoolean(true);
    }

    @Override
    public void run() {
        try {
            while (isRunning.get() && !socket.isClosed()) {
                Message message = (Message) in.readObject();
                // Pass the message to connection manager along with this handler
                connectionManager.handleMessage(message, this);
            }
        } catch (IOException e) {
            logger.error("Connection error with peer at {}: {}",
                    socket.getRemoteSocketAddress(), e.getMessage());
        } catch (ClassNotFoundException e) {
            logger.error("Received malformed message from {}",
                    socket.getRemoteSocketAddress());
        } finally {
            cleanup();
        }
    }

    /**
     * Sends a message to the connected peer
     */
    public synchronized void sendMessage(Message message) {
        try {
            out.writeObject(message);
            out.flush();
            logger.debug("Sent message type {} to {}",
                    message.getType(), socket.getRemoteSocketAddress());
        } catch (IOException e) {
            logger.error("Failed to send message to {}: {}",
                    socket.getRemoteSocketAddress(), e.getMessage());
            close();
        }
    }

    /**
     * Gracefully closes the connection
     */
    public void close() {
        isRunning.set(false);
        cleanup();
    }

    private void cleanup() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
                logger.info("Closed connection to {}", socket.getRemoteSocketAddress());
            }
        } catch (IOException e) {
            logger.error("Error during connection cleanup: {}", e.getMessage());
        }
    }

    /**
     * Returns the socket associated with this handler
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Checks if the connection is still active
     */
    public boolean isActive() {
        return isRunning.get() && !socket.isClosed();
    }
}