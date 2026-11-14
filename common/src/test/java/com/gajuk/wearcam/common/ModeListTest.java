package com.gajuk.wearcam.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ModeListTest {

    @Test
    public void parsesAndDeduplicates() {
        List<String> modes = ModeList.parse("PRO, VIDEO ,PHOTO,,PRO");
        assertEquals(Arrays.asList("PRO", "VIDEO", "PHOTO"), modes);
    }

    @Test
    public void emptyInput() {
        assertTrue(ModeList.parse(null).isEmpty());
        assertTrue(ModeList.parse("").isEmpty());
        assertNull(ModeList.next(ModeList.parse(""), "PHOTO"));
    }

    @Test
    public void cyclesThroughReportedModes() {
        List<String> modes = ModeList.parse("PRO,VIDEO,PHOTO,PORTRAIT,MORE");
        assertEquals("VIDEO", ModeList.next(modes, "PRO"));
        assertEquals("PHOTO", ModeList.next(modes, "VIDEO"));
        assertEquals("PRO", ModeList.next(modes, "MORE"));
        assertEquals("PRO", ModeList.next(modes, "UNKNOWN"));
        assertEquals("PORTRAIT", ModeList.next(modes, "photo"));
    }

    @Test
    public void singleModeStays() {
        List<String> modes = ModeList.parse("PHOTO");
        assertEquals("PHOTO", ModeList.next(modes, "PHOTO"));
        assertEquals("PHOTO", ModeList.next(modes, null));
    }
}
