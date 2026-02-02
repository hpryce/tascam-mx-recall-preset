package uk.co.hpnet.tascam.client;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fake Tascam server for testing. Simulates the device protocol.
 */
public class FakeTascamServer implements AutoCloseable {

    private static final Pattern GET_PATTERN = Pattern.compile("GET (.+) CID:(\\w+)");
    private static final Pattern PRESET_QUERY_PATTERN = Pattern.compile("PRESET/(\\d+)/(NAME|LOCK|CLEARED)");

    private final ServerSocket serverSocket;
    private final Map<Integer, TestPreset> presets;
    private final int currentPresetNumber;
    private final String password;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread serverThread;

    public record TestPreset(String name, boolean locked) {}

    public FakeTascamServer(Map<Integer, TestPreset> presets, int currentPresetNumber, String password) throws IOException {
        this.serverSocket = new ServerSocket(0); // Ephemeral port
        this.presets = new HashMap<>(presets);
        this.currentPresetNumber = currentPresetNumber;
        this.password = password;
        startServer();
    }

    public FakeTascamServer(Map<Integer, TestPreset> presets, int currentPresetNumber) throws IOException {
        this(presets, currentPresetNumber, "");
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    private void startServer() {
        serverThread = new Thread(() -> {
            while (running.get()) {
                try {
                    Socket client = serverSocket.accept();
                    handleClient(client);
                } catch (IOException e) {
                    if (running.get()) {
                        e.printStackTrace();
                    }
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void handleClient(Socket client) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {

            // Login handshake
            String line = reader.readLine(); // Initial CR+LF
            writer.print("Enter Password\r\n");
            writer.flush();

            String passwordAttempt = reader.readLine();
            if (!password.equals(passwordAttempt)) {
                writer.print("Login Failed\r\n");
                writer.flush();
                return;
            }

            writer.print("Login Successful\r\n");
            writer.flush();

            // Command loop
            while ((line = reader.readLine()) != null) {
                String response = handleCommand(line);
                writer.print(response + "\r\n");
                writer.flush();
            }
        }
    }

    private String handleCommand(String command) {
        Matcher getMatcher = GET_PATTERN.matcher(command);
        if (getMatcher.matches()) {
            String params = getMatcher.group(1);
            String cid = getMatcher.group(2);
            return handleGetCommand(params, cid);
        }
        return "NG " + command;
    }

    private String handleGetCommand(String params, String cid) {
        StringBuilder response = new StringBuilder("OK GET");

        // Handle PRESET/CUR and PRESET/NAME
        if (params.contains("PRESET/CUR")) {
            response.append(" PRESET/CUR:").append(currentPresetNumber);
        }
        if (params.contains("PRESET/NAME") && !params.contains("PRESET/NAME:")) {
            TestPreset current = presets.get(currentPresetNumber);
            if (current != null) {
                response.append(" PRESET/NAME:\"").append(current.name()).append("\"");
            }
        }

        // Handle individual preset queries
        Matcher presetMatcher = PRESET_QUERY_PATTERN.matcher(params);
        while (presetMatcher.find()) {
            int num = Integer.parseInt(presetMatcher.group(1));
            String field = presetMatcher.group(2);
            TestPreset preset = presets.get(num);

            switch (field) {
                case "NAME" -> {
                    if (preset != null) {
                        response.append(" PRESET/").append(num).append("/NAME:\"").append(preset.name()).append("\"");
                    } else {
                        response.append(" PRESET/").append(num).append("/NAME:ERR5");
                    }
                }
                case "LOCK" -> {
                    if (preset != null) {
                        response.append(" PRESET/").append(num).append("/LOCK:").append(preset.locked() ? "ON" : "OFF");
                    } else {
                        response.append(" PRESET/").append(num).append("/LOCK:ERR5");
                    }
                }
                case "CLEARED" -> {
                    response.append(" PRESET/").append(num).append("/CLEARED:").append(preset == null ? "TRUE" : "FALSE");
                }
            }
        }

        response.append(" CID:").append(cid).append(" ");
        return response.toString();
    }

    @Override
    public void close() {
        running.set(false);
        try {
            serverSocket.close();
        } catch (IOException e) {
            // Ignore
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }
}
