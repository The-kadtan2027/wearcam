package com.gajuk.wearcam.watch.state;

import com.gajuk.wearcam.common.StateMachine;

/**
 * Recording indicator driven by the phone's {@code REC}/{@code STOP} statuses. Owns the start
 * timestamp so the capsule counter is derived from one source of truth.
 */
public final class RecordingMachine extends StateMachine<RecordingMachine.State, RecordingMachine.Event> {

    public enum State { IDLE, RECORDING }

    public enum Event { START, STOP }

    private volatile long startedAt;

    public RecordingMachine() {
        super("recording", State.IDLE);
    }

    public boolean isRecording() {
        return state() == State.RECORDING;
    }

    public void start() {
        startedAt = System.nanoTime();
        send(Event.START);
    }

    public void stop() {
        send(Event.STOP);
    }

    public long elapsedSeconds() {
        if (state() != State.RECORDING) return 0;
        return (System.nanoTime() - startedAt) / 1_000_000_000L;
    }

    @Override
    protected State transition(State from, Event event) {
        switch (event) {
            case START:
                return from == State.IDLE ? State.RECORDING : null;
            case STOP:
                return from == State.RECORDING ? State.IDLE : null;
            default:
                return null;
        }
    }
}
