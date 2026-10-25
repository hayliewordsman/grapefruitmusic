package com.grapefruitmusic.source;

import android.content.Context;

import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.lyrics.Lyrics;
import com.grapefruitmusic.model.MediaItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Everything downloaded for offline listening, from every server. Works offline. */
public final class DownloadedSource implements MusicSource {
    public static final String ID = "downloaded";

    private final Downloads downloads;

    public DownloadedSource(Context context) {
        downloads = Downloads.get(context);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Downloaded Music";
    }

    @Override
    public String typeLabel() {
        return "Available offline";
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public boolean canDownload() {
        return false;
    }

    @Override
    public List<MediaItem> playlists() {
        return downloads.playlists(ID);
    }

    @Override
    public List<MediaItem> artists() {
        return downloads.artists(null, null, ID);
    }

    @Override
    public List<MediaItem> albums() {
        return downloads.albums(null, null, "album COLLATE NOCASE", ID);
    }

    @Override
    public List<MediaItem> songs() {
        return downloads.doneTracks(null, null, "title COLLATE NOCASE");
    }

    @Override
    public List<MediaItem> recentAlbums(int limit) {
        List<MediaItem> all = downloads.albums(null, null, "added DESC", ID);
        return all.size() > limit ? new ArrayList<MediaItem>(all.subList(0, limit)) : all;
    }

    @Override
    public List<MediaItem> artistAlbums(MediaItem artist) {
        return downloads.albums("album_artist = ?", new String[]{artist.id}, "MAX(year), album", ID);
    }

    @Override
    public List<MediaItem> tracks(MediaItem container) {
        String[] parts = container.id.split(Downloads.SEP, 2);
        if (parts.length < 2) return new ArrayList<MediaItem>();
        if (container.kind == MediaItem.PLAYLIST) {
            return downloads.playlistTracks(parts[0], parts[1]);
        }
        return downloads.doneTracks("source_id = ? AND album_id = ?", parts,
                "disc_no, track_no, title COLLATE NOCASE");
    }

    @Override
    public List<MediaItem> search(String query) {
        String like = "%" + query + "%";
        List<MediaItem> out = new ArrayList<MediaItem>();
        out.addAll(downloads.artists("album_artist LIKE ?", new String[]{like}, ID));
        out.addAll(downloads.albums("album LIKE ? OR album_artist LIKE ?", new String[]{like, like},
                "album COLLATE NOCASE", ID));
        out.addAll(downloads.doneTracks("title LIKE ? OR artist LIKE ? OR album LIKE ?",
                new String[]{like, like, like}, "title COLLATE NOCASE"));
        return out;
    }

    @Override
    public String artworkUrl(String artKey, int sizePx) {
        return artKey; // Already a file path (or "embedded:" path).
    }

    @Override
    public StreamRequest stream(MediaItem track, int quality) {
        return null; // Played from track.path.
    }

    @Override
    public StreamRequest download(MediaItem track, int quality) {
        return null;
    }

    @Override
    public Lyrics lyrics(MediaItem track) throws IOException {
        return LocalSource.sidecarLyrics(track.path, "Saved lyrics");
    }

    /** Downloaded songs belong to their server; playlists are edited there. */
    @Override
    public boolean canEditPlaylists() {
        return false;
    }

    @Override
    public void addToPlaylist(MediaItem playlist, List<MediaItem> tracks) throws IOException {
        throw new IOException("Downloaded playlists can't be changed");
    }

    @Override
    public void createPlaylist(String name, List<MediaItem> tracks) throws IOException {
        throw new IOException("Downloaded playlists can't be changed");
    }

    @Override
    public boolean editPlaylist(MediaItem playlist, String name, String description, List<MediaItem> removed)
            throws IOException {
        throw new IOException("Downloaded playlists can't be changed");
    }
}
