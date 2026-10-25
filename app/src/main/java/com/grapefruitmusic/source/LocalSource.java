package com.grapefruitmusic.source;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.grapefruitmusic.lyrics.LrcParser;
import com.grapefruitmusic.lyrics.Lyrics;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Music files stored on this phone (via the system media library). Works offline. */
public final class LocalSource implements MusicSource {
    public static final String ID = "device";

    private static final Uri MEDIA = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    private static final Uri ALBUMS = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
    private static final Uri ARTISTS = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
    private static final Uri PLAYLISTS = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;

    private static final String[] TRACK_COLUMNS = {
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.YEAR};
    private static final String IS_MUSIC = MediaStore.Audio.Media.IS_MUSIC + " != 0";

    private final ContentResolver resolver;
    private final Map<String, String> artCache = new HashMap<String, String>();

    public LocalSource(Context context) {
        resolver = context.getApplicationContext().getContentResolver();
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

    private Cursor query(Uri uri, String[] columns, String selection, String[] args, String order) throws IOException {
        try {
            Cursor c = resolver.query(uri, columns, selection, args, order);
            if (c == null) throw new IOException("Media library unavailable (is the SD card mounted?)");
            return c;
        } catch (SecurityException e) {
            throw new IOException("Grapefruit Music isn't allowed to read music files");
        }
    }

    private List<MediaItem> tracksFrom(Cursor c, String idColumn) {
        List<MediaItem> out = new ArrayList<MediaItem>();
        try {
            int id = c.getColumnIndexOrThrow(idColumn);
            int title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int artistId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID);
            int album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            int track = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK);
            int duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int data = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
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
        return tracksFrom(query(MEDIA, TRACK_COLUMNS, IS_MUSIC, null, MediaStore.Audio.Media.TITLE_KEY),
                MediaStore.Audio.Media._ID);
    }

    @Override
    public List<MediaItem> albums() throws IOException {
        return albumsWhere(null, null, MediaStore.Audio.Albums.ALBUM_KEY);
    }

    private List<MediaItem> albumsWhere(String selection, String[] args, String order) throws IOException {
        Cursor c = query(ALBUMS, new String[]{MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.NUMBER_OF_SONGS,
                MediaStore.Audio.Albums.FIRST_YEAR}, selection, args, order);
        List<MediaItem> out = new ArrayList<MediaItem>();
        try {
            while (c.moveToNext()) {
                MediaItem m = new MediaItem(MediaItem.ALBUM, ID, c.getString(0), c.getString(1));
                m.artist = unknownToNull(c.getString(2));
                m.count = c.getInt(3);
                m.year = c.getInt(4);
                m.artKey = "album:" + m.id;
                out.add(m);
            }
        } finally {
            c.close();
        }
        return out;
    }

    @Override
    public List<MediaItem> artists() throws IOException {
        Cursor c = query(ARTISTS, new String[]{MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Artists.NUMBER_OF_ALBUMS}, null, null, MediaStore.Audio.Artists.ARTIST_KEY);
        List<MediaItem> out = new ArrayList<MediaItem>();
        try {
            while (c.moveToNext()) {
                String name = unknownToNull(c.getString(1));
                MediaItem m = new MediaItem(MediaItem.ARTIST, ID, c.getString(0), name == null ? "Unknown Artist" : name);
                m.count = c.getInt(2);
                out.add(m);
            }
        } finally {
            c.close();
        }
        return out;
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
        Cursor c = query(MEDIA, new String[]{MediaStore.Audio.Media.ALBUM_ID}, IS_MUSIC, null,
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
        Cursor c = query(MEDIA, new String[]{MediaStore.Audio.Media.ALBUM_ID},
                IS_MUSIC + " AND " + MediaStore.Audio.Media.ARTIST_ID + " = ?", new String[]{artist.id},
                MediaStore.Audio.Media.YEAR);
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
        List<MediaItem> found = albumsWhere(MediaStore.Audio.Albums._ID + " IN (" + in + ")", null, null);
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
            String[] cols = TRACK_COLUMNS.clone();
            cols[0] = MediaStore.Audio.Playlists.Members.AUDIO_ID;
            return tracksFrom(query(members, cols, null, null, MediaStore.Audio.Playlists.Members.PLAY_ORDER),
                    MediaStore.Audio.Playlists.Members.AUDIO_ID);
        }
        return tracksFrom(query(MEDIA, TRACK_COLUMNS, MediaStore.Audio.Media.ALBUM_ID + " = ?",
                new String[]{container.id}, MediaStore.Audio.Media.TRACK + ", " + MediaStore.Audio.Media.TITLE_KEY),
                MediaStore.Audio.Media._ID);
    }

    @Override
    public List<MediaItem> search(String query) throws IOException {
        String like = "%" + query.replace("%", "").replace("_", "") + "%";
        List<MediaItem> out = new ArrayList<MediaItem>();
        Cursor a = query(ARTISTS, new String[]{MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Artists.NUMBER_OF_ALBUMS}, MediaStore.Audio.Artists.ARTIST + " LIKE ?",
                new String[]{like}, MediaStore.Audio.Artists.ARTIST_KEY);
        try {
            while (a.moveToNext() && out.size() < 10) {
                MediaItem m = new MediaItem(MediaItem.ARTIST, ID, a.getString(0), a.getString(1));
                m.count = a.getInt(2);
                out.add(m);
            }
        } finally {
            a.close();
        }
        out.addAll(albumsWhere(MediaStore.Audio.Albums.ALBUM + " LIKE ? OR " + MediaStore.Audio.Albums.ARTIST
                + " LIKE ?", new String[]{like, like}, MediaStore.Audio.Albums.ALBUM_KEY));
        out.addAll(tracksFrom(query(MEDIA, TRACK_COLUMNS, IS_MUSIC + " AND (" + MediaStore.Audio.Media.TITLE
                        + " LIKE ? OR " + MediaStore.Audio.Media.ARTIST + " LIKE ? OR " + MediaStore.Audio.Media.ALBUM
                        + " LIKE ?)", new String[]{like, like, like}, MediaStore.Audio.Media.TITLE_KEY),
                MediaStore.Audio.Media._ID));
        return out;
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
                Cursor t = query(MEDIA, new String[]{MediaStore.Audio.Media.DATA},
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
