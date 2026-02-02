package uk.co.hpnet.tascam.client;

import org.junit.jupiter.api.Test;
import uk.co.hpnet.tascam.model.Preset;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TascamTcpClientTest {

    @Test
    void listPresetsWithSinglePreset() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Default Mix", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 1);
             TascamClient client = new TascamTcpClient(new AtomicInteger(1000))) {
            
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
             TascamClient client = new TascamTcpClient(new AtomicInteger(1000))) {
            
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
             TascamClient client = new TascamTcpClient(new AtomicInteger(1000))) {
            
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
             TascamClient client = new TascamTcpClient(new AtomicInteger(1000))) {
            
            client.connect("localhost", server.getPort(), "");
            Optional<Preset> current = client.getCurrentPreset();

            // Lock status unknown for current preset query
            assertEquals(Optional.of(new Preset(2, "Quiet Mode")), current);
        }
    }

    @Test
    void recallPresetChangesCurrentPreset() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Default Mix", false),
            2, new FakeTascamServer.TestPreset("Quiet Mode", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 2);
             TascamClient client = new TascamTcpClient(new AtomicInteger(1000))) {
            
            client.connect("localhost", server.getPort(), "");
            
            // Initially on preset 2
            assertEquals(2, server.getCurrentPresetNumber());
            
            // Recall preset 1
            client.recallPreset(1);
            
            // Server should now be on preset 1
            assertEquals(1, server.getCurrentPresetNumber());
        }
    }

    @Test
    void recallPresetWithInvalidNumberThrows() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Test", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 1);
             TascamClient client = new TascamTcpClient(new AtomicInteger(1000))) {
            
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
             TascamClient client = new TascamTcpClient(new AtomicInteger(1000))) {
            
            client.connect("localhost", server.getPort(), "secret123");
            List<Preset> result = client.listPresets();

            assertEquals(List.of(new Preset(1, "Test Preset", false)), result);
        }
    }

    @Test
    void loginWithWrongPasswordFails() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of();

        try (FakeTascamServer server = new FakeTascamServer(presets, 0, "correct")) {
            TascamClient client = new TascamTcpClient(new AtomicInteger(1000));
            
            assertThrows(IOException.class, () -> 
                client.connect("localhost", server.getPort(), "wrong"));
        }
    }

    @Test
    void connectionRefusedThrowsIOException() {
        TascamClient client = new TascamTcpClient(new AtomicInteger(1000));
        
        // Port 70 (gopher) - unlikely to be listening, requires root to bind
        assertThrows(IOException.class, () -> 
            client.connect("localhost", 70, ""));
    }
}
