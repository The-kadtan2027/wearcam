package com.gajuk.wearcam.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CalibrationTest {

    @Test
    public void roundTrip() {
        List<Calibration.Entry> entries = new ArrayList<>();
        entries.add(new Calibration.Entry("shutter", "", "Shutter", "com.x:id/shutter", 100, 200, 300, 400));
        entries.add(new Calibration.Entry("mode:VIDEO", "VIDEO", "", "", 10, 20, 30, 40));
        Calibration original = new Calibration("com.example.camera", entries);

        Calibration decoded = Calibration.decode(original.encode());
        assertNotNull(decoded);
        assertEquals("com.example.camera", decoded.packageName);
        assertEquals(2, decoded.entries.size());

        Calibration.Entry shutter = decoded.entry("shutter");
        assertNotNull(shutter);
        assertEquals("Shutter", shutter.desc);
        assertEquals("com.x:id/shutter", shutter.id);
        assertEquals(100, shutter.left);
        assertEquals(400, shutter.bottom);
        assertTrue(shutter.hasBounds());

        assertNotNull(decoded.entry("MODE:video"));
        assertEquals(1, decoded.entries("mode:").size());
        assertTrue(decoded.entries("zoom:").isEmpty());
    }

    @Test
    public void escapesDelimiters() {
        Calibration c = new Calibration("pkg|with|pipes", Arrays.asList(
                new Calibration.Entry("shutter", "a|b", "c\nd", "id%", 1, 2, 3, 4)));
        Calibration decoded = Calibration.decode(c.encode());
        assertEquals("pkg|with|pipes", decoded.packageName);
        assertEquals("a|b", decoded.entry("shutter").text);
        assertEquals("c\nd", decoded.entry("shutter").desc);
        assertEquals("id%", decoded.entry("shutter").id);
    }

    @Test
    public void rejectsForeignPayloads() {
        assertNull(Calibration.decode(""));
        assertNull(Calibration.decode("garbage"));
        Calibration empty = Calibration.decode("wearcam-calibration-v1\npkg");
        assertNotNull(empty);
        assertTrue(empty.isEmpty());
        Calibration broken = Calibration.decode("wearcam-calibration-v1\npkg\nbroken-line");
        assertNotNull(broken);
        assertTrue(broken.isEmpty());
    }

    @Test
    public void ignoresMalformedRows() {
        Calibration c = Calibration.decode(
                "wearcam-calibration-v1\npkg\nshutter|t|d|id|1,2,3,4\nbad|row\nx|y|z|w|1,2");
        assertNotNull(c);
        assertEquals(1, c.entries.size());
        assertEquals("shutter", c.entries.get(0).role);
    }
}
