// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps.barlow;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.SharedPreferences;
import android.os.Build;
import org.mapsforge.core.model.LatLong;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/**
 * Google Maps Platform over plain HTTPS: Map Tiles sessions and attribution, Geocoding search,
 * Street View Static imagery and Routes API driving directions. No Play services or SDK.
 */
final class Net {
    static final String ROUTES = "https://routes.googleapis.com/directions/v2:computeRoutes";
    static final String TILES = "https://tile.googleapis.com", WEB = "https://maps.googleapis.com/maps/api";
    static final String[] SESSION_TYPES = {"roadmap", "satellite", "terrain"};
    static final String AGENT = "GrapefruitMapsBarlow/1.0 (https://github.com/hayliewordsman/grapefruitmusic)";
    private static final String FIELDS = "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline,"
            + "routes.legs.steps.distanceMeters,routes.legs.steps.navigationInstruction";

    static final class Place {
        final String name; final LatLong point;
        Place(String name, LatLong point) { this.name = name; this.point = point; }
    }

    static HttpURLConnection open(String url) throws IOException {
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol())) throw new IOException("HTTPS required");
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(20000); c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", AGENT);
        return c;
    }
    static String read(InputStream in) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = input.read(b)) != -1) {
                if (out.size() + n > 8 * 1024 * 1024) throw new IOException("Response too large");
                out.write(b, 0, n);
            }
            return out.toString("UTF-8");
        }
    }
    static LatLong coordinate(String s) {
        try {
            String[] p = s.trim().split(",");
            if (p.length != 2) return null;
            double lat = Double.parseDouble(p[0].trim()), lon = Double.parseDouble(p[1].trim());
            if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 85.05112878 || Math.abs(lon) > 180) return null;
            return new LatLong(lat, lon);
        } catch (RuntimeException e) { return null; }
    }
    static String key() { return BuildConfig.MAPS_API_KEY; }
    static boolean configured() { return !key().isEmpty(); }
    private static void identify(Context c, HttpURLConnection http) {
        // Lets an Android-app-restricted key authorize web-service calls that can carry headers.
        http.setRequestProperty("X-Android-Package", c.getPackageName());
        String cert = certificate(c); if (cert != null) http.setRequestProperty("X-Android-Cert", cert);
    }
    static String get(Context c, String url) throws IOException {
        HttpURLConnection http = open(url);
        try {
            identify(c, http);
            int code = http.getResponseCode();
            if (code != 200) throw new IOException("Google returned HTTP " + code + detail(http));
            return read(http.getInputStream());
        } finally { http.disconnect(); }
    }
    static byte[] bytes(Context c, String url) throws IOException {
        HttpURLConnection http = open(url);
        try {
            identify(c, http);
            int code = http.getResponseCode();
            if (code != 200) throw new IOException("Google returned HTTP " + code);
            try (InputStream in = http.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) { if (out.size() + n > 4 * 1024 * 1024) throw new IOException("Image too large"); out.write(b, 0, n); }
                return out.toByteArray();
            }
        } finally { http.disconnect(); }
    }
    static JSONObject post(Context c, String url, JSONObject body, String fieldMask) throws IOException, JSONException {
        HttpURLConnection http = open(url);
        try {
            http.setRequestMethod("POST"); http.setDoOutput(true);
            http.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (fieldMask != null) { http.setRequestProperty("X-Goog-Api-Key", key()); http.setRequestProperty("X-Goog-FieldMask", fieldMask); }
            identify(c, http);
            try (OutputStream out = http.getOutputStream()) { out.write(body.toString().getBytes("UTF-8")); }
            int code = http.getResponseCode();
            if (code != 200) throw new IOException("Google returned HTTP " + code + detail(http));
            return new JSONObject(read(http.getInputStream()));
        } finally { http.disconnect(); }
    }
    private static String detail(HttpURLConnection http) {
        try { String m = new JSONObject(read(http.getErrorStream())).getJSONObject("error").optString("message"); return m.isEmpty() ? "" : ": " + m; }
        catch (Exception e) { return ""; }
    }
    private static String enc(String s) throws UnsupportedEncodingException { return URLEncoder.encode(s, "UTF-8"); }

    /**
     * A Map Tiles API session for a view (Map, Satellite with labels, Terrain), reused until shortly
     * before Google's expiry. Night styling applies to the Map view only.
     */
    static String session(Context c, SharedPreferences prefs, int type, JSONArray styles) throws IOException, JSONException {
        String name = "tiles_" + type + (styles != null ? "_night" : "") + "_" + Locale.getDefault().toString();
        long now = System.currentTimeMillis() / 1000;
        if (prefs.getLong(name + "_expiry", 0) > now + 3600) return prefs.getString(name, "");
        Locale locale = Locale.getDefault();
        String region = locale.getCountry().isEmpty() ? "US" : locale.getCountry();
        JSONObject body = new JSONObject().put("mapType", SESSION_TYPES[type]).put("language", locale.getLanguage() + "-" + region).put("region", region);
        if (type > 0) body.put("layerTypes", new JSONArray().put("layerRoadmap"));
        if (styles != null && type == 0) body.put("styles", styles);
        JSONObject session = post(c, TILES + "/v1/createSession?key=" + enc(key()), body, null);
        String token = session.getString("session");
        prefs.edit().putString(name, token).putLong(name + "_expiry", session.optLong("expiry", now + 86400)).apply();
        return token;
    }
    static String tileUrl(String session, int zoom, long x, long y) {
        return TILES + "/v1/2dtiles/" + zoom + "/" + x + "/" + y + "?session=" + session + "&key=" + key();
    }
    /** The copyright Google requires to be shown for the visible area. */
    static String attribution(Context c, String session, int zoom, double north, double south, double east, double west) throws IOException, JSONException {
        String url = String.format(Locale.US, "%s/tile/v1/viewport?session=%s&key=%s&zoom=%d&north=%.6f&south=%.6f&east=%.6f&west=%.6f",
                TILES, session, enc(key()), zoom, north, south, east, west);
        return new JSONObject(get(c, url)).optString("copyright", "");
    }
    /** Google Geocoding API search. */
    static List<Place> search(Context c, String q) throws IOException, JSONException {
        if (!configured()) throw new IOException("No Google Maps API key is configured in this build");
        JSONObject body = new JSONObject(get(c, WEB + "/geocode/json?address=" + enc(q) + "&language=" + enc(Locale.getDefault().getLanguage()) + "&key=" + enc(key())));
        String status = body.optString("status");
        ArrayList<Place> places = new ArrayList<>();
        if ("ZERO_RESULTS".equals(status)) return places;
        if (!"OK".equals(status)) throw new IOException(body.optString("error_message", status));
        JSONArray results = body.getJSONArray("results");
        for (int i = 0; i < results.length() && i < 6; i++) {
            JSONObject r = results.getJSONObject(i), at = r.getJSONObject("geometry").getJSONObject("location");
            places.add(new Place(PlaceNames.canonical(r.optString("formatted_address")), new LatLong(at.getDouble("lat"), at.getDouble("lng"))));
        }
        return places;
    }
    static LatLong resolve(Context c, String text) throws Exception {
        LatLong point = coordinate(text);
        if (point == null) point = PlaceNames.find(text);
        if (point != null) return point;
        List<Place> found = search(c, text);
        if (found.isEmpty()) throw new IOException("No place found for " + text);
        return found.get(0).point;
    }
    /** Street View Static metadata (no quota charge): the nearest outdoor panorama, or null. */
    static LatLong streetView(Context c, LatLong near) throws IOException, JSONException {
        JSONObject meta = new JSONObject(get(c, String.format(Locale.US, "%s/streetview/metadata?location=%.6f,%.6f&radius=80&source=outdoor&key=%s",
                WEB, near.latitude, near.longitude, enc(key()))));
        if ("ZERO_RESULTS".equals(meta.optString("status"))) return null;
        if (!"OK".equals(meta.optString("status"))) throw new IOException(meta.optString("error_message", meta.optString("status")));
        JSONObject at = meta.getJSONObject("location");
        return new LatLong(at.getDouble("lat"), at.getDouble("lng"));
    }
    static String streetViewImage(LatLong at, int heading, int pitch, int fov, int width, int height) throws UnsupportedEncodingException {
        return String.format(Locale.US, "%s/streetview?size=%dx%d&location=%.6f,%.6f&heading=%d&pitch=%d&fov=%d&source=outdoor&key=%s",
                WEB, Math.min(640, width), Math.min(640, height), at.latitude, at.longitude, ((heading % 360) + 360) % 360, pitch, fov, enc(key()));
    }
    /** A waypoint the Routes API understands: coordinates, Grapefruit's own place names, or free text. */
    static JSONObject waypoint(String text) throws JSONException {
        LatLong point = coordinate(text);
        if (point == null) point = PlaceNames.find(text);
        JSONObject w = new JSONObject();
        if (point == null) return w.put("address", text.trim());
        return w.put("location", new JSONObject().put("latLng", new JSONObject().put("latitude", point.latitude).put("longitude", point.longitude)));
    }
    static JSONObject route(Context c, String from, String to) throws Exception {
        if (!configured()) throw new IOException("No Google Maps API key is configured in this build");
        JSONObject body = new JSONObject().put("origin", waypoint(from)).put("destination", waypoint(to))
                .put("travelMode", "DRIVE").put("routingPreference", "TRAFFIC_AWARE").put("units", "METRIC");
        return parseRoute(post(c, ROUTES, body, FIELDS));
    }
    /** Grapefruit's saved route: distance (m), duration (s), points [[lat,lng]...] and written steps. */
    static JSONObject parseRoute(JSONObject response) throws JSONException, IOException {
        JSONArray routes = response.optJSONArray("routes");
        if (routes == null || routes.length() == 0) throw new IOException("No driving route found");
        JSONObject r = routes.getJSONObject(0), saved = new JSONObject();
        saved.put("distance", r.optDouble("distanceMeters", 0));
        String duration = r.optString("duration", "0s");
        saved.put("duration", Double.parseDouble(duration.endsWith("s") ? duration.substring(0, duration.length() - 1) : duration));
        JSONArray points = new JSONArray();
        for (LatLong p : decode(r.getJSONObject("polyline").getString("encodedPolyline"))) points.put(new JSONArray().put(p.latitude).put(p.longitude));
        saved.put("points", points);
        JSONArray steps = new JSONArray(), legs = r.optJSONArray("legs");
        if (legs != null) for (int l = 0; l < legs.length(); l++) {
            JSONArray s = legs.getJSONObject(l).optJSONArray("steps");
            if (s != null) for (int i = 0; i < s.length(); i++) {
                JSONObject step = s.getJSONObject(i), nav = step.optJSONObject("navigationInstruction");
                String text = nav == null ? "" : nav.optString("instructions", "");
                if (text.isEmpty()) text = "Continue";
                steps.put(new JSONObject().put("text", text).put("distance", step.optDouble("distanceMeters", 0)));
            }
        }
        return saved.put("steps", steps);
    }
    /** Google's encoded polyline algorithm format. */
    static List<LatLong> decode(String encoded) {
        ArrayList<LatLong> points = new ArrayList<>();
        int index = 0, lat = 0, lng = 0;
        while (index < encoded.length()) {
            int[] value = new int[2];
            for (int k = 0; k < 2; k++) {
                int result = 0, shift = 0, b;
                do { b = encoded.charAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20 && index < encoded.length());
                value[k] = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
            }
            lat += value[0]; lng += value[1];
            points.add(new LatLong(lat / 1e5, lng / 1e5));
        }
        return points;
    }
    /** SHA-1 of the signing certificate, as Google Cloud key restrictions list it. */
    @SuppressWarnings("deprecation")
    static String certificate(Context c) {
        try {
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo info = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
            } else signatures = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
            if (signatures == null || signatures.length == 0) return null;
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(signatures[0].toByteArray());
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format(Locale.US, "%02X", b));
            return hex.toString();
        } catch (Exception e) { return null; }
    }
    /** Hands off to the Google Maps app for live turn-by-turn navigation. */
    static String navigationUrl(String from, String to) throws UnsupportedEncodingException {
        return "https://www.google.com/maps/dir/?api=1&origin=" + URLEncoder.encode(from.trim(), "UTF-8")
                + "&destination=" + URLEncoder.encode(to.trim(), "UTF-8") + "&travelmode=driving";
    }
}
