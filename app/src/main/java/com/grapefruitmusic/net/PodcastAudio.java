package com.grapefruitmusic.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Podcast audio commonly crosses more tracking redirects than Android's built-in limit. */
public final class PodcastAudio {
    private PodcastAudio() {}

    public static HttpURLConnection open(String address, Map<String, String> headers)
            throws IOException {
        return open(address, headers, url -> (HttpURLConnection) url.openConnection());
    }

    interface Connector {
        HttpURLConnection open(URL url) throws IOException;
    }

    static HttpURLConnection open(String address, Map<String, String> headers, Connector connector)
            throws IOException {
        return open(address, headers, connector, null);
    }

    public interface ConnectionListener {
        void opened(HttpURLConnection c) throws IOException;
    }

    public static HttpURLConnection openObserved(
            String address, Map<String, String> headers, ConnectionListener listener)
            throws IOException {
        return open(address, headers, url -> (HttpURLConnection) url.openConnection(), listener);
    }

    /**
     * One episode, streamed: every request for it (the player's first, then one for each seek or
     * reconnect) reads the same copy of the audio. Hosts that put ads in as they serve it stitch a
     * new copy for each request to the feed's address, with other ads and another length, so a
     * request part-way through read another copy from the same byte: the episode jumped, repeated
     * or ended in the wrong place. Where its redirects end is the copy's own address, which keeps
     * giving the same copy: after the first answer, requests go there.
     */
    public static final class Episode {
        private final String address;
        private final Connector connector;
        private volatile String copy;

        public Episode(String address) {
            this(address, url -> (HttpURLConnection) url.openConnection());
        }

        Episode(String address, Connector connector) {
            this.address = address;
            this.connector = connector;
        }

        /** Opens the episode (with a Range among the headers, for part of it). */
        public HttpURLConnection open(Map<String, String> headers) throws IOException {
            String pinned = copy;
            if (pinned != null) {
                // Credentials go only where the feed's own address would send them.
                HttpURLConnection c = PodcastAudio.open(pinned, address, headers, connector, null);
                int code = Http.status(c);
                // 416: the range is past the end of this same copy, which is the copy's answer.
                if (code < 400 || code == 416) return c;
                // The copy has gone (its address expired): a new one, from the feed's address.
                c.disconnect();
                copy = null;
            }
            HttpURLConnection c = PodcastAudio.open(address, address, headers, connector, null);
            int code = Http.status(c);
            if (code >= 200 && code < 300) copy = c.getURL().toString();
            return c;
        }

        /** The copy being read (its address), or null before the first answer. */
        String copy() {
            return copy;
        }
    }

    private static HttpURLConnection open(
            String address,
            Map<String, String> headers,
            Connector connector,
            ConnectionListener listener)
            throws IOException {
        return open(address, address, headers, connector, listener);
    }

    /** Like open(address, ...), sending credentials only to the origin's host. */
    private static HttpURLConnection open(
            String address,
            String original,
            Map<String, String> headers,
            Connector connector,
            ConnectionListener listener)
            throws IOException {
        long deadline = System.nanoTime() + 90L * 1000000000L;
        Set<String> seen = new HashSet<String>();
        for (int hop = 0; hop <= 20; hop++) {
            if (!web(address) || !seen.add(address))
                throw new IOException("Invalid or looping audio redirect");
            if (System.nanoTime() > deadline) throw new IOException("Audio redirects timed out");
            HttpURLConnection c = connector.open(new URL(address));
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", Http.USER_AGENT);
            c.setRequestProperty("Accept-Encoding", "identity");
            if (headers != null)
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    if (sameOrigin(original, address) || !sensitive(h.getKey()))
                        c.setRequestProperty(h.getKey(), h.getValue());
                }
            try {
                if (listener != null) listener.opened(c);
                int code = c.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = c.getHeaderField("Location");
                    if (location == null || hop == 20)
                        throw new IOException("Too many or invalid audio redirects");
                    String next = new URL(new URL(address), location).toString();
                    if (!web(next)
                            || ("https".equalsIgnoreCase(new URL(address).getProtocol())
                                    && !"https".equalsIgnoreCase(new URL(next).getProtocol())))
                        throw new IOException("Unsafe audio redirect");
                    c.disconnect();
                    address = next;
                    continue;
                }
                return c; // Caller owns the response, including non-success status codes.
            } catch (IOException e) {
                c.disconnect();
                throw e;
            }
        }
        throw new IOException("Too many audio redirects");
    }

    public interface Progress {
        void update(long received, long total) throws IOException;
    }

    /** Refuse a truncated or empty response instead of committing a broken offline file. */
    public static long copy(
            java.io.InputStream in, java.io.OutputStream out, long total, Progress progress)
            throws IOException {
        byte[] buffer = new byte[32768];
        long received = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
            received += n;
            if (progress != null) progress.update(received, total);
        }
        if (received == 0 || (total >= 0 && received != total))
            throw new IOException("Audio transfer ended early; retry the episode");
        return received;
    }

    private static boolean web(String s) {
        try {
            URI u = new URI(s);
            return ("https".equalsIgnoreCase(u.getScheme())
                            || "http".equalsIgnoreCase(u.getScheme()))
                    && u.getHost() != null
                    && u.getUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean sameOrigin(String a, String b) {
        try {
            URL x = new URL(a), y = new URL(b);
            int xp = x.getPort() < 0 ? x.getDefaultPort() : x.getPort();
            int yp = y.getPort() < 0 ? y.getDefaultPort() : y.getPort();
            return x.getProtocol().equalsIgnoreCase(y.getProtocol())
                    && x.getHost().equalsIgnoreCase(y.getHost())
                    && xp == yp;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean sensitive(String name) {
        return "Authorization".equalsIgnoreCase(name)
                || "Cookie".equalsIgnoreCase(name)
                || "Proxy-Authorization".equalsIgnoreCase(name)
                || "X-Emby-Token".equalsIgnoreCase(name)
                || "X-MediaBrowser-Token".equalsIgnoreCase(name);
    }
}
