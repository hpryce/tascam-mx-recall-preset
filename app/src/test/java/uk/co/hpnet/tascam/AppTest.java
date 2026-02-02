package uk.co.hpnet.tascam;

import org.junit.jupiter.api.Test;
import uk.co.hpnet.tascam.client.FakeTascamServer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    @Test
    void listCommandShowsPresets() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Default Mix", false),
            2, new FakeTascamServer.TestPreset("Quiet Mode", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 2)) {
            String output = runWithStdin("\n", "list", "--host", "localhost", "-p", String.valueOf(server.getPort()));
            
            assertTrue(output.contains("1: \"Default Mix\""), "Should show preset 1");
            assertTrue(output.contains("2: \"Quiet Mode\""), "Should show preset 2");
            assertTrue(output.contains("*"), "Should mark current preset");
        }
    }

    @Test
    void listCommandWithLockedPreset() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Locked Config", true)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 1)) {
            String output = runWithStdin("\n", "list", "--host", "localhost", "-p", String.valueOf(server.getPort()));
            
            assertTrue(output.contains("[locked]"), "Should show locked indicator");
        }
    }

    @Test
    void listCommandWithNoPresets() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of();

        try (FakeTascamServer server = new FakeTascamServer(presets, 0)) {
            String output = runWithStdin("\n", "list", "--host", "localhost", "-p", String.valueOf(server.getPort()));
            
            assertTrue(output.contains("No presets found"), "Should show no presets message");
        }
    }

    /**
     * Runs App.run() with captured stdout and provided stdin.
     */
    private String runWithStdin(String stdinContent, String... args) {
        PrintStream originalOut = System.out;
        InputStream originalIn = System.in;
        
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setIn(new ByteArrayInputStream(stdinContent.getBytes()));
        
        try {
            App.run(args);
        } finally {
            System.setOut(originalOut);
            System.setIn(originalIn);
        }
        
        return outContent.toString();
    }
}
