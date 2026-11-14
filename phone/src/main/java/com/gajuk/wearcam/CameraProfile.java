package com.gajuk.wearcam;

import java.util.Locale;

/**
 * Per-brand hints for the generic camera automation. The automation itself is
 * accessibility-tree driven (find by text/desc/view-id); profiles only supply the
 * vocabulary a given camera app uses and its known mode labels.
 */
public final class CameraProfile {

    private static final String[] BASE_MODES = {
            "photo", "video", "portrait", "pro", "night", "more", "slow motion",
            "hyperlapse", "panorama", "food", "single take", "macro", "movie",
            "dual view", "text", "astro", "super macro", "high resolution",
            "director's view", "action pan", "long exposure"
    };

    public final String id;
    public final String[] packages;
    public final String[] shutterHints;
    public final String[] videoHints;
    public final String[] modeHints;

    private CameraProfile(String id, String[] packages, String[] shutterHints,
                          String[] videoHints, String[] modeHints) {
        this.id = id;
        this.packages = packages;
        this.shutterHints = shutterHints;
        this.videoHints = videoHints;
        this.modeHints = modeHints;
    }

    public static final CameraProfile OPPO = new CameraProfile("oppo",
            new String[]{"com.oplus.camera", "com.coloros.camera"},
            new String[]{"shutter", "capture", "take photo", "take picture", "shoot"},
            new String[]{"record", "stop", "video"},
            BASE_MODES);

    public static final CameraProfile SAMSUNG = new CameraProfile("samsung",
            new String[]{"com.sec.android.app.camera"},
            new String[]{"shutter", "capture", "take picture", "take photo"},
            new String[]{"record", "stop", "pause"},
            BASE_MODES);

    public static final CameraProfile GOOGLE = new CameraProfile("google",
            new String[]{"com.google.android.GoogleCamera"},
            new String[]{"shutter", "capture", "take photo", "take picture"},
            new String[]{"record", "stop"},
            BASE_MODES);

    public static final CameraProfile XIAOMI = new CameraProfile("xiaomi",
            new String[]{"com.android.camera", "com.miui.camera"},
            new String[]{"shutter", "capture", "take photo", "take picture"},
            new String[]{"record", "stop", "video"},
            BASE_MODES);

    public static final CameraProfile GENERIC = new CameraProfile("generic",
            new String[]{},
            new String[]{"shutter", "capture", "take photo", "take picture", "shoot"},
            new String[]{"record", "stop", "video"},
            BASE_MODES);

    private static final CameraProfile[] KNOWN = {OPPO, SAMSUNG, GOOGLE, XIAOMI};

    public static CameraProfile forPackage(String pkg) {
        if (pkg != null) {
            for (CameraProfile profile : KNOWN) {
                for (String name : profile.packages) {
                    if (name.equalsIgnoreCase(pkg)) return profile;
                }
            }
        }
        return GENERIC;
    }

    public boolean isModeLabel(String text) {
        if (text == null) return false;
        String value = text.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 24) return false;
        for (String mode : modeHints) {
            if (value.equals(mode)) return true;
        }
        return false;
    }
}
