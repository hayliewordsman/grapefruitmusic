package com.grapefruitmusic.ui;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class SourcesTest {
    private static final UserSources.Source SOURCE=new UserSources.Source("abc","m3u","My TV","https://example.com/list.m3u","","");
    @Test public void readsAnM3uPlaylistByNameGroupAndUserAgent() throws Exception {
        String m3u="#EXTM3U\n"
            +"#EXTINF:-1 tvg-id=\"one\" tvg-name=\"One HD\" group-title=\"News\",One News\n"
            +"#EXTVLCOPT:http-user-agent=SomePlayer/1.0\n"
            +"http://tv.example.com/live/1.ts\n"
            +"#EXTINF:-1 group-title=\"Sports\" tvg-name=\"Two\",\n"
            +"https://tv.example.com/live/2.m3u8\n"
            +"#EXTINF:-1,Not a web address\n"
            +"rtmp://tv.example.com/3\n"
            +"#EXTINF:-1,Three\n"
            +"#EXTGRP:Movies\n"
            +"https://tv.example.com/live/3.m3u8\n";
        List<Channel> got=UserSources.parseM3u(SOURCE,m3u);
        assertEquals(3,got.size());
        assertEquals("One News",got.get(0).name);assertEquals("News",got.get(0).description);assertEquals("SomePlayer/1.0",got.get(0).userAgent);
        assertEquals("Two",got.get(1).name);assertEquals("Sports",got.get(1).description);assertEquals("",got.get(1).userAgent);
        assertEquals("Three",got.get(2).name);assertEquals("Movies",got.get(2).description);
        assertTrue(got.get(0).added());assertEquals("My TV",got.get(0).source);assertEquals("Added",got.get(0).category);
        assertEquals("src-abc-0",got.get(0).id);
    }
    @Test(expected=java.io.IOException.class) public void refusesWhatIsntAPlaylist() throws Exception { UserSources.parseM3u(SOURCE,"<html>Not found</html>"); }
    @Test public void tellsHlsFromDirectStreams() {
        assertTrue(LivePlayer.hls("https://a.example/live/index.m3u8?token=1"));
        assertTrue(LivePlayer.hls("http://a.example/hls/live/x/master"));
        assertFalse(LivePlayer.hls("http://a.example/live/user/pass/123.ts"));
        assertFalse(LivePlayer.hls("http://a.example:8080/123"));
    }
}
