package com.grapefruitmusic.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CrashReportTest {
    @Test
    public void addressesLoseTheirQueriesAndSignIns() {
        String r = CrashReport.redact("java.io.IOException: HTTP 500 at "
                + "https://me:secret@music.example/rest/stream.view?u=me&t=token&s=salt&id=42 (retrying)");
        assertFalse(r.contains("token"));
        assertFalse(r.contains("secret"));
        assertFalse(r.contains("u=me"));
        assertTrue(r.contains("https://music.example/rest/stream.view?…"));
        assertTrue(r.endsWith("(retrying)"));
    }

    @Test
    public void filePathsLoseTheirNames() {
        String r = CrashReport.redact("open failed: /storage/emulated/0/Music/Artist/Song.mp3: ENOENT");
        assertEquals("open failed: /storage/…: ENOENT", r);
    }

    @Test
    public void stackFramesAreKept() {
        String frame = "\tat com.grapefruitmusic.net.StreamProxy.serve(StreamProxy.java:240)";
        assertEquals(frame, CrashReport.redact(frame));
    }
}
