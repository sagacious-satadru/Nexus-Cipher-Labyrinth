package com.nexuscipher.labyrinth.monitoring;

// Add these imports at the top
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.nexuscipher.labyrinth.network.ConnectionManager;
import com.nexuscipher.labyrinth.core.PeerConnection;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.time.Instant;  // For timestamp comparisons
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for our network monitoring system.
 * Think of these tests as a series of health check scenarios - like running
 * diagnostics on a sophisticated medical monitoring system.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NetworkMonitorTest {

    @Mock
    private ConnectionManager connectionManager;

    private NetworkMonitor networkMonitor;
    private static final String TEST_NODE_ID = "test-node-1";
    private AutoCloseable mockitoContext;

    @BeforeEach
    void setUp() {
        mockitoContext = MockitoAnnotations.openMocks(this);
        networkMonitor = new NetworkMonitor(TEST_NODE_ID, connectionManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockitoContext != null) {
            mockitoContext.close();
        }
        if (networkMonitor != null) {
            networkMonitor.shutdown();
        }
    }

    @Test
    @DisplayName("Should detect unhealthy peers and attempt recovery")
    void testUnhealthyPeerDetection() {
        // Create test peer
        PeerConnection testPeer = new PeerConnection("test-peer", "localhost", 8080);
        when(connectionManager.getAllPeers())
                .thenReturn(Arrays.asList(testPeer));

        // Let the monitor run for a while
        try {
            TimeUnit.SECONDS.sleep(6); // Wait for health check cycle

            // Verify that appropriate events were recorded
            List<NetworkEvent> events = networkMonitor.getRecentEvents();
            assertTrue(events.stream()
                    .anyMatch(e -> e.getType() == NetworkEvent.EventType.PEER_UNHEALTHY));

            // Verify recovery attempt was made
            verify(connectionManager, timeout(1000))
                    .connectToPeer(eq("localhost"), eq(8080));

        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
    }

    @Test
    @DisplayName("Should maintain accurate network statistics")
    void testNetworkStatistics() {
        // Create multiple test peers
        Collection<PeerConnection> testPeers = Arrays.asList(
                new PeerConnection("peer-1", "host1", 8080),
                new PeerConnection("peer-2", "host2", 8081)
        );

        when(connectionManager.getAllPeers()).thenReturn(testPeers);

        // Let metrics collect
        try {
            TimeUnit.SECONDS.sleep(2);

            NetworkStats stats = networkMonitor.getNetworkStats();
            assertEquals(2, stats.getActivePeers(),
                    "Should report correct number of active peers");

            // Verify metrics are being collected
            assertTrue(stats.getAverageLatency() >= 0,
                    "Latency should be non-negative");
            assertTrue(stats.getMessageThroughput() >= 0,
                    "Throughput should be non-negative");

        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
    }

    @Test
    @DisplayName("Should properly handle peer recovery limits")
    void testPeerRecoveryLimits() {
        PeerConnection testPeer = new PeerConnection("failing-peer", "localhost", 8082);
        when(connectionManager.getAllPeers())
                .thenReturn(Arrays.asList(testPeer));

        // Simulate multiple failed recovery attempts
        for (int i = 0; i < 6; i++) {
            networkMonitor.recordEvent(new NetworkEvent(
                    NetworkEvent.EventType.RECOVERY_FAILED,
                    testPeer.getPeerId(),
                    "Recovery attempt " + (i + 1) + " failed"
            ));
        }

        List<NetworkEvent> events = networkMonitor.getRecentEvents();
        long recoveryAttempts = events.stream()
                .filter(e -> e.getType() == NetworkEvent.EventType.RECOVERY_FAILED)
                .count();

        assertTrue(recoveryAttempts <= 5,
                "Should not exceed maximum recovery attempts");
    }

    @Test
    @DisplayName("Should maintain event history within size limits")
    void testEventHistoryManagement() {
        // Generate many events
        for (int i = 0; i < 1500; i++) {
            networkMonitor.recordEvent(new NetworkEvent(
                    NetworkEvent.EventType.PEER_CONNECTED,
                    "test-peer-" + i,
                    "Test event " + i
            ));
        }

        List<NetworkEvent> events = networkMonitor.getRecentEvents();
        assertTrue(events.size() <= 1000,
                "Event history should not exceed maximum size");

        // Verify events are in chronological order
        assertTrue(events.stream()
                .reduce((a, b) -> {
                    assertTrue(a.getTimestamp().isBefore(b.getTimestamp())
                            || a.getTimestamp().equals(b.getTimestamp()));
                    return b;
                }).isPresent());
    }
}