package io.github.hayliewordsman.grapefruitai.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/**
 * One HTTPS request. Plain HTTP is refused outright (Android 4.2 ignores the network security
 * config, so this is the real guard), as are URLs carrying credentials. Certificates and host
 * names are checked by the platform's HttpsURLConnection with the TLS set up by {@link Tls};
 * nothing here relaxes that.
 *
 * Headers are never logged: they can hold API keys and passwords.
 */
public final class Http {
    public static final int CONNECT_TIMEOUT_MS = 20000;
    public static final int READ_TIMEOUT_MS = 120000;
    /** Streams can be quiet for a long time while a model or an agent thinks. */
    public static final int STREAM_READ_TIMEOUT_MS = 300000;
    private static final int MAX_BODY = 64 * 1024 * 1024;

    private final String method;
    private final String url;
    private final Map<String, String> headers = new LinkedHashMap<String, String>();
    private byte[] body;
    private int readTimeout = READ_TIMEOUT_MS;
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;

    public Http(String method, String url) throws IOException {
        this.method = method;
        this.url = requireHttps(url);
    }

    /** Only https:// URLs, with a host and without user:password@ in them. */
    public static String requireHttps(String url) throws IOException {
        if (url == null) throw new IOException("No URL");
        URL u;
        try {
            u = new URL(url.trim());
        } catch (java.net.MalformedURLException e) {
            throw new IOException("Not a valid URL: " + e.getMessage());
        }
        if (!"https".equalsIgnoreCase(u.getProtocol())) {
            throw new IOException("Only HTTPS connections are allowed");
        }
        if (u.getHost() == null || u.getHost().length() == 0) throw new IOException("The URL has no host");
        if (u.getUserInfo() != null) throw new IOException("Put credentials in Settings, not in the URL");
        return u.toString();
    }

    public Http header(String name, String value) {
        if (value != null) headers.put(name, value);
        return this;
    }

    public Http json(String json) {
        try {
            body = json.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        headers.put("Content-Type", "application/json");
        return this;
    }

    public Http readTimeout(int ms) {
        readTimeout = ms;
        return this;
    }

    /** Stops the request from another thread: the blocked read fails, the connection closes. */
    public void cancel() {
        cancelled = true;
        HttpURLConnection c = connection;
        if (c != null) {
            try {
                c.disconnect();
            } catch (RuntimeException ignored) {
                // Already gone.
            }
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** Sends the request; the caller reads and closes the response. */
    public Response open() throws IOException {
        if (cancelled) throw new IOException("Cancelled");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        if (!(c instanceof HttpsURLConnection)) {
            c.disconnect();
            throw new IOException("Only HTTPS connections are allowed");
        }
        connection = c;
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(readTimeout);
        c.setUseCaches(false);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", "GrapefruitAI/1.0 (Android)");
        c.setRequestProperty("Accept-Encoding", "identity");
        for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
        if (body != null) {
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(body.length);
            OutputStream out = c.getOutputStream();
            try {
                out.write(body);
            } finally {
                out.close();
            }
        }
        int code = c.getResponseCode();
        if (cancelled) {
            c.disconnect();
            throw new IOException("Cancelled");
        }
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        return new Response(c, code, in);
    }

    /** The whole response as text; HTTP errors become {@link HttpException}. */
    public String text() throws IOException {
        Response r = open();
        try {
            String s = new String(r.bytes(), "UTF-8");
            if (r.code >= 300) throw new HttpException(r.code, s);
            return s;
        } finally {
            r.close();
        }
    }

    public static final class Response {
        public final int code;
        private final HttpURLConnection connection;
        private final InputStream in;

        Response(HttpURLConnection connection, int code, InputStream in) {
            this.connection = connection;
            this.code = code;
            this.in = in;
        }

        public InputStream stream() {
            return in != null ? in : new java.io.ByteArrayInputStream(new byte[0]);
        }

        public String header(String name) {
            return connection.getHeaderField(name);
        }

        public byte[] bytes() throws IOException {
            return readAll(stream(), MAX_BODY);
        }

        public void close() {
            try {
                if (in != null) in.close();
            } catch (IOException ignored) {
                // Closing anyway.
            }
            connection.disconnect();
        }
    }

    public static byte[] readAll(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            if (out.size() > max) throw new IOException("Response too large");
        }
        return out.toByteArray();
    }

    /** Basic authentication for the OpenCode server. */
    public static String basic(String user, String password) {
        try {
            byte[] raw = (user + ":" + password).getBytes("UTF-8");
            return "Basic " + android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
