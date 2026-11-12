package com.gajuk.wearcam;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class ShutterService extends AccessibilityService {

    public static volatile ShutterService instance;

    private volatile long lastModePush;

    @Override
    protected void onServiceConnected() {
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
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastModePush < 300) return;
        lastModePush = now;
        String mode = currentMode();
        if (mode != null) BtBridge.onModeChanged(mode);
    }

    @Override
    public void onInterrupt() {
    }

    public boolean clickShutter() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo node = find(root, "shutter");
        if (node == null) {
            String mode = currentMode();
            dispatchTap(realWidth() * 0.5f, realHeight() * 0.816f);
            BtBridge.sendStatus("VIDEO".equals(mode) ? "REC" : "SHOT");
            return true;
        }
        AccessibilityNodeInfo clickable = node;
        while (clickable != null && !clickable.isClickable()) {
            clickable = clickable.getParent();
        }
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            BtBridge.sendStatus("SHOT");
            return true;
        }
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        dispatchTap(r.centerX(), r.centerY());
        BtBridge.sendStatus("SHOT");
        return true;
    }

    public boolean tapBar(float x480, float y96) {
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
        AccessibilityNodeInfo node = findDescription(root, "mode,");
        if (node == null) return null;
        String desc = node.getContentDescription().toString();
        int i = desc.indexOf("mode");
        if (i <= 0) return null;
        return desc.substring(0, i).trim().toUpperCase();
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
