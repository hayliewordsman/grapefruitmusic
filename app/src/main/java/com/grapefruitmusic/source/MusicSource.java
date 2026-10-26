package com.grapefruitmusic.source;

import com.grapefruitmusic.lyrics.Lyrics;
import com.grapefruitmusic.model.MediaItem;

import java.io.IOException;
import java.util.List;

/** A place music comes from: this phone, a server, or the downloads on this phone. */
public interface MusicSource {
    String id();

    /** "On This Device", "Home" ... */
    String name();

    /** "Navidrome / Subsonic", "Jellyfin" ... */
    String typeLabel();

    /** Needs a network connection to browse and stream. */
    boolean isRemote();

    /** Can tracks be downloaded for offline listening? */
    boolean canDownload();

    List<MediaItem> playlists() throws IOException;

    List<MediaItem> artists() throws IOException;

    List<MediaItem> albums() throws IOException;

    List<MediaItem> songs() throws IOException;

    List<MediaItem> recentAlbums(int limit) throws IOException;

    List<MediaItem> artistAlbums(MediaItem artist) throws IOException;

    /** Tracks of an album or playlist, in order. */
    List<MediaItem> tracks(MediaItem container) throws IOException;

    /** Artists, albums and songs matching the query. */
    List<MediaItem> search(String query) throws IOException;

    /** URL or file path for artwork, or null. May block (called off the main thread). */
    String artworkUrl(String artKey, int sizePx);

    /** How to stream a track at the given quality (see Prefs.QUALITIES). */
    StreamRequest stream(MediaItem track, int quality);

    /** How to download a track for offline listening, or null if not supported. */
    StreamRequest download(MediaItem track, int quality);

    /** Lyrics the source itself knows about (server lyrics, .lrc files), or null. Blocking. */
    Lyrics lyrics(MediaItem track) throws IOException;

    /** Can songs be added to this source's playlists (and new playlists made)? */
    boolean canEditPlaylists();

    /** Appends tracks (from this source) to one of its playlists. Blocking. */
    void addToPlaylist(MediaItem playlist, List<MediaItem> tracks) throws IOException;

    /** Makes a new playlist holding tracks (from this source). Blocking. */
    void createPlaylist(String name, List<MediaItem> tracks) throws IOException;

    /**
     * Changes a playlist: renames it (name not null), sets its description (description not
     * null), and removes tracks (as loaded by tracks(), identified by their entryId). Blocking.
     *
     * @return whether the source kept the description; if not, Grapefruit keeps it on the phone
     */
    boolean editPlaylist(MediaItem playlist, String name, String description, List<MediaItem> removed)
            throws IOException;
}
