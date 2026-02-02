package uk.co.hpnet.tascam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppTest {
    @Test 
    void mainRunsWithoutException() {
        assertDoesNotThrow(() -> App.main(new String[]{}));
    }
}
