package com.gajuk.wearcam.watch;

import java.io.DataInputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class WifiVideo {

    public interface Listener {
        void onConfig(byte[] csd);

        void onFrame(byte[] data);

        void onState(boolean connected);
    }

    private volatile String host;
    private final Listener listener;
    private volatile boolean running = true;
    private Thread thread;

    public WifiVideo(String host, Listener listener) {
        this.host = host;
        this.listener = listener;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void start() {
        thread = new Thread(this::loop, "wifi-video");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        while (running) {
            try {
                HttpURLConnection connection = (HttpURLConnection)
                        new URL("http://" + host + "/video").openConnection();
                connection.setConnectTimeout(1500);
                connection.setReadTimeout(4000);
                connection.connect();
                listener.onState(true);
                DataInputStream in = new DataInputStream(connection.getInputStream());
                while (running) {
                    int type = in.readUnsignedByte();
                    int length = in.readInt();
                    if (length <= 0 || length > 2_000_000) break;
                    byte[] buffer = new byte[length];
                    in.readFully(buffer);
                    if (type == 4) {
                        listener.onConfig(buffer);
                    } else if (type == 5) {
                        listener.onFrame(buffer);
                    }
                }
                connection.disconnect();
            } catch (Exception ignored) {
            }
            listener.onState(false);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
