package com.grapefruitmusic.net;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.*;
import java.net.*;
import java.util.*;

public class PodcastAudioTest {
    private static final class Connection extends HttpURLConnection {
        final int status;
        final String location;
        boolean closed;

        Connection(URL u, int status, String location) {
            super(u);
            this.status = status;
            this.location = location;
        }

        @Override
        public int getResponseCode() {
            return status;
        }

        @Override
        public String getHeaderField(String key) {
            return "Location".equalsIgnoreCase(key) ? location : null;
        }

        @Override
        public void disconnect() {
            closed = true;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {}
    }

    @Test
    public void followsTheDailysSixRedirectsAndPreservesRange() throws Exception {
        List<Connection> seen = new ArrayList<Connection>();
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Range", "bytes=100-199");
        HttpURLConnection result =
                PodcastAudio.open(
                        "https://tracker.example/0",
                        headers,
                        u -> {
                            int hop = seen.size();
                            Connection c = new Connection(u, hop < 6 ? 302 : 206, "/" + (hop + 1));
                            seen.add(c);
                            return c;
                        });
        assertEquals(7, seen.size());
        assertEquals(206, result.getResponseCode());
        assertEquals("bytes=100-199", result.getRequestProperty("Range"));
        assertEquals("identity", result.getRequestProperty("Accept-Encoding"));
        assertFalse(result.getInstanceFollowRedirects());
        for (int i = 0; i < 6; i++) assertTrue(seen.get(i).closed);
        assertFalse(seen.get(6).closed);
    }

    @Test
    public void followsHttpToHttpsAndRelativeRedirects() throws Exception {
        List<URL> urls = new ArrayList<URL>();
        HttpURLConnection c =
                PodcastAudio.open(
                        "http://example.org/audio",
                        null,
                        u -> {
                            urls.add(u);
                            return new Connection(
                                    u,
                                    urls.size() == 1 ? 301 : urls.size() == 2 ? 307 : 200,
                                    urls.size() == 1
                                            ? "https://cdn.example.org/path/audio"
                                            : "../final.mp3");
                        });
        assertEquals("https://cdn.example.org/final.mp3", c.getURL().toString());
    }

    @Test
    public void doesNotForwardCredentialsToTrackingOrCdnHosts() throws Exception {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer private");
        headers.put("Cookie", "private=yes");
        headers.put("Range", "bytes=0-99");
        List<Connection> seen = new ArrayList<Connection>();
        HttpURLConnection c =
                PodcastAudio.open(
                        "https://origin.example/a",
                        headers,
                        u -> {
                            Connection n =
                                    new Connection(
                                            u, seen.isEmpty() ? 308 : 206, "https://cdn.example/a");
                            seen.add(n);
                            return n;
                        });
        assertEquals("Bearer private", seen.get(0).getRequestProperty("Authorization"));
        assertNull(c.getRequestProperty("Authorization"));
        assertNull(c.getRequestProperty("Cookie"));
        assertEquals("bytes=0-99", c.getRequestProperty("Range"));
    }

    @Test
    public void rejectsDowngradesAndNonWebRedirects() throws Exception {
        for (String location :
                new String[] {
                    "http://example.org/audio",
                    "file:///etc/passwd",
                    "https://user:pass@example.org/audio"
                }) {
            Connection[] first = new Connection[1];
            try {
                PodcastAudio.open(
                        "https://example.org/audio",
                        null,
                        u -> first[0] = new Connection(u, 302, location));
                fail(location);
            } catch (IOException expected) {
                assertTrue(first[0].closed);
            }
        }
    }

    @Test
    public void rejectsLoopsAndUnboundedChains() throws Exception {
        try {
            PodcastAudio.open("https://example.org/a", null, u -> new Connection(u, 302, "/a"));
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("loop"));
        }
        List<Connection> seen = new ArrayList<Connection>();
        try {
            PodcastAudio.open(
                    "https://example.org/0",
                    null,
                    u -> {
                        Connection c = new Connection(u, 302, "/" + (seen.size() + 1));
                        seen.add(c);
                        return c;
                    });
            fail();
        } catch (IOException expected) {
            assertEquals(21, seen.size());
        }
        for (Connection c : seen) assertTrue(c.closed);
    }

    @Test
    public void copiesKnownAndUnknownLengthAudioWithoutBufferingTheWholeFile() throws Exception {
        byte[] audio = new byte[100000];
        Arrays.fill(audio, (byte) 0x55);
        for (long length : new long[] {audio.length, -1}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            List<Long> updates = new ArrayList<Long>();
            assertEquals(
                    audio.length,
                    PodcastAudio.copy(
                            new ByteArrayInputStream(audio),
                            out,
                            length,
                            (n, total) -> updates.add(n)));
            assertArrayEquals(audio, out.toByteArray());
            assertEquals(Long.valueOf(audio.length), updates.get(updates.size() - 1));
            assertTrue(updates.size() > 1);
        }
    }

    @Test
    public void refusesTruncatedAndEmptyDownloads() throws Exception {
        for (int size : new int[] {0, 10}) {
            try {
                PodcastAudio.copy(
                        new ByteArrayInputStream(new byte[size]),
                        new ByteArrayOutputStream(),
                        100,
                        null);
                fail();
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("ended early"));
            }
        }
    }

    @Test
    public void cancellationStopsCopyingBeforeCompletion() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PodcastAudio.copy(
                    new ByteArrayInputStream(new byte[100000]),
                    out,
                    100000,
                    (n, total) -> {
                        throw new IOException("canceled");
                    });
            fail();
        } catch (IOException expected) {
            assertEquals("canceled", expected.getMessage());
        }
        assertTrue(out.size() < 100000);
    }
}
