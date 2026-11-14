package com.gajuk.wearcam;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Transparent full-screen overlay that captures the first tap's screen coordinates.
 *
 * <p>Used by the calibration wizard because several camera apps (including ColorOS) draw their
 * controls outside the accessibility tree. The overlay consumes the tap, records the point and
 * removes itself; the camera underneath stays visible the whole time.
 */
public final class TapOverlay {

    public interface Listener {
        void onTap(float x, float y);
    }

    private TapOverlay() {
    }

    public static void show(Context context, String hint, Listener listener) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        FrameLayout root = new FrameLayout(context);
        TextView banner = new TextView(context);
        banner.setText(hint);
        banner.setTextColor(Color.WHITE);
        banner.setTextSize(16);
        banner.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xCC000000);
        background.setCornerRadius(24);
        banner.setBackground(background);
        banner.setPadding(32, 16, 32, 16);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.topMargin = 80;
        root.addView(banner, params);

        root.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float x = event.getRawX();
                float y = event.getRawY();
                try {
                    wm.removeView(root);
                } catch (Exception ignored) {
                }
                listener.onTap(x, y);
            }
            return true;
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        try {
            wm.addView(root, lp);
        } catch (Exception ignored) {
        }
    }
}
