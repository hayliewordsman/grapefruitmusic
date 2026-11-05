package com.grapefruitmusic.source;

import android.content.Context;
import com.grapefruitmusic.lyrics.Lyrics;
import com.grapefruitmusic.model.MediaItem;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** The advance EP, installed only in Blue Grapefruit and unpacked inside its internal sandbox. */
public final class BundledSource implements MusicSource {
    public static final String ID = "blue_victory_advance";
    private final Context context;
    private final List<MediaItem> songs = new ArrayList<MediaItem>();
    private MediaItem album, artist;
    private boolean ready;

    BundledSource(Context context) { this.context = context.getApplicationContext(); }
    public static boolean owns(MediaItem item) { return item != null && ID.equals(item.sourceId); }
    @Override public String id() { return ID; }
    @Override public String name() { return "Victory Lights (Advance Copy)"; }
    @Override public String typeLabel() { return "Included with Blue Grapefruit"; }
    @Override public boolean isRemote() { return false; }
    @Override public boolean canDownload() { return false; }
    @Override public boolean canEditPlaylists() { return false; }

    /** Library calls run on background workers. Verify and atomically install each file once. */
    private synchronized void prepare() throws IOException {
        if (ready) return;
        songs.clear();
        try {
            JSONObject catalog = new JSONObject(new String(readAsset("catalog.json"), "UTF-8"));
            if (!"approved".equals(catalog.getString("reviewStatus"))) throw new IOException("EP tags await approval");
            String bundleId = catalog.getString("bundleId");
            if (!bundleId.matches("[a-f0-9]{64}")) throw new IOException("Invalid EP identity");
            File root = new File(context.getFilesDir(), "victory/" + bundleId);
            if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Couldn't prepare the EP");
            new File(root, ".nomedia").createNewFile();
            String coverAsset = catalog.optString("coverFile", "cover.jpg");
            if (!coverAsset.matches("cover\\.(png|jpg)")) throw new IOException("Invalid EP cover path");
            File cover = new File(root, coverAsset);
            if (!cover.exists()) {
                byte[] picture = readAsset(coverAsset);
                FileOutputStream output = new FileOutputStream(cover);
                try { output.write(picture); } finally { output.close(); }
            }
            album = new MediaItem(MediaItem.ALBUM, ID, "victory-lights-advance", catalog.getString("album"));
            album.artist = catalog.getString("artist");
            album.albumArtist = album.artist;
            album.artKey = cover.getPath();
            album.year = catalog.optInt("year");
            album.added = catalog.optLong("added");
            artist = new MediaItem(MediaItem.ARTIST, ID, "poppy-peppers-advance", album.artist);
            artist.artKey = cover.getPath();
            artist.count = 1;
            byte[] key = readAsset("bundle.key");
            JSONArray tracks = catalog.getJSONArray("tracks");
            if (tracks.length() != 5) throw new IOException("The advance EP must have five tracks");
            for (int i = 0; i < tracks.length(); i++) {
                JSONObject track = tracks.getJSONObject(i);
                String filename = track.getString("filename"), asset = track.getString("asset");
                if (filename.contains("/") || filename.contains("\\") || !filename.endsWith(".mp3")
                        || !asset.matches("[0-9]{2}\\.bgv") || track.getInt("track") != i + 1)
                    throw new IOException("Invalid EP track order or path");
                File file = new File(root, filename);
                unpack(file, asset, track, key);
                MediaItem song = new MediaItem(MediaItem.TRACK, ID, "advance-" + (i + 1), track.getString("title"));
                song.artist = album.artist;
                song.albumArtist = album.artist;
                song.album = album.title;
                song.albumId = album.id;
                song.artistId = artist.id;
                song.year = album.year;
                song.trackNumber = i + 1;
                song.discNumber = 1;
                song.suffix = "mp3";
                song.path = file.getPath();
                song.artKey = cover.getPath();
                song.durationMs = track.getLong("durationMs");
                songs.add(song);
                album.durationMs += song.durationMs;
            }
            album.count = songs.size();
            ready = true;
            // A beta can replace the display artwork without retaining a second installed EP copy.
            File[] versions = root.getParentFile().listFiles();
            if (versions != null) for (File old : versions)
                if (!old.equals(root) && old.getName().matches("[a-f0-9]{64}")) removeOldBundle(old);
        } catch (JSONException e) { throw new IOException("Couldn't read the included EP", e); }
    }

    private static void removeOldBundle(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) removeOldBundle(child);
        file.delete();
    }

    private void unpack(File file, String asset, JSONObject track, byte[] key) throws IOException, JSONException {
        if (file.isFile() && file.length() == track.getLong("bytes")) return;
        File temporary = new File(file.getPath() + ".part");
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            InputStream input = context.getAssets().open("victory/" + asset);
            OutputStream output = new DigestOutputStream(new FileOutputStream(temporary), hash);
            try { BundleCrypto.decrypt(input, output, track.getLong("encryptedBytes"), key); }
            finally { try { output.close(); } finally { input.close(); } }
            if (temporary.length() != track.getLong("bytes") || !hex(hash.digest()).equals(track.getString("sha256")))
                throw new IOException("The included track failed its integrity check");
            if (!temporary.renameTo(file)) throw new IOException("Couldn't save the included track");
        } catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 unavailable", e); }
        finally { temporary.delete(); }
    }

    private byte[] readAsset(String name) throws IOException {
        InputStream input = context.getAssets().open("victory/" + name);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
            return output.toByteArray();
        } finally { input.close(); }
    }

    private static String hex(byte[] data) {
        StringBuilder value = new StringBuilder();
        for (byte b : data) value.append(String.format(Locale.US, "%02x", b & 255));
        return value.toString();
    }

    public File fileFor(MediaItem track) throws IOException {
        prepare();
        for (MediaItem song : songs) if (song.sameAs(track)) return new File(song.path);
        throw new IOException("Unknown advance-copy track");
    }
    @Override public List<MediaItem> songs() throws IOException { prepare(); return new ArrayList<MediaItem>(songs); }
    @Override public List<MediaItem> albums() throws IOException { prepare(); return Collections.singletonList(album); }
    @Override public List<MediaItem> artists() throws IOException { prepare(); return Collections.singletonList(artist); }
    @Override public List<MediaItem> playlists() { return Collections.emptyList(); }
    @Override public List<MediaItem> recentAlbums(int limit) throws IOException {
        return limit <= 0 ? Collections.<MediaItem>emptyList() : albums();
    }
    @Override public List<MediaItem> artistAlbums(MediaItem selected) throws IOException {
        prepare(); return artist.id.equals(selected.id) ? albums() : Collections.<MediaItem>emptyList();
    }
    @Override public List<MediaItem> tracks(MediaItem container) throws IOException {
        prepare(); return container.kind == MediaItem.ALBUM && album.id.equals(container.id)
                ? songs() : Collections.<MediaItem>emptyList();
    }
    @Override public List<MediaItem> search(String query) throws IOException {
        prepare();
        String needle = query.toLowerCase(Locale.US);
        List<MediaItem> matches = new ArrayList<MediaItem>();
        if (artist.title.toLowerCase(Locale.US).contains(needle)) matches.add(artist);
        if (album.title.toLowerCase(Locale.US).contains(needle)) matches.add(album);
        for (MediaItem song : songs)
            if ((song.title + " " + song.artist + " " + song.album).toLowerCase(Locale.US).contains(needle)) matches.add(song);
        return matches;
    }
    @Override public String artworkUrl(String artKey, int sizePx) { return artKey; }
    @Override public StreamRequest stream(MediaItem track, int quality) { return null; }
    @Override public StreamRequest download(MediaItem track, int quality) { return null; }
    @Override public Lyrics lyrics(MediaItem track) { return null; }
    @Override public void addToPlaylist(MediaItem playlist, List<MediaItem> tracks) throws IOException { throw readOnly(); }
    @Override public void createPlaylist(String name, List<MediaItem> tracks) throws IOException { throw readOnly(); }
    @Override public void deletePlaylist(MediaItem playlist) throws IOException { throw readOnly(); }
    @Override public boolean editPlaylist(MediaItem playlist, String name, String description, List<MediaItem> removed)
            throws IOException { throw readOnly(); }
    private static IOException readOnly() { return new IOException("The included advance EP is read-only"); }
}
