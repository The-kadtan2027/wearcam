package com.gajuk.wearcam;

import android.graphics.Bitmap;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

public class H264Encoder {

    private final int width;
    private final int height;
    private final int fps;
    private final int bitrate;

    private MediaCodec codec;
    private Thread drainThread;
    private volatile boolean running;
    private volatile long latestCaptureUs;
    private boolean configSent;
    private int frameCount;
    private long fpsMark;

    private int[] argb;
    private final java.util.concurrent.ArrayBlockingQueue<Bitmap> queue =
            new java.util.concurrent.ArrayBlockingQueue<>(1);
    private final java.util.concurrent.ArrayBlockingQueue<Bitmap> free =
            new java.util.concurrent.ArrayBlockingQueue<>(3);
    private Thread worker;

    public H264Encoder(int width, int height, int fps, int bitrate) {
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.bitrate = bitrate;
    }

    public void start() throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format.setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        format.setInteger(MediaFormat.KEY_OPERATING_RATE, fps);
        try {
            format.setInteger(MediaFormat.KEY_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
            format.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0);
            format.setInteger(MediaFormat.KEY_LATENCY, 1);
        } catch (Exception ignored) {
        }

        codec = MediaCodec.createEncoderByType("video/avc");
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();

        argb = new int[width * height];
        for (int i = 0; i < 3; i++) {
            free.offer(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888));
        }

        running = true;
        worker = new Thread(this::workerLoop, "h264-worker");
        worker.start();
        drainThread = new Thread(this::drainLoop, "h264-drain");
        drainThread.start();
        BtBridge.setKeyframeRequest(this::requestKeyframe);
        android.util.Log.i("WearCam", "h264 encoder started " + width + "x" + height);
    }

    public void requestKeyframe() {
        try {
            android.os.Bundle params = new android.os.Bundle();
            params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            codec.setParameters(params);
        } catch (Exception ignored) {
        }
    }

    public Bitmap acquire() {
        return running ? free.poll() : null;
    }

    public void submit(Bitmap bitmap) {
        if (bitmap == null) return;
        latestCaptureUs = android.os.SystemClock.elapsedRealtime() * 1000;
        Bitmap stale = queue.poll();
        if (stale != null) free.offer(stale);
        if (!queue.offer(bitmap)) free.offer(bitmap);
    }

    private void workerLoop() {
        while (running) {
            try {
                Bitmap bitmap = queue.take();
                try {
                    int index = codec.dequeueInputBuffer(0);
                    if (index >= 0) {
                        Image image = codec.getInputImage(index);
                        if (image != null) {
                            convert(bitmap, image);
                            codec.queueInputBuffer(index, 0, width * height * 3 / 2,
                                    latestCaptureUs, 0);
                        } else {
                            codec.queueInputBuffer(index, 0, 0, latestCaptureUs, 0);
                        }
                    }
                } finally {
                    free.offer(bitmap);
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                android.util.Log.w("WearCam", "h264 worker: " + e);
            }
        }
    }

    private void convert(Bitmap bitmap, Image image) {
        bitmap.getPixels(argb, 0, width, 0, 0, width, height);

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        int yRow = planes[0].getRowStride();
        int yPixel = planes[0].getPixelStride();
        ByteBuffer uBuffer = planes[1].getBuffer();
        int uRow = planes[1].getRowStride();
        int uPixel = planes[1].getPixelStride();
        ByteBuffer vBuffer = planes[2].getBuffer();
        int vRow = planes[2].getRowStride();
        int vPixel = planes[2].getPixelStride();

        int yi = 0;
        for (int j = 0; j < height; j++) {
            int rowBase = j * width;
            for (int i = 0; i < width; i++) {
                int c = argb[rowBase + i];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                int y = (((66 * r + 129 * g + 25 * b + 128) >> 8) + 16);
                yBuffer.put(j * yRow + i * yPixel, (byte) clamp(y));
                if ((i & 1) == 0 && (j & 1) == 0) {
                    int u = (((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128);
                    int v = (((112 * r - 94 * g - 18 * b + 128) >> 8) + 128);
                    int ci = i >> 1;
                    int cj = j >> 1;
                    uBuffer.put(cj * uRow + ci * uPixel, (byte) clamp(u));
                    vBuffer.put(cj * vRow + ci * vPixel, (byte) clamp(v));
                }
            }
        }
    }

    private int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }

    private void drainLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (running) {
            try {
                int index = codec.dequeueOutputBuffer(info, 10000);
                if (index >= 0) {
                    ByteBuffer buffer = codec.getOutputBuffer(index);
                    if (buffer != null && info.size > 0) {
                        byte[] data = new byte[info.size];
                        buffer.position(info.offset);
                        buffer.get(data);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            BtBridge.sendVideoConfig(data);
                            VideoHub.broadcast(BtBridge.packet(4, data));
                            configSent = true;
                        } else {
                            if (!configSent) {
                                try {
                                    ByteBuffer csd = codec.getOutputFormat().getByteBuffer("csd-0");
                                    if (csd != null) {
                                        byte[] c = new byte[csd.remaining()];
                                        csd.get(c);
                                        BtBridge.sendVideoConfig(c);
                                        configSent = true;
                                        android.util.Log.i("WearCam", "csd from output format");
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                            byte[] payload = new byte[8 + data.length];
                            long captureUs = info.presentationTimeUs;
                            for (int i = 0; i < 8; i++) {
                                payload[7 - i] = (byte) (captureUs >>> (8 * i));
                            }
                            System.arraycopy(data, 0, payload, 8, data.length);
                            BtBridge.sendVideoFrame(payload);
                            VideoHub.broadcast(BtBridge.packet(6, payload));
                            frameCount++;
                            long now = android.os.SystemClock.uptimeMillis();
                            if (fpsMark == 0) fpsMark = now;
                            else if (now - fpsMark >= 2000) {
                                android.util.Log.i("WearCam", "h264 enc fps="
                                        + (frameCount * 1000f / (now - fpsMark)));
                                frameCount = 0;
                                fpsMark = now;
                            }
                        }
                    }
                    codec.releaseOutputBuffer(index, false);
                }
            } catch (Exception e) {
                if (!running) break;
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (worker != null) worker.interrupt();
            if (drainThread != null) drainThread.join(500);
        } catch (InterruptedException ignored) {
        }
        try {
            if (codec != null) {
                codec.stop();
                codec.release();
            }
        } catch (Exception ignored) {
        }
        codec = null;
    }
}
