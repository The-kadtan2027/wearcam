package com.gajuk.wearcam;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import com.gajuk.wearcam.state.CaptureMachine;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class CaptureService extends Service {

    private static final String TAG = "WearCam";
    private static final String CHANNEL = "wearcam";
    private static final int NOTIF_ID = 42;
    private static final int W = 480;
    private static final int H = 1056;

    private static final CaptureMachine MACHINE = new CaptureMachine();

    public static CaptureMachine machine() {
        return MACHINE;
    }

    private MediaProjection projection;
    private VirtualDisplay display;
    private H264Encoder encoder;
    private ImageReader reader;
    private HandlerThread thread;
    private MjpegServer server;
    private PowerManager.WakeLock wake;
    private Bitmap reusable;
    private Bitmap btBitmap;
    private Bitmap btStrip;
    private long lastStripAt;
    private final Paint btPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private long lastFrameAt;
    private long lastBtAt;
    private long lastH264At;

    @Override
    public void onCreate() {
        super.onCreate();
        com.gajuk.wearcam.common.StateMachine.setLogger(
                line -> android.util.Log.i("WearCamState", line));
        createChannel();
        server = new MjpegServer(this, 8080);
        server.start();
        BtBridge.start(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        Log.i(TAG, "onStartCommand");

        startForeground(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        Log.i(TAG, "foreground ok");
        if (MACHINE.state() != CaptureMachine.State.IDLE
                && MACHINE.state() != CaptureMachine.State.ERROR) {
            Log.i(TAG, "duplicate start ignored in " + MACHINE.state());
            return START_NOT_STICKY;
        }
        MACHINE.send(CaptureMachine.Event.REQUEST);

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        int code = intent.getIntExtra("code", 0);
        Intent data = intent.getParcelableExtra("data");
        if (data == null) {
            MACHINE.send(CaptureMachine.Event.FAILED);
            stopSelf();
            return START_NOT_STICKY;
        }

        projection = mpm.getMediaProjection(code, data);
        Log.i(TAG, "projection=" + (projection != null));
        if (projection == null) {
            MACHINE.send(CaptureMachine.Event.FAILED);
            stopSelf();
            return START_NOT_STICKY;
        }

        thread = new HandlerThread("capture");
        thread.start();

        reader = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(r -> onImage(r.acquireLatestImage()), new Handler(thread.getLooper()));

        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                CaptureMachine.State state = MACHINE.state();
                if (state == CaptureMachine.State.STARTING
                        || state == CaptureMachine.State.STREAMING) {
                    MACHINE.send(CaptureMachine.Event.STOP);
                }
                stopSelf();
            }
        }, new Handler(getMainLooper()));

        Log.i(TAG, "creating display");
        display = projection.createVirtualDisplay("wearcam", W, H, 160,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, null);

        Log.i(TAG, "creating encoder");
        try {
            encoder = new H264Encoder(480, 480, 30, 2_500_000);
            encoder.start();
        } catch (Throwable t) {
            Log.w(TAG, "h264 unavailable: " + t);
            encoder = null;
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wake = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "wearcam:screen");
        wake.acquire();

        MACHINE.send(CaptureMachine.Event.STARTED);
        return START_NOT_STICKY;
    }

    private void onImage(Image image) {
        if (image == null) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastFrameAt < 20) {
            image.close();
            return;
        }
        lastFrameAt = now;
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int rowStride = plane.getRowStride();
            int pixelStride = plane.getPixelStride();
            int rowPadding = rowStride - pixelStride * W;

            Bitmap padded;
            if (rowPadding == 0) {
                if (reusable == null) reusable = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
                padded = reusable;
            } else {
                padded = Bitmap.createBitmap(W + rowPadding / pixelStride, H, Bitmap.Config.ARGB_8888);
            }
            padded.copyPixelsFromBuffer(buffer);
            image.close();

            Bitmap out = padded;
            if (rowPadding != 0) out = Bitmap.createBitmap(padded, 0, 0, W, H);

            if (encoder != null && now - lastH264At >= 20) {
                lastH264At = now;
                Bitmap target = encoder.acquire();
                if (target != null) {
                    int side = Math.min(out.getWidth(), out.getHeight());
                    int sx = (out.getWidth() - side) / 2;
                    int sy = (out.getHeight() - side) / 2;
                    new Canvas(target).drawBitmap(out,
                            new Rect(sx, sy, sx + side, sy + side),
                            new Rect(0, 0, target.getWidth(), target.getHeight()), btPaint);
                    encoder.submit(target);
                }
            }
            if (server.hasClients()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(65536);
                out.compress(Bitmap.CompressFormat.JPEG, 45, bos);
                server.setFrame(bos.toByteArray());
            }
            pushBt(out);

            if (out != padded && out != reusable) out.recycle();
            if (padded != reusable) padded.recycle();
        } catch (Throwable t) {
            Log.w(TAG, "frame error", t);
        }
    }

    private void pushBt(Bitmap full) {
        if (!BtBridge.isConnected()) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastStripAt >= 300) {
            lastStripAt = now;
            try {
                int top = (int) (full.getHeight() * 0.865f);
                int bottom = (int) (full.getHeight() * 0.955f);
                if (btStrip == null) btStrip = Bitmap.createBitmap(480, 96, Bitmap.Config.ARGB_8888);
                Canvas stripCanvas = new Canvas(btStrip);
                stripCanvas.drawBitmap(full, new Rect(0, top, full.getWidth(), bottom),
                        new Rect(0, 0, 480, 96), btPaint);
                ByteArrayOutputStream stripOut = new ByteArrayOutputStream(16384);
                btStrip.compress(Bitmap.CompressFormat.JPEG, 60, stripOut);
                BtBridge.sendStrip(stripOut.toByteArray());
            } catch (Throwable t) {
                Log.w(TAG, "strip error", t);
            }
        }
        if (encoder != null) return;
        if (now - lastBtAt < 140) return;
        lastBtAt = now;
        try {
            int side = Math.min(full.getWidth(), full.getHeight());
            int x = (full.getWidth() - side) / 2;
            int y = (full.getHeight() - side) / 2;
            int target = BtBridge.frameSide();
            if (btBitmap == null || btBitmap.getWidth() != target) {
                btBitmap = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888);
            }
            Canvas canvas = new Canvas(btBitmap);
            canvas.drawBitmap(full, new Rect(x, y, x + side, y + side),
                    new Rect(0, 0, target, target), btPaint);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(32768);
            btBitmap.compress(Bitmap.CompressFormat.JPEG, BtBridge.quality(), bos);
            BtBridge.sendFrame(bos.toByteArray());
        } catch (Throwable t) {
            Log.w(TAG, "bt frame error", t);
        }
    }

    @Override
    public void onDestroy() {
        MACHINE.send(CaptureMachine.Event.DESTROYED);
        if (wake != null && wake.isHeld()) wake.release();
        if (encoder != null) encoder.stop();
        if (display != null) display.release();
        if (projection != null) projection.stop();
        if (reader != null) reader.close();
        if (thread != null) thread.quitSafely();
        if (server != null) server.stop();
        if (reusable != null) reusable.recycle();
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "WearCam",
                NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification notification() {
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("WearCam streaming")
                .setContentText("Screen mirror on port 8080")
                .setOngoing(true)
                .build();
    }
}
