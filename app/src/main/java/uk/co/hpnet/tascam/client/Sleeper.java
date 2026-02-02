package uk.co.hpnet.tascam.client;

/**
 * Abstraction for sleeping/waiting, to allow mocking in tests.
 */
public interface Sleeper {
    
    /**
     * Sleep for the specified duration.
     * Implementations should handle InterruptedException internally.
     *
     * @param millis milliseconds to sleep
     * @throws RuntimeException if the sleep is interrupted
     */
    void sleep(long millis);
    
    /**
     * Default implementation using Thread.sleep.
     */
    static Sleeper defaultSleeper() {
        return millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Sleep interrupted", e);
            }
        };
    }
}
