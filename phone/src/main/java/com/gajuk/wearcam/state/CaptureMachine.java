package com.gajuk.wearcam.state;

import com.gajuk.wearcam.common.StateMachine;

/**
 * Screen-capture service lifecycle. Replaces the {@code running} boolean and guards against
 * duplicate projection requests creating a second virtual display.
 */
public final class CaptureMachine extends StateMachine<CaptureMachine.State, CaptureMachine.Event> {

    public enum State { IDLE, STARTING, STREAMING, STOPPING, ERROR }

    public enum Event { REQUEST, STARTED, STOP, FAILED, DESTROYED }

    public CaptureMachine() {
        super("capture", State.IDLE);
    }

    public boolean isStreaming() {
        return state() == State.STREAMING;
    }

    @Override
    protected State transition(State from, Event event) {
        switch (event) {
            case REQUEST:
                return (from == State.IDLE || from == State.ERROR) ? State.STARTING : null;
            case STARTED:
                return from == State.STARTING ? State.STREAMING : null;
            case STOP:
                return (from == State.STARTING || from == State.STREAMING) ? State.STOPPING : null;
            case FAILED:
                return from == State.STARTING ? State.ERROR : null;
            case DESTROYED:
                return from == State.IDLE ? null : State.IDLE;
            default:
                return null;
        }
    }
}
