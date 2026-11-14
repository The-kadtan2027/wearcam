package com.gajuk.wearcam;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import com.gajuk.wearcam.common.Calibration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One-time calibration: the user performs the actions in the real camera app, the accessibility
 * service records the clicked nodes, and this session turns them into a {@link Calibration}.
 * While a step is armed, the service captures the next TYPE_VIEW_CLICKED node.
 */
public final class CalibrationSession {

    public enum Step {
        SHUTTER("shutter", "Tap the shutter button."),
        MODE_VIDEO("mode:VIDEO", "Open the mode bar and tap VIDEO."),
        MODE_PHOTO("mode:PHOTO", "Tap PHOTO to go back."),
        MODE_PORTRAIT("mode:PORTRAIT", "Tap PORTRAIT in the mode bar."),
        ZOOM("zoom:1", "Tap the 1× zoom preset.");

        public final String role;
        public final String instruction;

        Step(String role, String instruction) {
            this.role = role;
            this.instruction = instruction;
        }
    }

    public interface Listener {
        void onCaptured(Step step);
    }

    private static volatile CalibrationSession current;

    public static CalibrationSession current() {
        return current;
    }

    public static CalibrationSession start() {
        current = new CalibrationSession();
        return current;
    }

    public static void finish() {
        current = null;
    }

    private final Map<Step, Calibration.Entry> captured = new LinkedHashMap<>();
    private final Map<String, Calibration.Entry> discovered = new LinkedHashMap<>();
    private final Map<Step, String> summary = new LinkedHashMap<>();
    private final Set<Step> skipped = EnumSet.noneOf(Step.class);
    private Step armed;
    private String packageName = "";
    private volatile Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public synchronized void arm(Step step) {
        armed = step;
    }

    public synchronized Step armed() {
        return armed;
    }

    public synchronized boolean has(Step step) {
        return captured.containsKey(step) || skipped.contains(step);
    }

    public synchronized boolean captured(Step step) {
        return captured.containsKey(step);
    }

    public synchronized String summary(Step step) {
        return summary.get(step);
    }

    public synchronized String packageName() {
        return packageName;
    }

    public synchronized void skip(Step step) {
        skipped.add(step);
        if (armed == step) armed = null;
    }

    /** Records the tap point captured by the overlay (camera controls may not be in the tree). */
    public synchronized void capturePoint(Step step, float x, float y, String pkg) {
        if (pkg != null && !pkg.isEmpty()) packageName = pkg;
        int half = 48;
        int left = Math.max(0, Math.round(x) - half);
        int top = Math.max(0, Math.round(y) - half);
        Calibration.Entry entry = new Calibration.Entry(roleFor(step, ""), "", "", "",
                left, top, Math.round(x) + half, Math.round(y) + half);
        captured.put(step, entry);
        summary.put(step, "point " + Math.round(x) + "," + Math.round(y));
        armed = null;
        Listener l = listener;
        if (l != null) l.onCaptured(step);
    }

    /** First step that has neither been captured nor skipped. */
    public synchronized Step nextStep() {
        for (Step step : Step.values()) {
            if (!has(step)) return step;
        }
        return null;
    }

    /** Centre of the point recorded for a step, or null. */
    public synchronized float[] point(Step step) {
        Calibration.Entry entry = captured.get(step);
        if (entry == null || !entry.hasBounds()) return null;
        return new float[]{(entry.left + entry.right) / 2f, (entry.top + entry.bottom) / 2f};
    }

    /** Replaces a step's point (used by the automatic mode-point verification). */
    public synchronized void movePoint(Step step, float x, float y) {
        Calibration.Entry entry = captured.get(step);
        if (entry == null) return;
        int half = 48;
        captured.put(step, new Calibration.Entry(entry.role, entry.text, entry.desc, entry.id,
                Math.max(0, Math.round(x) - half), Math.max(0, Math.round(y) - half),
                Math.round(x) + half, Math.round(y) + half));
    }

    public synchronized Calibration build(int screenWidth, int screenHeight) {
        Map<String, Calibration.Entry> byRole = new LinkedHashMap<>();
        for (Calibration.Entry entry : captured.values()) {
            byRole.putIfAbsent(entry.role, entry);
        }
        for (Calibration.Entry entry : discovered.values()) {
            byRole.putIfAbsent(entry.role, entry);
        }
        List<Calibration.Entry> entries = new ArrayList<>(byRole.values());

        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = 0;
        int bottom = 0;
        boolean any = false;
        for (Calibration.Entry entry : entries) {
            if (!entry.role.startsWith("mode:") || !entry.hasBounds()) continue;
            any = true;
            left = Math.min(left, entry.left);
            top = Math.min(top, entry.top);
            right = Math.max(right, entry.right);
            bottom = Math.max(bottom, entry.bottom);
        }
        if (any) {
            int barLeft = 0;
            int barRight = screenWidth > 0 ? screenWidth : right;
            entries.add(new Calibration.Entry("bar", "", "", "",
                    barLeft, Math.max(0, top - 20), barRight, bottom + 20));
        }
        return new Calibration(packageName, entries);
    }

    /** Called by the accessibility service for the next click while a step is armed. */
    public synchronized void capture(String pkg, AccessibilityNodeInfo node,
                                     AccessibilityNodeInfo root, CameraProfile profile) {
        if (armed == null || node == null) return;
        Step step = armed;
        Calibration.Entry tapped = entry(roleFor(step, node), node);
        captured.put(step, tapped);
        summary.put(step, !tapped.desc.isEmpty() ? tapped.desc
                : !tapped.text.isEmpty() ? tapped.text : tapped.id);

        if (root != null) {
            if (step == Step.MODE_VIDEO || step == Step.MODE_PHOTO) {
                for (String label : CameraUi.discoverModes(root, profile)) {
                    AccessibilityNodeInfo mode = CameraUi.findMode(root, label);
                    if (mode != null) discovered.putIfAbsent("mode:" + label, entry("mode:" + label, mode));
                }
            } else if (step == Step.ZOOM) {
                for (String label : CameraUi.discoverZooms(root)) {
                    AccessibilityNodeInfo zoom = CameraUi.findZoom(root, label);
                    if (zoom != null) discovered.putIfAbsent("zoom:" + label, entry("zoom:" + label, zoom));
                }
            }
        }
        if (!pkg.isEmpty()) packageName = pkg;
        armed = null;
        Listener l = listener;
        if (l != null) l.onCaptured(step);
    }

    private static Calibration.Entry entry(String role, AccessibilityNodeInfo node) {
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        String text = node.getText() != null ? node.getText().toString().trim() : "";
        String desc = node.getContentDescription() != null
                ? node.getContentDescription().toString().trim() : "";
        String id = node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "";
        return new Calibration.Entry(role, text, desc, id,
                rect.left, rect.top, rect.right, rect.bottom);
    }

    private static String roleFor(Step step, AccessibilityNodeInfo node) {
        String label = node.getText() != null ? node.getText().toString().trim() : "";
        if (label.isEmpty() && node.getContentDescription() != null) {
            label = node.getContentDescription().toString().trim();
        }
        return roleFor(step, label);
    }

    private static String roleFor(Step step, String label) {
        switch (step) {
            case MODE_VIDEO:
                return "mode:" + (label.isEmpty() ? "VIDEO" : label.toUpperCase());
            case MODE_PHOTO:
                return "mode:" + (label.isEmpty() ? "PHOTO" : label.toUpperCase());
            case MODE_PORTRAIT:
                return "mode:" + (label.isEmpty() ? "PORTRAIT" : label.toUpperCase());
            case ZOOM:
                return "zoom:" + (label.isEmpty() ? "1"
                        : label.replace("×", "").replace("x", "").trim());
            default:
                return step.role;
        }
    }
}
