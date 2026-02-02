package uk.co.hpnet.tascam.client;

import org.junit.jupiter.api.Test;
import uk.co.hpnet.tascam.model.Preset;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TascamTcpClientTest {

    @Test
    void listPresetsWithMultiplePresets() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Sunday Service", false),
            2, new FakeTascamServer.TestPreset("Weekday Mass", false),
            5, new FakeTascamServer.TestPreset("Evensong", true)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 2);
             TascamClient client = new TascamTcpClient()) {
            
            client.connect("localhost", server.getPort(), "");
            List<Preset> result = client.listPresets();

            assertEquals(3, result.size());
            
            Preset first = result.get(0);
            assertEquals(1, first.number());
            assertEquals("Sunday Service", first.name());
            assertFalse(first.locked());

            Preset second = result.get(1);
            assertEquals(2, second.number());
            assertEquals("Weekday Mass", second.name());

            Preset third = result.get(2);
            assertEquals(5, third.number());
            assertEquals("Evensong", third.name());
            assertTrue(third.locked());
        }
    }

    @Test
    void listPresetsWithNoPresets() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of();

        try (FakeTascamServer server = new FakeTascamServer(presets, 0);
             TascamClient client = new TascamTcpClient()) {
            
            client.connect("localhost", server.getPort(), "");
            List<Preset> result = client.listPresets();

            assertTrue(result.isEmpty());
        }
    }

    @Test
    void getCurrentPreset() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Sunday Service", false),
            2, new FakeTascamServer.TestPreset("Weekday Mass", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 2);
             TascamClient client = new TascamTcpClient()) {
            
            client.connect("localhost", server.getPort(), "");
            Optional<Preset> current = client.getCurrentPreset();

            assertTrue(current.isPresent());
            assertEquals(2, current.get().number());
            assertEquals("Weekday Mass", current.get().name());
        }
    }

    @Test
    void loginWithPassword() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Test Preset", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 1, "secret123");
             TascamClient client = new TascamTcpClient()) {
            
            client.connect("localhost", server.getPort(), "secret123");
            List<Preset> result = client.listPresets();

            assertEquals(1, result.size());
        }
    }

    @Test
    void loginWithWrongPasswordFails() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of();

        try (FakeTascamServer server = new FakeTascamServer(presets, 0, "correct")) {
            TascamClient client = new TascamTcpClient();
            
            assertThrows(IOException.class, () -> 
                client.connect("localhost", server.getPort(), "wrong"));
        }
    }

    @Test
    void connectionRefusedThrowsIOException() {
        TascamClient client = new TascamTcpClient();
        
        assertThrows(IOException.class, () -> 
            client.connect("localhost", 59999, "")); // Unlikely to be listening
    }
}
