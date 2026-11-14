package com.gajuk.wearcam.common;

import java.util.ArrayList;
import java.util.List;

/** Parsing/cycling for the camera mode list reported by the phone. */
public final class ModeList {

    private ModeList() {
    }

    public static List<String> parse(String csv) {
        List<String> modes = new ArrayList<>();
        if (csv == null) return modes;
        for (String part : csv.split(",")) {
            String mode = part.trim();
            if (!mode.isEmpty() && !modes.contains(mode)) modes.add(mode);
        }
        return modes;
    }

    /** Next mode after {@code current}; the first one when current is unknown. */
    public static String next(List<String> modes, String current) {
        if (modes == null || modes.isEmpty()) return null;
        int index = -1;
        if (current != null) {
            for (int i = 0; i < modes.size(); i++) {
                if (modes.get(i).equalsIgnoreCase(current)) {
                    index = i;
                    break;
                }
            }
        }
        return modes.get((index + 1) % modes.size());
    }
}
