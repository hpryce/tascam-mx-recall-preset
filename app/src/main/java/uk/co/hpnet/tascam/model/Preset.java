package uk.co.hpnet.tascam.model;

import java.util.Optional;

/**
 * Represents a preset stored on the Tascam mixer.
 *
 * @param number the preset slot number (1-50)
 * @param name the preset name
 * @param locked whether the preset is locked from modification (empty if unknown)
 */
public record Preset(int number, String name, Optional<Boolean> locked) {
    
    public Preset {
        if (number < 1 || number > 50) {
            throw new IllegalArgumentException("Preset number must be between 1 and 50");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Preset name cannot be null or blank");
        }
        if (locked == null) {
            throw new IllegalArgumentException("Locked must not be null (use Optional.empty())");
        }
    }

    /**
     * Convenience constructor when lock status is known.
     */
    public Preset(int number, String name, boolean locked) {
        this(number, name, Optional.of(locked));
    }

    /**
     * Convenience constructor when lock status is unknown.
     */
    public Preset(int number, String name) {
        this(number, name, Optional.empty());
    }
}
