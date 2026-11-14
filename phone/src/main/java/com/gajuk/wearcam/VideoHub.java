package com.gajuk.wearcam;

import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fan-out of H.264 packets to TCP clients. Each client has its own sender thread and a
 * two-packet queue that drops the oldest entry, so a slow or stalled client can never block the
 * encoder's drain thread (and therefore never add latency to the other clients).
 */
public class VideoHub {

    private static final class Client {
        final OutputStream out;
        final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(2);

        Client(OutputStream out) {
            this.out = out;
        }
    }

    private static final List<Client> CLIENTS = new CopyOnWriteArrayList<>();

    public static void add(OutputStream out) {
        Client client = new Client(out);
        CLIENTS.add(client);
        Thread sender = new Thread(() -> pump(client), "video-client");
        sender.setDaemon(true);
        sender.start();
    }

    public static void remove(OutputStream out) {
        CLIENTS.removeIf(client -> client.out == out);
    }

    public static int size() {
        return CLIENTS.size();
    }

    public static void broadcast(byte[] packet) {
        for (Client client : CLIENTS) {
            if (!client.queue.offer(packet)) {
                client.queue.poll();
                client.queue.offer(packet);
            }
        }
    }

    private static void pump(Client client) {
        while (true) {
            try {
                byte[] packet = client.queue.take();
                synchronized (client.out) {
                    client.out.write(packet);
                    client.out.flush();
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                CLIENTS.remove(client);
                break;
            }
        }
    }
}
