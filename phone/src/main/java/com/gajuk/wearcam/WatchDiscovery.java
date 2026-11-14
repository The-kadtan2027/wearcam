package com.gajuk.wearcam;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the watch's wireless-debugging endpoints over mDNS (NsdManager): the pairing port
 * ({@code _adb-tls-pairing._tcp}) and the connect port ({@code _adb-tls-connect._tcp}), so the
 * user only has to enter the 6-digit pairing code.
 */
public final class WatchDiscovery {

    public interface Listener {
        void onFound(String pairingHost, int pairingPort, String connectHost, int connectPort);

        void onStatus(String text);
    }

    private final NsdManager nsdManager;
    private final Listener listener;
    private final List<NsdManager.DiscoveryListener> active = new ArrayList<>();

    private String pairingName;
    private String pairingHost;
    private int pairingPort;
    private String connectName;
    private String connectHost;
    private int connectPort;

    public WatchDiscovery(Context context, Listener listener) {
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.listener = listener;
    }

    public void start() {
        if (nsdManager == null) {
            listener.onStatus("mDNS unavailable on this device");
            return;
        }
        discover("_adb-tls-pairing._tcp", true);
        discover("_adb-tls-connect._tcp", false);
    }

    private void discover(String type, boolean pairing) {
        NsdManager.DiscoveryListener discovery = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String serviceType) {
                listener.onStatus("Searching for the watch…");
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo info) {
                        String host = info.getHost() != null
                                ? info.getHost().getHostAddress() : null;
                        if (host == null) return;
                        synchronized (WatchDiscovery.this) {
                            if (pairing) {
                                pairingName = info.getServiceName();
                                pairingHost = host;
                                pairingPort = info.getPort();
                            } else {
                                connectName = info.getServiceName();
                                connectHost = host;
                                connectPort = info.getPort();
                            }
                            report();
                        }
                    }
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                listener.onStatus("Discovery failed: " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            }
        };
        active.add(discovery);
        nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discovery);
    }

    private void report() {
        if (pairingHost == null || connectHost == null) return;
        listener.onFound(pairingHost, pairingPort, connectHost, connectPort);
    }

    public void stop() {
        for (NsdManager.DiscoveryListener discovery : active) {
            try {
                nsdManager.stopServiceDiscovery(discovery);
            } catch (Exception ignored) {
            }
        }
        active.clear();
    }
}
