// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmaps;

import java.io.*;
import java.net.*;
import org.json.*;
import org.mapsforge.core.model.LatLong;

final class Net {
    static final String SEARCH="https://nominatim.openstreetmap.org", ROUTING="https://router.project-osrm.org";
    static final String AGENT = "GrapefruitMaps/1.0-beta1 (https://github.com/hayliewordsman/grapefruitmusic)";
    static HttpURLConnection open(String url) throws IOException {
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol())) throw new IOException("HTTPS required");
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(20000); c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", AGENT);
        return c;
    }
    static String get(String url) throws IOException {
        HttpURLConnection c = open(url);
        try {
            if (c.getResponseCode() != 200) throw new IOException("Service returned HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) {
                    if (out.size() + n > 8 * 1024 * 1024) throw new IOException("Response too large");
                    out.write(b, 0, n);
                }
                return out.toString("UTF-8");
            }
        } finally { c.disconnect(); }
    }
    private static long lastSearch;
    static synchronized JSONArray search(String q,String server) throws Exception {
        long wait = 1100 - (System.currentTimeMillis() - lastSearch);
        if (wait > 0) Thread.sleep(wait);
        lastSearch = System.currentTimeMillis();
        return new JSONArray(get(server+"/search?format=jsonv2&limit=6&q=" + URLEncoder.encode(q, "UTF-8")));
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
    static LatLong resolve(String s,String server) throws Exception {
        LatLong point = coordinate(s);
        if(point==null) point=PlaceNames.find(s);
        if (point != null) return point;
        JSONArray results = search(s,server);
        if (results.length() == 0) throw new IOException("No place found for " + s);
        JSONObject r = results.getJSONObject(0);
        return new LatLong(r.getDouble("lat"), r.getDouble("lon"));
    }
    static JSONObject route(LatLong from, LatLong to,String server) throws Exception {
        JSONObject body = new JSONObject(get(server+"/route/v1/driving/"
                + from.longitude + "," + from.latitude + ";" + to.longitude + "," + to.latitude
                + "?overview=full&geometries=geojson&steps=true"));
        if (!"Ok".equals(body.optString("code"))) throw new IOException("No driving route found");
        return body.getJSONArray("routes").getJSONObject(0);
    }
}
