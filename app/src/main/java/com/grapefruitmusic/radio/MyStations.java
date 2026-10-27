package com.grapefruitmusic.radio;

import android.content.Context;
import android.util.Log;

import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.source.InternetRadioSource;
import com.grapefruitmusic.util.Hashes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The internet radio stations the listener kept, in the order they were added. Main thread. */
public final class MyStations {
    private static final String TAG = "GrapefruitMyStations";
    /** Ids of stations added by link (not from the directory) start with this. */
    public static final String CUSTOM = "link-";

    public interface Listener {
        void onStationsChanged();
    }

    private static MyStations instance;

    private final File file;
    private final List<MediaItem> stations = new ArrayList<MediaItem>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public static synchronized MyStations get(Context context) {
        if (instance == null) instance = new MyStations(context.getApplicationContext());
        return instance;
    }

    private MyStations(Context context) {
        file = new File(context.getFilesDir(), "my_stations.json");
        load();
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public List<MediaItem> all() {
        return new ArrayList<MediaItem>(stations);
    }

    public boolean contains(MediaItem station) {
        return indexOf(station) >= 0;
    }

    public void add(MediaItem station) {
        if (contains(station)) return;
        stations.add(station);
        save();
    }

    public void remove(MediaItem station) {
        int i = indexOf(station);
        if (i < 0) return;
        stations.remove(i);
        save();
    }

    /** A station from a link the listener typed in. */
    public MediaItem addLink(String name, String url) {
        MediaItem m = InternetRadioSource.station(CUSTOM + Hashes.md5(url), name, url, null, "Added by link");
        add(m);
        return m;
    }

    private int indexOf(MediaItem station) {
        for (int i = 0; i < stations.size(); i++) {
            if (stations.get(i).id.equals(station.id)) return i;
        }
        return -1;
    }

    private void load() {
        if (!file.exists()) return;
        try {
            FileInputStream in = new FileInputStream(file);
            byte[] data;
            try {
                data = new byte[(int) file.length()];
                int off = 0;
                int n;
                while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
            } finally {
                in.close();
            }
            JSONArray a = new JSONArray(new String(data, "UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                stations.add(InternetRadioSource.station(o.getString("id"), o.getString("name"), o.getString("url"),
                        o.optString("art", null), o.optString("about", null)));
            }
        } catch (IOException e) {
            Log.w(TAG, "couldn't read my stations", e);
        } catch (JSONException e) {
            Log.w(TAG, "couldn't read my stations", e);
        }
    }

    private void save() {
        JSONArray a = new JSONArray();
        try {
            for (MediaItem m : stations) {
                JSONObject o = new JSONObject();
                o.put("id", m.id);
                o.put("name", m.title);
                o.put("url", m.streamUrl);
                if (m.artKey != null && m.artKey.startsWith("http")) o.put("art", m.artKey);
                if (m.artist != null) o.put("about", m.artist);
                a.put(o);
            }
            FileOutputStream out = new FileOutputStream(file);
            try {
                out.write(a.toString().getBytes("UTF-8"));
            } finally {
                out.close();
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            Log.w(TAG, "couldn't save my stations", e);
        }
        for (Listener l : listeners) l.onStationsChanged();
    }
}
