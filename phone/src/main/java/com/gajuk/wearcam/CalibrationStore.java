package com.gajuk.wearcam;

import android.content.Context;

import com.gajuk.wearcam.common.Calibration;

/** Persists one calibration per camera package. */
public final class CalibrationStore {

    private static final String PREFS = "wearcam_calibration";

    private CalibrationStore() {
    }

    public static Calibration load(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) return null;
        String encoded = context.getSharedPreferences(PREFS, 0).getString(packageName, null);
        return Calibration.decode(encoded);
    }

    public static void save(Context context, Calibration calibration) {
        if (context == null || calibration == null || calibration.packageName.isEmpty()) return;
        context.getSharedPreferences(PREFS, 0).edit()
                .putString(calibration.packageName, calibration.encode()).apply();
    }

    public static void clear(Context context, String packageName) {
        if (context == null || packageName == null) return;
        context.getSharedPreferences(PREFS, 0).edit().remove(packageName).apply();
    }
}
