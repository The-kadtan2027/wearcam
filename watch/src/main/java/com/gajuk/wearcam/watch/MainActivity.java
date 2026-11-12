package com.gajuk.wearcam.watch;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.view.SurfaceView;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity implements BtClient.Listener {

    private static final int BT_SIZE = 384;
    private static final int STREAM_W = 480;

    private ImageView preview;
    private SurfaceView videoView;
    private android.widget.ProgressBar spinner;
    private TextView modePill;
    private ShutterView shutterView;
    private VideoPlayer player;
    private byte[] pendingCsd;
    private WifiVideo wifiVideoClient;
    private volatile boolean wifiVideo;
    private ImageView strip;
    private TextView status;
    private Button modeButton;
    private LinearLayout config;
    private EditText ipField;

    private BtClient bt;
    private String linkState = "idle";
    private volatile String phoneMode = "?";
    private volatile String phoneDetail = "";
    private long lastStripTapAt;
    private int lastBarX;
    private int lastBarY;
    private String modeAtTap = "";
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final String[] modes = {"PHOTO", "PORTRAIT", "VIDEO"};
    private int modeIndex = 0;
    private int requested = -1;
    private TextView timerPill;
    private TextView countdownView;
    private volatile boolean timerArmed;
    private volatile boolean recording;
    private volatile boolean countingDown;
    private volatile long recStartAt;

    private volatile boolean httpRunning;
    private Thread httpThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
        }

        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                java.io.FileWriter writer = new java.io.FileWriter(
                        new java.io.File(getFilesDir(), "crash.txt"), true);
                writer.write(new java.util.Date() + " " + thread.getName() + "\n");
                writer.write(android.util.Log.getStackTraceString(error));
                writer.write("\n\n");
                writer.close();
            } catch (Exception ignored) {
            }
            android.os.Process.killProcess(android.os.Process.myPid());
        });

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        videoView = new SurfaceView(this);
        root.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        videoView.getHolder().addCallback(new android.view.SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(android.view.SurfaceHolder holder) {
                player = new VideoPlayer(holder.getSurface());
                byte[] csd = pendingCsd;
                if (csd != null) player.configure(csd);
            }

            @Override
            public void surfaceChanged(android.view.SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(android.view.SurfaceHolder holder) {
                if (player != null) {
                    player.release();
                    player = null;
                }
            }
        });

        spinner = new android.widget.ProgressBar(this);
        spinner.setVisibility(View.GONE);
        root.addView(spinner, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundColor(0xFF000000);
        preview.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_UP) onPreviewTap(v, ev.getX(), ev.getY());
            return true;
        });
        root.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        status = new TextView(this);
        status.setTextSize(9);
        status.setTextColor(0xCCFFFFFF);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(10), dp(3), dp(10), dp(3));
        android.graphics.drawable.GradientDrawable statusBg = new android.graphics.drawable.GradientDrawable();
        statusBg.setColor(0x66000000);
        statusBg.setCornerRadius(dp(12));
        status.setBackground(statusBg);
        status.setText("connecting");
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        statusParams.setMargins(0, dp(8), 0, 0);
        root.addView(status, statusParams);
        status.setOnClickListener(v -> {
            setStripVisible(strip.getVisibility() != View.VISIBLE);
        });
        status.setOnLongClickListener(v -> {
            config.setVisibility(config.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            return true;
        });

        timerPill = new TextView(this);
        pill(timerPill, "3s");
        timerPill.setTextSize(10);
        timerPill.setOnClickListener(v -> toggleTimer());
        FrameLayout.LayoutParams timerParams = new FrameLayout.LayoutParams(
                dp(36), dp(24), Gravity.TOP | Gravity.LEFT);
        timerParams.setMargins(dp(10), dp(8), 0, 0);
        root.addView(timerPill, timerParams);
        updateTimerPill();

        countdownView = new TextView(this);
        countdownView.setTextSize(46);
        countdownView.setTextColor(0xFFFFFFFF);
        countdownView.setGravity(Gravity.CENTER);
        countdownView.setShadowLayer(dp(8), 0, dp(2), 0xCC000000);
        countdownView.setVisibility(View.GONE);
        root.addView(countdownView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        strip = new ImageView(this);
        strip.setScaleType(ImageView.ScaleType.FIT_XY);
        android.graphics.drawable.GradientDrawable stripBg = new android.graphics.drawable.GradientDrawable();
        stripBg.setColor(0xCC000000);
        stripBg.setCornerRadius(dp(10));
        strip.setBackground(stripBg);
        strip.setVisibility(View.GONE);
        strip.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                float x = ev.getX() / v.getWidth() * 480f;
                float y = ev.getY() / v.getHeight() * 96f;
                long now = android.os.SystemClock.uptimeMillis();
                if (now - lastStripTapAt < 400) return true;
                lastStripTapAt = now;
                lastBarX = Math.round(x);
                lastBarY = Math.round(y);
                modeAtTap = phoneMode;
                android.util.Log.i("WearCamW", "strip tap send B:" + lastBarX + "," + lastBarY);
                sendCmd("B:" + lastBarX + "," + lastBarY);
                ui.postDelayed(() -> setStripVisible(false), 2600);
            }
            return true;
        });
        strip.setOnLongClickListener(v -> {
            ViewGroup.LayoutParams lp = strip.getLayoutParams();
            lp.height = lp.height > dp(70) ? dp(40) : dp(96);
            strip.setLayoutParams(lp);
            return true;
        });
        FrameLayout.LayoutParams stripParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        stripParams.setMargins(dp(30), 0, dp(30), dp(140));
        root.addView(strip, stripParams);

        modePill = new TextView(this);
        pill(modePill, "PHOTO");
        modePill.setOnClickListener(v -> cycleModeIntent());
        modePill.setOnLongClickListener(v -> {
            setStripVisible(strip.getVisibility() != View.VISIBLE);
            return true;
        });
        FrameLayout.LayoutParams modeParams = new FrameLayout.LayoutParams(
                dp(96), dp(26), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        modeParams.setMargins(0, 0, 0, dp(108));
        root.addView(modePill, modeParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        TextView zoomOut = new TextView(this);
        pill(zoomOut, "1×");
        zoomOut.setOnClickListener(v -> sendCmd("Z:1"));
        controls.addView(zoomOut, new LinearLayout.LayoutParams(dp(48), dp(32)));

        shutterView = new ShutterView(this);
        shutterView.setAction(() -> {
            vibrate(30);
            if (timerArmed) {
                if (!countingDown) countdown(3);
            } else {
                sendCmd("S");
            }
        });
        LinearLayout.LayoutParams shutterParams = new LinearLayout.LayoutParams(dp(68), dp(68));
        shutterParams.leftMargin = dp(14);
        shutterParams.rightMargin = dp(14);
        controls.addView(shutterView, shutterParams);

        TextView zoomIn = new TextView(this);
        pill(zoomIn, "3.5×");
        zoomIn.setOnClickListener(v -> sendCmd("Z:3.5"));
        controls.addView(zoomIn, new LinearLayout.LayoutParams(dp(48), dp(32)));

        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(68),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        controlsParams.setMargins(0, 0, 0, dp(30));
        root.addView(controls, controlsParams);

        config = new LinearLayout(this);
        config.setOrientation(LinearLayout.VERTICAL);
        config.setBackgroundColor(0xEE000000);
        config.setVisibility(View.GONE);
        config.setPadding(dp(10), dp(10), dp(10), dp(10));
        ipField = new EditText(this);
        ipField.setTextSize(12);
        ipField.setSingleLine(true);
        ipField.setText(getSharedPreferences("wearcam", 0)
                .getString("host", "192.168.1.6:8080"));
        config.addView(ipField);
        Button wifi = new Button(this);
        style(wifi, "Use Wi-Fi stream", 0xFF2F6FED, 0xFFFFFFFF, 11);
        wifi.setOnClickListener(v -> {
            String host = ipField.getText().toString().trim();
            getSharedPreferences("wearcam", 0).edit().putString("host", host).apply();
            if (wifiVideoClient != null) wifiVideoClient.setHost(host);
            startHttp(host);
        });
        config.addView(wifi);
        FrameLayout.LayoutParams configParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        configParams.setMargins(dp(14), dp(46), dp(14), 0);
        root.addView(config, configParams);

        setContentView(root);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        startForegroundService(new Intent(this, KeepAlive.class));
        attachClient(0);
        wifiVideoClient = new WifiVideo(getSharedPreferences("wearcam", 0)
                .getString("host", "192.168.1.6:8080"), new WifiVideo.Listener() {
            @Override
            public void onConfig(byte[] csd) {
                wifiVideo = true;
                applyVideoConfig(csd);
            }

            @Override
            public void onFrame(byte[] data) {
                wifiVideo = true;
                applyVideoFrame(data);
            }

            @Override
            public void onState(boolean connected) {
                if (!connected) wifiVideo = false;
            }
        });
        wifiVideoClient.start();
        refreshStatus();
    }

    private void pill(TextView v, String text) {
        v.setText(text);
        v.setTextSize(11);
        v.setTextColor(0xFFFFFFFF);
        v.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xCC1C1C1E);
        bg.setCornerRadius(dp(16));
        v.setBackground(bg);
    }

    private final Runnable recTick = new Runnable() {
        @Override
        public void run() {
            if (recording) refreshStatus();
        }
    };

    private void toggleTimer() {
        timerArmed = !timerArmed;
        updateTimerPill();
        vibrate(15);
    }

    private void updateTimerPill() {
        if (timerPill == null) return;
        timerPill.setText("3s");
        timerPill.setTextColor(timerArmed ? 0xFF000000 : 0xCCFFFFFF);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(timerArmed ? 0xFFFF9F0A : 0xCC1C1C1E);
        bg.setCornerRadius(dp(16));
        timerPill.setBackground(bg);
    }

    private void countdown(int n) {
        if (n <= 0) {
            countingDown = false;
            if (countdownView != null) countdownView.setVisibility(View.GONE);
            sendCmd("S");
            return;
        }
        countingDown = true;
        vibrate(20);
        countdownView.setText(String.valueOf(n));
        countdownView.setVisibility(View.VISIBLE);
        countdownView.setScaleX(0.7f);
        countdownView.setScaleY(0.7f);
        countdownView.setAlpha(1f);
        countdownView.animate().scaleX(1.12f).scaleY(1.12f).alpha(0.55f).setDuration(850).start();
        ui.postDelayed(() -> countdown(n - 1), 1000);
    }

    private void setStripVisible(boolean show) {
        if (strip == null) return;
        if (show) {
            strip.setAlpha(0f);
            strip.setVisibility(View.VISIBLE);
            strip.animate().alpha(1f).setDuration(150).start();
        } else {
            strip.animate().alpha(0f).setDuration(150)
                    .withEndAction(() -> strip.setVisibility(View.GONE)).start();
        }
    }

    private void cycleModeIntent() {
        int next;
        if ("PHOTO".equalsIgnoreCase(phoneMode)) next = 2;
        else if ("PORTRAIT".equalsIgnoreCase(phoneMode)) next = 6;
        else next = 0;
        sendCmd("M:" + next);
        vibrate(25);
    }

    private void style(Button b, String text, int bg, int fg, int size) {
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(size);
        b.setTextColor(fg);
        b.setBackgroundColor(bg);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
    }

    private void cycleMode() {
        if (requested < 0) requested = modeIndexOf(phoneMode);
        requested = (requested + 1) % modes.length;
        phoneMode = modes[requested];
        sendCmd("M:" + phoneMode.toLowerCase());
        refreshStatus();
    }

    private int modeIndexOf(String name) {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equalsIgnoreCase(name)) return i;
        }
        return 0;
    }

    private void onPreviewTap(View v, float x, float y) {
        if (!bt.isConnected()) return;
        float scale = v.getWidth() / (float) BT_SIZE;
        int bx = Math.round(x / scale);
        int by = Math.round(y / scale);
        if (bx < 0 || by < 0 || bx >= BT_SIZE || by >= BT_SIZE) return;
        sendCmd("T:" + bx + "," + by);
    }

    @Override
    public void onFrame(Bitmap bitmap) {
        runOnUiThread(() -> preview.setImageBitmap(bitmap));
    }

    @Override
    public void onVideoConfig(byte[] csd) {
        if (wifiVideo) return;
        applyVideoConfig(csd);
    }

    @Override
    public void onVideoFrame(byte[] data) {
        if (wifiVideo) return;
        applyVideoFrame(data);
    }

    private void applyVideoConfig(byte[] csd) {
        pendingCsd = csd;
        runOnUiThread(() -> {
            if (player != null) player.configure(csd);
        });
    }

    private void applyVideoFrame(byte[] data) {
        VideoPlayer p = player;
        if (p != null) p.feed(data);
        runOnUiThread(() -> {
            if (preview.getVisibility() != View.GONE) preview.setVisibility(View.GONE);
        });
    }

    @Override
    public void onStrip(Bitmap bitmap) {
        runOnUiThread(() -> strip.setImageBitmap(bitmap));
    }

    @Override
    public void onStatus(String text) {
        if (text == null) return;
        if (text.equals("SHOT") || text.equals("STOP")) {
            vibrate(45);
            recording = false;
            runOnUiThread(() -> status.setText("saved"));
            ui.postDelayed(this::refreshStatus, 1200);
            return;
        }
        if (text.equals("REC")) {
            vibrate(60);
            recording = true;
            recStartAt = android.os.SystemClock.elapsedRealtime();
            refreshStatus();
            return;
        }
        if (text.startsWith("MODE=")) {
            String[] parts = text.split(";");
            phoneMode = parts[0].substring(5);
            String battery = "";
            String thermal = "";
            for (int i = 1; i < parts.length; i++) {
                if (parts[i].startsWith("BAT=")) battery = parts[i].substring(4);
                if (parts[i].startsWith("TH=")) thermal = parts[i].substring(3);
            }
            StringBuilder detail = new StringBuilder();
            if (!battery.isEmpty() && !battery.equals("-1")) detail.append(battery).append('%');
            if (!thermal.isEmpty() && !thermal.equals("-1") && !thermal.equals("0")) {
                if (detail.length() > 0) detail.append(" · ");
                detail.append("th").append(thermal);
            }
            phoneDetail = detail.toString();
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].equalsIgnoreCase(phoneMode)) modeIndex = i;
            }
        }
        refreshStatus();
    }

    @Override
    public void onLink(String state) {
        linkState = state;
        refreshStatus();
    }

    private void refreshStatus() {
        final boolean down = linkState.contains("retry") || linkState.contains("failed")
                || linkState.contains("no phone") || linkState.contains("lost");
        final String mode = phoneMode;
        final String detail = phoneDetail;
        final String main;
        if (recording) {
            long s = (android.os.SystemClock.elapsedRealtime() - recStartAt) / 1000;
            main = String.format("REC %d:%02d", s / 60, s % 60);
        } else {
            main = wifiVideo ? "wifi" : "bt";
        }
        final String text = down ? "reconnecting…"
                : (detail.isEmpty() ? main : main + " · " + detail);
        runOnUiThread(() -> {
            if (spinner != null) spinner.setVisibility(down ? View.VISIBLE : View.GONE);
            if (modePill != null) modePill.setText(mode);
            if (shutterView != null) shutterView.setVideo("VIDEO".equalsIgnoreCase(mode));
            if (status != null) status.setText(text);
        });
        if (recording) {
            ui.removeCallbacks(recTick);
            ui.postDelayed(recTick, 1000);
        }
    }

    private void sendCmd(String command) {
        BtClient client = bt;
        if (client != null) client.send(command);
    }

    private void attachClient(int attempt) {
        bt = KeepAlive.client();
        if (bt != null) {
            bt.setListener(this);
            refreshStatus();
            return;
        }
        if (attempt < 15) {
            ui.postDelayed(() -> attachClient(attempt + 1), 200);
        }
    }

    private void vibrate(int ms) {
        try {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(android.os.VibrationEffect.createOneShot(ms, 200));
            }
        } catch (Exception ignored) {
        }
    }

    private void startHttp(String host) {
        stopHttp();
        httpRunning = true;
        httpThread = new Thread(() -> {
            while (httpRunning) {
                try {
                    URL url = new URL("http://" + host + "/stream");
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    c.setConnectTimeout(4000);
                    c.setReadTimeout(8000);
                    c.connect();
                    runOnUiThread(() -> status.setText("wifi: connected"));
                    InputStream in = new BufferedInputStream(c.getInputStream(), 1 << 16);
                    int length = 0;
                    while (httpRunning) {
                        String line = readLine(in);
                        if (line == null) break;
                        if (line.startsWith("Content-Length:")) {
                            length = Integer.parseInt(line.substring(15).trim());
                        } else if (line.isEmpty() && length > 0) {
                            byte[] buf = new byte[length];
                            int off = 0;
                            while (off < length) {
                                int r = in.read(buf, off, length - off);
                                if (r < 0) throw new IOException("eof");
                                off += r;
                            }
                            Bitmap bmp = BitmapFactory.decodeByteArray(buf, 0, length);
                            length = 0;
                            if (bmp != null) onFrame(bmp);
                        }
                    }
                    c.disconnect();
                } catch (Exception e) {
                    runOnUiThread(() -> status.setText("wifi: retry"));
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }, "http");
        httpThread.setDaemon(true);
        httpThread.start();
    }

    private void stopHttp() {
        httpRunning = false;
        if (httpThread != null) {
            httpThread.interrupt();
            httpThread = null;
        }
    }

    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') return sb.toString().trim();
            sb.append((char) c);
            if (sb.length() > 512) return sb.toString().trim();
        }
        return sb.length() == 0 ? null : sb.toString().trim();
    }

    @Override
    protected void onDestroy() {
        stopHttp();
        if (bt != null) bt.setListener(null);
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
