package com.grapefruitmusic.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SourceHelpersTest {
    @Test
    public void subsonicTokenAuth() {
        // Example from the Subsonic API docs: password "sesame", salt "c19b2d".
        String q = SubsonicSource.authQuery("admin", "sesame", "c19b2d");
        assertTrue(q, q.startsWith("u=admin&t=26719a1196d2a940705a59634eb18eab&s=c19b2d&v=1.16.1"));
        assertTrue(q.endsWith("&f=json"));
    }

    @Test
    public void playableFormats() {
        assertTrue(Formats.playable("MP3"));
        assertTrue(Formats.playable("flac"));
        assertFalse(Formats.playable("opus"));
        assertFalse(Formats.playable(null));
        assertEquals("flac", Formats.extensionFor("audio/flac", "mp3"));
        assertEquals("mp3", Formats.extensionFor("audio/mpeg; charset=binary", "x"));
        assertEquals("m4a", Formats.extensionFor("audio/mp4", "x"));
        assertEquals("ogg", Formats.extensionFor(null, "ogg"));
    }
}
