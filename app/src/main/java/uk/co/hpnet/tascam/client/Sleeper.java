package uk.co.hpnet.tascam.client;

/**
 * Abstraction for sleeping/waiting, to allow mocking in tests.
 */
public interface Sleeper {
    
    /**
     * Sleep for the specified duration.
     *
     * @param millis milliseconds to sleep
     * @throws InterruptedException if the sleep is interrupted
     */
    void sleep(long millis) throws InterruptedException;
    
    /**
     * Default implementation using Thread.sleep.
     */
    static Sleeper defaultSleeper() {
        return Thread::sleep;
    }
}
