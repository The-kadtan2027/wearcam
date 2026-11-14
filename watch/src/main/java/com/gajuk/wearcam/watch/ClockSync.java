package com.gajuk.wearcam.watch;

import android.os.SystemClock;

/**
 * Estimates the phone's clock offset from the {@code UP=} field in status lines, so video
 * frames (which carry the phone's capture timestamp) can be converted to an end-to-end latency.
 * The minimum observed offset is used: it is the sample least polluted by transport delay.
 */
public final class ClockSync {

    private static volatile long offset = Long.MIN_VALUE;

    private ClockSync() {
    }

    public static void sample(long phoneUptimeMs) {
        if (phoneUptimeMs <= 0) return;
        long candidate = SystemClock.elapsedRealtime() - phoneUptimeMs;
        if (offset == Long.MIN_VALUE || candidate < offset) offset = candidate;
    }

    public static boolean ready() {
        return offset != Long.MIN_VALUE;
    }

    /** End-to-end age of a frame captured at {@code phoneCaptureUs}, or -1 when unsynced. */
    public static long latencyMs(long phoneCaptureUs) {
        if (phoneCaptureUs <= 0 || offset == Long.MIN_VALUE) return -1;
        long captureMs = phoneCaptureUs / 1000;
        return SystemClock.elapsedRealtime() - (captureMs + offset);
    }
}
