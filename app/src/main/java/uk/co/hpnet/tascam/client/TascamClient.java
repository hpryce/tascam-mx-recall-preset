package uk.co.hpnet.tascam.client;

import uk.co.hpnet.tascam.model.Preset;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Client interface for communicating with Tascam MX-DCP series mixers.
 */
public interface TascamClient extends AutoCloseable {

    /**
     * Connect to the mixer and authenticate.
     *
     * @param host the hostname or IP address
     * @param port the TCP port (typically 54726)
     * @param password the login password (empty string if none)
     * @throws IOException if connection or authentication fails
     */
    void connect(String host, int port, String password) throws IOException;

    /**
     * Disconnect from the mixer.
     */
    @Override
    void close();

    /**
     * Get all non-empty presets from the mixer.
     *
     * @return list of presets, sorted by number
     * @throws IOException if communication fails
     */
    List<Preset> listPresets() throws IOException;

    /**
     * Get the currently active preset.
     *
     * @return the current preset, or empty if no preset is active
     * @throws IOException if communication fails
     */
    Optional<Preset> getCurrentPreset() throws IOException;
}
