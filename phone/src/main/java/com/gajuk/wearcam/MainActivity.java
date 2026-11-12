package com.gajuk.wearcam;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class MainActivity extends Activity {

    private static final int REQ_PROJ = 1001;

    private TextView accValue, svcValue, streamValue, linkValue, urlText;
    private View accDot, svcDot, streamDot, linkDot;

    private int cBg, cCard, cText, cSecondary, cSeparator, cBlue, cGreen, cRed, cGrayDot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.gajuk.wearcam.common.StateMachine.setLogger(
                line -> android.util.Log.i("WearCamState", line));

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
        }
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 3);
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 4);
        }
        BtBridge.start(this);
        palette();

        getWindow().setStatusBarColor(cBg);
        getWindow().setNavigationBarColor(cBg);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(cBg);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(10), dp(20), dp(36));
        scroll.addView(root);

        root.addView(text("WearCam", 34, cText, true));
        TextView subtitle = text("Remote camera · Galaxy Watch 4", 15, cSecondary, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(2);
        root.addView(subtitle, subLp);

        section(root, "STATUS");
        LinearLayout statusCard = card();
        accDot = dot();
        accValue = valueText();
        statusCard.addView(row("Accessibility", accDot, accValue));
        statusCard.addView(separator());
        svcDot = dot();
        svcValue = valueText();
        statusCard.addView(row("Shutter service", svcDot, svcValue));
        statusCard.addView(separator());
        streamDot = dot();
        streamValue = valueText();
        statusCard.addView(row("Stream", streamDot, streamValue));
        statusCard.addView(separator());
        linkDot = dot();
        linkValue = valueText();
        statusCard.addView(row("Watch link", linkDot, linkValue));
        root.addView(statusCard, cardParams());

        section(root, "ADDRESS");
        LinearLayout addressCard = card();
        urlText = text("", 16, cBlue, false);
        urlText.setTypeface(Typeface.MONOSPACE);
        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setPadding(dp(16), 0, dp(16), 0);
        addressRow.setMinimumHeight(dp(52));
        addressRow.addView(urlText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        addressRow.addView(text("Copy", 15, cBlue, false));
        addressRow.setOnClickListener(v -> {
            String url = urlText.getText().toString();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("WearCam", url));
            Toast.makeText(this, "Address copied", Toast.LENGTH_SHORT).show();
        });
        addressRow.setBackground(ripple());
        addressCard.addView(addressRow);
        root.addView(addressCard, cardParams());

        primary(root, "Start streaming", this::startProjection);

        LinearLayout actions = card();
        actions.addView(actionRow("Stop stream", false, () -> {
            stopService(new Intent(this, CaptureService.class));
            urlText.postDelayed(this::refresh, 500);
        }));
        actions.addView(separator());
        actions.addView(actionRow("Test shutter", false, () -> {
            ShutterService s = ShutterService.instance;
            boolean ok = s != null && s.clickShutter();
            Toast.makeText(this, ok ? "Shutter clicked" : "Accessibility not running",
                    Toast.LENGTH_SHORT).show();
        }));
        actions.addView(separator());
        actions.addView(actionRow("Enable accessibility", true, this::enableAccessibility));
        root.addView(actions, cardParams());

        TextView foot = text("Open the OPPO Camera app after starting the stream.\n"
                + "Streaming holds a screen wake lock.", 13, cSecondary, false);
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footLp.topMargin = dp(28);
        root.addView(foot, footLp);

        setContentView(scroll);
        refresh();
    }

    private void startProjection() {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent projection;
        if (Build.VERSION.SDK_INT >= 35) {
            projection = mpm.createScreenCaptureIntent(
                    android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay());
        } else {
            projection = mpm.createScreenCaptureIntent();
        }
        startActivityForResult(projection, REQ_PROJ);
    }

    private void enableAccessibility() {
        try {
            Settings.Secure.putString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    getPackageName() + "/.ShutterService");
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            Toast.makeText(this, "Accessibility enabled", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, "Enable WearCam Phone in Accessibility", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception ignored) {
            }
        }
        refresh();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        android.util.Log.i("WearCam", "onActivityResult req=" + requestCode + " res=" + resultCode
                + " data=" + (data != null));
        if (requestCode == REQ_PROJ && resultCode == RESULT_OK && data != null) {
            Intent svc = new Intent(this, CaptureService.class);
            svc.putExtra("code", resultCode);
            svc.putExtra("data", data);
            startForegroundService(svc);
            Toast.makeText(this, "Streaming — open the camera app", Toast.LENGTH_SHORT).show();
            refresh();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
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
        cRed = 0xFFFF3B30;
        cGrayDot = dark ? 0xFF48484A : 0xFFC7C7CC;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private TextView valueText() {
        return text("", 17, cSecondary, false);
    }

    private View dot() {
        View v = new View(this);
        tintDot(v, cGrayDot);
        return v;
    }

    private void tintDot(View dot, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        dot.setBackground(bg);
    }

    private LinearLayout row(String label, View dot, TextView value) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), 0, dp(16), 0);
        r.setMinimumHeight(dp(50));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.rightMargin = dp(10);
        r.addView(dot, dotLp);
        r.addView(text(label, 17, cText, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.addView(value);
        return r;
    }

    private View separator() {
        View v = new View(this);
        v.setBackgroundColor(cSeparator);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.leftMargin = dp(16);
        v.setLayoutParams(lp);
        return v;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cCard);
        bg.setCornerRadius(dp(12));
        c.setBackground(bg);
        c.setClipToOutline(true);
        return c;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        return lp;
    }

    private void section(LinearLayout parent, String title) {
        TextView t = text(title, 13, cSecondary, false);
        t.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(4);
        lp.topMargin = dp(24);
        lp.bottomMargin = dp(8);
        parent.addView(t, lp);
    }

    private void primary(LinearLayout parent, String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        b.setTypeface(null, Typeface.BOLD);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(cBlue);
        normal.setCornerRadius(dp(14));
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(0xFF0A6CD6);
        pressed.setCornerRadius(dp(14));
        android.graphics.drawable.StateListDrawable states =
                new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        b.setBackground(states);
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(24);
        parent.addView(b, lp);
        b.setOnClickListener(v -> action.run());
    }

    private LinearLayout actionRow(String title, boolean chevron, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), 0, dp(16), 0);
        r.setMinimumHeight(dp(52));
        r.setBackground(ripple());
        r.addView(text(title, 17, cBlue, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (chevron) r.addView(text("›", 20, cSecondary, false));
        r.setOnClickListener(v -> action.run());
        return r;
    }

    private android.graphics.drawable.Drawable ripple() {
        TypedValue out = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true);
        return getDrawable(out.resourceId);
    }

    private void refresh() {
        if (accValue == null) return;
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean acc = enabled != null && enabled.contains(getPackageName());
        boolean svc = ShutterService.instance != null;
        boolean stream = CaptureService.machine().isStreaming();
        boolean link = BtBridge.isConnected();

        accValue.setText(acc ? "On" : "Off");
        tintDot(accDot, acc ? cGreen : cRed);
        svcValue.setText(svc ? "Connected" : "Off");
        tintDot(svcDot, svc ? cGreen : cRed);
        streamValue.setText(stream ? "Running" : "Stopped");
        tintDot(streamDot, stream ? cGreen : cGrayDot);
        linkValue.setText(link ? "Connected" : "Waiting");
        tintDot(linkDot, link ? cGreen : cGrayDot);
        if (urlText != null) urlText.setText("http://" + localIp() + ":8080");
    }

    private String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "phone-ip";
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
