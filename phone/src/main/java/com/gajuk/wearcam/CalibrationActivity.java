package com.gajuk.wearcam;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.MediaStore;
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
 * One-time wizard: the user performs five taps in the real camera app; the accessibility service
 * records the clicked nodes and the mapping is stored per camera package.
 */
public class CalibrationActivity extends Activity {

    private CalibrationSession session;
    private final Map<CalibrationSession.Step, TextView> rows = new LinkedHashMap<>();
    private TextView instruction;
    private TextView status;
    private Button openButton;
    private Button saveButton;

    private int cBg, cCard, cText, cSecondary, cSeparator, cBlue, cGreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = CalibrationSession.current();
        if (session == null) session = CalibrationSession.start();
        session.setListener(step -> runOnUiThread(this::render));
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
        root.addView(text("One-time calibration for this camera app.", 14, cSecondary, false), subLp);

        LinearLayout card = card();
        for (CalibrationSession.Step step : CalibrationSession.Step.values()) {
            TextView row = text("○  " + step.instruction, 15, cText, false);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            rows.put(step, row);
            card.addView(row);
            card.addView(separator());
        }
        root.addView(card, cardParams());

        instruction = text("", 15, cText, false);
        root.addView(instruction, cardParams());

        status = text("", 14, cSecondary, false);
        root.addView(status);

        openButton = primary("Open camera");
        openButton.setOnClickListener(v -> openCamera());
        root.addView(openButton, primaryParams());

        Button skip = secondary("Skip this step");
        skip.setOnClickListener(v -> {
            CalibrationSession.Step step = currentStep();
            if (step != null) {
                session.skip(step);
                render();
            }
        });
        root.addView(skip, primaryParams());

        saveButton = primary("Save & finish");
        saveButton.setOnClickListener(v -> save());
        root.addView(saveButton, primaryParams());

        setContentView(scroll);
        render();
    }

    private void openCamera() {
        CalibrationSession.Step step = currentStep();
        if (step == null) return;
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow 'Display over other apps', then try again",
                    Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
            return;
        }
        session.arm(step);
        render();
        TapOverlay.show(this, step.instruction, (x, y) -> {
            float[] point = ShutterService.instance != null
                    ? ShutterService.instance.refinePoint(step.name(), x, y)
                    : new float[]{x, y};
            String pkg = ShutterService.instance != null
                    ? ShutterService.instance.activePackage() : "";
            session.capturePoint(step, point[0], point[1], pkg);
            runOnUiThread(this::render);
        });
        try {
            Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void save() {
        android.graphics.Point size = new android.graphics.Point();
        android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm != null && android.os.Build.VERSION.SDK_INT >= 30) {
            android.graphics.Rect bounds = wm.getMaximumWindowMetrics().getBounds();
            size.set(bounds.width(), bounds.height());
        }
        Calibration calibration = session.build(size.x, size.y);
        if (calibration.isEmpty()) {
            Toast.makeText(this, "Capture at least the shutter", Toast.LENGTH_SHORT).show();
            return;
        }
        CalibrationStore.save(this, calibration);
        BtBridge.sendStatus(BtBridge.statusLine());
        Toast.makeText(this, "Saved for " + calibration.packageName, Toast.LENGTH_SHORT).show();
        session.finish();
        finish();
    }

    private CalibrationSession.Step currentStep() {
        for (CalibrationSession.Step step : CalibrationSession.Step.values()) {
            if (!session.has(step)) return step;
        }
        return null;
    }

    private void render() {
        for (CalibrationSession.Step step : CalibrationSession.Step.values()) {
            TextView row = rows.get(step);
            if (session.captured(step)) {
                String summary = session.summary(step);
                row.setText("✓  " + (summary == null || summary.isEmpty() ? step.instruction : summary));
                row.setTextColor(cGreen);
            } else if (session.has(step)) {
                row.setText("–  " + step.instruction + " (skipped)");
                row.setTextColor(cSecondary);
            } else {
                row.setText("○  " + step.instruction);
                row.setTextColor(cText);
            }
        }
        CalibrationSession.Step step = currentStep();
        if (step == null) {
            instruction.setText("All steps done. Save the mapping to use it now.");
            status.setText(session.packageName().isEmpty() ? "" : "Camera: " + session.packageName());
            openButton.setEnabled(false);
            saveButton.setEnabled(true);
        } else {
            instruction.setText(step.instruction);
            if (session.armed() == step) {
                status.setText("Waiting for your tap in the camera app…");
            } else {
                status.setText("Tap Open camera, then " + step.instruction.toLowerCase());
            }
            openButton.setEnabled(true);
            saveButton.setEnabled(session.captured(CalibrationSession.Step.SHUTTER));
        }
    }

    @Override
    protected void onDestroy() {
        session.setListener(null);
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
