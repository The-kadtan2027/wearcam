package com.gajuk.wearcam;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

public class BtBridge {

    private static final String TAG = "WearCamBt";
    public static final UUID SVC = UUID.fromString("7d8f0e10-3c2a-4f5e-9b1d-2a6c8e4f0b21");

    private static final Object WRITE_LOCK = new Object();
    private static final ArrayBlockingQueue<byte[]> QUEUE = new ArrayBlockingQueue<>(6);

    private static volatile BluetoothSocket socket;
    private static volatile OutputStream out;
    private static volatile boolean started;
    private static volatile String lastMode = "";
    private static volatile int level;
    private static volatile Context appContext;
    private static volatile Runnable keyframeRequest;
    private static volatile byte[] lastVideoConfig;

    public static boolean isConnected() {
        return out != null;
    }

    public static int quality() {
        return level >= 2 ? 34 : level == 1 ? 40 : 48;
    }

    public static int frameSide() {
        return level >= 2 ? 288 : level == 1 ? 336 : 384;
    }

    public static void start(Context ctx) {
        if (started) return;
        started = true;
        appContext = ctx.getApplicationContext();
        new Thread(() -> acceptLoop(appContext), "bt-accept").start();
        new Thread(BtBridge::sendLoop, "bt-send").start();
        new Thread(BtBridge::statusLoop, "bt-status").start();
    }

    private static void acceptLoop(Context ctx) {
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Log.w(TAG, "bluetooth unavailable");
            started = false;
            return;
        }
        while (true) {
            try (BluetoothServerSocket server =
                         adapter.listenUsingRfcommWithServiceRecord("WearCam", SVC)) {
                Log.i(TAG, "listening");
                while (true) {
                    BluetoothSocket s = server.accept();
                    synchronized (WRITE_LOCK) {
                        socket = s;
                        out = s.getOutputStream();
                    }
                    Log.i(TAG, "client connected");
                    byte[] config = lastVideoConfig;
                    if (config != null) sendVideoConfig(config);
                    Runnable kf = keyframeRequest;
                    if (kf != null) kf.run();
                    if (ShutterService.instance != null) {
                        String m = ShutterService.instance.currentMode();
                        if (m != null) lastMode = m;
                    }
                    sendStatus(statusLine());
                    new Thread(() -> readLoop(s), "bt-read").start();
                }
            } catch (Exception e) {
                Log.w(TAG, "accept: " + e);
                sleep(1500);
            }
        }
    }

    private static void readLoop(BluetoothSocket s) {
        try {
            InputStream in = s.getInputStream();
            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') {
                    String cmd = sb.toString().trim();
                    sb.setLength(0);
                    if (!cmd.isEmpty()) handle(cmd);
                } else if (b != '\r') {
                    sb.append((char) b);
                }
                if (sb.length() > 64) sb.setLength(0);
            }
        } catch (Exception ignored) {
        } finally {
            synchronized (WRITE_LOCK) {
                if (socket == s) {
                    socket = null;
                    out = null;
                }
            }
            try {
                s.close();
            } catch (Exception ignored) {
            }
            Log.i(TAG, "client disconnected");
        }
    }

    private static void handle(String cmd) {
        ShutterService s = ShutterService.instance;
        if (s == null) {
            sendStatus("ERR=accessibility");
            return;
        }
        try {
            switch (cmd.charAt(0)) {
                case 'S':
                    s.clickShutter();
                    break;
                case 'I':
                    s.pinch(true);
                    break;
                case 'O':
                    s.pinch(false);
                    break;
                case 'Z':
                    s.zoomPreset(cmd.substring(Math.min(2, cmd.length())));
                    break;
                case 'M': {
                    if (cmd.startsWith("MODE:")) {
                        s.setMode(cmd.substring(5).trim());
                    } else {
                        int mode = Integer.parseInt(cmd.substring(Math.min(2, cmd.length())));
                        CameraModes.setMode(appContext, mode);
                    }
                    break;
                }
                case 'T': {
                    String[] p = cmd.substring(1).split(",");
                    loopbackTap(Float.parseFloat(p[0]), Float.parseFloat(p[1]));
                    break;
                }
                case 'B': {
                    String[] p = cmd.substring(Math.min(2, cmd.length())).split(",");
                    final float bx = Float.parseFloat(p[0]);
                    final float by = p.length > 1 ? Float.parseFloat(p[1]) : 48f;
                    new Thread(() -> loopbackBar(bx, by), "bt-bar").start();
                    break;
                }
                case 'Q':
                    if (s != null) {
                        String m = s.currentMode();
                        if (m != null) lastMode = m;
                    }
                    sendStatus(statusLine());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            Log.w(TAG, "cmd " + cmd + ": " + e);
        }
    }

    private static void loopbackTap(float x, float y) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(
                    "http://127.0.0.1:8080/tap?x=" + x + "&y=" + y).openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(10000);
            c.getResponseCode();
            c.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "loopback tap: " + e);
        }
    }

    private static void loopbackBar(float x480, float y96) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(
                    "http://127.0.0.1:8080/bartap?x=" + x480 + "&y=" + y96).openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(10000);
            c.getResponseCode();
            c.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "loopback bar: " + e);
        }
    }

    public static void onModeChanged(String mode) {
        if (mode == null || mode.equals(lastMode)) return;
        lastMode = mode;
        sendStatus(statusLine());
    }

    public static String statusLine() {
        String mode = lastMode;
        ShutterService service = ShutterService.instance;
        if (service != null) {
            String current = service.currentMode();
            if (current != null && !current.isEmpty()) {
                mode = current;
                lastMode = current;
            }
        }
        if (mode.isEmpty()) mode = "?";
        int battery = -1;
        int thermal = -1;
        Context ctx = appContext;
        if (ctx != null) {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null) thermal = pm.getCurrentThermalStatus();
        }
        String modes = "";
        String zoom = "";
        if (service != null) {
            modes = service.availableModes();
            zoom = service.zoomLabels();
        }
        return "MODE=" + mode + ";BAT=" + battery + ";TH=" + thermal
                + ";MODES=" + modes + ";ZOOM=" + zoom
                + ";UP=" + SystemClock.elapsedRealtime();
    }

    public static void sendStatus(String text) {
        offer(packet(2, text.getBytes()));
    }

    public static void sendFrame(byte[] jpeg) {
        offer(packet(1, jpeg));
    }

    public static void sendStrip(byte[] jpeg) {
        offer(packet(3, jpeg));
    }

    public static void sendVideoConfig(byte[] csd) {
        lastVideoConfig = csd;
        offer(packet(4, csd));
    }

    public static void sendVideoFrame(byte[] payload) {
        offer(packet(6, payload));
    }

    public static void setKeyframeRequest(Runnable r) {
        keyframeRequest = r;
    }

    public static byte[] packet(int type, byte[] payload) {
        byte[] p = new byte[5 + payload.length];
        p[0] = (byte) type;
        p[1] = (byte) (payload.length >>> 24);
        p[2] = (byte) (payload.length >>> 16);
        p[3] = (byte) (payload.length >>> 8);
        p[4] = (byte) payload.length;
        System.arraycopy(payload, 0, p, 5, payload.length);
        return p;
    }

    private static void offer(byte[] packet) {
        if (!QUEUE.offer(packet)) {
            QUEUE.poll();
            QUEUE.offer(packet);
        }
    }

    private static void sendLoop() {
        long ema = 0;
        while (true) {
            try {
                byte[] p = QUEUE.take();
                OutputStream o = out;
                if (o == null) continue;
                long t0 = SystemClock.uptimeMillis();
                synchronized (WRITE_LOCK) {
                    o.write(p);
                    o.flush();
                }
                long dt = SystemClock.uptimeMillis() - t0;
                ema = ema == 0 ? dt : (ema * 3 + dt) / 4;
                if (ema > 320) level = Math.min(2, level + 1);
                else if (ema < 110) level = Math.max(0, level - 1);
            } catch (Exception e) {
                Log.w(TAG, "send: " + e);
                try {
                    BluetoothSocket s = socket;
                    if (s != null) s.close();
                } catch (Exception ignored) {
                }
                sleep(200);
            }
        }
    }

    private static void statusLoop() {
        while (true) {
            sleep(5000);
            if (isConnected()) sendStatus(statusLine());
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
