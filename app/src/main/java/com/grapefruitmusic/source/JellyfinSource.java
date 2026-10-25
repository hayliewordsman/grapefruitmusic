package com.grapefruitmusic.source;

import com.grapefruitmusic.lyrics.Lyrics;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.util.Prefs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Jellyfin servers (10.8+; lyrics need 10.9+). */
public final class JellyfinSource implements MusicSource {
    private static final long TICKS_PER_MS = 10000L;
    private static final int MAX_ITEMS = 5000;

    private final Account account;

    public JellyfinSource(Account account) {
        this.account = account;
    }

    /** Signs in and returns {accessToken, userId}. */
    public static String[] login(String baseUrl, String username, String password) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("Username", username);
            body.put("Pw", password);
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", authorization(null));
        JSONObject r = Http.parseObject(Http.post(baseUrl + "/Users/AuthenticateByName", headers,
                "application/json", body.toString()));
        String token = Json.str(r, "AccessToken");
        JSONObject user = r.optJSONObject("User");
        String userId = user != null ? Json.str(user, "Id") : null;
        if (token == null || userId == null) throw new IOException("Jellyfin didn't return a session");
        return new String[]{token, userId};
    }

    static String authorization(String token) {
        return "MediaBrowser Client=\"Grapefruit Music\", Device=\"Android\", DeviceId=\""
                + Prefs.deviceId() + "\", Version=\"1.0\"" + (token == null ? "" : ", Token=\"" + token + "\"");
    }

    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<String, String>();
        h.put("Authorization", authorization(account.token));
        return h;
    }

    private JSONObject get(String pathAndQuery) throws IOException {
        return Http.getJson(account.url + pathAndQuery, headers());
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
        return "Jellyfin";
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    @Override
    public boolean canDownload() {
        return true;
    }

    private String itemsQuery(String types, String extra) {
        return "/Items?userId=" + Http.enc(account.userId) + "&Recursive=true&IncludeItemTypes=" + types
                + "&Fields=ChildCount&EnableTotalRecordCount=false" + (extra == null ? "" : "&" + extra);
    }

    private List<MediaItem> items(String query) throws IOException {
        List<MediaItem> out = new ArrayList<MediaItem>();
        JSONArray items = get(query).optJSONArray("Items");
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            MediaItem m = item(items.optJSONObject(i));
            if (m != null) out.add(m);
        }
        return out;
    }

    @Override
    public List<MediaItem> playlists() throws IOException {
        List<MediaItem> out = new ArrayList<MediaItem>();
        for (MediaItem m : items(itemsQuery("Playlist", "SortBy=SortName"))) {
            if (m.kind == MediaItem.PLAYLIST) out.add(m);
        }
        return out;
    }

    @Override
    public List<MediaItem> artists() throws IOException {
        List<MediaItem> out = new ArrayList<MediaItem>();
        JSONArray items = get("/Artists/AlbumArtists?userId=" + Http.enc(account.userId)
                + "&SortBy=SortName&Limit=" + MAX_ITEMS).optJSONArray("Items");
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            MediaItem m = item(items.optJSONObject(i));
            if (m != null) {
                m.kind = MediaItem.ARTIST;
                out.add(m);
            }
        }
        return out;
    }

    @Override
    public List<MediaItem> albums() throws IOException {
        return items(itemsQuery("MusicAlbum", "SortBy=SortName&Limit=" + MAX_ITEMS));
    }

    @Override
    public List<MediaItem> songs() throws IOException {
        return items(itemsQuery("Audio", "SortBy=SortName&Limit=" + MAX_ITEMS));
    }

    @Override
    public List<MediaItem> recentAlbums(int limit) throws IOException {
        return items(itemsQuery("MusicAlbum", "SortBy=DateCreated&SortOrder=Descending&Limit=" + limit));
    }

    @Override
    public List<MediaItem> artistAlbums(MediaItem artist) throws IOException {
        List<MediaItem> out = items(itemsQuery("MusicAlbum",
                "AlbumArtistIds=" + Http.enc(artist.id) + "&SortBy=ProductionYear,SortName"));
        if (out.isEmpty()) {
            out = items(itemsQuery("MusicAlbum", "ArtistIds=" + Http.enc(artist.id)
                    + "&SortBy=ProductionYear,SortName"));
        }
        return out;
    }

    @Override
    public List<MediaItem> tracks(MediaItem container) throws IOException {
        if (container.kind == MediaItem.PLAYLIST) {
            return items("/Playlists/" + Http.enc(container.id) + "/Items?userId=" + Http.enc(account.userId));
        }
        return items(itemsQuery("Audio", "ParentId=" + Http.enc(container.id)
                + "&SortBy=ParentIndexNumber,IndexNumber,SortName"));
    }

    @Override
    public List<MediaItem> search(String query) throws IOException {
        List<MediaItem> found = items(itemsQuery("MusicArtist,MusicAlbum,Audio",
                "searchTerm=" + Http.enc(query) + "&Limit=80"));
        // Artists first, then albums, then songs.
        List<MediaItem> out = new ArrayList<MediaItem>();
        for (int kind : new int[]{MediaItem.ARTIST, MediaItem.ALBUM, MediaItem.TRACK}) {
            for (MediaItem m : found) {
                if (m.kind == kind) out.add(m);
            }
        }
        return out;
    }

    @Override
    public String artworkUrl(String artKey, int sizePx) {
        if (artKey == null) return null;
        int bar = artKey.indexOf('|');
        String itemId = bar >= 0 ? artKey.substring(0, bar) : artKey;
        String tag = bar >= 0 ? artKey.substring(bar + 1) : null;
        return account.url + "/Items/" + Http.enc(itemId) + "/Images/Primary?maxWidth=" + sizePx
                + "&maxHeight=" + sizePx + "&quality=90" + (tag != null ? "&tag=" + Http.enc(tag) : "");
    }

    @Override
    public StreamRequest stream(MediaItem track, int quality) {
        boolean original = quality == Prefs.QUALITY_ORIGINAL;
        String url = account.url + "/Audio/" + Http.enc(track.id) + "/universal?UserId=" + Http.enc(account.userId)
                + "&DeviceId=" + Http.enc(Prefs.deviceId())
                + "&MaxStreamingBitrate=" + (original ? 140000000 : quality * 1000)
                + "&Container=" + Http.enc(original ? Formats.JELLYFIN_CONTAINERS : "mp3")
                + "&TranscodingContainer=mp3&TranscodingProtocol=http&AudioCodec=mp3";
        return new StreamRequest(url, headers(), original && Formats.playable(track.suffix) ? track.suffix : "mp3");
    }

    @Override
    public StreamRequest download(MediaItem track, int quality) {
        return stream(track, quality);
    }

    @Override
    public Lyrics lyrics(MediaItem track) throws IOException {
        JSONObject r;
        try {
            r = get("/Audio/" + Http.enc(track.id) + "/Lyrics");
        } catch (Http.StatusException e) {
            // 404: no lyrics (or a server older than 10.9).
            return null;
        }
        JSONArray lines = r.optJSONArray("Lyrics");
        if (lines == null || lines.length() == 0) return null;
        boolean anyStart = false;
        List<Lyrics.Line> out = new ArrayList<Lyrics.Line>();
        for (int i = 0; i < lines.length(); i++) {
            JSONObject l = lines.optJSONObject(i);
            if (l == null) continue;
            boolean hasStart = l.has("Start") && !l.isNull("Start");
            anyStart |= hasStart;
            out.add(new Lyrics.Line(hasStart ? l.optLong("Start") / TICKS_PER_MS : 0, l.optString("Text")));
        }
        JSONObject meta = r.optJSONObject("Metadata");
        boolean synced = meta != null && meta.has("IsSynced") ? meta.optBoolean("IsSynced") : anyStart;
        return out.isEmpty() ? null : new Lyrics(synced, out, name());
    }

    private MediaItem item(JSONObject o) {
        if (o == null) return null;
        String type = o.optString("Type");
        int kind;
        if (type.equals("Audio")) kind = MediaItem.TRACK;
        else if (type.equals("MusicAlbum")) kind = MediaItem.ALBUM;
        else if (type.equals("MusicArtist")) kind = MediaItem.ARTIST;
        else if (type.equals("Playlist")) kind = MediaItem.PLAYLIST;
        else return null;
        if (kind == MediaItem.PLAYLIST && "Video".equals(Json.str(o, "MediaType"))) return null;
        MediaItem m = new MediaItem(kind, id(), o.optString("Id"), o.optString("Name"));
        m.album = Json.str(o, "Album");
        m.albumId = Json.str(o, "AlbumId");
        m.albumArtist = Json.str(o, "AlbumArtist");
        JSONArray artists = o.optJSONArray("Artists");
        if (artists != null && artists.length() > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < artists.length(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(artists.optString(i));
            }
            m.artist = sb.toString();
        }
        if (m.artist == null) m.artist = m.albumArtist;
        if (kind == MediaItem.ALBUM) m.artist = m.albumArtist != null ? m.albumArtist : m.artist;
        JSONArray artistItems = o.optJSONArray("AlbumArtists");
        if (artistItems == null || artistItems.length() == 0) artistItems = o.optJSONArray("ArtistItems");
        if (artistItems != null && artistItems.length() > 0) {
            JSONObject first = artistItems.optJSONObject(0);
            if (first != null) m.artistId = Json.str(first, "Id");
        }
        m.trackNumber = o.optInt("IndexNumber");
        m.discNumber = o.optInt("ParentIndexNumber");
        m.year = o.optInt("ProductionYear");
        m.count = o.optInt("ChildCount");
        m.durationMs = o.optLong("RunTimeTicks") / TICKS_PER_MS;
        m.suffix = Json.str(o, "Container");
        JSONObject tags = o.optJSONObject("ImageTags");
        String primary = tags != null ? Json.str(tags, "Primary") : null;
        if (primary != null) {
            m.artKey = m.id + "|" + primary;
        } else if (Json.str(o, "AlbumPrimaryImageTag") != null && m.albumId != null) {
            m.artKey = m.albumId + "|" + Json.str(o, "AlbumPrimaryImageTag");
        }
        return m;
    }
}
