package uk.co.hpnet.tascam.client;

import uk.co.hpnet.tascam.model.Preset;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and builds protocol messages for Tascam MX-DCP series mixers.
 */
public class ProtocolParser {

    private static final Pattern PRESET_NAME_PATTERN = Pattern.compile("PRESET/(\\d+)/NAME:\"([^\"]+)\"");
    private static final Pattern PRESET_LOCK_PATTERN = Pattern.compile("PRESET/(\\d+)/LOCK:(ON|OFF)");
    private static final Pattern PRESET_CLEARED_PATTERN = Pattern.compile("PRESET/(\\d+)/CLEARED:(TRUE|FALSE)");
    private static final Pattern CURRENT_PRESET_PATTERN = Pattern.compile("PRESET/CUR:(\\d+)");
    private static final Pattern CURRENT_NAME_PATTERN = Pattern.compile("PRESET/NAME:\"([^\"]+)\"");

    /**
     * Builds a GET command for a batch of presets.
     *
     * @param startPreset first preset number in batch
     * @param batchSize number of presets to query
     * @param maxPreset maximum preset number
     * @param cid command ID
     * @return the command string
     */
    public String buildPresetBatchCommand(int startPreset, int batchSize, int maxPreset, String cid) {
        StringBuilder cmd = new StringBuilder("GET");
        for (int j = startPreset; j < startPreset + batchSize && j <= maxPreset; j++) {
            cmd.append(" PRESET/").append(j).append("/NAME");
            cmd.append(" PRESET/").append(j).append("/LOCK");
            cmd.append(" PRESET/").append(j).append("/CLEARED");
        }
        cmd.append(" CID:").append(cid);
        return cmd.toString();
    }

    /**
     * Builds a GET command for current preset info.
     *
     * @param cid command ID
     * @return the command string
     */
    public String buildCurrentPresetCommand(String cid) {
        return "GET PRESET/CUR PRESET/NAME CID:" + cid;
    }

    /**
     * Builds a SET command to recall a preset.
     *
     * @param presetNumber preset to recall
     * @param cid command ID
     * @return the command string
     */
    public String buildRecallCommand(int presetNumber, String cid) {
        return "SET PRESET/LOAD:" + presetNumber + " CID:" + cid;
    }

    /**
     * Builds the expected NOTIFY string for a preset change.
     *
     * @param presetNumber the preset number
     * @return the expected notify prefix
     */
    public String buildPresetNotifyPrefix(int presetNumber) {
        return "NOTIFY PRESET/CUR:" + presetNumber;
    }

    /**
     * Parses a batch response containing preset info.
     *
     * @param response the raw response string
     * @return list of non-cleared presets found in the response
     */
    public List<Preset> parsePresetBatch(String response) {
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
        List<Preset> presets = new ArrayList<>();
        for (Integer num : names.keySet()) {
            if (!cleared.getOrDefault(num, true)) {
                presets.add(new Preset(num, names.get(num), locks.get(num)));
            }
        }
        return presets;
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
