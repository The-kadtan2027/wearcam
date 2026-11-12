package com.gajuk.wearcam.watch.state;

import com.gajuk.wearcam.common.StateMachine;

/**
 * Camera-mode selection with confirmation. The watch requests a mode via an intent and shows the
 * target optimistically; the phone confirms with a {@code MODE=} status. If no confirmation
 * arrives before the owner's timeout, the display reverts to the last confirmed mode instead of
 * lying indefinitely.
 */
public final class ModeMachine extends StateMachine<ModeMachine.State, ModeMachine.Event> {

    public enum State { SYNCED, REQUESTING }

    public enum Event { REQUEST, CONFIRM, TIMEOUT }

    private volatile String confirmed = "?";
    private volatile String target = "?";

    public ModeMachine() {
        super("mode", State.SYNCED);
    }

    public String confirmed() {
        return confirmed;
    }

    /** What the UI should show: the target while a request is in flight, else the confirmed mode. */
    public String display() {
        return state() == State.REQUESTING ? target : confirmed;
    }

    public boolean isRequesting() {
        return state() == State.REQUESTING;
    }

    public void request(String mode) {
        target = mode;
        send(Event.REQUEST);
    }

    public void confirm(String mode) {
        confirmed = mode;
        send(Event.CONFIRM);
    }

    public void timeout() {
        send(Event.TIMEOUT);
    }

    @Override
    protected State transition(State from, Event event) {
        switch (event) {
            case REQUEST:
                return State.REQUESTING;
            case CONFIRM:
                return State.SYNCED;
            case TIMEOUT:
                return from == State.REQUESTING ? State.SYNCED : null;
            default:
                return null;
        }
    }
}
