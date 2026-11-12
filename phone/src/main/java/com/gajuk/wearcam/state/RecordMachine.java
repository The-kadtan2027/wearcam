package com.gajuk.wearcam.state;

import com.gajuk.wearcam.common.StateMachine;

/**
 * Stock-camera recorder state for the accessibility shutter. The watch sends a single {@code S}
 * click; this machine decides whether that click starts or stops recording and which status
 * ({@code REC}/{@code STOP}/{@code SHOT}) to report.
 */
public final class RecordMachine extends StateMachine<RecordMachine.State, RecordMachine.Event> {

    public enum State { IDLE, RECORDING }

    public enum Event { START, STOP }

    public RecordMachine() {
        super("record", State.IDLE);
    }

    public boolean isRecording() {
        return state() == State.RECORDING;
    }

    /** @return the status to report, or null when the click must not be reported. */
    public String click(boolean videoMode) {
        if (!videoMode) return "SHOT";
        if (isRecording()) {
            send(Event.STOP);
            return "STOP";
        }
        send(Event.START);
        return "REC";
    }

    /** Leaving video mode stops the recorder on the stock camera. */
    public boolean modeChanged(boolean videoMode) {
        if (!videoMode && isRecording()) {
            send(Event.STOP);
            return true;
        }
        return false;
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
