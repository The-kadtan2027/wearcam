package com.gajuk.wearcam;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Generic accessibility-tree automation shared by every camera app.
 *
 * <p>Nothing here is brand-specific: the shutter, record button, mode carousel items and zoom
 * presets are located by text/content-description/view-id, with the caller's profile supplying
 * the vocabulary. All lookups are breadth-first and bounded, so a deep camera UI cannot stall
 * the accessibility thread.
 */
public final class CameraUi {

    private static final int MAX_NODES = 600;

    private CameraUi() {
    }

    private static String text(AccessibilityNodeInfo node) {
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.length() > 0) return desc.toString().toLowerCase(Locale.ROOT);
        CharSequence label = node.getText();
        if (label != null && label.length() > 0) return label.toString().toLowerCase(Locale.ROOT);
        return null;
    }

    private static List<AccessibilityNodeInfo> breadthFirst(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        if (root == null) return nodes;
        nodes.add(root);
        for (int i = 0; i < nodes.size() && nodes.size() < MAX_NODES; i++) {
            AccessibilityNodeInfo node = nodes.get(i);
            int children = node.getChildCount();
            for (int c = 0; c < children; c++) {
                AccessibilityNodeInfo child = node.getChild(c);
                if (child != null) nodes.add(child);
            }
        }
        return nodes;
    }

    /** First node whose text/description contains a hint, or whose view id contains an id hint. */
    public static AccessibilityNodeInfo find(AccessibilityNodeInfo root, String[] hints,
                                             String[] idHints) {
        for (AccessibilityNodeInfo node : breadthFirst(root)) {
            String value = text(node);
            if (value != null) {
                for (String hint : hints) {
                    if (value.contains(hint)) return node;
                }
            }
            String id = node.getViewIdResourceName();
            if (id != null) {
                String lower = id.toLowerCase(Locale.ROOT);
                for (String hint : idHints) {
                    if (lower.contains(hint)) return node;
                }
            }
        }
        return null;
    }

    public static boolean click(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < 6 && current != null; i++) {
            if (current.isClickable()
                    && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    public static AccessibilityNodeInfo findMode(AccessibilityNodeInfo root, String name) {
        if (root == null || name == null) return null;
        for (AccessibilityNodeInfo node : breadthFirst(root)) {
            CharSequence label = node.getText();
            if (label != null && label.toString().trim().equalsIgnoreCase(name)) return node;
            CharSequence desc = node.getContentDescription();
            if (desc != null && desc.toString().trim().equalsIgnoreCase(name)) return node;
        }
        return null;
    }

    /** Mode reported by the carousel's selected item, if the app exposes one. */
    public static String selectedMode(AccessibilityNodeInfo root, CameraProfile profile) {
        for (AccessibilityNodeInfo node : breadthFirst(root)) {
            if (!node.isSelected()) continue;
            CharSequence label = node.getText();
            if (label != null && profile.isModeLabel(label.toString())) {
                return label.toString().trim().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    /** Mode labels visible in the tree, in visual order. */
    public static List<String> discoverModes(AccessibilityNodeInfo root, CameraProfile profile) {
        Set<String> modes = new LinkedHashSet<>();
        for (AccessibilityNodeInfo node : breadthFirst(root)) {
            CharSequence label = node.getText();
            if (label == null) continue;
            String value = label.toString().trim();
            if (profile.isModeLabel(value)) {
                modes.add(value.toUpperCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(modes);
    }

    /** Numeric zoom preset labels visible in the tree ("0.6", "1", "2", "3.5", "7"). */
    public static List<String> discoverZooms(AccessibilityNodeInfo root) {
        Set<String> labels = new LinkedHashSet<>();
        for (AccessibilityNodeInfo node : breadthFirst(root)) {
            CharSequence label = node.getText();
            if (label == null) continue;
            String value = label.toString().trim().toLowerCase(Locale.ROOT).replace("×", "x");
            if (value.matches("\\d+(\\.\\d+)?x?")) {
                labels.add(value.replace("x", ""));
            }
        }
        return new ArrayList<>(labels);
    }

    /** Zoom preset by label ("1", "3.5", "0.6"), tolerant of the × suffix. */
    public static AccessibilityNodeInfo findZoom(AccessibilityNodeInfo root, String value) {
        if (root == null || value == null) return null;
        String want = value.trim().toLowerCase(Locale.ROOT).replace("×", "x");
        for (AccessibilityNodeInfo node : breadthFirst(root)) {
            CharSequence label = node.getText();
            if (label == null) continue;
            String got = label.toString().trim().toLowerCase(Locale.ROOT).replace("×", "x");
            if (got.equals(want) || got.equals(want + "x")) return node;
        }
        return null;
    }
}
