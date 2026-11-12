package com.gajuk.wearcam.watch.state;

import com.gajuk.wearcam.common.StateMachine;

/**
 * Video source selection. BT is the always-available fallback; Wi-Fi takes over when its TCP
 * connection opens and falls back to BT when it drops. Only one source may feed the decoder.
 */
public final class TransportMachine extends StateMachine<TransportMachine.State, TransportMachine.Event> {

    public enum State { BT, WIFI_CONNECTING, WIFI }

    public enum Event { WIFI_CONNECT, WIFI_OPEN, WIFI_CLOSED }

    public TransportMachine() {
        super("transport", State.BT);
    }

    public boolean isWifi() {
        return state() == State.WIFI;
    }

    public String label() {
        return isWifi() ? "wifi" : "bt";
    }

    @Override
    protected State transition(State from, Event event) {
        switch (event) {
            case WIFI_CONNECT:
                return from == State.BT ? State.WIFI_CONNECTING : null;
            case WIFI_OPEN:
                return from == State.BT ? State.WIFI : from == State.WIFI_CONNECTING ? State.WIFI : null;
            case WIFI_CLOSED:
                return from == State.WIFI ? State.WIFI_CONNECTING
                        : from == State.WIFI_CONNECTING ? State.WIFI_CONNECTING : null;
            default:
                return null;
        }
    }
}
