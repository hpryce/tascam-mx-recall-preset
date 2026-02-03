package uk.co.hpnet.tascam.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.co.hpnet.tascam.model.Preset;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP implementation of TascamClient for communicating with Tascam MX-DCP series mixers.
 */
public class TascamTcpClient implements TascamClient {

    private static final Logger logger = LogManager.getLogger(TascamTcpClient.class);

    private static final int DEFAULT_TIMEOUT_MS = 10000;
    private static final AtomicInteger GLOBAL_CID_COUNTER = new AtomicInteger(1000);

    private final AtomicInteger cidCounter;
    private final long recallWaitMs;
    private final Sleeper sleeper;
    private final ProtocolParser parser = new ProtocolParser();
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    /**
     * Creates a client with custom recall wait time.
     *
     * @param recallWaitMs milliseconds to wait after recall before verification (0 to skip verification)
     */
    public TascamTcpClient(long recallWaitMs) {
        this(GLOBAL_CID_COUNTER, recallWaitMs, Sleeper.defaultSleeper());
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
            throw new TascamProtocolException("Unexpected response: " + response);
        }

        // Send password
        logger.debug("Sending password");
        sendRaw(password + "\r\n");

        // Read login result
        response = readLine();
        if (response == null) {
            throw new TascamProtocolException("No response after password");
        }
        if (response.contains("Another User Already Connected")) {
            throw new TascamProtocolException("Another user is already connected to the mixer");
        }
        if (!response.contains("Login Successful")) {
            throw new TascamProtocolException("Login failed: " + response);
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
        for (int i = 1; i <= ProtocolParser.MAX_PRESET_NUMBER; i += ProtocolParser.BATCH_SIZE) {
            String cmd = parser.buildPresetBatchCommand(i, generateCid());
            String response = sendCommand(cmd);
            presets.addAll(parser.parsePresetBatch(response));
        }

        presets.sort(Comparator.comparingInt(Preset::number));
        return presets;
    }

    @Override
    public Optional<Preset> getCurrentPreset() throws IOException {
        String cmd = parser.buildCurrentPresetCommand(generateCid());
        String response = sendCommand(cmd);
        return parser.parseCurrentPreset(response);
    }

    @Override
    public void recallPreset(int presetNumber) throws IOException {
        if (presetNumber < 1 || presetNumber > ProtocolParser.MAX_PRESET_NUMBER) {
            throw new IllegalArgumentException("Preset number must be between 1 and " + ProtocolParser.MAX_PRESET_NUMBER);
        }
        
        // Check if we're already on this preset
        Optional<Preset> currentBefore = getCurrentPreset();
        boolean alreadyOnPreset = currentBefore.isPresent() && currentBefore.get().number() == presetNumber;
        
        String cmd = parser.buildRecallCommand(presetNumber, generateCid());
        String response = sendCommand(cmd);
        
        // Response should be "OK SET CID:<id>"
        if (!response.startsWith("OK SET")) {
            throw new TascamProtocolException("Failed to recall preset: " + response);
        }
        
        // Wait for NOTIFY PRESET/CUR:<n> confirming the preset change is complete
        // If already on this preset, mixer won't send NOTIFY - skip waiting for it
        if (!alreadyOnPreset) {
            waitForPresetNotify(presetNumber);
        }
        
        // Wait for mixer to stabilize after preset load and verify
        if (recallWaitMs > 0) {
            sleeper.sleep(recallWaitMs);
            verifyPresetLoaded(presetNumber);
            logger.debug("Preset {} recalled and verified successfully", presetNumber);
        } else {
            logger.debug("Preset {} recall sent (verification skipped)", presetNumber);
        }
    }

    /**
     * Waits for the NOTIFY PRESET/CUR message confirming preset change.
     * The mixer sends multiple NOTIFYs (mutes, levels, etc.) before the preset NOTIFY.
     */
    private void waitForPresetNotify(int presetNumber) throws IOException {
        String expectedNotify = parser.buildPresetNotifyPrefix(presetNumber);
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

    /**
     * Verifies that the expected preset is now active.
     */
    private void verifyPresetLoaded(int expectedPresetNumber) throws IOException {
        Optional<Preset> current = getCurrentPreset();
        if (current.isEmpty()) {
            throw new PresetRecallException("Failed to verify preset after recall");
        }
        if (current.get().number() != expectedPresetNumber) {
            throw new PresetRecallException("Preset recall verification failed: expected " + expectedPresetNumber 
                + " but got " + current.get().number());
        }
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
            throw new TascamProtocolException("No response from device");
        }
        return response;
    }

    private String generateCid() {
        return String.valueOf(cidCounter.getAndIncrement());
    }
}
