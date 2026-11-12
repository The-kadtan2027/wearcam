package com.gajuk.wearcam;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.io.File;

/**
 * Client for OPPO's exported remote capture API:
 * com.oplus.camera.remote.RemoteService (permission CAPTURE_PREVIEW is normal).
 * AIDL: capturePreview(String uri, CapturePreviewCallback cb) -> boolean
 * Callback transactions: 1 = onFail(int), 2 = onCaptured()
 */
public class RemoteCapture {

    private static final String TAG = "WearCamRemote";
    private static final String PACKAGE = "com.oplus.camera";
    private static final String ACTION = "com.oplus.camera.remote.service";
    private static final String DESCRIPTOR = "com.oplus.camera.remote.RemoteInterface";

    public interface Listener {
        void onResult(boolean ok, File file);
    }

    public static void capture(Context context, Listener listener) {
        Intent intent = new Intent(ACTION);
        intent.setPackage(PACKAGE);
        final Context app = context.getApplicationContext();
        final Listener callback = listener;

        ServiceConnection connection = new ServiceConnection() {
            private Binder callbackBinder;

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                try {
                    callbackBinder = new Binder() {
                        @Override
                        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                            Log.i(TAG, "callback transact " + code);
                            if (code == 1) {
                                Log.i(TAG, "onCaptured");
                                callback.onResult(true, CaptureProvider.file(app));
                            } else if (code == 2) {
                                int reason = data.readInt();
                                Log.i(TAG, "onFail reason=" + reason);
                                callback.onResult(false, null);
                            }
                            return true;
                        }
                    };

                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(DESCRIPTOR);
                        data.writeString(CaptureProvider.URI.toString());
                        data.writeStrongBinder(callbackBinder);
                        boolean sent = service.transact(1, data, reply, 0);
                        reply.readException();
                        boolean accepted = reply.readInt() != 0;
                        Log.i(TAG, "capturePreview sent=" + sent + " accepted=" + accepted);
                        if (!accepted) {
                            callback.onResult(false, null);
                        }
                    } finally {
                        reply.recycle();
                        data.recycle();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "capture: " + e);
                    callback.onResult(false, null);
                } finally {
                    try {
                        app.unbindService(this);
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };

        try {
            boolean bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            Log.i(TAG, "bind=" + bound);
            if (!bound) {
                listener.onResult(false, null);
            }
        } catch (Exception e) {
            Log.w(TAG, "bind: " + e);
            listener.onResult(false, null);
        }
    }
}
