package com.gajuk.wearcam;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.gajuk.wearcam.common.Calibration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One-go calibration: a single "Start" opens the camera once, a persistent transparent overlay
 * walks the user through five taps (shutter, VIDEO, PHOTO, PORTRAIT, 1×), the mode points are
 * verified automatically, and the mapping is saved — no back-and-forth.
 */
public class CalibrationActivity extends Activity {

    private CalibrationSession session;
    private TapOverlay overlay;
    private final Map<CalibrationSession.Step, TextView> rows = new LinkedHashMap<>();
    private TextView status;
    private Button startButton;

    private int cBg, cCard, cText, cSecondary, cSeparator, cBlue, cGreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(cBg);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(10), dp(20), dp(36));
        scroll.addView(root);

        root.addView(text("Camera setup", 30, cText, true));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(2);
        root.addView(text("One go: open the camera once and tap five controls.",
                14, cSecondary, false), subLp);

        LinearLayout card = card();
        for (CalibrationSession.Step step : CalibrationSession.Step.values()) {
            TextView row = text("○  " + step.instruction, 15, cText, false);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            rows.put(step, row);
            card.addView(row);
            card.addView(separator());
        }
        root.addView(card, cardParams());

        status = text("Tap Start, then follow the banner over the camera.", 14, cSecondary, false);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(16);
        root.addView(status, statusLp);

        startButton = primary("Start calibration");
        startButton.setOnClickListener(v -> start());
        root.addView(startButton, primaryParams());

        Button reset = secondary("Reset stored mapping");
        reset.setOnClickListener(v -> {
            String pkg = ShutterService.instance != null
                    ? ShutterService.instance.activePackage() : "";
            CalibrationStore.clear(this, pkg);
            Toast.makeText(this, "Cleared for " + pkg, Toast.LENGTH_SHORT).show();
            render(null);
        });
        root.addView(reset, primaryParams());

        setContentView(scroll);
        render(null);
    }

    private void start() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow 'Display over other apps', then tap Start again",
                    Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
            return;
        }
        session = CalibrationSession.start();
        CalibrationSession.Step first = session.nextStep();
        if (first == null) return;
        overlay = TapOverlay.show(this, first.instruction, this::onTap);
        if (overlay == null) {
            Toast.makeText(this, "Overlay unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        status.setText("Waiting for the first tap…");
        launchCamera();
    }

    private long lastTapAt;

    private void onTap(float x, float y) {
        if (session == null) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastTapAt < 500) return;
        lastTapAt = now;
        CalibrationSession.Step step = session.nextStep();
        if (step == null) return;
        String pkg = ShutterService.instance != null
                ? ShutterService.instance.activePackage() : "";
        session.capturePoint(step, x, y, pkg);
        vibrate(18);
        CalibrationSession.Step next = session.nextStep();
        if (next != null) {
            overlay.setHint(next.instruction);
            render(next);
            return;
        }
        overlay.setHint("Verifying and saving…");
        overlay.dismiss();
        overlay = null;
        vibrate(45);
        finishSession();
    }

    private void finishSession() {
        final int width = displayWidth();
        final int height = displayHeight();
        new Thread(() -> {
            ShutterService service = ShutterService.instance;
            if (service != null) {
                for (CalibrationSession.Step step : new CalibrationSession.Step[]{
                        CalibrationSession.Step.MODE_VIDEO,
                        CalibrationSession.Step.MODE_PHOTO,
                        CalibrationSession.Step.MODE_PORTRAIT}) {
                    if (!session.captured(step)) continue;
                    float[] point = session.point(step);
                    if (point == null) continue;
                    float[] refined = service.refinePoint(step.name(), point[0], point[1]);
                    if (refined[0] != point[0] || refined[1] != point[1]) {
                        session.movePoint(step, refined[0], refined[1]);
                    }
                }
            }
            Calibration calibration = session.build(width, height);
            CalibrationStore.save(this, calibration);
            BtBridge.sendStatus(BtBridge.statusLine());
            session.finish();
            runOnUiThread(() -> {
                Toast.makeText(this, "Saved for " + calibration.packageName,
                        Toast.LENGTH_LONG).show();
                render(null);
                startActivity(new Intent(this, CalibrationActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            });
        }, "calibration-save").start();
    }

    private void vibrate(int ms) {
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(ms, 200));
            }
        } catch (Exception ignored) {
        }
    }

    private void launchCamera() {
        if (CameraModes.setMode(this, CameraModes.PHOTO)) return;
        try {
            Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void render(CalibrationSession.Step current) {
        for (CalibrationSession.Step step : CalibrationSession.Step.values()) {
            TextView row = rows.get(step);
            boolean done = session != null && session.captured(step);
            row.setText((done ? "✓  " : step == current ? "▶  " : "○  ") + step.instruction);
            row.setTextColor(done ? cGreen : step == current ? cBlue : cText);
        }
        if (session == null) {
            status.setText("Tap Start, then follow the banner over the camera.");
        } else if (session.nextStep() == null) {
            status.setText("All steps captured — mapping saved.");
        } else {
            status.setText("Tap the highlighted control; the banner shows the next one.");
        }
        startButton.setEnabled(session == null || session.nextStep() == null);
        startButton.setText(session != null && session.nextStep() == null
                ? "Run again" : "Start calibration");
    }

    private int displayWidth() {
        if (Build.VERSION.SDK_INT >= 30) {
            return getWindowManager().getMaximumWindowMetrics().getBounds().width();
        }
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int displayHeight() {
        if (Build.VERSION.SDK_INT >= 30) {
            return getWindowManager().getMaximumWindowMetrics().getBounds().height();
        }
        return getResources().getDisplayMetrics().heightPixels;
    }

    @Override
    protected void onDestroy() {
        if (overlay != null) {
            overlay.dismiss();
            overlay = null;
        }
        if (isFinishing() && CalibrationSession.current() == session) {
            CalibrationSession.finish();
        }
        super.onDestroy();
    }

    private void palette() {
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        cBg = dark ? 0xFF000000 : 0xFFF2F2F7;
        cCard = dark ? 0xFF1C1C1E : 0xFFFFFFFF;
        cText = dark ? 0xFFFFFFFF : 0xFF000000;
        cSecondary = 0xFF8E8E93;
        cSeparator = dark ? 0xFF38383A : 0xFFC6C6C8;
        cBlue = 0xFF007AFF;
        cGreen = 0xFF34C759;
        getWindow().setStatusBarColor(cBg);
        getWindow().setNavigationBarColor(cBg);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(cCard);
        background.setCornerRadius(dp(12));
        layout.setBackground(background);
        layout.setClipToOutline(true);
        return layout;
    }

    private ViewGroup.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(16);
        return params;
    }

    private ViewGroup.LayoutParams primaryParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        params.topMargin = dp(12);
        return params;
    }

    private Button primary(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setTextColor(0xFFFFFFFF);
        button.setTypeface(null, Typeface.BOLD);
        GradientDrawable background = new GradientDrawable();
        background.setColor(cBlue);
        background.setCornerRadius(dp(12));
        button.setBackground(background);
        button.setStateListAnimator(null);
        return button;
    }

    private Button secondary(String label) {
        Button button = primary(label);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x228E8E93);
        background.setCornerRadius(dp(12));
        button.setBackground(background);
        button.setTextColor(cBlue);
        return button;
    }

    private TextView separator() {
        TextView line = new TextView(this);
        line.setBackgroundColor(cSeparator);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.leftMargin = dp(16);
        line.setLayoutParams(params);
        return line;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
