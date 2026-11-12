package com.gajuk.wearcam;

import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class VideoHub {

    private static final List<OutputStream> CLIENTS = new CopyOnWriteArrayList<>();

    public static void add(OutputStream out) {
        CLIENTS.add(out);
    }

    public static void remove(OutputStream out) {
        CLIENTS.remove(out);
    }

    public static int size() {
        return CLIENTS.size();
    }

    public static void broadcast(byte[] packet) {
        for (OutputStream out : CLIENTS) {
            try {
                synchronized (out) {
                    out.write(packet);
                    out.flush();
                }
            } catch (Exception e) {
                CLIENTS.remove(out);
            }
        }
    }
}
