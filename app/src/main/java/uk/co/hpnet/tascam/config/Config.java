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
    
    private final String host;
    private final Integer port;
    private final String password;
    
    private Config(String host, Integer port, String password) {
        this.host = host;
        this.port = port;
        this.password = password;
    }
    
    public Optional<String> host() {
        return Optional.ofNullable(host);
    }
    
    public Optional<Integer> port() {
        return Optional.ofNullable(port);
    }
    
    public Optional<String> password() {
        return Optional.ofNullable(password);
    }
    
    /**
     * Loads config from ~/.tascam-preset.conf if it exists.
     * Returns empty config if file doesn't exist.
     */
    public static Config load() {
        Path configPath = Path.of(System.getProperty("user.home"), CONFIG_FILENAME);
        
        if (!Files.exists(configPath)) {
            return new Config(null, null, null);
        }
        
        try {
            Properties props = new Properties();
            props.load(Files.newBufferedReader(configPath));
            
            String host = props.getProperty("host");
            Integer port = props.containsKey("port") 
                ? Integer.parseInt(props.getProperty("port")) 
                : null;
            String password = props.getProperty("password");
            
            return new Config(host, port, password);
        } catch (IOException | NumberFormatException e) {
            // If config file is invalid, treat as empty
            return new Config(null, null, null);
        }
    }
}
