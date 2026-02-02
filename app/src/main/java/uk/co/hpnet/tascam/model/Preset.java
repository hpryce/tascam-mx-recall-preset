package uk.co.hpnet.tascam.model;

/**
 * Represents a preset stored on the Tascam mixer.
 *
 * @param number the preset slot number (1-50)
 * @param name the preset name
 * @param locked whether the preset is locked from modification
 */
public record Preset(int number, String name, boolean locked) {
    
    public Preset {
        if (number < 1 || number > 50) {
            throw new IllegalArgumentException("Preset number must be between 1 and 50");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Preset name cannot be null or blank");
        }
    }
}
