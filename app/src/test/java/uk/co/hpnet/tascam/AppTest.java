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
            CapturedOutput output = runWithStdin("\n", "list", "--host", "localhost", "-p", String.valueOf(server.getPort()));
            
            assertTrue(output.stdout.contains("1: \"Default Mix\""), "Should show preset 1");
            assertTrue(output.stdout.contains("2: \"Quiet Mode\""), "Should show preset 2");
            assertTrue(output.stdout.contains("*"), "Should mark current preset");
        }
    }

    @Test
    void listCommandWithLockedPreset() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Locked Config", true)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 1)) {
            CapturedOutput output = runWithStdin("\n", "list", "--host", "localhost", "-p", String.valueOf(server.getPort()));
            
            assertTrue(output.stdout.contains("[locked]"), "Should show locked indicator");
        }
    }

    @Test
    void listCommandWithNoPresets() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of();

        try (FakeTascamServer server = new FakeTascamServer(presets, 0)) {
            CapturedOutput output = runWithStdin("\n", "list", "--host", "localhost", "-p", String.valueOf(server.getPort()));
            
            assertTrue(output.stdout.contains("No presets found"), "Should show no presets message");
        }
    }

    @Test
    void debugFlagOutputsProtocolMessages() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Test Preset", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 1)) {
            CapturedOutput output = runWithStdin("\n", "list", "--debug", "--host", "localhost", "-p", String.valueOf(server.getPort()));
            
            // Verify stdout still has normal output
            assertTrue(output.stdout.contains("1: \"Test Preset\""), "Should still list presets");
            
            // Verify stderr has debug protocol messages
            assertTrue(output.stderr.contains("SEND:"), 
                "Debug output should contain SEND messages, got stderr: " + output.stderr);
            assertTrue(output.stderr.contains("RECV:"), 
                "Debug output should contain RECV messages, got stderr: " + output.stderr);
            assertTrue(output.stderr.contains("GET PRESET"), 
                "Debug output should show protocol commands");
        }
    }

    @Test
    void noDebugOutputWithoutFlag() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of(
            1, new FakeTascamServer.TestPreset("Test Preset", false)
        );

        try (FakeTascamServer server = new FakeTascamServer(presets, 1)) {
            CapturedOutput output = runWithStdin("\n", "list", "--host", "localhost", "-p", String.valueOf(server.getPort()));
            
            // Without --debug, stderr should be empty (no protocol messages)
            assertFalse(output.stderr.contains("SEND:"), "Should not have debug output without --debug flag");
            assertFalse(output.stderr.contains("RECV:"), "Should not have debug output without --debug flag");
        }
    }

    record CapturedOutput(String stdout, String stderr) {}

    /**
     * Runs App.run() with captured stdout/stderr and provided stdin.
     */
    private CapturedOutput runWithStdin(String stdinContent, String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        InputStream originalIn = System.in;
        
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
        System.setIn(new ByteArrayInputStream(stdinContent.getBytes()));
        
        try {
            App.run(args);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            System.setIn(originalIn);
        }
        
        return new CapturedOutput(outContent.toString(), errContent.toString());
    }
}
