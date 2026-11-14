package com.gajuk.wearcam;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.content.Intent;
import android.graphics.Rect;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.gajuk.wearcam.common.Calibration;
import com.gajuk.wearcam.state.RecordMachine;

import java.util.ArrayList;
import java.util.List;

public class ShutterService extends AccessibilityService {

    public static volatile ShutterService instance;

    private volatile long lastModePush;
    private volatile String activePackage;

    @Override
    protected void onServiceConnected() {
        com.gajuk.wearcam.common.StateMachine.setLogger(
                line -> android.util.Log.i("WearCamState", line));
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            CalibrationSession session = CalibrationSession.current();
            if (session != null && session.armed() != null && event.getPackageName() != null) {
                String pkg = event.getPackageName().toString();
                if (!pkg.equals(getPackageName())) {
                    AccessibilityNodeInfo source = event.getSource();
                    if (source != null) {
                        session.capture(pkg, source, getRootInActiveWindow(),
                                CameraProfile.forPackage(pkg));
                    }
                }
            }
            return;
        }
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getPackageName() != null) {
            String pkg = event.getPackageName().toString();
            if (!pkg.equals(getPackageName())
                    && !pkg.equals("com.android.systemui")
                    && !pkg.equals("android")) {
                activePackage = pkg;
            }
        }
        long now = System.currentTimeMillis();
        if (now - lastModePush < 300) return;
        lastModePush = now;
        String mode = currentMode();
        if (mode != null) {
            if (record.modeChanged("VIDEO".equals(mode))) BtBridge.sendStatus("STOP");
            BtBridge.onModeChanged(mode);
        }
    }

    @Override
    public void onInterrupt() {
    }

    private final RecordMachine record = new RecordMachine();

    public String activePackage() {
        return activePackage;
    }

    public CameraProfile profile() {
        return CameraProfile.forPackage(activePackage);
    }

    /** The camera app's package: the active window wins over the last accessibility event. */
    private String packageOf(AccessibilityNodeInfo root) {
        if (root != null && root.getPackageName() != null) return root.getPackageName().toString();
        return activePackage;
    }

    public CameraProfile profile(AccessibilityNodeInfo root) {
        return CameraProfile.forPackage(packageOf(root));
    }

    public Calibration calibration() {
        return CalibrationStore.load(this, activePackage);
    }

    public Calibration calibration(AccessibilityNodeInfo root) {
        return CalibrationStore.load(this, packageOf(root));
    }

    private boolean tapEntry(AccessibilityNodeInfo root, Calibration.Entry entry) {
        if (root != null) {
            List<String> hints = new ArrayList<>();
            if (!entry.desc.isEmpty()) hints.add(entry.desc.toLowerCase());
            if (!entry.text.isEmpty()) hints.add(entry.text.toLowerCase());
            List<String> ids = new ArrayList<>();
            if (!entry.id.isEmpty()) ids.add(entry.id.toLowerCase());
            AccessibilityNodeInfo node = CameraUi.find(root,
                    hints.toArray(new String[0]), ids.toArray(new String[0]));
            if (node != null && CameraUi.click(node)) return true;
        }
        if (entry.hasBounds()) {
            dispatchTap((entry.left + entry.right) / 2f, (entry.top + entry.bottom) / 2f);
            return true;
        }
        return false;
    }

    public boolean clickShutter() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        boolean video = "VIDEO".equalsIgnoreCase(currentMode());
        String status = record.click(video);
        Calibration calibration = calibration(root);
        CameraProfile profile = profile(root);
        if (calibration != null) {
            Calibration.Entry entry = calibration.entry(video ? "record" : "shutter");
            if (entry == null && video) entry = calibration.entry("shutter");
            if (entry != null && tapEntry(root, entry)) {
                BtBridge.sendStatus(status);
                return true;
            }
        }
        AccessibilityNodeInfo node = CameraUi.find(root,
                video ? profile.videoHints : profile.shutterHints,
                video ? new String[]{"record", "stop"} : new String[]{"shutter", "capture"});
        if (node != null && CameraUi.click(node)) {
            BtBridge.sendStatus(status);
            return true;
        }
        Rect r = new Rect();
        if (node != null) {
            node.getBoundsInScreen(r);
            dispatchTap(r.centerX(), r.centerY());
        } else {
            dispatchTap(realWidth() * 0.5f, realHeight() * 0.816f);
        }
        BtBridge.sendStatus(status);
        return true;
    }

    /** Switch the camera to a mode by name: carousel node first, OPPO intent, then launch+retry. */
    public boolean setMode(String name) {
        if (name == null || name.isEmpty()) return false;
        // The camera's mode carousel scrolls, so calibrated points drift. A deterministic
        // shortcut intent (when the app provides one) is always preferable.
        Integer code = CameraModes.codeFor(name);
        if (code != null && CameraModes.setMode(this, code)) return true;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        Calibration calibration = calibration(root);
        if (calibration != null) {
            Calibration.Entry entry = calibration.entry("mode:" + name.trim().toUpperCase());
            if (entry != null && tapEntry(root, entry)) return true;
        }
        if (root != null) {
            AccessibilityNodeInfo node = CameraUi.findMode(root, name);
            if (node != null && CameraUi.click(node)) return true;
        }
        launchCamera();
        try {
            Thread.sleep(1200);
        } catch (InterruptedException ignored) {
        }
        root = getRootInActiveWindow();
        if (root != null) {
            AccessibilityNodeInfo node = CameraUi.findMode(root, name);
            return node != null && CameraUi.click(node);
        }
        return false;
    }

    /** Comma-separated mode labels the camera currently exposes, for the watch's cycle. */
    public String availableModes() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        Calibration calibration = calibration(root);
        if (calibration != null) {
            List<String> calibrated = new ArrayList<>();
            for (Calibration.Entry entry : calibration.entries("mode:")) {
                calibrated.add(entry.role.substring(5));
            }
            if (!calibrated.isEmpty()) return String.join(",", calibrated);
        }
        if (root == null) return "";
        return String.join(",", CameraUi.discoverModes(root, profile(root)));
    }

    /** Zoom preset labels from the calibration ("1,2,3.5"), for the watch's pills. */
    public String zoomLabels() {
        Calibration calibration = calibration(getRootInActiveWindow());
        if (calibration == null) return "";
        List<String> labels = new ArrayList<>();
        for (Calibration.Entry entry : calibration.entries("zoom:")) {
            labels.add(entry.role.substring(5));
        }
        return String.join(",", labels);
    }

    /** Mode-bar rect in the captured frame's coordinates, when calibrated. */
    public static android.graphics.Rect barInFrame(int frameWidth, int frameHeight) {
        ShutterService service = instance;
        if (service == null) return null;
        Calibration calibration = service.calibration(service.getRootInActiveWindow());
        if (calibration == null) return null;
        Calibration.Entry bar = calibration.entry("bar");
        if (bar == null || !bar.hasBounds()) return null;
        float sx = frameWidth / service.realWidth();
        float sy = frameHeight / service.realHeight();
        return new Rect(Math.round(bar.left * sx), Math.round(bar.top * sy),
                Math.round(bar.right * sx), Math.round(bar.bottom * sy));
    }

    private void launchCamera() {
        try {
            Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    /**
     * Verifies a calibrated mode point by tapping it and checking that the camera actually
     * switched; small vertical offsets cover bars whose touch target sits below the label.
     */
    public float[] refinePoint(String step, float x, float y) {
        String expected;
        switch (step) {
            case "MODE_VIDEO":
                expected = "VIDEO";
                break;
            case "MODE_PHOTO":
                expected = "PHOTO";
                break;
            case "MODE_PORTRAIT":
                expected = "PORTRAIT";
                break;
            default:
                return new float[]{x, y};
        }
        float[] offsets = {0, 60, 120, -60, 180};
        for (float offset : offsets) {
            dispatchTap(x, y + offset);
            try {
                Thread.sleep(450);
            } catch (InterruptedException ignored) {
            }
            String now = currentMode();
            if (expected.equalsIgnoreCase(now)) {
                return new float[]{x, y + offset};
            }
        }
        return new float[]{x, y};
    }

    public boolean tapBar(float x480, float y96) {
        Calibration calibration = calibration(getRootInActiveWindow());
        if (calibration != null) {
            Calibration.Entry bar = calibration.entry("bar");
            if (bar != null && bar.hasBounds()) {
                float x = bar.left + x480 / 480f * (bar.right - bar.left);
                float y = bar.top + y96 / 96f * (bar.bottom - bar.top);
                dispatchTap(x, y);
                return true;
            }
        }
        float width = realWidth();
        float height = realHeight();
        float x = x480 / 480f * width;
        float base = height * 0.865f + (y96 / 96f) * height * 0.09f;
        String before = currentMode();
        float[] offsets = {34f, 64f, 4f, 94f};
        for (float offset : offsets) {
            float y = Math.max(height * 0.905f,
                    Math.min(height * 0.945f, base + offset));
            dispatchTap(x, y);
            android.util.Log.i("WearCamTap", "tap x=" + x + " y=" + y + " before=" + before);
            for (int i = 0; i < 12; i++) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ignored) {
                }
                String now = currentMode();
                if (now != null && !now.equals(before)) {
                    android.util.Log.i("WearCamTap", "hit " + now + " after " + (i + 1) * 250 + "ms");
                    return true;
                }
            }
        }
        return false;
    }

    public boolean zoomPreset(String value) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        Calibration calibration = calibration(root);
        if (calibration != null) {
            Calibration.Entry entry = calibration.entry("zoom:" + value.trim());
            if (entry != null && tapEntry(root, entry)) return true;
        }
        if (root != null) {
            AccessibilityNodeInfo node = CameraUi.findZoom(root, value);
            if (node != null && CameraUi.click(node)) return true;
        }
        float fraction;
        switch (value) {
            case "0.6": fraction = 0.315f; break;
            case "1": fraction = 0.405f; break;
            case "2": fraction = 0.495f; break;
            case "3.5": fraction = 0.594f; break;
            case "7": fraction = 0.700f; break;
            default: return false;
        }
        dispatchTap(realWidth() * fraction, realHeight() * 0.709f);
        return true;
    }

    public boolean tapAtScreen(float x, float y) {
        dispatchTap(x, y);
        return true;
    }

    public boolean tapAtStream384(float x, float y) {
        float width = realWidth();
        float height = realHeight();
        float side = Math.min(width, height);
        float sx = x / 384f * side;
        float sy = (height - side) / 2f + y / 384f * side;
        dispatchTap(sx, sy);
        return true;
    }

    private float realWidth() {
        try {
            android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                return wm.getMaximumWindowMetrics().getBounds().width();
            }
            android.graphics.Point p = new android.graphics.Point();
            wm.getDefaultDisplay().getRealSize(p);
            return p.x;
        } catch (Exception e) {
            return getResources().getDisplayMetrics().widthPixels;
        }
    }

    private float realHeight() {
        try {
            android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                return wm.getMaximumWindowMetrics().getBounds().height();
            }
            android.graphics.Point p = new android.graphics.Point();
            wm.getDefaultDisplay().getRealSize(p);
            return p.y;
        } catch (Exception e) {
            return getResources().getDisplayMetrics().heightPixels;
        }
    }

    public boolean pinch(boolean zoomIn) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float cx = dm.widthPixels / 2f;
        float cy = dm.heightPixels / 2f;
        float start = Math.min(dm.widthPixels, dm.heightPixels) * 0.12f;
        float end = start * 2.4f;
        float a = zoomIn ? start : end;
        float b = zoomIn ? end : start;

        Path left = new Path();
        left.moveTo(cx - a, cy);
        left.lineTo(cx - b, cy);

        Path right = new Path();
        right.moveTo(cx + a, cy);
        right.lineTo(cx + b, cy);

        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(left, 0, 280))
                .addStroke(new GestureDescription.StrokeDescription(right, 0, 280))
                .build();
        dispatchGesture(g, null, null);
        return true;
    }

    public String currentMode() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        String selected = CameraUi.selectedMode(root, profile(root));
        if (selected != null) return selected;
        AccessibilityNodeInfo node = findDescription(root, "mode,");
        if (node == null) return null;
        String desc = node.getContentDescription().toString();
        int i = desc.indexOf("mode");
        if (i <= 0) return null;
        String name = desc.substring(0, i).trim().toUpperCase();
        // OPPO appends the sub-mode ("VIDEO : STAGE") — the watch matches plain mode names.
        int colon = name.indexOf(':');
        if (colon > 0) name = name.substring(0, colon).trim();
        return name.isEmpty() ? null : name;
    }

    private void dispatchTap(float x, float y) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 60))
                .build();
        dispatchGesture(g, null, null);
    }

    private AccessibilityNodeInfo findDescription(AccessibilityNodeInfo node, String needle) {
        if (node == null) return null;
        CharSequence cd = node.getContentDescription();
        if (cd != null && cd.toString().toLowerCase().contains(needle)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findDescription(node.getChild(i), needle);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String needle) {
        if (node == null) return null;
        CharSequence cd = node.getContentDescription();
        if (cd != null && cd.toString().toLowerCase().contains(needle)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), needle);
            if (found != null) return found;
        }
        return null;
    }
}
