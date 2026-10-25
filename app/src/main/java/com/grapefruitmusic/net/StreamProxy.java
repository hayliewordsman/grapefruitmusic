package com.grapefruitmusic.net;

import android.util.Log;

import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Hashes;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local HTTP relay for streaming. Android 4.2's media player has its own HTTP stack that can't
 * do modern TLS or send auth headers, so MediaPlayer reads from http://127.0.0.1 and this class
 * fetches the real URL with our own (Conscrypt) connection, passing Range requests through.
 */
public final class StreamProxy {
    private static final String TAG = "GrapefruitProxy";
    private static final int MAX_TARGETS = 16;

    private static final class Target {
        final String url;
        final Map<String, String> headers;

        Target(String url, Map<String, String> headers) {
            this.url = url;
            this.headers = headers;
        }
    }

    private static StreamProxy instance;

    private final Map<String, Target> targets = new LinkedHashMap<String, Target>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Target> eldest) {
            return size() > MAX_TARGETS;
        }
    };
    private final ExecutorService workers = Executors.newCachedThreadPool(Bg.named("gf-proxy"));
    private volatile ServerSocket server;

    private StreamProxy() {
    }

    public static synchronized StreamProxy get() {
        if (instance == null) instance = new StreamProxy();
        return instance;
    }

    /** Returns a loopback URL MediaPlayer can open. Starts the relay if needed. */
    public String register(String url, Map<String, String> headers) throws IOException {
        ensureStarted();
        String token = Hashes.randomHex(8);
        synchronized (targets) {
            targets.put(token, new Target(url, headers == null ? new HashMap<String, String>() : headers));
        }
        return "http://127.0.0.1:" + server.getLocalPort() + "/s/" + token;
    }

    public synchronized void ensureStarted() throws IOException {
        if (server != null && !server.isClosed()) return;
        final ServerSocket s = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        server = s;
        Thread accept = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!s.isClosed()) {
                    try {
                        final Socket client = s.accept();
                        workers.execute(new Runnable() {
                            @Override
                            public void run() {
                                serve(client);
                            }
                        });
                    } catch (IOException e) {
                        if (!s.isClosed()) Log.w(TAG, "accept failed", e);
                    }
                }
            }
        }, "gf-proxy-accept");
        accept.setDaemon(true);
        accept.start();
    }

    private void serve(Socket client) {
        HttpURLConnection upstream = null;
        try {
            client.setSoTimeout(30000);
            InputStream in = client.getInputStream();
            String requestLine = readLine(in);
            if (requestLine == null) return;
            String range = null;
            String line;
            while ((line = readLine(in)) != null && line.length() > 0) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("range")) {
                    range = line.substring(colon + 1).trim();
                }
            }
            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "GET";
            String path = parts.length > 1 ? parts[1] : "/";
            Target target;
            synchronized (targets) {
                target = path.startsWith("/s/") ? targets.get(path.substring(3)) : null;
            }
            OutputStream out = new BufferedOutputStream(client.getOutputStream(), 16384);
            if (target == null) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        .getBytes("US-ASCII"));
                out.flush();
                return;
            }

            upstream = (HttpURLConnection) new URL(target.url).openConnection();
            upstream.setConnectTimeout(15000);
            upstream.setReadTimeout(30000);
            upstream.setRequestProperty("User-Agent", Http.USER_AGENT);
            // Byte ranges must refer to the raw bytes.
            upstream.setRequestProperty("Accept-Encoding", "identity");
            for (Map.Entry<String, String> h : target.headers.entrySet()) {
                upstream.setRequestProperty(h.getKey(), h.getValue());
            }
            if (range != null) upstream.setRequestProperty("Range", range);
            int code = Http.status(upstream);

            StringBuilder head = new StringBuilder();
            head.append("HTTP/1.1 ").append(code).append(' ').append(reason(code)).append("\r\n");
            copyHeader(upstream, head, "Content-Type");
            copyHeader(upstream, head, "Content-Length");
            copyHeader(upstream, head, "Content-Range");
            copyHeader(upstream, head, "Accept-Ranges");
            head.append("Connection: close\r\n\r\n");
            out.write(head.toString().getBytes("US-ASCII"));

            if (!"HEAD".equalsIgnoreCase(method) && code < 400) {
                InputStream body = upstream.getInputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = body.read(buf)) > 0) out.write(buf, 0, n);
            }
            out.flush();
        } catch (IOException e) {
            // MediaPlayer closes connections mid-stream when seeking; that's normal.
            Log.d(TAG, "stream ended: " + e.getMessage());
        } finally {
            if (upstream != null) upstream.disconnect();
            try {
                client.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
    }

    private static void copyHeader(HttpURLConnection c, StringBuilder head, String name) {
        String v = c.getHeaderField(name);
        if (v != null) head.append(name).append(": ").append(v).append("\r\n");
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 206: return "Partial Content";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 416: return "Range Not Satisfiable";
            default: return code < 400 ? "OK" : "Error";
        }
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
