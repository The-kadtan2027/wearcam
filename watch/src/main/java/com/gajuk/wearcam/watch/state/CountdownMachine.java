package com.gajuk.wearcam.watch.state;

import com.gajuk.wearcam.common.StateMachine;

/**
 * Shutter countdown. Owns the remaining seconds so a second shutter press cannot start an
 * overlapping countdown; {@link #tick()} reports completion exactly once.
 */
public final class CountdownMachine extends StateMachine<CountdownMachine.State, CountdownMachine.Event> {

    public enum State { IDLE, COUNTING }

    public enum Event { START, FINISH, CANCEL }

    private volatile int remaining;

    public CountdownMachine() {
        super("countdown", State.IDLE);
    }

    public int remaining() {
        return remaining;
    }

    /** @return false when a countdown is already running. */
    public boolean start(int seconds) {
        if (state() != State.IDLE) return false;
        remaining = seconds;
        send(Event.START);
        return true;
    }

    /** @return true when this tick completes the countdown. */
    public boolean tick() {
        if (state() != State.COUNTING) return false;
        remaining--;
        if (remaining <= 0) {
            send(Event.FINISH);
            return true;
        }
        return false;
    }

    public void cancel() {
        send(Event.CANCEL);
    }

    @Override
    protected State transition(State from, Event event) {
        switch (event) {
            case START:
                return from == State.IDLE ? State.COUNTING : null;
            case FINISH:
            case CANCEL:
                return from == State.COUNTING ? State.IDLE : null;
            default:
                return null;
        }
    }
}
