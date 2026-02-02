package uk.co.hpnet.tascam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppTest {
    @Test 
    void appClassExists() {
        // Simple smoke test that the main class can be instantiated
        assertDoesNotThrow(() -> App.class.getDeclaredConstructor());
    }
}
