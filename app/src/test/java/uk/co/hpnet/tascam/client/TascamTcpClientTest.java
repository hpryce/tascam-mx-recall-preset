package uk.co.hpnet.tascam.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.hpnet.tascam.model.Preset;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TascamTcpClientTest {

    @Mock
    private Sleeper mockSleeper;

    /**
     * Creates a test client with a mock sleeper.
     */
    private TascamTcpClient createTestClient() {
        return new TascamTcpClient(new AtomicInteger(1000), 5000, mockSleeper, new ProtocolParser());
    }

    /**
     * Creates a test client with no wait (for tests that don't need to verify sleep).
     */
    private TascamTcpClient createTestClientNoWait() {
        return new TascamTcpClient(new AtomicInteger(1000), 0, mockSleeper, new ProtocolParser());
    }

    @Test
    void listPresetsWithSinglePreset() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Default Mix", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 1);
             TascamClient client = createTestClientNoWait()) {
            
            client.connect("localhost", server.getPort(), "");
            List<Preset> result = client.listPresets();

            assertEquals(List.of(new Preset(1, "Default Mix", false)), result);
        }
    }

    @Test
    void listPresetsWithMultiplePresets() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Default Mix", false),
            2, new FakeTascamServer.TestPreset("Quiet Mode", false),
            5, new FakeTascamServer.TestPreset("Backup Config", true)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 2);
             TascamClient client = createTestClientNoWait()) {
            
            client.connect("localhost", server.getPort(), "");
            List<Preset> result = client.listPresets();

            assertEquals(List.of(
                new Preset(1, "Default Mix", false),
                new Preset(2, "Quiet Mode", false),
                new Preset(5, "Backup Config", true)
            ), result);
        }
    }

    @Test
    void listPresetsWithNoPresets() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of();

        try (FakeTascamServer server = new FakeTascamServer(presets, 0);
             TascamClient client = createTestClientNoWait()) {
            
            client.connect("localhost", server.getPort(), "");
            List<Preset> result = client.listPresets();

            assertEquals(List.of(), result);
        }
    }

    @Test
    void getCurrentPreset() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Default Mix", false),
            2, new FakeTascamServer.TestPreset("Quiet Mode", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 2);
             TascamClient client = createTestClientNoWait()) {
            
            client.connect("localhost", server.getPort(), "");
            Optional<Preset> current = client.getCurrentPreset();

            // Lock status unknown for current preset query
            assertEquals(Optional.of(new Preset(2, "Quiet Mode")), current);
        }
    }

    @Test
    void recallPresetChangesCurrentPreset() throws Exception {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Default Mix", false),
            2, new FakeTascamServer.TestPreset("Quiet Mode", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 2);
             TascamClient client = createTestClient()) {
            
            client.connect("localhost", server.getPort(), "");
            
            // Initially on preset 2
            assertEquals(2, server.getCurrentPresetNumber());
            
            // Recall preset 1
            client.recallPreset(1);
            
            // Server should now be on preset 1
            assertEquals(1, server.getCurrentPresetNumber());
            
            // Verify sleep was called with correct duration
            verify(mockSleeper).sleep(5000);
        }
    }

    @Test
    void recallPresetWaitsForStabilization() throws Exception {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Default Mix", false),
            2, new FakeTascamServer.TestPreset("Quiet Mode", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 1);
             TascamClient client = createTestClient()) {
            
            client.connect("localhost", server.getPort(), "");
            
            // Recall preset 2
            client.recallPreset(2);
            
            // Verify the sleeper was called exactly once with 5000ms
            verify(mockSleeper, times(1)).sleep(5000);
            
            // Client should report preset 2 as current
            Optional<Preset> current = client.getCurrentPreset();
            assertEquals(2, current.orElseThrow().number());
        }
    }

    @Test
    void recallCurrentPresetStillRecallsButSkipsNotifyWait() throws Exception {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Default Mix", false),
            2, new FakeTascamServer.TestPreset("Quiet Mode", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 2);
             TascamClient client = createTestClient()) {
            
            client.connect("localhost", server.getPort(), "");
            
            // Already on preset 2, recall preset 2 should still recall (settings may have changed)
            // but skip waiting for NOTIFY since mixer won't send one
            client.recallPreset(2);
            
            // Sleep IS called since we still wait for stabilization
            verify(mockSleeper).sleep(5000);
            
            // Still on preset 2
            assertEquals(2, server.getCurrentPresetNumber());
        }
    }

    @Test
    void recallPresetHandlesMultipleNotifications() throws Exception {
        // This test verifies that the client correctly waits for NOTIFY PRESET/CUR
        // and ignores other NOTIFYs (mutes, levels, etc.) that come before it
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Default Mix", false),
            2, new FakeTascamServer.TestPreset("Quiet Mode", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 1);
             TascamClient client = createTestClient()) {
            
            client.connect("localhost", server.getPort(), "");
            
            // Recall preset 2 - FakeTascamServer sends multiple NOTIFYs before preset NOTIFY
            client.recallPreset(2);
            
            // Should complete successfully despite multiple NOTIFYs
            assertEquals(2, server.getCurrentPresetNumber());
            verify(mockSleeper).sleep(5000);
        }
    }

    @Test
    void recallPresetWithInvalidNumberThrows() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Test", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 1);
             TascamClient client = createTestClientNoWait()) {
            
            client.connect("localhost", server.getPort(), "");
            
            assertThrows(IllegalArgumentException.class, () -> client.recallPreset(0));
            assertThrows(IllegalArgumentException.class, () -> client.recallPreset(51));
        }
    }

    @Test
    void loginWithPassword() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Test Preset", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 1, "secret123");
             TascamClient client = createTestClientNoWait()) {
            
            client.connect("localhost", server.getPort(), "secret123");
            List<Preset> result = client.listPresets();

            assertEquals(List.of(new Preset(1, "Test Preset", false)), result);
        }
    }

    @Test
    void loginWithWrongPasswordFails() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of();

        try (FakeTascamServer server = new FakeTascamServer(presets, 0, "correct")) {
            TascamClient client = createTestClientNoWait();
            
            assertThrows(IOException.class, () -> 
                client.connect("localhost", server.getPort(), "wrong"));
        }
    }

    @Test
    void connectionRefusedThrowsIOException() {
        TascamClient client = createTestClientNoWait();
        
        // Port 70 (gopher) - unlikely to be listening, requires root to bind
        assertThrows(IOException.class, () -> 
            client.connect("localhost", 70, ""));
    }
}
