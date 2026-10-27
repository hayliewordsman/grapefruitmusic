package com.grapefruitmusic.radio;

import android.util.Log;

import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.source.InternetRadioSource;
import com.grapefruitmusic.util.Bg;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Radio Browser (radio-browser.info): a free, open directory of internet radio stations, run by
 * volunteers on several servers. Blocking calls; not the main thread.
 */
public final class RadioBrowser {
    private static final String TAG = "GrapefruitRadioBrowser";
    private static final String DIRECTORY = "https://all.api.radio-browser.info";
    /** Asks for more than are shown, as some are left out (streams Android 4.2 can't play). */
    private static final int ASK = 120;
    private static final int SHOW = 60;

    /** The server in use, e.g. "https://de1.api.radio-browser.info"; set by tests to a local one. */
    private static volatile String server;

    private RadioBrowser() {
    }

    /** Uses this server from now on (tests point it at a stand-in). */
    public static void useServer(String base) {
        server = base;
    }

    /** Stations whose name matches. */
    public static List<MediaItem> search(String name) throws IOException {
        return stations("/json/stations/search?name=" + Http.enc(name.trim())
                + "&order=clickcount&reverse=true&hidebroken=true&limit=" + ASK);
    }

    /** The most listened-to stations with this tag ("jazz"). */
    public static List<MediaItem> byTag(String tag) throws IOException {
        return stations("/json/stations/search?tag=" + Http.enc(tag)
                + "&order=clickcount&reverse=true&hidebroken=true&limit=" + ASK);
    }

    /** The most listened-to stations in this country ("US"), or anywhere when it's empty. */
    public static List<MediaItem> top(String countryCode) throws IOException {
        if (countryCode == null || countryCode.length() != 2) {
            return stations("/json/stations/topclick/" + ASK + "?hidebroken=true");
        }
        return stations("/json/stations/search?countrycode=" + Http.enc(countryCode.toUpperCase(Locale.US))
                + "&order=clickcount&reverse=true&hidebroken=true&limit=" + ASK);
    }

    /**
     * Tells the directory a station was played, as it asks apps to (it ranks stations by it). In
     * the background; failures don't matter.
     */
    public static void countPlay(final MediaItem station) {
        if (station == null || station.id == null || station.id.startsWith(MyStations.CUSTOM)) return;
        Bg.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Http.get(base() + "/json/url/" + Http.enc(station.id), null);
                } catch (IOException e) {
                    Log.d(TAG, "couldn't count play: " + e.getMessage());
                }
            }
        });
    }

    private static List<MediaItem> stations(String path) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            String base = base();
            try {
                return parse(Http.getJsonArray(base + path, null));
            } catch (IOException e) {
                // That server's down: try another.
                last = e;
                if (server != null && server.equals(base) && !base.startsWith("http://")) server = null;
            }
        }
        throw last;
    }

    /** Picks one of the directory's servers, at random as it asks. */
    private static String base() {
        String s = server;
        if (s != null) return s;
        List<String> names = new ArrayList<String>();
        try {
            JSONArray list = Http.getJsonArray(DIRECTORY + "/json/servers", null);
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.optJSONObject(i);
                String name = o != null ? o.optString("name", "") : "";
                if (name.length() > 0 && !names.contains(name)) names.add(name);
            }
        } catch (IOException e) {
            Log.d(TAG, "server list: " + e.getMessage());
        }
        if (names.isEmpty()) {
            s = DIRECTORY;
        } else {
            Collections.shuffle(names);
            s = "https://" + names.get(0);
        }
        server = s;
        return s;
    }

    static List<MediaItem> parse(JSONArray a) {
        List<MediaItem> out = new ArrayList<MediaItem>();
        List<String> seen = new ArrayList<String>();
        for (int i = 0; i < a.length() && out.size() < SHOW; i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String url = o.optString("url_resolved", "");
            if (url.length() == 0) url = o.optString("url", "");
            if (!url.startsWith("http://") && !url.startsWith("https://")) continue;
            if (!StationText.playable(o.optString("codec", ""), o.optInt("hls", 0) == 1)) continue;
            String name = o.optString("name", "").trim();
            if (name.length() == 0) continue;
            // The directory lists some stations more than once.
            String dedupe = name.toLowerCase(Locale.US) + "|" + url;
            if (seen.contains(dedupe)) continue;
            seen.add(dedupe);
            out.add(InternetRadioSource.station(o.optString("stationuuid", ""), name, url,
                    o.optString("favicon", ""),
                    StationText.describe(o.optString("tags", ""), o.optString("country", ""))));
        }
        return out;
    }
}
