package uk.co.hpnet.tascam.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hpnet.tascam.model.Preset;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TCP implementation of TascamClient for communicating with Tascam MX-DCP series mixers.
 */
public class TascamTcpClient implements TascamClient {

    private static final Logger logger = LogManager.getLogger(TascamTcpClient.class);

    private static final int DEFAULT_TIMEOUT_MS = 10000;
    private static final long DEFAULT_RECALL_WAIT_MS = 5000;
    private static final AtomicInteger GLOBAL_CID_COUNTER = new AtomicInteger(1000);
    
    private static final Pattern PRESET_NAME_PATTERN = Pattern.compile("PRESET/(\\d+)/NAME:\"([^\"]+)\"");
    private static final Pattern PRESET_LOCK_PATTERN = Pattern.compile("PRESET/(\\d+)/LOCK:(ON|OFF)");
    private static final Pattern PRESET_CLEARED_PATTERN = Pattern.compile("PRESET/(\\d+)/CLEARED:(TRUE|FALSE)");
    private static final Pattern CURRENT_PRESET_PATTERN = Pattern.compile("PRESET/CUR:(\\d+)");
    private static final Pattern CURRENT_NAME_PATTERN = Pattern.compile("PRESET/NAME:\"([^\"]+)\"");

    private final AtomicInteger cidCounter;
    private final long recallWaitMs;
    private final Sleeper sleeper;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    /**
     * Creates a client using the global CID counter and default wait times.
     */
    public TascamTcpClient() {
        this(GLOBAL_CID_COUNTER, DEFAULT_RECALL_WAIT_MS, Sleeper.defaultSleeper());
    }

    /**
     * Creates a client with custom dependencies (for testing).
     */
    TascamTcpClient(AtomicInteger cidCounter, long recallWaitMs, Sleeper sleeper) {
        this.cidCounter = cidCounter;
        this.recallWaitMs = recallWaitMs;
        this.sleeper = sleeper;
    }

    @Override
    public void connect(String host, int port, String password) throws IOException {
        logger.debug("Connecting to {}:{}", host, port);
        socket = new Socket(host, port);
        socket.setSoTimeout(DEFAULT_TIMEOUT_MS);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        // Send initial CR+LF to start login
        sendRaw("\r\n");

        // Read "Enter Password" prompt
        String response = readLine();
        if (response == null || !response.contains("Enter Password")) {
            throw new IOException("Unexpected response: " + response);
        }

        // Send password
        logger.debug("Sending password");
        sendRaw(password + "\r\n");

        // Read login result
        response = readLine();
        if (response == null) {
            throw new IOException("No response after password");
        }
        if (response.contains("Another User Already Connected")) {
            throw new IOException("Another user is already connected to the mixer");
        }
        if (!response.contains("Login Successful")) {
            throw new IOException("Login failed: " + response);
        }
        logger.debug("Login successful");
    }

    @Override
    public void close() {
        logger.debug("Closing connection");
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore close errors
        }
    }

    @Override
    public List<Preset> listPresets() throws IOException {
        List<Preset> presets = new ArrayList<>();
        
        // Query presets in batches to stay under 1024 byte limit
        for (int i = 1; i <= 50; i += 5) {
            StringBuilder cmd = new StringBuilder("GET");
            for (int j = i; j < i + 5 && j <= 50; j++) {
                cmd.append(" PRESET/").append(j).append("/NAME");
                cmd.append(" PRESET/").append(j).append("/LOCK");
                cmd.append(" PRESET/").append(j).append("/CLEARED");
            }
            cmd.append(" CID:").append(generateCid());
            
            String response = sendCommand(cmd.toString());
            parsePresetResponse(response, presets);
        }

        presets.sort(Comparator.comparingInt(Preset::number));
        return presets;
    }

    @Override
    public Optional<Preset> getCurrentPreset() throws IOException {
        String cid = generateCid();
        String response = sendCommand("GET PRESET/CUR PRESET/NAME CID:" + cid);
        
        Matcher curMatcher = CURRENT_PRESET_PATTERN.matcher(response);
        Matcher nameMatcher = CURRENT_NAME_PATTERN.matcher(response);
        
        if (!curMatcher.find() || !nameMatcher.find()) {
            return Optional.empty();
        }

        int number = Integer.parseInt(curMatcher.group(1));
        String name = nameMatcher.group(1);
        // Lock status not queried for current preset - use Optional.empty()
        return Optional.of(new Preset(number, name));
    }

    @Override
    public void recallPreset(int presetNumber) throws IOException {
        if (presetNumber < 1 || presetNumber > 50) {
            throw new IllegalArgumentException("Preset number must be between 1 and 50");
        }
        
        // Check if we're already on this preset
        Optional<Preset> currentBefore = getCurrentPreset();
        boolean alreadyOnPreset = currentBefore.isPresent() && currentBefore.get().number() == presetNumber;
        
        String cid = generateCid();
        String response = sendCommand("SET PRESET/LOAD:" + presetNumber + " CID:" + cid);
        
        // Response should be "OK SET CID:<id>"
        if (!response.startsWith("OK SET")) {
            throw new IOException("Failed to recall preset: " + response);
        }
        
        // Wait for NOTIFY PRESET/CUR:<n> confirming the preset change is complete
        // If already on this preset, mixer won't send NOTIFY - skip waiting for it
        if (!alreadyOnPreset) {
            // The mixer sends multiple NOTIFYs (mutes, levels, etc.) before the preset NOTIFY
            String expectedNotify = "NOTIFY PRESET/CUR:" + presetNumber;
            String notify;
            while ((notify = readLine()) != null) {
                logger.debug("Received: {}", notify);
                if (notify.startsWith(expectedNotify)) {
                    logger.debug("Preset change confirmed: {}", notify);
                    break;
                }
                // Continue reading other NOTIFYs until we get the preset one
            }
        }
        
        // Wait for mixer to stabilize after preset load
        if (recallWaitMs > 0) {
            sleeper.sleep(recallWaitMs);
        }
        
        // Verify the preset was actually loaded
        Optional<Preset> current = getCurrentPreset();
        if (current.isEmpty()) {
            throw new PresetRecallException("Failed to verify preset after recall");
        }
        if (current.get().number() != presetNumber) {
            throw new PresetRecallException("Preset recall verification failed: expected " + presetNumber 
                + " but got " + current.get().number());
        }
        
        logger.debug("Preset {} recalled and verified successfully", presetNumber);
    }

    private void sendRaw(String data) {
        writer.print(data);
        writer.flush();
    }

    private String readLine() throws IOException {
        String line = reader.readLine();
        if (line != null) {
            logger.debug("RECV: {}", line);
        }
        return line;
    }

    private String sendCommand(String command) throws IOException {
        logger.debug("SEND: {}", command);
        writer.print(command + "\r\n");
        writer.flush();
        
        String response = readLine();
        if (response == null) {
            throw new IOException("No response from device");
        }
        return response;
    }

    private void parsePresetResponse(String response, List<Preset> presets) {
        // Extract preset data from response
        Map<Integer, String> names = new HashMap<>();
        Map<Integer, Boolean> locks = new HashMap<>();
        Map<Integer, Boolean> cleared = new HashMap<>();

        Matcher nameMatcher = PRESET_NAME_PATTERN.matcher(response);
        while (nameMatcher.find()) {
            int num = Integer.parseInt(nameMatcher.group(1));
            names.put(num, nameMatcher.group(2));
        }

        Matcher lockMatcher = PRESET_LOCK_PATTERN.matcher(response);
        while (lockMatcher.find()) {
            int num = Integer.parseInt(lockMatcher.group(1));
            locks.put(num, "ON".equals(lockMatcher.group(2)));
        }

        Matcher clearedMatcher = PRESET_CLEARED_PATTERN.matcher(response);
        while (clearedMatcher.find()) {
            int num = Integer.parseInt(clearedMatcher.group(1));
            cleared.put(num, "TRUE".equals(clearedMatcher.group(2)));
        }

        // Build preset objects for non-cleared slots
        for (Integer num : names.keySet()) {
            if (!cleared.getOrDefault(num, true)) {
                presets.add(new Preset(num, names.get(num), locks.get(num)));
            }
        }
    }

    private String generateCid() {
        return String.valueOf(cidCounter.getAndIncrement());
    }
}
