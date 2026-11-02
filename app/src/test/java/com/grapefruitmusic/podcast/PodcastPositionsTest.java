package com.grapefruitmusic.podcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class PodcastPositionsTest {
    @Test
    public void positionsSurviveAWriteAndARead() throws Exception {
        LinkedHashMap<String, Long> saved = new LinkedHashMap<String, Long>();
        saved.put("27b688c00571", 1234567L);
        saved.put("a1b2c3", 42000L);
        Map<String, Long> read = new LinkedHashMap<String, Long>();
        PodcastPositions.parse(new BufferedReader(new StringReader(PodcastPositions.format(saved))), read);
        assertEquals(saved, read);
    }

    @Test
    public void damagedLinesAreSkipped() throws Exception {
        Map<String, Long> read = new LinkedHashMap<String, Long>();
        PodcastPositions.parse(new BufferedReader(new StringReader(
                "good\t5000\nno tab here\n\t77\nbad\tx12\nzero\t0\nalso good\t9\n")), read);
        assertEquals(2, read.size());
        assertEquals(Long.valueOf(5000), read.get("good"));
        assertEquals(Long.valueOf(9), read.get("also good"));
    }

    @Test
    public void onlyTheNewestAreKept() {
        LinkedHashMap<String, Long> positions = new LinkedHashMap<String, Long>();
        for (int i = 0; i < 10; i++) positions.put("ep" + i, 1000L + i);
        PodcastPositions.prune(positions, 4);
        assertEquals(4, positions.size());
        assertFalse(positions.containsKey("ep5"));
        assertTrue(positions.containsKey("ep6"));
        assertTrue(positions.containsKey("ep9"));
    }
}
