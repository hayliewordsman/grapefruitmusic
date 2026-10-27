package com.grapefruitmusic.net;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A live radio stream, opened with its song titles asked for. Plain-HTTP stations are opened by
 * hand: SHOUTcast answers "ICY 200 OK", which Android 4.2's HTTP client refuses. Redirects are
 * followed (including between http and https), and playlist links (.pls, .m3u) are opened to the
 * stream they name.
 */
public final class LiveStream {
    private static final int MAX_HOPS = 6;

    public final int code;
    /** Response headers, names in lower case. */
    public final Map<String, String> headers;
    public final InputStream body;
    private final Socket socket;
    private final HttpURLConnection connection;

    private LiveStream(int code, Map<String, String> headers, InputStream body, Socket socket,
                       HttpURLConnection connection) {
        this.code = code;
        this.headers = headers;
        this.body = body;
        this.socket = socket;
        this.connection = connection;
    }

    /** Bytes of sound between song-title blocks, or 0 if the station doesn't send them. */
    public int metaInterval() {
        try {
            String v = headers.get("icy-metaint");
            return v == null ? 0 : Math.max(0, Integer.parseInt(v.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String contentType() {
        return headers.get("content-type");
    }

    public void close() {
        if (connection != null) connection.disconnect();
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
    }

    /** Opens url, following redirects and playlists to the stream itself. */
    public static LiveStream open(String url, Map<String, String> headers) throws IOException {
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            LiveStream s = url.regionMatches(true, 0, "https:", 0, 6) ? openTls(url, headers) : openPlain(url, headers);
            String location = s.headers.get("location");
            if (s.code >= 300 && s.code < 400 && location != null) {
                s.close();
                url = new URL(new URL(url), location).toString();
                continue;
            }
            if (s.code < 300 && isPlaylist(s.contentType(), url)) {
                String text;
                try {
                    text = readText(s.body, 64 * 1024);
                } finally {
                    s.close();
                }
                url = firstStream(text, url);
                continue;
            }
            return s;
        }
        throw new IOException("Too many redirects");
    }

    private static LiveStream openTls(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", Http.USER_AGENT);
        c.setRequestProperty("Icy-MetaData", "1");
        c.setRequestProperty("Accept-Encoding", "identity");
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
        }
        int code = Http.status(c);
        Map<String, String> out = new HashMap<String, String>();
        for (int i = 0; ; i++) {
            String name = c.getHeaderFieldKey(i);
            String value = c.getHeaderField(i);
            if (name == null && value == null) break;
            if (name != null) out.put(name.toLowerCase(Locale.US), value);
        }
        InputStream body = code < 400 ? c.getInputStream() : null;
        return new LiveStream(code, out, body, null, c);
    }

    private static LiveStream openPlain(String url, Map<String, String> headers) throws IOException {
        URL u = new URL(url);
        String host = u.getHost();
        int port = u.getPort() > 0 ? u.getPort() : 80;
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), 15000);
            s.setSoTimeout(30000);
            String path = u.getFile();
            if (path == null || path.length() == 0) path = "/";
            StringBuilder req = new StringBuilder();
            // HTTP/1.0: the reply can't come chunked.
            req.append("GET ").append(path).append(" HTTP/1.0\r\n");
            req.append("Host: ").append(host).append(u.getPort() > 0 ? ":" + u.getPort() : "").append("\r\n");
            req.append("User-Agent: ").append(Http.USER_AGENT).append("\r\n");
            req.append("Accept: */*\r\n");
            req.append("Icy-MetaData: 1\r\n");
            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    req.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
                }
            }
            req.append("Connection: close\r\n\r\n");
            OutputStream out = s.getOutputStream();
            out.write(req.toString().getBytes("ISO-8859-1"));
            out.flush();
            InputStream in = new BufferedInputStream(s.getInputStream(), 16384);
            int code = statusCode(readLine(in));
            Map<String, String> h = new HashMap<String, String>();
            String line;
            while ((line = readLine(in)) != null && line.length() > 0) {
                int colon = line.indexOf(':');
                if (colon > 0) h.put(line.substring(0, colon).trim().toLowerCase(Locale.US), line.substring(colon + 1).trim());
            }
            return new LiveStream(code, h, in, s, null);
        } catch (IOException e) {
            s.close();
            throw e;
        }
    }

    /** The code in "HTTP/1.1 200 OK" or SHOUTcast's "ICY 200 OK". */
    static int statusCode(String line) throws IOException {
        if (line != null) {
            String[] parts = line.trim().split(" +");
            if (parts.length >= 2 && (parts[0].startsWith("HTTP/") || parts[0].equals("ICY"))) {
                try {
                    return Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    // Falls through.
                }
            }
        }
        throw new IOException("Not a radio stream");
    }

    static boolean isPlaylist(String contentType, String url) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        if (type.contains("mpegurl") || type.contains("scpls") || type.contains("pls+xml")) return true;
        if (type.startsWith("audio/")) return false;
        String path = url.toLowerCase(Locale.US);
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        return path.endsWith(".pls") || path.endsWith(".m3u") || path.endsWith(".m3u8");
    }

    /** The first stream a .pls or .m3u playlist names (relative links read against base). */
    static String firstStream(String text, String base) throws IOException {
        if (text.contains("#EXT-X-")) throw new IOException("This station's stream type isn't supported");
        for (String raw : text.split("[\r\n]+")) {
            String line = raw.trim();
            if (line.length() == 0 || line.startsWith("#") || line.startsWith("[")) continue;
            if (line.regionMatches(true, 0, "File", 0, 4) && line.indexOf('=') > 0) {
                line = line.substring(line.indexOf('=') + 1).trim();
            } else if (line.indexOf('=') > 0 && !line.contains("://")) {
                continue; // Title1=..., NumberOfEntries=...
            }
            try {
                String url = new URL(new URL(base), line).toString();
                if (url.startsWith("http://") || url.startsWith("https://")) return url;
            } catch (IOException ignored) {
                // Not a link.
            }
        }
        throw new IOException("This station's playlist has no stream in it");
    }

    private static String readText(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while (out.size() < max && (n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), "UTF-8");
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) throw new IOException("header too long");
        }
        if (c < 0 && sb.length() == 0) return null;
        return sb.toString();
    }
}
