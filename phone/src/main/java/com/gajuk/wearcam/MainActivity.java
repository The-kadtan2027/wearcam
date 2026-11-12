package com.gajuk.wearcam;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
    private TextView status;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        int p = dp(16);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("WearCam — phone side");
        title.setTextSize(22);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        TextView ipView = new TextView(this);
        ipView.setTextSize(18);
        ipView.setText("http://" + localIp() + ":8080");
        ipView.setPadding(0, 0, 0, dp(12));
        root.addView(ipView);

        addButton("1. Enable accessibility", v -> {
            try {
                Settings.Secure.putString(getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        getPackageName() + "/.ShutterService");
                Settings.Secure.putInt(getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                Toast.makeText(this, "Accessibility enabled", Toast.LENGTH_SHORT).show();
            } catch (SecurityException e) {
                Toast.makeText(this, "adb shell pm grant " + getPackageName()
                        + " android.permission.WRITE_SECURE_SETTINGS", Toast.LENGTH_LONG).show();
            }
            refresh();
        });

        addButton("2. Start screen stream", v -> {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJ);
        });

        addButton("Stop stream", v -> {
            stopService(new Intent(this, CaptureService.class));
            status.postDelayed(this::refresh, 500);
        });

        addButton("Test shutter now", v -> {
            ShutterService s = ShutterService.instance;
            boolean ok = s != null && s.clickShutter();
            Toast.makeText(this, ok ? "Shutter clicked" : "Accessibility not running", Toast.LENGTH_SHORT).show();
        });

        TextView help = new TextView(this);
        help.setTextSize(13);
        help.setPadding(0, dp(16), 0, 0);
        help.setText("After starting the stream, open the OPPO Camera app and leave the screen on.\n\n"
                + "The watch connects to the address above. Streaming holds a screen wake lock.");
        root.addView(help);

        setContentView(scroll);
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
            status.setText("Streaming on port 8080 — open the camera app.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private Button addButton(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        root.addView(b, lp);
        b.setOnClickListener(l);
        return b;
    }

    private void refresh() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean acc = enabled != null && enabled.contains(getPackageName());
        status.setText("Accessibility: " + (acc ? "ON" : "OFF")
                + "  ·  Service: " + (ShutterService.instance != null ? "connected" : "not connected")
                + "\nStream: " + (CaptureService.running ? "running on :8080" : "stopped")
                + "  ·  BT: " + (BtBridge.isConnected() ? "watch linked" : "waiting"));
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
