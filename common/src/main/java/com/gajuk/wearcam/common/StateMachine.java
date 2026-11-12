package com.gajuk.wearcam.common;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Explicit, typed state machine.
 *
 * <p>Subclasses declare their {@code State} and {@code Event} enums and implement the pure
 * {@link #transition(Enum, Enum)} table. Every accepted transition is logged with a stable
 * prefix ({@code WearCamState <name>: FROM --EVENT--> TO}) so the state of the whole system is
 * inspectable from logcat, and listeners get a typed callback.
 *
 * <p>Invalid events are logged and ignored: a state machine must never throw at runtime.
 */
public abstract class StateMachine<S extends Enum<S>, E extends Enum<E>> {

    public interface Logger {
        void log(String line);
    }

    public interface Listener<S, E> {
        void onTransition(S from, S to, E event);
    }

    private static volatile Logger logger = System.out::println;

    /** Route transition logs somewhere else (logcat, tests). Null disables logging. */
    public static void setLogger(Logger newLogger) {
        logger = newLogger != null ? newLogger : line -> { };
    }

    private final String name;
    private volatile S state;
    private final List<Listener<S, E>> listeners = new CopyOnWriteArrayList<>();

    protected StateMachine(String name, S initial) {
        this.name = name;
        this.state = initial;
    }

    public final String name() {
        return name;
    }

    public final S state() {
        return state;
    }

    public final void addListener(Listener<S, E> listener) {
        listeners.add(listener);
    }

    public final void removeListener(Listener<S, E> listener) {
        listeners.remove(listener);
    }

    /** Pure transition table: the next state, or {@code null} when the event is invalid here. */
    protected abstract S transition(S from, E event);

    public final S send(E event) {
        S from;
        S to;
        synchronized (this) {
            from = state;
            to = transition(from, event);
            if (to == null) {
                logger.log("WearCamState " + name + ": " + from + " --" + event + "--> (ignored)");
                return from;
            }
            if (to == from) {
                return from;
            }
            state = to;
        }
        logger.log("WearCamState " + name + ": " + from + " --" + event + "--> " + to);
        for (Listener<S, E> listener : listeners) {
            listener.onTransition(from, to, event);
        }
        return to;
    }
}
