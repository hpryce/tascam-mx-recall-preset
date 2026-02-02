package uk.co.hpnet.tascam;

import org.junit.jupiter.api.Test;
import uk.co.hpnet.tascam.client.FakeTascamServer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
            ByteArrayOutputStream outContent = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            System.setOut(new PrintStream(outContent));
            
            // Provide empty password on stdin
            ByteArrayInputStream inContent = new ByteArrayInputStream("\n".getBytes());
            System.setIn(inContent);
            
            try {
                App.main(new String[]{"list", "--host", "localhost", "-p", String.valueOf(server.getPort())});
            } catch (SecurityException e) {
                // System.exit() called - expected
            } finally {
                System.setOut(originalOut);
            }
            
            String output = outContent.toString();
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
            ByteArrayOutputStream outContent = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            System.setOut(new PrintStream(outContent));
            
            ByteArrayInputStream inContent = new ByteArrayInputStream("\n".getBytes());
            System.setIn(inContent);
            
            try {
                App.main(new String[]{"list", "--host", "localhost", "-p", String.valueOf(server.getPort())});
            } catch (SecurityException e) {
                // System.exit() called - expected
            } finally {
                System.setOut(originalOut);
            }
            
            String output = outContent.toString();
            assertTrue(output.contains("[locked]"), "Should show locked indicator");
        }
    }

    @Test
    void listCommandWithNoPresets() throws IOException {
        Map<Integer, FakeTascamServer.TestPreset> presets = Map.of();

        try (FakeTascamServer server = new FakeTascamServer(presets, 0)) {
            ByteArrayOutputStream outContent = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            System.setOut(new PrintStream(outContent));
            
            ByteArrayInputStream inContent = new ByteArrayInputStream("\n".getBytes());
            System.setIn(inContent);
            
            try {
                App.main(new String[]{"list", "--host", "localhost", "-p", String.valueOf(server.getPort())});
            } catch (SecurityException e) {
                // System.exit() called - expected
            } finally {
                System.setOut(originalOut);
            }
            
            String output = outContent.toString();
            assertTrue(output.contains("No presets found"), "Should show no presets message");
        }
    }
}
