package com.gajuk.wearcam.watch;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import java.nio.ByteBuffer;

public class VideoPlayer {

    private static final int FPS = 12;

    private final Surface surface;
    private MediaCodec codec;
    private volatile boolean configured;
    private long pts;
    private int frames;
    private long mark;

    public VideoPlayer(Surface surface) {
        this.surface = surface;
    }

    public synchronized void configure(byte[] csd) {
        release();
        try {
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", 480, 480);
            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd));
            codec = MediaCodec.createDecoderByType("video/avc");
            codec.configure(format, surface, null, 0);
            codec.start();
            configured = true;
            pts = 0;
            android.util.Log.i("WearCamV", "decoder configured");
        } catch (Exception e) {
            configured = false;
            android.util.Log.w("WearCamV", "configure: " + e);
        }
    }

    public synchronized void feed(byte[] data) {
        if (!configured) {
            byte[] csd = extractCsd(data);
            if (csd == null) return;
            configure(csd);
            if (!configured) return;
        }
        try {
            int index = codec.dequeueInputBuffer(0);
            if (index >= 0) {
                ByteBuffer buffer = codec.getInputBuffer(index);
                if (buffer != null) {
                    buffer.clear();
                    buffer.put(data);
                    codec.queueInputBuffer(index, 0, data.length, pts, 0);
                    pts += 1_000_000 / FPS;
                }
            }
            frames++;
            long now = android.os.SystemClock.uptimeMillis();
            if (mark == 0) mark = now;
            else if (now - mark >= 2000) {
                android.util.Log.i("WearCamV", "dec fps=" + (frames * 1000f / (now - mark)));
                frames = 0;
                mark = now;
            }
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                int out = codec.dequeueOutputBuffer(info, 0);
                if (out >= 0) {
                    codec.releaseOutputBuffer(out, true);
                } else {
                    break;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private byte[] extractCsd(byte[] data) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        boolean found = false;
        int i = 0;
        while (i + 4 < data.length) {
            if (data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 0 && data[i + 3] == 1) {
                int type = data[i + 4] & 0x1F;
                int start = i;
                int j = i + 4;
                while (j + 3 < data.length) {
                    if (data[j] == 0 && data[j + 1] == 0 && data[j + 2] == 0 && data[j + 3] == 1) break;
                    j++;
                }
                if (type == 7 || type == 8) {
                    out.write(data, start, j - start);
                    found = true;
                }
                i = j;
            } else {
                i++;
            }
        }
        return found ? out.toByteArray() : null;
    }

    public synchronized void release() {
        configured = false;
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
