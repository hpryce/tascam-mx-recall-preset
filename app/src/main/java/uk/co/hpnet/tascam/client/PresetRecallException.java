package uk.co.hpnet.tascam.client;

/**
 * Thrown when a preset recall completes but verification fails.
 * This is a runtime exception because the IO completed successfully,
 * but the mixer state is not as expected.
 */
public class PresetRecallException extends RuntimeException {
    
    public PresetRecallException(String message) {
        super(message);
    }
    
    public PresetRecallException(String message, Throwable cause) {
        super(message, cause);
    }
}
