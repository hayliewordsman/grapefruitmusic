package com.grapefruitmusic.source;

import com.grapefruitmusic.lyrics.Lyrics;
import com.grapefruitmusic.model.MediaItem;

import java.io.IOException;
import java.util.List;

/** A place music comes from: this phone, a server, or the downloads on this phone. */
public interface MusicSource {
    String id();

    /** "On This Phone", "Home" ... */
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
}
