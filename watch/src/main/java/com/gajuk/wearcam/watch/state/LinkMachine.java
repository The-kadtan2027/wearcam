package com.gajuk.wearcam.watch.state;

import com.gajuk.wearcam.common.StateMachine;

/**
 * Bluetooth control-link lifecycle. Replaces the free-form "bt: connecting/lost/retry" strings.
 *
 * <pre>
 * IDLE --START--> CONNECTING --CONNECTED--> CONNECTED
 *                      |  \--RETRY/NO_PHONE--> RECONNECTING --CONNECTED--> CONNECTED
 *                      |                          ^
 * CONNECTED --LOST-----+--------------------------+
 * any --UNAVAILABLE--> UNAVAILABLE --START--> CONNECTING
 * </pre>
 */
public final class LinkMachine extends StateMachine<LinkMachine.State, LinkMachine.Event> {

    public enum State { IDLE, CONNECTING, CONNECTED, RECONNECTING, UNAVAILABLE }

    public enum Event { START, CONNECTED, LOST, RETRY, NO_PHONE, UNAVAILABLE }

    public LinkMachine() {
        super("link", State.IDLE);
    }

    public boolean isConnected() {
        return state() == State.CONNECTED;
    }

    public boolean isRetrying() {
        State s = state();
        return s == State.CONNECTING || s == State.RECONNECTING;
    }

    @Override
    protected State transition(State from, Event event) {
        switch (event) {
            case UNAVAILABLE:
                return State.UNAVAILABLE;
            case START:
                return (from == State.IDLE || from == State.UNAVAILABLE) ? State.CONNECTING : null;
            case CONNECTED:
                return (from == State.CONNECTING || from == State.RECONNECTING) ? State.CONNECTED : null;
            case LOST:
                return from == State.CONNECTED ? State.RECONNECTING : null;
            case RETRY:
            case NO_PHONE:
                return (from == State.CONNECTING || from == State.RECONNECTING)
                        ? State.RECONNECTING : null;
            default:
                return null;
        }
    }
}
