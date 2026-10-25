package com.grapefruitmusic.source;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.grapefruitmusic.lyrics.LrcParser;
import com.grapefruitmusic.lyrics.Lyrics;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.util.Prefs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Music files stored on this phone (via the system media library). Works offline. Songs in
 * folders the user excluded, and Grapefruit's own downloads, are left out.
 */
public final class LocalSource implements MusicSource {
    public static final String ID = "device";

    private static final Uri MEDIA = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    private static final Uri ALBUMS = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
    private static final Uri PLAYLISTS = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;

    private static final String DATA = MediaStore.Audio.Media.DATA;
    private static final String[] TRACK_COLUMNS = {
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DURATION, DATA,
            MediaStore.Audio.Media.YEAR};
    private static final String IS_MUSIC = MediaStore.Audio.Media.IS_MUSIC + " != 0";

    private final Context context;
    private final ContentResolver resolver;
    private final Map<String, String> artCache = new HashMap<String, String>();

    public LocalSource(Context context) {
        this.context = context.getApplicationContext();
        resolver = this.context.getContentResolver();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "On This Phone";
    }

    @Override
    public String typeLabel() {
        return "Music files on this phone";
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public boolean canDownload() {
        return false;
    }

    /** Grapefruit's own folder (downloads, cover art), which is never shown here. */
    private String ownFolder() {
        File files = context.getExternalFilesDir(null);
        File own = files != null ? files.getParentFile() : null;
        return own != null ? Folders.normalize(own.getPath()) : null;
    }

    private List<String> hiddenFolders() {
        List<String> out = new ArrayList<String>(Prefs.excludedFolders());
        String own = ownFolder();
        if (own != null) out.add(own);
        return out;
    }

    /** " AND _data NOT LIKE ? ..." for every hidden folder; adds the arguments. */
    private String notHidden(List<String> args) {
        StringBuilder sb = new StringBuilder();
        for (String folder : hiddenFolders()) {
            sb.append(" AND ").append(DATA).append(" NOT LIKE ? ESCAPE '\\'");
            args.add(Folders.likeInside(folder));
        }
        return sb.toString();
    }

    private Cursor query(Uri uri, String[] columns, String selection, String[] args, String order) throws IOException {
        try {
            Cursor c = resolver.query(uri, columns, selection, args, order);
            if (c == null) throw new IOException("Media library unavailable (is the SD card mounted?)");
            return c;
        } catch (SecurityException e) {
            throw new IOException("Grapefruit Music isn't allowed to read music files");
        }
    }

    /** Visible music, optionally narrowed by an extra condition. */
    private Cursor media(String[] columns, String extra, String[] extraArgs, String order) throws IOException {
        List<String> args = new ArrayList<String>();
        if (extraArgs != null) args.addAll(Arrays.asList(extraArgs));
        String selection = IS_MUSIC + (extra != null ? " AND (" + extra + ")" : "") + notHidden(args);
        return query(MEDIA, columns, selection, args.toArray(new String[args.size()]), order);
    }

    private List<MediaItem> tracksFrom(Cursor c, String idColumn) {
        return tracksFrom(c, idColumn, null);
    }

    /** entryColumn: the playlist member row, for playlists. */
    private List<MediaItem> tracksFrom(Cursor c, String idColumn, String entryColumn) {
        List<MediaItem> out = new ArrayList<MediaItem>();
        try {
            int id = c.getColumnIndexOrThrow(idColumn);
            int entry = entryColumn != null ? c.getColumnIndexOrThrow(entryColumn) : -1;
            int title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int artistId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID);
            int album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            int track = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK);
            int duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int data = c.getColumnIndexOrThrow(DATA);
            int year = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR);
            while (c.moveToNext()) {
                MediaItem m = new MediaItem(MediaItem.TRACK, ID, c.getString(id), c.getString(title));
                m.artist = unknownToNull(c.getString(artist));
                m.albumArtist = m.artist;
                m.artistId = c.getString(artistId);
                m.album = c.getString(album);
                m.albumId = c.getString(albumId);
                int t = c.getInt(track);
                m.trackNumber = t % 1000;
                m.discNumber = t / 1000;
                m.durationMs = c.getLong(duration);
                m.path = c.getString(data);
                m.year = c.getInt(year);
                m.artKey = "album:" + m.albumId;
                if (entry >= 0) m.entryId = c.getString(entry);
                String p = m.path;
                int dot = p == null ? -1 : p.lastIndexOf('.');
                if (dot > 0) m.suffix = p.substring(dot + 1);
                out.add(m);
            }
        } finally {
            c.close();
        }
        return out;
    }

    private static String unknownToNull(String artist) {
        return artist == null || artist.equals(MediaStore.UNKNOWN_STRING) ? null : artist;
    }

    @Override
    public List<MediaItem> songs() throws IOException {
        return tracksFrom(media(TRACK_COLUMNS, null, null, MediaStore.Audio.Media.TITLE_KEY),
                MediaStore.Audio.Media._ID);
    }

    /**
     * Albums built from the visible songs (the system's album table would also count songs in
     * excluded folders).
     */
    private List<MediaItem> albumsFromSongs(String extra, String[] args, String order) throws IOException {
        Cursor c = media(new String[]{MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.YEAR}, extra, args, order);
        Map<String, MediaItem> albums = new LinkedHashMap<String, MediaItem>();
        try {
            while (c.moveToNext()) {
                String id = c.getString(0);
                String artist = unknownToNull(c.getString(2));
                MediaItem m = albums.get(id);
                if (m == null) {
                    m = new MediaItem(MediaItem.ALBUM, ID, id, c.getString(1));
                    m.artist = artist;
                    m.artKey = "album:" + id;
                    albums.put(id, m);
                } else if (artist != null && m.artist != null && !artist.equals(m.artist)) {
                    m.artist = "Various Artists";
                }
                m.count++;
                m.year = Math.max(m.year, c.getInt(3));
            }
        } finally {
            c.close();
        }
        return new ArrayList<MediaItem>(albums.values());
    }

    @Override
    public List<MediaItem> albums() throws IOException {
        return albumsFromSongs(null, null, MediaStore.Audio.Media.ALBUM_KEY);
    }

    private List<MediaItem> artistsFromSongs(String extra, String[] args) throws IOException {
        Cursor c = media(new String[]{MediaStore.Audio.Media.ARTIST_ID, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID}, extra, args, MediaStore.Audio.Media.ARTIST_KEY);
        Map<String, MediaItem> artists = new LinkedHashMap<String, MediaItem>();
        Map<String, Set<String>> albums = new HashMap<String, Set<String>>();
        try {
            while (c.moveToNext()) {
                String id = c.getString(0);
                MediaItem m = artists.get(id);
                if (m == null) {
                    String name = unknownToNull(c.getString(1));
                    m = new MediaItem(MediaItem.ARTIST, ID, id, name == null ? "Unknown Artist" : name);
                    artists.put(id, m);
                    albums.put(id, new HashSet<String>());
                }
                albums.get(id).add(c.getString(2));
            }
        } finally {
            c.close();
        }
        for (MediaItem m : artists.values()) m.count = albums.get(m.id).size();
        return new ArrayList<MediaItem>(artists.values());
    }

    @Override
    public List<MediaItem> artists() throws IOException {
        return artistsFromSongs(null, null);
    }

    @Override
    public List<MediaItem> playlists() throws IOException {
        Cursor c = query(PLAYLISTS, new String[]{MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME},
                null, null, MediaStore.Audio.Playlists.NAME);
        List<MediaItem> out = new ArrayList<MediaItem>();
        try {
            while (c.moveToNext()) {
                out.add(new MediaItem(MediaItem.PLAYLIST, ID, c.getString(0), c.getString(1)));
            }
        } finally {
            c.close();
        }
        return out;
    }

    @Override
    public List<MediaItem> recentAlbums(int limit) throws IOException {
        // Albums of the most recently added songs, newest first.
        Cursor c = media(new String[]{MediaStore.Audio.Media.ALBUM_ID}, null, null,
                MediaStore.Audio.Media.DATE_ADDED + " DESC");
        Set<String> ids = new LinkedHashSet<String>();
        try {
            while (c.moveToNext() && ids.size() < limit) ids.add(c.getString(0));
        } finally {
            c.close();
        }
        return albumsInOrder(ids);
    }

    @Override
    public List<MediaItem> artistAlbums(MediaItem artist) throws IOException {
        Cursor c = media(new String[]{MediaStore.Audio.Media.ALBUM_ID}, MediaStore.Audio.Media.ARTIST_ID + " = ?",
                new String[]{artist.id}, MediaStore.Audio.Media.YEAR);
        Set<String> ids = new LinkedHashSet<String>();
        try {
            while (c.moveToNext()) ids.add(c.getString(0));
        } finally {
            c.close();
        }
        return albumsInOrder(ids);
    }

    private List<MediaItem> albumsInOrder(Set<String> ids) throws IOException {
        if (ids.isEmpty()) return new ArrayList<MediaItem>();
        StringBuilder in = new StringBuilder();
        for (String id : ids) {
            if (in.length() > 0) in.append(',');
            in.append(Long.parseLong(id));
        }
        List<MediaItem> found = albumsFromSongs(MediaStore.Audio.Media.ALBUM_ID + " IN (" + in + ")", null, null);
        Map<String, MediaItem> byId = new HashMap<String, MediaItem>();
        for (MediaItem m : found) byId.put(m.id, m);
        List<MediaItem> out = new ArrayList<MediaItem>();
        for (String id : ids) {
            MediaItem m = byId.get(id);
            if (m != null) out.add(m);
        }
        return out;
    }

    @Override
    public List<MediaItem> tracks(MediaItem container) throws IOException {
        if (container.kind == MediaItem.PLAYLIST) {
            Uri members = MediaStore.Audio.Playlists.Members.getContentUri("external", Long.parseLong(container.id));
            String[] cols = Arrays.copyOf(TRACK_COLUMNS, TRACK_COLUMNS.length + 1);
            cols[0] = MediaStore.Audio.Playlists.Members.AUDIO_ID;
            cols[cols.length - 1] = MediaStore.Audio.Playlists.Members._ID;
            List<String> args = new ArrayList<String>();
            String selection = "1" + notHidden(args);
            return tracksFrom(query(members, cols, selection, args.toArray(new String[args.size()]),
                    MediaStore.Audio.Playlists.Members.PLAY_ORDER), MediaStore.Audio.Playlists.Members.AUDIO_ID,
                    MediaStore.Audio.Playlists.Members._ID);
        }
        return tracksFrom(media(TRACK_COLUMNS, MediaStore.Audio.Media.ALBUM_ID + " = ?", new String[]{container.id},
                MediaStore.Audio.Media.TRACK + ", " + MediaStore.Audio.Media.TITLE_KEY), MediaStore.Audio.Media._ID);
    }

    @Override
    public List<MediaItem> search(String query) throws IOException {
        String like = "%" + query.replace("%", "").replace("_", "") + "%";
        List<MediaItem> out = new ArrayList<MediaItem>();
        List<MediaItem> artists = artistsFromSongs(MediaStore.Audio.Media.ARTIST + " LIKE ?", new String[]{like});
        out.addAll(artists.size() > 10 ? artists.subList(0, 10) : artists);
        out.addAll(albumsFromSongs(MediaStore.Audio.Media.ALBUM + " LIKE ? OR " + MediaStore.Audio.Media.ARTIST
                + " LIKE ?", new String[]{like, like}, MediaStore.Audio.Media.ALBUM_KEY));
        out.addAll(tracksFrom(media(TRACK_COLUMNS, MediaStore.Audio.Media.TITLE + " LIKE ? OR "
                        + MediaStore.Audio.Media.ARTIST + " LIKE ? OR " + MediaStore.Audio.Media.ALBUM + " LIKE ?",
                new String[]{like, like, like}, MediaStore.Audio.Media.TITLE_KEY), MediaStore.Audio.Media._ID));
        return out;
    }

    /**
     * Every folder that directly holds music, with its number of songs, sorted by path. Includes
     * excluded folders (so they can be shown again) but not Grapefruit's own downloads.
     */
    public Map<String, Integer> musicFolders() throws IOException {
        List<String> args = new ArrayList<String>();
        String own = ownFolder();
        String selection = IS_MUSIC;
        if (own != null) {
            selection += " AND " + DATA + " NOT LIKE ? ESCAPE '\\'";
            args.add(Folders.likeInside(own));
        }
        Cursor c = query(MEDIA, new String[]{DATA}, selection, args.toArray(new String[args.size()]), null);
        Map<String, Integer> folders = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
        try {
            while (c.moveToNext()) {
                String folder = Folders.parent(c.getString(0));
                if (folder == null) continue;
                Integer n = folders.get(folder);
                folders.put(folder, n == null ? 1 : n + 1);
            }
        } finally {
            c.close();
        }
        return folders;
    }

    @Override
    public synchronized String artworkUrl(String artKey, int sizePx) {
        if (artKey == null || !artKey.startsWith("album:")) return artKey;
        if (artCache.containsKey(artKey)) return artCache.get(artKey);
        String albumId = artKey.substring("album:".length());
        String result = null;
        try {
            Cursor c = query(ALBUMS, new String[]{MediaStore.Audio.Albums.ALBUM_ART},
                    MediaStore.Audio.Albums._ID + " = ?", new String[]{albumId}, null);
            try {
                if (c.moveToFirst()) result = c.getString(0);
            } finally {
                c.close();
            }
            if (result == null || !new File(result).exists()) {
                // No cached album art: read the picture embedded in one of the album's files.
                result = null;
                Cursor t = query(MEDIA, new String[]{DATA},
                        MediaStore.Audio.Media.ALBUM_ID + " = ?", new String[]{albumId}, null);
                try {
                    if (t.moveToFirst() && t.getString(0) != null) result = "embedded:" + t.getString(0);
                } finally {
                    t.close();
                }
            }
        } catch (IOException ignored) {
            // No art.
        }
        artCache.put(artKey, result);
        return result;
    }

    @Override
    public StreamRequest stream(MediaItem track, int quality) {
        return null; // Local files are played straight from track.path.
    }

    @Override
    public StreamRequest download(MediaItem track, int quality) {
        return null;
    }

    @Override
    public Lyrics lyrics(MediaItem track) throws IOException {
        return sidecarLyrics(track.path, "Lyrics file");
    }

    @Override
    public boolean canEditPlaylists() {
        return true;
    }

    @Override
    public void addToPlaylist(MediaItem playlist, List<MediaItem> tracks) throws IOException {
        addMembers(Long.parseLong(playlist.id), tracks);
    }

    @Override
    public void createPlaylist(String name, List<MediaItem> tracks) throws IOException {
        ContentValues v = new ContentValues();
        long now = System.currentTimeMillis() / 1000;
        v.put(MediaStore.Audio.Playlists.NAME, name);
        v.put(MediaStore.Audio.Playlists.DATE_ADDED, now);
        v.put(MediaStore.Audio.Playlists.DATE_MODIFIED, now);
        Uri created;
        try {
            created = resolver.insert(PLAYLISTS, v);
        } catch (RuntimeException e) {
            throw new IOException("This phone didn't let Grapefruit make a playlist");
        }
        if (created == null) throw new IOException("This phone didn't let Grapefruit make a playlist");
        addMembers(ContentUris.parseId(created), tracks);
    }

    @Override
    public boolean editPlaylist(MediaItem playlist, String name, String description, List<MediaItem> removed)
            throws IOException {
        // MediaStore playlists have no description; Grapefruit keeps it on the phone.
        long id = Long.parseLong(playlist.id);
        try {
            if (name != null) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Audio.Playlists.NAME, name);
                v.put(MediaStore.Audio.Playlists.DATE_MODIFIED, System.currentTimeMillis() / 1000);
                resolver.update(ContentUris.withAppendedId(PLAYLISTS, id), v, null, null);
            }
            if (!removed.isEmpty()) {
                StringBuilder in = new StringBuilder();
                for (MediaItem t : removed) {
                    if (in.length() > 0) in.append(',');
                    in.append(Long.parseLong(t.entryId));
                }
                resolver.delete(MediaStore.Audio.Playlists.Members.getContentUri("external", id),
                        MediaStore.Audio.Playlists.Members._ID + " IN (" + in + ")", null);
            }
        } catch (RuntimeException e) {
            throw new IOException("This phone didn't let Grapefruit change the playlist");
        }
        return false;
    }

    private void addMembers(long playlistId, List<MediaItem> tracks) throws IOException {
        Uri members = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        int base = 0;
        Cursor c = query(members, new String[]{MediaStore.Audio.Playlists.Members.PLAY_ORDER}, null, null, null);
        try {
            while (c.moveToNext()) base = Math.max(base, c.getInt(0));
        } finally {
            c.close();
        }
        List<ContentValues> rows = new ArrayList<ContentValues>();
        for (MediaItem t : tracks) {
            if (!ID.equals(t.sourceId)) continue;
            ContentValues v = new ContentValues();
            v.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, Long.parseLong(t.id));
            v.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, base + rows.size() + 1);
            rows.add(v);
        }
        try {
            resolver.bulkInsert(members, rows.toArray(new ContentValues[rows.size()]));
        } catch (RuntimeException e) {
            throw new IOException("This phone didn't let Grapefruit change the playlist");
        }
    }

    /** Reads "Song.lrc" next to "Song.mp3", if there is one. */
    public static Lyrics sidecarLyrics(String audioPath, String credit) throws IOException {
        if (audioPath == null) return null;
        int dot = audioPath.lastIndexOf('.');
        String base = dot > audioPath.lastIndexOf('/') ? audioPath.substring(0, dot) : audioPath;
        File lrc = new File(base + ".lrc");
        if (!lrc.exists()) lrc = new File(base + ".LRC");
        if (!lrc.exists()) return null;
        InputStream in = new FileInputStream(lrc);
        try {
            return LrcParser.parse(Http.readAll(in), credit);
        } finally {
            in.close();
        }
    }
}
