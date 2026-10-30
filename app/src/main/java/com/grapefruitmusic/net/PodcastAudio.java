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

    private static HttpURLConnection open(
            String address,
            Map<String, String> headers,
            Connector connector,
            ConnectionListener listener)
            throws IOException {
        long deadline = System.nanoTime() + 90L * 1000000000L;
        String original = address;
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
