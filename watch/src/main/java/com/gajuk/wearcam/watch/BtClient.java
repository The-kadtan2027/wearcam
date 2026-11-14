package com.gajuk.wearcam.watch;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.gajuk.wearcam.watch.state.LinkMachine;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BtClient {

    public interface Listener {
        void onFrame(Bitmap bitmap);

        void onStatus(String text);

        void onStrip(Bitmap bitmap);

        void onVideoConfig(byte[] csd);

        void onVideoFrame(byte[] data, long tsUs);

        void onApkChunk(int total, int offset, byte[] data, int dataOffset, int length);

        void onLink(LinkMachine.State state);
    }

    private static final UUID SVC = UUID.fromString("7d8f0e10-3c2a-4f5e-9b1d-2a6c8e4f0b21");

    private final BluetoothAdapter adapter;
    private volatile Listener listener;
    private final ExecutorService tx = Executors.newSingleThreadExecutor();

    private volatile BluetoothSocket socket;
    private volatile OutputStream out;
    private volatile boolean running = true;
    private final LinkMachine link = new LinkMachine();

    public BtClient(BluetoothAdapter adapter, Listener listener) {
        this.adapter = adapter;
        this.listener = listener;
        link.addListener((from, to, event) -> {
            Listener l = this.listener;
            if (l != null) l.onLink(to);
        });
    }

    public LinkMachine link() {
        return link;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return out != null;
    }

    public void connect() {
        if (adapter == null) {
            link.send(LinkMachine.Event.UNAVAILABLE);
            return;
        }
        if (!adapter.isEnabled()) {
            link.send(LinkMachine.Event.UNAVAILABLE);
            return;
        }
        link.send(LinkMachine.Event.START);
        new Thread(this::doConnect, "bt-connect").start();
    }

    private void doConnect() {
        while (running) {
            try {
                Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                BluetoothDevice phone = null;
                for (BluetoothDevice d : bonded) {
                    String name = d.getName();
                    if (name != null && name.toLowerCase().contains("oppo")) {
                        phone = d;
                        break;
                    }
                }
                if (phone == null) {
                    link.send(LinkMachine.Event.NO_PHONE);
                    Thread.sleep(3000);
                    continue;
                }
                BluetoothSocket s = phone.createRfcommSocketToServiceRecord(SVC);
                socket = s;
                s.connect();
                out = s.getOutputStream();
                link.send(LinkMachine.Event.CONNECTED);
                readLoop(s);
            } catch (Exception e) {
                out = null;
                link.send(LinkMachine.Event.RETRY);
            }
            if (!running) break;
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void readLoop(BluetoothSocket s) {
        try {
            DataInputStream in = new DataInputStream(s.getInputStream());
            while (running) {
                int type = in.readUnsignedByte();
                int len = in.readInt();
                if (len <= 0 || len > 2_000_000) {
                    android.util.Log.w("WearCamW", "bad frame type=" + type + " len=" + len);
                    break;
                }
                byte[] buf = new byte[len];
                in.readFully(buf);
                // Frames keep flowing while the UI is gone (service-owned client);
                // drop them instead of killing the link with a null listener.
                Listener l = listener;
                if (l == null) continue;
                if (type == 1) {
                    Bitmap bmp = BitmapFactory.decodeByteArray(buf, 0, len);
                    if (bmp != null) l.onFrame(bmp);
                } else if (type == 2) {
                    l.onStatus(new String(buf));
                } else if (type == 3) {
                    Bitmap bmp = BitmapFactory.decodeByteArray(buf, 0, len);
                    if (bmp != null) l.onStrip(bmp);
                } else if (type == 4) {
                    l.onVideoConfig(buf);
                } else if (type == 5) {
                    l.onVideoFrame(buf, 0);
                } else if (type == 7 && len >= 8) {
                    int total = ((buf[0] & 0xFF) << 24) | ((buf[1] & 0xFF) << 16)
                            | ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
                    int offset = ((buf[4] & 0xFF) << 24) | ((buf[5] & 0xFF) << 16)
                            | ((buf[6] & 0xFF) << 8) | (buf[7] & 0xFF);
                    l.onApkChunk(total, offset, buf, 8, len - 8);
                } else if (type == 6 && len >= 8) {
                    long ts = 0;
                    for (int i = 0; i < 8; i++) ts = (ts << 8) | (buf[i] & 0xFF);
                    byte[] nal = new byte[len - 8];
                    System.arraycopy(buf, 8, nal, 0, nal.length);
                    l.onVideoFrame(nal, ts);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("WearCamW", "read loop: " + e);
        } finally {
            out = null;
            link.send(LinkMachine.Event.LOST);
        }
    }

    public void send(final String command) {
        tx.execute(() -> {
            OutputStream o = out;
            if (o == null) return;
            try {
                o.write((command + "\n").getBytes());
                o.flush();
            } catch (Exception ignored) {
            }
        });
    }

    public void close() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
    }
}
