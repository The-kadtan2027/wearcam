package com.gajuk.wearcam.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class StateMachineTest {

    enum S { A, B, C }

    enum E { GO, BACK, RESET }

    static final class Machine extends StateMachine<S, E> {
        Machine() {
            super("test", S.A);
        }

        @Override
        protected S transition(S from, E event) {
            switch (event) {
                case GO:
                    return from == S.A ? S.B : from == S.B ? S.C : S.C;
                case BACK:
                    return from == S.C ? S.A : null;
                case RESET:
                    return from;
                default:
                    return null;
            }
        }
    }

    @Before
    public void quiet() {
        StateMachine.setLogger(null);
    }

    @Test
    public void transitionsThroughTable() {
        Machine m = new Machine();
        assertEquals(S.A, m.state());
        assertEquals(S.B, m.send(E.GO));
        assertEquals(S.C, m.send(E.GO));
        assertEquals(S.A, m.send(E.BACK));
    }

    @Test
    public void invalidEventIsIgnoredAndLogged() {
        List<String> lines = new ArrayList<>();
        StateMachine.setLogger(lines::add);
        Machine m = new Machine();
        assertEquals(S.A, m.send(E.BACK));
        assertEquals(S.A, m.state());
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("(ignored)"));
    }

    @Test
    public void selfTransitionDoesNotNotify() {
        Machine m = new Machine();
        m.send(E.GO);
        final int[] calls = {0};
        m.addListener((from, to, event) -> calls[0]++);
        m.send(E.RESET);
        assertEquals(0, calls[0]);
        m.send(E.BACK);
        assertEquals(0, calls[0]);
        m.send(E.GO);
        assertEquals(1, calls[0]);
    }

    @Test
    public void listenerReceivesTypedTransition() {
        Machine m = new Machine();
        List<String> seen = new ArrayList<>();
        m.addListener((from, to, event) -> seen.add(from + "->" + to + ":" + event));
        m.send(E.GO);
        assertEquals(1, seen.size());
        assertEquals("A->B:GO", seen.get(0));
    }
}
