package com.gajuk.wearcam.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gajuk.wearcam.common.StateMachine;

import org.junit.Before;
import org.junit.Test;

public class PhoneMachinesTest {

    @Before
    public void quiet() {
        StateMachine.setLogger(null);
    }

    @Test
    public void captureLifecycle() {
        CaptureMachine m = new CaptureMachine();
        assertEquals(CaptureMachine.State.IDLE, m.state());

        m.send(CaptureMachine.Event.STARTED);
        assertEquals(CaptureMachine.State.IDLE, m.state());

        m.send(CaptureMachine.Event.REQUEST);
        assertEquals(CaptureMachine.State.STARTING, m.state());

        m.send(CaptureMachine.Event.REQUEST);
        assertEquals(CaptureMachine.State.STARTING, m.state());

        m.send(CaptureMachine.Event.STARTED);
        assertEquals(CaptureMachine.State.STREAMING, m.state());
        assertTrue(m.isStreaming());

        m.send(CaptureMachine.Event.STOP);
        assertEquals(CaptureMachine.State.STOPPING, m.state());

        m.send(CaptureMachine.Event.DESTROYED);
        assertEquals(CaptureMachine.State.IDLE, m.state());
    }

    @Test
    public void captureFailureThenRetry() {
        CaptureMachine m = new CaptureMachine();
        m.send(CaptureMachine.Event.REQUEST);
        m.send(CaptureMachine.Event.FAILED);
        assertEquals(CaptureMachine.State.ERROR, m.state());

        m.send(CaptureMachine.Event.REQUEST);
        assertEquals(CaptureMachine.State.STARTING, m.state());

        m.send(CaptureMachine.Event.STARTED);
        assertEquals(CaptureMachine.State.STREAMING, m.state());
    }

    @Test
    public void captureIgnoresDuplicateStart() {
        CaptureMachine m = new CaptureMachine();
        m.send(CaptureMachine.Event.REQUEST);
        m.send(CaptureMachine.Event.STARTED);
        m.send(CaptureMachine.Event.REQUEST);
        assertEquals(CaptureMachine.State.STREAMING, m.state());
    }

    @Test
    public void recordTogglesInVideoMode() {
        RecordMachine r = new RecordMachine();
        assertEquals("REC", r.click(true));
        assertTrue(r.isRecording());
        assertEquals("STOP", r.click(true));
        assertFalse(r.isRecording());
    }

    @Test
    public void recordPhotoNeverRecords() {
        RecordMachine r = new RecordMachine();
        assertEquals("SHOT", r.click(false));
        assertFalse(r.isRecording());
        assertEquals("REC", r.click(true));
        assertTrue(r.isRecording());
    }

    @Test
    public void leavingVideoStopsRecorder() {
        RecordMachine r = new RecordMachine();
        r.click(true);
        assertTrue(r.isRecording());
        assertTrue(r.modeChanged(false));
        assertFalse(r.isRecording());
        assertFalse(r.modeChanged(false));
    }
}
