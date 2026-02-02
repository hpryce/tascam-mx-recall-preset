package uk.co.hpnet.tascam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {
    @Test 
    void mainRunsWithoutException() {
        // Test that help output works
        assertDoesNotThrow(() -> App.main(new String[]{"--help"}));
    }

    @Test
    void listSubcommandHelp() {
        assertDoesNotThrow(() -> App.main(new String[]{"list", "--help"}));
    }
}
