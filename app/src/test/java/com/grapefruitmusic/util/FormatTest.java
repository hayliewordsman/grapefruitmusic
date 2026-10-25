package com.grapefruitmusic.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormatTest {
    @Test
    public void durations() {
        assertEquals("0:00", Format.duration(0));
        assertEquals("3:07", Format.duration(187000));
        assertEquals("1:02:03", Format.duration(3723000));
        assertEquals("-1:00", Format.remaining(120000, 180000));
        assertEquals("-0:00", Format.remaining(200000, 180000));
    }

    @Test
    public void sizesAndSummaries() {
        assertEquals("512 B", Format.bytes(512));
        assertEquals("1.5 MB", Format.bytes(1572864));
        assertEquals("12 songs, 47 minutes", Format.songsSummary(12, 47 * 60000L));
        assertEquals("1 song", Format.songsSummary(1, 0));
    }

    @Test
    public void md5MatchesKnownValue() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", Hashes.md5("hello"));
    }
}
