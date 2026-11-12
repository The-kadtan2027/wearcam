package com.gajuk.wearcam.watch.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gajuk.wearcam.common.StateMachine;

import org.junit.Before;
import org.junit.Test;

public class WatchMachinesTest {

    @Before
    public void quiet() {
        StateMachine.setLogger(null);
    }

    @Test
    public void linkLifecycle() {
        LinkMachine m = new LinkMachine();
        assertEquals(LinkMachine.State.IDLE, m.state());

        m.send(LinkMachine.Event.START);
        assertEquals(LinkMachine.State.CONNECTING, m.state());
        assertTrue(m.isRetrying());
        assertFalse(m.isConnected());

        m.send(LinkMachine.Event.NO_PHONE);
        assertEquals(LinkMachine.State.RECONNECTING, m.state());

        m.send(LinkMachine.Event.CONNECTED);
        assertEquals(LinkMachine.State.CONNECTED, m.state());
        assertTrue(m.isConnected());

        m.send(LinkMachine.Event.LOST);
        assertEquals(LinkMachine.State.RECONNECTING, m.state());

        m.send(LinkMachine.Event.UNAVAILABLE);
        assertEquals(LinkMachine.State.UNAVAILABLE, m.state());

        m.send(LinkMachine.Event.START);
        assertEquals(LinkMachine.State.CONNECTING, m.state());
    }

    @Test
    public void linkIgnoresInvalidEvents() {
        LinkMachine m = new LinkMachine();
        m.send(LinkMachine.Event.CONNECTED);
        assertEquals(LinkMachine.State.IDLE, m.state());
        m.send(LinkMachine.Event.LOST);
        assertEquals(LinkMachine.State.IDLE, m.state());
    }

    @Test
    public void transportUpgradeAndFallback() {
        TransportMachine t = new TransportMachine();
        assertEquals(TransportMachine.State.BT, t.state());
        assertEquals("bt", t.label());

        t.send(TransportMachine.Event.WIFI_CONNECT);
        assertEquals(TransportMachine.State.WIFI_CONNECTING, t.state());

        t.send(TransportMachine.Event.WIFI_OPEN);
        assertEquals(TransportMachine.State.WIFI, t.state());
        assertEquals("wifi", t.label());

        t.send(TransportMachine.Event.WIFI_CLOSED);
        assertEquals(TransportMachine.State.WIFI_CONNECTING, t.state());

        t.send(TransportMachine.Event.WIFI_OPEN);
        assertEquals(TransportMachine.State.WIFI, t.state());

        t.send(TransportMachine.Event.WIFI_CLOSED);
        t.send(TransportMachine.Event.WIFI_CLOSED);
        assertEquals(TransportMachine.State.WIFI_CONNECTING, t.state());
    }

    @Test
    public void modeRequestConfirmsAndTimesOut() {
        ModeMachine m = new ModeMachine();
        assertEquals("?", m.display());

        m.confirm("PHOTO");
        assertEquals("PHOTO", m.display());
        assertEquals(ModeMachine.State.SYNCED, m.state());

        m.request("VIDEO");
        assertEquals(ModeMachine.State.REQUESTING, m.state());
        assertEquals("VIDEO", m.display());

        m.confirm("VIDEO");
        assertEquals(ModeMachine.State.SYNCED, m.state());
        assertEquals("VIDEO", m.display());

        m.request("PORTRAIT");
        m.timeout();
        assertEquals(ModeMachine.State.SYNCED, m.state());
        assertEquals("VIDEO", m.display());
    }

    @Test
    public void countdownFiresOnce() {
        CountdownMachine c = new CountdownMachine();
        assertTrue(c.start(3));
        assertFalse(c.start(3));

        assertFalse(c.tick());
        assertEquals(2, c.remaining());
        assertFalse(c.tick());
        assertEquals(1, c.remaining());
        assertTrue(c.tick());
        assertEquals(CountdownMachine.State.IDLE, c.state());
        assertFalse(c.tick());
    }

    @Test
    public void countdownCancel() {
        CountdownMachine c = new CountdownMachine();
        assertTrue(c.start(3));
        c.cancel();
        assertEquals(CountdownMachine.State.IDLE, c.state());
        assertFalse(c.tick());
    }

    @Test
    public void recordingTracksElapsed() {
        RecordingMachine r = new RecordingMachine();
        assertFalse(r.isRecording());
        assertEquals(0, r.elapsedSeconds());

        r.start();
        assertTrue(r.isRecording());
        assertTrue(r.elapsedSeconds() >= 0);

        r.stop();
        assertFalse(r.isRecording());
        assertEquals(0, r.elapsedSeconds());
    }
}
