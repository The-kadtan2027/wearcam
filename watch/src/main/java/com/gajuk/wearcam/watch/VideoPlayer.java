package com.gajuk.wearcam.watch;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.SystemClock;
import android.view.Surface;

import java.nio.ByteBuffer;

public class VideoPlayer {

    private static final long MAX_AGE_MS = 150;

    private final Surface surface;
    private MediaCodec codec;
    private volatile boolean configured;
    private int frames;
    private int outputs;
    private int dropped;
    private long mark;
    private long latencySum;
    private int latencyCount;
    private long latencyMax;

    public VideoPlayer(Surface surface) {
        this.surface = surface;
    }

    public synchronized void configure(byte[] csd) {
        release();
        try {
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", 480, 480);
            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd));
            if (Build.VERSION.SDK_INT >= 30) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
            }
            codec = MediaCodec.createDecoderByType("video/avc");
            codec.configure(format, surface, null, 0);
            codec.start();
            configured = true;
            android.util.Log.i("WearCamV", "decoder configured");
        } catch (Exception e) {
            configured = false;
            android.util.Log.w("WearCamV", "configure: " + e);
        }
    }

    /** @param phoneTsUs capture timestamp from the phone (0 when unknown). */
    public synchronized void feed(byte[] data, long phoneTsUs) {
        if (!configured) {
            byte[] csd = extractCsd(data);
            if (csd == null) return;
            configure(csd);
            if (!configured) return;
        }
        long age = ClockSync.latencyMs(phoneTsUs);
        if (age > MAX_AGE_MS) {
            dropped++;
            return;
        }
        try {
            int index = codec.dequeueInputBuffer(0);
            if (index >= 0) {
                ByteBuffer buffer = codec.getInputBuffer(index);
                if (buffer != null) {
                    buffer.clear();
                    buffer.put(data);
                    // Presentation time is the arrival time: live frames must not be paced
                    // against the phone's clock, whose offset is unknown.
                    codec.queueInputBuffer(index, 0, data.length,
                            SystemClock.elapsedRealtime() * 1000, 0);
                }
            }
            frames++;
            long latency = ClockSync.latencyMs(phoneTsUs);
            if (latency >= 0) {
                latencySum += latency;
                latencyCount++;
                latencyMax = Math.max(latencyMax, latency);
            }
            long now = SystemClock.uptimeMillis();
            if (mark == 0) {
                mark = now;
            } else if (now - mark >= 2000) {
                long avg = latencyCount > 0 ? latencySum / latencyCount : -1;
                android.util.Log.i("WearCamV", "dec fps=" + (outputs * 1000f / (now - mark))
                        + " in=" + frames + " drop=" + dropped
                        + " latency avg=" + avg + "ms max=" + latencyMax + "ms");
                frames = 0;
                outputs = 0;
                dropped = 0;
                mark = now;
                latencySum = 0;
                latencyCount = 0;
                latencyMax = 0;
            }
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                int out = codec.dequeueOutputBuffer(info, 0);
                if (out >= 0) {
                    codec.releaseOutputBuffer(out, true);
                    outputs++;
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
