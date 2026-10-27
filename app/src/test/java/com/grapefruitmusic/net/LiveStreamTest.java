package com.grapefruitmusic.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;

public class LiveStreamTest {
    @Test
    public void readsShoutcastAndHttpStatusLines() throws Exception {
        assertEquals(200, LiveStream.statusCode("ICY 200 OK"));
        assertEquals(200, LiveStream.statusCode("HTTP/1.0 200 OK"));
        assertEquals(302, LiveStream.statusCode("HTTP/1.1 302 Found"));
        try {
            LiveStream.statusCode("<html>");
            fail();
        } catch (IOException expected) {
            // Not a stream.
        }
    }

    @Test
    public void tellsPlaylistsFromStreams() {
        assertTrue(LiveStream.isPlaylist("audio/x-scpls", "http://a/listen"));
        assertTrue(LiveStream.isPlaylist("audio/x-mpegurl", "http://a/listen"));
        assertTrue(LiveStream.isPlaylist(null, "http://a/radio.pls?x=1"));
        assertTrue(LiveStream.isPlaylist("text/plain", "http://a/radio.m3u"));
        assertFalse(LiveStream.isPlaylist("audio/mpeg", "http://a/radio.m3u"));
        assertFalse(LiveStream.isPlaylist("audio/aacp", "http://a/stream"));
        assertFalse(LiveStream.isPlaylist(null, "http://a/stream.mp3"));
    }

    @Test
    public void findsTheStreamInAPlaylist() throws Exception {
        assertEquals("http://s1.example.com:8000/live", LiveStream.firstStream(
                "[playlist]\nNumberOfEntries=2\nFile1=http://s1.example.com:8000/live\nTitle1=One\n"
                        + "File2=http://s2.example.com/live\nVersion=2\n", "http://example.com/radio.pls"));
        assertEquals("https://cdn.example.com/hi.aac", LiveStream.firstStream(
                "#EXTM3U\r\n#EXTINF:-1,Station\r\nhttps://cdn.example.com/hi.aac\r\n", "http://example.com/a.m3u"));
        assertEquals("http://example.com/radio/stream.mp3", LiveStream.firstStream("stream.mp3\n",
                "http://example.com/radio/list.m3u"));
        try {
            LiveStream.firstStream("#EXTM3U\n#EXT-X-TARGETDURATION:10\nseg1.ts\n", "http://a/x.m3u8");
            fail();
        } catch (IOException expected) {
            // HLS isn't supported.
        }
    }
}
