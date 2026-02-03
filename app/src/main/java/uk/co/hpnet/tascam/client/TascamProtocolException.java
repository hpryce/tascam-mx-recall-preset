package uk.co.hpnet.tascam.client;

import java.io.IOException;

/**
 * Thrown when the mixer returns an unexpected response or protocol error.
 * Extends IOException to distinguish from network-level failures.
 */
public class TascamProtocolException extends IOException {
    
    public TascamProtocolException(String message) {
        super(message);
    }
    
    public TascamProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
