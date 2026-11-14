package com.gajuk.wearcam.watch;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * H.264-over-TCP client. Uses a raw socket (not HttpURLConnection) so TCP_NODELAY and a large
 * receive buffer can be set: both matter for live latency.
 */
public class WifiVideo {

    public interface Listener {
        void onConfig(byte[] csd);

        void onFrame(byte[] data, long tsUs);

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
            Socket socket = null;
            try {
                String address = host;
                int port = 8080;
                int colon = address.lastIndexOf(':');
                if (colon > 0) {
                    port = Integer.parseInt(address.substring(colon + 1));
                    address = address.substring(0, colon);
                }
                socket = new Socket();
                socket.setTcpNoDelay(true);
                socket.setReceiveBufferSize(1 << 20);
                socket.connect(new InetSocketAddress(address, port), 1500);

                OutputStream out = socket.getOutputStream();
                out.write(("GET /video HTTP/1.1\r\nHost: " + address + "\r\n\r\n")
                        .getBytes("ISO-8859-1"));
                out.flush();

                InputStream raw = socket.getInputStream();
                StringBuilder line = new StringBuilder();
                int b;
                while ((b = raw.read()) != -1) {
                    if (b == '\n') {
                        if (line.length() == 0) break;
                        line.setLength(0);
                    } else if (b != '\r') {
                        line.append((char) b);
                    }
                }
                listener.onState(true);

                DataInputStream in = new DataInputStream(new BufferedInputStream(raw, 1 << 16));
                while (running) {
                    int type = in.readUnsignedByte();
                    int length = in.readInt();
                    if (length <= 0 || length > 2_000_000) break;
                    byte[] buffer = new byte[length];
                    in.readFully(buffer);
                    if (type == 4) {
                        listener.onConfig(buffer);
                    } else if (type == 5) {
                        listener.onFrame(buffer, 0);
                    } else if (type == 6 && length >= 8) {
                        long ts = 0;
                        for (int i = 0; i < 8; i++) ts = (ts << 8) | (buffer[i] & 0xFF);
                        byte[] nal = new byte[length - 8];
                        System.arraycopy(buffer, 8, nal, 0, nal.length);
                        listener.onFrame(nal, ts);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Exception ignored) {
                    }
                }
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
