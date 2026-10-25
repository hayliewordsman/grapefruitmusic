package com.grapefruitmusic.source;

import com.grapefruitmusic.lyrics.LrcParser;
import com.grapefruitmusic.lyrics.Lyrics;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.util.Hashes;
import com.grapefruitmusic.util.Prefs;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Navidrome and other Subsonic / OpenSubsonic servers. */
public final class SubsonicSource implements MusicSource {
    private static final String API_VERSION = "1.16.1";
    private static final String CLIENT = "GrapefruitMusic";
    private static final int PAGE = 500;
    private static final int MAX_ITEMS = 5000;

    private final Account account;

    public SubsonicSource(Account account) {
        this.account = account;
    }

    @Override
    public String id() {
        return account.id;
    }

    @Override
    public String name() {
        return account.name;
    }

    @Override
    public String typeLabel() {
        return "Navidrome / Subsonic";
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    @Override
    public boolean canDownload() {
        return true;
    }

    /** Salted-token auth query string (Subsonic API 1.13+). */
    static String authQuery(String user, String password, String salt) {
        return "u=" + Http.enc(user) + "&t=" + Hashes.md5(password + salt) + "&s=" + salt
                + "&v=" + API_VERSION + "&c=" + CLIENT + "&f=json";
    }

    String url(String method, String params) {
        return account.url + "/rest/" + method + ".view?"
                + authQuery(account.username, account.password, Hashes.randomHex(6))
                + (params == null || params.length() == 0 ? "" : "&" + params);
    }

    private JSONObject call(String method, String params) throws IOException {
        JSONObject root = Http.getJson(url(method, params), null);
        JSONObject r = root.optJSONObject("subsonic-response");
        if (r == null) throw new IOException("Not a Subsonic server");
        if (!"ok".equals(r.optString("status"))) {
            JSONObject err = r.optJSONObject("error");
            String msg = err != null ? err.optString("message", "Server error") : "Server error";
            int code = err != null ? err.optInt("code", 0) : 0;
            throw new SubsonicException(code, msg);
        }
        return r;
    }

    static final class SubsonicException extends IOException {
        final int code;

        SubsonicException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    /** Checks the URL and credentials. */
    public void ping() throws IOException {
        call("ping", null);
    }

    @Override
    public List<MediaItem> playlists() throws IOException {
        List<MediaItem> out = new ArrayList<MediaItem>();
        JSONObject r = call("getPlaylists", null);
        for (JSONObject p : Json.list(r.optJSONObject("playlists"), "playlist")) {
            MediaItem m = new MediaItem(MediaItem.PLAYLIST, id(), p.optString("id"), p.optString("name"));
            m.count = p.optInt("songCount");
            m.durationMs = p.optLong("duration") * 1000L;
            m.artKey = Json.str(p, "coverArt");
            m.artist = Json.str(p, "owner");
            out.add(m);
        }
        return out;
    }

    @Override
    public List<MediaItem> artists() throws IOException {
        List<MediaItem> out = new ArrayList<MediaItem>();
        JSONObject r = call("getArtists", null);
        for (JSONObject index : Json.list(r.optJSONObject("artists"), "index")) {
            for (JSONObject a : Json.list(index, "artist")) out.add(artist(a));
        }
        return out;
    }

    @Override
    public List<MediaItem> albums() throws IOException {
        return albumList("alphabeticalByName", MAX_ITEMS);
    }

    @Override
    public List<MediaItem> recentAlbums(int limit) throws IOException {
        return albumList("newest", limit);
    }

    private List<MediaItem> albumList(String type, int max) throws IOException {
        List<MediaItem> out = new ArrayList<MediaItem>();
        int offset = 0;
        while (out.size() < max) {
            int size = Math.min(PAGE, max - out.size());
            JSONObject r = call("getAlbumList2", "type=" + type + "&size=" + size + "&offset=" + offset);
            List<JSONObject> page = Json.list(r.optJSONObject("albumList2"), "album");
            for (JSONObject a : page) out.add(album(a));
            if (page.size() < size) break;
            offset += page.size();
        }
        return out;
    }

    @Override
    public List<MediaItem> songs() throws IOException {
        // An empty search3 query lists every song on Navidrome and other OpenSubsonic servers.
        List<MediaItem> out = new ArrayList<MediaItem>();
        int offset = 0;
        while (out.size() < MAX_ITEMS) {
            JSONObject r = call("search3", "query=&artistCount=0&albumCount=0&songCount=" + PAGE
                    + "&songOffset=" + offset);
            List<JSONObject> page = Json.list(r.optJSONObject("searchResult3"), "song");
            for (JSONObject s : page) out.add(track(s));
            if (page.size() < PAGE) break;
            offset += page.size();
        }
        return out;
    }

    @Override
    public List<MediaItem> artistAlbums(MediaItem artist) throws IOException {
        List<MediaItem> out = new ArrayList<MediaItem>();
        JSONObject r = call("getArtist", "id=" + Http.enc(artist.id));
        for (JSONObject a : Json.list(r.optJSONObject("artist"), "album")) out.add(album(a));
        return out;
    }

    @Override
    public List<MediaItem> tracks(MediaItem container) throws IOException {
        List<MediaItem> out = new ArrayList<MediaItem>();
        if (container.kind == MediaItem.PLAYLIST) {
            JSONObject r = call("getPlaylist", "id=" + Http.enc(container.id));
            for (JSONObject s : Json.list(r.optJSONObject("playlist"), "entry")) out.add(track(s));
        } else {
            JSONObject r = call("getAlbum", "id=" + Http.enc(container.id));
            JSONObject album = r.optJSONObject("album");
            String albumArtist = album != null ? Json.str(album, "artist") : null;
            for (JSONObject s : Json.list(album, "song")) {
                MediaItem t = track(s);
                if (albumArtist != null) t.albumArtist = albumArtist;
                out.add(t);
            }
        }
        return out;
    }

    @Override
    public List<MediaItem> search(String query) throws IOException {
        List<MediaItem> out = new ArrayList<MediaItem>();
        JSONObject r = call("search3", "query=" + Http.enc(query)
                + "&artistCount=10&albumCount=20&songCount=50");
        JSONObject res = r.optJSONObject("searchResult3");
        for (JSONObject a : Json.list(res, "artist")) out.add(artist(a));
        for (JSONObject a : Json.list(res, "album")) out.add(album(a));
        for (JSONObject s : Json.list(res, "song")) out.add(track(s));
        return out;
    }

    @Override
    public String artworkUrl(String artKey, int sizePx) {
        if (artKey == null) return null;
        return url("getCoverArt", "id=" + Http.enc(artKey) + "&size=" + sizePx);
    }

    @Override
    public StreamRequest stream(MediaItem track, int quality) {
        String id = "id=" + Http.enc(track.id);
        if (quality == Prefs.QUALITY_ORIGINAL && Formats.playable(track.suffix)) {
            return new StreamRequest(url("stream", id + "&format=raw"), null, track.suffix);
        }
        int kbps = quality == Prefs.QUALITY_ORIGINAL ? 320 : quality;
        return new StreamRequest(url("stream", id + "&format=mp3&maxBitRate=" + kbps), null, "mp3");
    }

    @Override
    public StreamRequest download(MediaItem track, int quality) {
        if (quality == Prefs.QUALITY_ORIGINAL && Formats.playable(track.suffix)) {
            return new StreamRequest(url("download", "id=" + Http.enc(track.id)), null, track.suffix);
        }
        return stream(track, quality);
    }

    @Override
    public Lyrics lyrics(MediaItem track) throws IOException {
        // OpenSubsonic "songLyrics" extension: synced lyrics by song id (Navidrome 0.53+).
        try {
            JSONObject r = call("getLyricsBySongId", "id=" + Http.enc(track.id));
            List<JSONObject> all = Json.list(r.optJSONObject("lyricsList"), "structuredLyrics");
            JSONObject best = null;
            for (JSONObject l : all) {
                if (best == null || (l.optBoolean("synced") && !best.optBoolean("synced"))) best = l;
            }
            if (best != null) {
                boolean synced = best.optBoolean("synced");
                long offset = best.optLong("offset");
                List<Lyrics.Line> lines = new ArrayList<Lyrics.Line>();
                for (JSONObject line : Json.list(best, "line")) {
                    long start = Math.max(0, line.optLong("start") - offset);
                    lines.add(new Lyrics.Line(synced ? start : 0, line.optString("value")));
                }
                if (!lines.isEmpty()) return new Lyrics(synced, lines, name());
            }
        } catch (SubsonicException e) {
            // Older servers don't have the extension; fall through to plain lyrics.
        } catch (Http.StatusException e) {
            // Same, on servers that answer unknown methods with an HTTP error.
        }
        if (track.artist == null || track.title == null) return null;
        JSONObject r = call("getLyrics", "artist=" + Http.enc(track.artist) + "&title=" + Http.enc(track.title));
        JSONObject l = r.optJSONObject("lyrics");
        String value = l != null ? Json.str(l, "value") : null;
        return LrcParser.parse(value, name());
    }

    @Override
    public boolean canEditPlaylists() {
        return true;
    }

    private static String songIds(String param, List<MediaItem> tracks) {
        StringBuilder sb = new StringBuilder();
        for (MediaItem t : tracks) sb.append('&').append(param).append('=').append(Http.enc(t.id));
        return sb.toString();
    }

    @Override
    public void addToPlaylist(MediaItem playlist, List<MediaItem> tracks) throws IOException {
        call("updatePlaylist", "playlistId=" + Http.enc(playlist.id) + songIds("songIdToAdd", tracks));
    }

    @Override
    public void createPlaylist(String name, List<MediaItem> tracks) throws IOException {
        call("createPlaylist", "name=" + Http.enc(name) + songIds("songId", tracks));
    }

    private MediaItem artist(JSONObject a) {
        MediaItem m = new MediaItem(MediaItem.ARTIST, id(), a.optString("id"), a.optString("name"));
        m.count = a.optInt("albumCount");
        m.artKey = Json.str(a, "coverArt");
        return m;
    }

    private MediaItem album(JSONObject a) {
        String title = Json.str(a, "name");
        if (title == null) title = Json.str(a, "title");
        MediaItem m = new MediaItem(MediaItem.ALBUM, id(), a.optString("id"), title);
        m.artist = Json.str(a, "artist");
        m.artistId = Json.str(a, "artistId");
        m.count = a.optInt("songCount");
        m.year = a.optInt("year");
        m.durationMs = a.optLong("duration") * 1000L;
        m.artKey = Json.str(a, "coverArt");
        return m;
    }

    private MediaItem track(JSONObject s) {
        MediaItem m = new MediaItem(MediaItem.TRACK, id(), s.optString("id"), s.optString("title"));
        m.artist = Json.str(s, "artist");
        m.artistId = Json.str(s, "artistId");
        m.album = Json.str(s, "album");
        m.albumId = Json.str(s, "albumId");
        m.albumArtist = Json.str(s, "displayAlbumArtist");
        if (m.albumArtist == null) m.albumArtist = m.artist;
        m.trackNumber = s.optInt("track");
        m.discNumber = s.optInt("discNumber");
        m.year = s.optInt("year");
        m.durationMs = s.optLong("duration") * 1000L;
        m.artKey = Json.str(s, "coverArt");
        m.suffix = Json.str(s, "suffix");
        return m;
    }
}
