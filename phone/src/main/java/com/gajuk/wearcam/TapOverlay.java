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
 * Transparent full-screen overlay that reports every tap's screen coordinates.
 *
 * <p>Used by the one-go calibration: the user opens the camera once and taps each control in
 * sequence; the banner updates with the next instruction and the overlay stays until the owner
 * dismisses it.
 */
public final class TapOverlay {

    public interface Listener {
        void onTap(float x, float y);
    }

    private final WindowManager windowManager;
    private final FrameLayout root;
    private final TextView banner;

    private TapOverlay(WindowManager windowManager, FrameLayout root, TextView banner) {
        this.windowManager = windowManager;
        this.root = root;
        this.banner = banner;
    }

    public static TapOverlay show(Context context, String hint, Listener listener) {
        WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return null;

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
                listener.onTap(event.getRawX(), event.getRawY());
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
            windowManager.addView(root, lp);
        } catch (Exception e) {
            return null;
        }
        return new TapOverlay(windowManager, root, banner);
    }

    public void setHint(String hint) {
        banner.setText(hint);
    }

    public void dismiss() {
        try {
            windowManager.removeView(root);
        } catch (Exception ignored) {
        }
    }
}
