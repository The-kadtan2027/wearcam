package com.gajuk.wearcam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MjpegServer {

    private final android.content.Context context;
    private final int port;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private volatile byte[] frame;
    private volatile long frameId;
    private final AtomicInteger clients = new AtomicInteger();

    public MjpegServer(android.content.Context context, int port) {
        this.context = context.getApplicationContext();
        this.port = port;
    }

    public boolean hasClients() {
        return clients.get() > 0;
    }

    public void setFrame(byte[] jpeg) {
        this.frame = jpeg;
        this.frameId++;
    }

    public void start() {
        running = true;
        Thread t = new Thread(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(port));
                while (running) {
                    final Socket s = serverSocket.accept();
                    Thread c = new Thread(() -> handle(s), "mjpeg-client");
                    c.setDaemon(true);
                    c.start();
                }
            } catch (Exception ignored) {
            }
        }, "mjpeg-server");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
    }

    private void handle(Socket s) {
        try {
            s.setTcpNoDelay(true);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "ISO-8859-1"));
            String req = in.readLine();
            if (req == null) return;
            String[] parts = req.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";
            if (path.startsWith("/stream")) {
                stream(s);
                return;
            }
            if (path.startsWith("/video")) {
                java.io.OutputStream os = s.getOutputStream();
                os.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n"
                        + "Connection: keep-alive\r\n\r\n").getBytes("ISO-8859-1"));
                os.flush();
                VideoHub.add(os);
                try {
                    java.io.InputStream input = s.getInputStream();
                    while (input.read() != -1) {
                    }
                } catch (Exception ignored) {
                } finally {
                    VideoHub.remove(os);
                }
                return;
            }
            boolean local = s.getInetAddress().isLoopbackAddress();
            if (!local && !path.startsWith("/ping")) {
                respond(s, 403, "forbidden");
                return;
            }
            respond(s, 200, route(path));
        } catch (Exception ignored) {
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void respond(Socket s, int code, String body) throws IOException {
        byte[] b = body.getBytes("UTF-8");
        OutputStream os = s.getOutputStream();
        os.write(("HTTP/1.1 " + code + " OK\r\nContent-Type: text/plain\r\nContent-Length: "
                + b.length + "\r\nConnection: close\r\n\r\n").getBytes("ISO-8859-1"));
        os.write(b);
        os.flush();
    }

    private void stream(Socket s) throws IOException {
        clients.incrementAndGet();
        try {
            OutputStream os = s.getOutputStream();
            os.write(("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\n"
                    + "Cache-Control: no-cache, no-store\r\nPragma: no-cache\r\nConnection: close\r\n\r\n")
                    .getBytes("ISO-8859-1"));
            long sent = -1;
            while (running && !s.isClosed()) {
                byte[] f = frame;
                long id = frameId;
                if (f == null || id == sent) {
                    try {
                        Thread.sleep(8);
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                }
                os.write(("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: "
                        + f.length + "\r\n\r\n").getBytes("ISO-8859-1"));
                os.write(f);
                os.write("\r\n".getBytes("ISO-8859-1"));
                os.flush();
                sent = id;
            }
        } finally {
            clients.decrementAndGet();
        }
    }

    private String route(String path) {
        try {
            URI uri = new URI("http://x" + path);
            String p = uri.getPath();
            Map<String, String> q = new HashMap<>();
            if (uri.getQuery() != null) {
                for (String kv : uri.getQuery().split("&")) {
                    int i = kv.indexOf('=');
                    if (i > 0) q.put(kv.substring(0, i), kv.substring(i + 1));
                }
            }
            ShutterService s = ShutterService.instance;
            if ("/ping".equals(p)) return "pong " + (s != null ? "acc" : "noacc");
            if (s == null) return "no-accessibility";
            if ("/shutter".equals(p)) return s.clickShutter() ? "ok" : "fail";
            if ("/setmode".equals(p)) {
                int mode = Integer.parseInt(q.getOrDefault("m", "0"));
                boolean ok = CameraModes.setMode(context, mode);
                return ok ? "ok" : "fail";
            }
            if ("/remotecap".equals(p)) {
                RemoteCapture.capture(context, (ok, file) ->
                        android.util.Log.i("WearCam", "remote capture ok=" + ok + " file=" + file));
                return "started";
            }
            if ("/mode".equals(p)) return "MODE=" + s.currentMode();
            if ("/zoom".equals(p)) return s.pinch("in".equals(q.getOrDefault("dir", "in"))) ? "ok" : "fail";
            if ("/bartap".equals(p)) {
                float x = Float.parseFloat(q.getOrDefault("x", "0"));
                float y = Float.parseFloat(q.getOrDefault("y", "48"));
                return s.tapBar(x, y) ? "ok" : "fail";
            }
            if ("/tap".equals(p)) {
                float x = Float.parseFloat(q.getOrDefault("x", "0"));
                float y = Float.parseFloat(q.getOrDefault("y", "0"));
                return s.tapAtStream384(x, y) ? "ok" : "fail";
            }
            return "unknown";
        } catch (Exception e) {
            return "err";
        }
    }
}
