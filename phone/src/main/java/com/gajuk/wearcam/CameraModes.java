package com.gajuk.wearcam;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class CameraModes {

    public static final int PHOTO = 0;
    public static final int PORTRAIT = 2;
    public static final int VIDEO = 6;

    public static boolean setMode(Context context, int mode) {
        try {
            Intent intent = new Intent("com.oplus.camera.action.SHORTCUT_TYPE_MENU");
            intent.setComponent(new ComponentName("com.oplus.camera", "com.oplus.camera.Camera"));
            intent.putExtra("mode", mode);
            intent.putExtra("rear", true);
            intent.putExtra("front", false);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            android.util.Log.w("WearCam", "setMode: " + e);
            return false;
        }
    }
}
