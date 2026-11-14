package com.gajuk.wearcam.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Learned camera mapping: which node (or screen rect) is the shutter, the record button, each
 * mode item and each zoom preset, for one camera package. Produced by the one-time calibration
 * wizard and used by the phone automation instead of guessing.
 */
public final class Calibration {

    private static final String MAGIC = "wearcam-calibration-v1";

    public static final class Entry {
        public final String role;
        public final String text;
        public final String desc;
        public final String id;
        public final int left;
        public final int top;
        public final int right;
        public final int bottom;

        public Entry(String role, String text, String desc, String id,
                     int left, int top, int right, int bottom) {
            this.role = role;
            this.text = text;
            this.desc = desc;
            this.id = id;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public boolean hasBounds() {
            return right > left && bottom > top;
        }
    }

    public final String packageName;
    public final List<Entry> entries;

    public Calibration(String packageName, List<Entry> entries) {
        this.packageName = packageName == null ? "" : packageName;
        this.entries = entries == null ? new ArrayList<>() : entries;
    }

    public Entry entry(String role) {
        for (Entry entry : entries) {
            if (entry.role.equalsIgnoreCase(role)) return entry;
        }
        return null;
    }

    public List<Entry> entries(String rolePrefix) {
        List<Entry> matches = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.role.startsWith(rolePrefix)) matches.add(entry);
        }
        return matches;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public String encode() {
        StringBuilder out = new StringBuilder();
        out.append(MAGIC).append('\n');
        out.append(escape(packageName)).append('\n');
        for (Entry entry : entries) {
            out.append(escape(entry.role)).append('|')
                    .append(escape(entry.text)).append('|')
                    .append(escape(entry.desc)).append('|')
                    .append(escape(entry.id)).append('|')
                    .append(entry.left).append(',').append(entry.top).append(',')
                    .append(entry.right).append(',').append(entry.bottom).append('\n');
        }
        return out.toString();
    }

    public static Calibration decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        String[] lines = encoded.split("\n");
        if (lines.length < 2 || !MAGIC.equals(lines[0])) return null;
        String pkg = unescape(lines[1]);
        List<Entry> entries = new ArrayList<>();
        for (int i = 2; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\|", -1);
            if (parts.length != 5) continue;
            String[] bounds = parts[4].split(",");
            if (bounds.length != 4) continue;
            try {
                entries.add(new Entry(
                        unescape(parts[0]), unescape(parts[1]), unescape(parts[2]),
                        unescape(parts[3]),
                        Integer.parseInt(bounds[0]), Integer.parseInt(bounds[1]),
                        Integer.parseInt(bounds[2]), Integer.parseInt(bounds[3])));
            } catch (NumberFormatException ignored) {
            }
        }
        return new Calibration(pkg, entries);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("%", "%25").replace("|", "%7C").replace("\n", "%0A");
    }

    private static String unescape(String value) {
        if (value == null) return "";
        return value.replace("%0A", "\n").replace("%7C", "|").replace("%25", "%");
    }
}
