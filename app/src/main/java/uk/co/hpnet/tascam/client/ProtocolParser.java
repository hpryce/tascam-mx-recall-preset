package uk.co.hpnet.tascam.client;

import uk.co.hpnet.tascam.model.Preset;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses protocol responses from Tascam MX-DCP series mixers.
 */
public class ProtocolParser {

    private static final Pattern PRESET_NAME_PATTERN = Pattern.compile("PRESET/(\\d+)/NAME:\"([^\"]+)\"");
    private static final Pattern PRESET_LOCK_PATTERN = Pattern.compile("PRESET/(\\d+)/LOCK:(ON|OFF)");
    private static final Pattern PRESET_CLEARED_PATTERN = Pattern.compile("PRESET/(\\d+)/CLEARED:(TRUE|FALSE)");
    private static final Pattern CURRENT_PRESET_PATTERN = Pattern.compile("PRESET/CUR:(\\d+)");
    private static final Pattern CURRENT_NAME_PATTERN = Pattern.compile("PRESET/NAME:\"([^\"]+)\"");

    /**
     * Parses a batch response containing preset info and adds non-cleared presets to the list.
     *
     * @param response the raw response string
     * @param presets list to add parsed presets to
     */
    public void parsePresetBatch(String response, List<Preset> presets) {
        Map<Integer, String> names = new HashMap<>();
        Map<Integer, Boolean> locks = new HashMap<>();
        Map<Integer, Boolean> cleared = new HashMap<>();

        Matcher nameMatcher = PRESET_NAME_PATTERN.matcher(response);
        while (nameMatcher.find()) {
            int num = Integer.parseInt(nameMatcher.group(1));
            names.put(num, nameMatcher.group(2));
        }

        Matcher lockMatcher = PRESET_LOCK_PATTERN.matcher(response);
        while (lockMatcher.find()) {
            int num = Integer.parseInt(lockMatcher.group(1));
            locks.put(num, "ON".equals(lockMatcher.group(2)));
        }

        Matcher clearedMatcher = PRESET_CLEARED_PATTERN.matcher(response);
        while (clearedMatcher.find()) {
            int num = Integer.parseInt(clearedMatcher.group(1));
            cleared.put(num, "TRUE".equals(clearedMatcher.group(2)));
        }

        // Build preset objects for non-cleared slots
        for (Integer num : names.keySet()) {
            if (!cleared.getOrDefault(num, true)) {
                presets.add(new Preset(num, names.get(num), locks.get(num)));
            }
        }
    }

    /**
     * Parses the current preset response.
     *
     * @param response the raw response string
     * @return the current preset, or empty if not found
     */
    public Optional<Preset> parseCurrentPreset(String response) {
        Matcher curMatcher = CURRENT_PRESET_PATTERN.matcher(response);
        Matcher nameMatcher = CURRENT_NAME_PATTERN.matcher(response);
        
        if (!curMatcher.find() || !nameMatcher.find()) {
            return Optional.empty();
        }

        int number = Integer.parseInt(curMatcher.group(1));
        String name = nameMatcher.group(1);
        return Optional.of(new Preset(number, name));
    }
}
