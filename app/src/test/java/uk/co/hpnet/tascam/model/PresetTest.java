package uk.co.hpnet.tascam.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PresetTest {

    @Test
    void presetNumberTooLowThrows() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Preset(0, "Test", false));
    }

    @Test
    void presetNumberTooHighThrows() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Preset(51, "Test", false));
    }

    @Test
    void nullNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Preset(1, null, false));
    }

    @Test
    void blankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Preset(1, "   ", false));
    }
}
