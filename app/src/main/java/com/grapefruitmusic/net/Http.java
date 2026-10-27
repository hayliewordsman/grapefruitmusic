package com.grapefruitmusic.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

/** Minimal HttpURLConnection wrapper. */
public final class Http {
    public static final String USER_AGENT =
            "GrapefruitMusic/1.0 (+https://github.com/hayliewordsman/grapefruitmusic)";

    /** A non-2xx response. */
    public static final class StatusException extends IOException {
        public final int code;

        public StatusException(int code, String message) {
            super("HTTP " + code + (message == null || message.length() == 0 ? "" : ": " + message));
            this.code = code;
        }
    }

    private Http() {
    }

    /** Opens a connection and checks the status; the caller reads and disconnects. */
    public static HttpURLConnection open(String method, String url, Map<String, String> headers,
                                         String contentType, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", USER_AGENT);
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                c.setRequestProperty(h.getKey(), h.getValue());
            }
        }
        if (body != null) {
            byte[] bytes = body.getBytes("UTF-8");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", contentType);
            c.setFixedLengthStreamingMode(bytes.length);
            OutputStream out = c.getOutputStream();
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
        }
        int code = status(c);
        if (code < 200 || code >= 300) {
            String message = null;
            try {
                InputStream err = c.getErrorStream();
                if (err != null) {
                    message = readAll(err);
                    if (message.length() > 200) message = message.substring(0, 200);
                }
            } catch (IOException ignored) {
                // Best effort only.
            }
            c.disconnect();
            throw new StatusException(code, message);
        }
        return c;
    }

    /**
     * Android before 4.4 throws "No authentication challenges found" when a 401 has no
     * WWW-Authenticate header; asking a second time returns the code.
     */
    public static int status(HttpURLConnection c) throws IOException {
        try {
            return c.getResponseCode();
        } catch (IOException e) {
            int code = c.getResponseCode();
            if (code > 0) return code;
            throw e;
        }
    }

    public static String get(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection c = open("GET", url, headers, null, null);
        try {
            return readAll(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    public static String post(String url, Map<String, String> headers, String contentType, String body)
            throws IOException {
        HttpURLConnection c = open("POST", url, headers, contentType, body);
        try {
            return readAll(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    public static void delete(String url, Map<String, String> headers) throws IOException {
        open("DELETE", url, headers, null, null).disconnect();
    }

    public static JSONObject getJson(String url, Map<String, String> headers) throws IOException {
        return parseObject(get(url, headers));
    }

    public static JSONArray getJsonArray(String url, Map<String, String> headers) throws IOException {
        String body = get(url, headers);
        try {
            return new JSONArray(body);
        } catch (JSONException e) {
            throw new IOException("Unexpected response from server");
        }
    }

    public static JSONObject parseObject(String body) throws IOException {
        try {
            return new JSONObject(body);
        } catch (JSONException e) {
            throw new IOException("Unexpected response from server");
        }
    }

    public static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    public static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** "music.example.com:4533/" -> "http://music.example.com:4533". */
    public static String normalizeBaseUrl(String url) {
        String u = url == null ? "" : url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    public static boolean isOnline(Context context) {
        NetworkInfo info = network(context);
        return info != null && info.isConnected();
    }

    public static boolean isOnWifi(Context context) {
        NetworkInfo info = network(context);
        return info != null && info.isConnected()
                && (info.getType() == ConnectivityManager.TYPE_WIFI
                || info.getType() == ConnectivityManager.TYPE_ETHERNET);
    }

    private static NetworkInfo network(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm == null ? null : cm.getActiveNetworkInfo();
    }
}
