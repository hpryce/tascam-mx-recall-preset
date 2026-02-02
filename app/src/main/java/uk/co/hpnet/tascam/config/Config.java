package uk.co.hpnet.tascam.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Reads configuration from ~/.tascam-preset.conf
 * 
 * Format (Java properties):
 * <pre>
 * host=192.168.1.100
 * port=54726
 * password=secret
 * </pre>
 */
public class Config {
    
    private static final String CONFIG_FILENAME = ".tascam-preset.conf";
    
    private final Optional<String> host;
    private final Optional<Integer> port;
    private final Optional<String> password;
    
    private Config(Optional<String> host, Optional<Integer> port, Optional<String> password) {
        this.host = host;
        this.port = port;
        this.password = password;
    }
    
    public Optional<String> host() {
        return host;
    }
    
    public Optional<Integer> port() {
        return port;
    }
    
    public Optional<String> password() {
        return password;
    }
    
    /**
     * Loads config from ~/.tascam-preset.conf if it exists.
     * Returns empty config if file doesn't exist.
     */
    public static Config load() {
        Path configPath = Path.of(System.getProperty("user.home"), CONFIG_FILENAME);
        
        if (!Files.exists(configPath)) {
            return empty();
        }
        
        try {
            Properties props = new Properties();
            props.load(Files.newBufferedReader(configPath));
            
            Optional<String> host = Optional.ofNullable(props.getProperty("host"));
            Optional<Integer> port = Optional.ofNullable(props.getProperty("port"))
                .map(Integer::parseInt);
            Optional<String> password = Optional.ofNullable(props.getProperty("password"));
            
            return new Config(host, port, password);
        } catch (IOException | NumberFormatException e) {
            // If config file is invalid, treat as empty
            return empty();
        }
    }
    
    /**
     * Returns an empty config with no values set.
     */
    public static Config empty() {
        return new Config(Optional.empty(), Optional.empty(), Optional.empty());
    }
}
