package com.grapefruitmusic.source;

import android.content.Context;

import com.grapefruitmusic.util.Hashes;
import com.grapefruitmusic.util.Prefs;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** All music sources: this phone, downloads, and the user's servers. */
public final class Sources {
    public interface Listener {
        void onSourcesChanged();
    }

    private static Sources instance;

    private final LocalSource local;
    private final DownloadedSource downloaded;
    private final List<Account> accounts = new ArrayList<Account>();
    private volatile List<MusicSource> all = new ArrayList<MusicSource>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public static synchronized Sources get(Context context) {
        if (instance == null) instance = new Sources(context.getApplicationContext());
        return instance;
    }

    private Sources(Context context) {
        local = new LocalSource(context);
        downloaded = new DownloadedSource(context);
        loadAccounts();
        rebuild();
    }

    private void loadAccounts() {
        accounts.clear();
        String raw = Prefs.raw().getString("accounts", "[]");
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) accounts.add(Account.fromJson(a.getJSONObject(i)));
        } catch (JSONException ignored) {
            // Corrupt settings: start without servers.
        }
    }

    private void saveAccounts() {
        JSONArray a = new JSONArray();
        try {
            for (Account acc : accounts) a.put(acc.toJson());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        Prefs.raw().edit().putString("accounts", a.toString()).apply();
    }

    private void rebuild() {
        List<MusicSource> list = new ArrayList<MusicSource>();
        list.add(local);
        for (Account a : accounts) {
            if (Account.JELLYFIN.equals(a.type)) list.add(new JellyfinSource(a));
            else list.add(new SubsonicSource(a));
        }
        list.add(downloaded);
        all = list;
        for (Listener l : listeners) l.onSourcesChanged();
    }

    /** Something about a source's contents changed (e.g. excluded folders). */
    public void notifyChanged() {
        for (Listener l : listeners) l.onSourcesChanged();
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public List<MusicSource> all() {
        return Collections.unmodifiableList(all);
    }

    public List<Account> accounts() {
        return Collections.unmodifiableList(new ArrayList<Account>(accounts));
    }

    public MusicSource byId(String id) {
        for (MusicSource s : all) {
            if (s.id().equals(id)) return s;
        }
        return null;
    }

    public LocalSource local() {
        return local;
    }

    public DownloadedSource downloaded() {
        return downloaded;
    }

    /** The source the Library tab shows. Defaults to the first server, else this phone. */
    public MusicSource current() {
        MusicSource s = byId(Prefs.currentSource() == null ? "" : Prefs.currentSource());
        if (s != null) return s;
        return all.size() > 2 ? all.get(1) : local;
    }

    public void setCurrent(MusicSource s) {
        Prefs.setCurrentSource(s.id());
        for (Listener l : listeners) l.onSourcesChanged();
    }

    public Account addAccount(String type, String name, String url, String username, String password,
                              String token, String userId) {
        Account a = new Account();
        a.id = "acc_" + Hashes.randomHex(6);
        a.type = type;
        a.name = name;
        a.url = url;
        a.username = username;
        a.password = password;
        a.token = token;
        a.userId = userId;
        accounts.add(a);
        saveAccounts();
        Prefs.setCurrentSource(a.id);
        rebuild();
        return a;
    }

    public void removeAccount(String id) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).id.equals(id)) {
                accounts.remove(i);
                break;
            }
        }
        saveAccounts();
        rebuild();
    }
}
