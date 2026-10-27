package com.grapefruitmusic.radio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StationTextTest {
    @Test
    public void playsWhatAndroid42Can() {
        assertTrue(StationText.playable("MP3", false));
        assertTrue(StationText.playable("aac+", false));
        assertTrue(StationText.playable("OGG", false));
        assertFalse(StationText.playable("AAC", true));
        assertFalse(StationText.playable("OPUS", false));
        assertFalse(StationText.playable("FLAC", false));
        assertFalse(StationText.playable("", false));
    }

    @Test
    public void describesStations() {
        assertEquals("Jazz, Smooth Jazz · United States", StationText.describe("jazz,smooth jazz,lounge", "United States"));
        assertEquals("BBC, 80s", StationText.describe("BBC, 80s", ""));
        assertEquals("Germany", StationText.describe("", "Germany"));
        assertEquals("Internet Radio", StationText.describe(null, null));
        assertEquals("Hip-Hop/R&B", StationText.titleCase("hip-hop/r&b"));
    }

    @Test
    public void splitsWhatsOn() {
        assertArrayEquals(new String[]{"Song", "Band — KJAZ"}, StationText.nowPlaying("KJAZ", "Jazz", "Band - Song"));
        assertArrayEquals(new String[]{"Morning Show", "KJAZ"}, StationText.nowPlaying("KJAZ", "Jazz", "Morning Show"));
        assertArrayEquals(new String[]{"KJAZ", "Jazz"}, StationText.nowPlaying("KJAZ", "Jazz", null));
        assertArrayEquals(new String[]{"KJAZ", "Jazz"}, StationText.nowPlaying("KJAZ", "Jazz", "  "));
    }
}
