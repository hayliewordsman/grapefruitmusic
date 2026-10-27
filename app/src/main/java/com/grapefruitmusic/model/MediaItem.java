package com.grapefruitmusic.model;

import java.io.Serializable;

/** An artist, album, playlist or track from any source. */
public class MediaItem implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int ARTIST = 1;
    public static final int ALBUM = 2;
    public static final int PLAYLIST = 3;
    public static final int TRACK = 4;

    public int kind;
    /** The source this item belongs to (downloaded items keep their original source). */
    public String sourceId;
    public String id;
    public String title;
    /** Track artist, or album artist for albums. */
    public String artist;
    public String album;
    public String albumId;
    public String artistId;
    /** Album artist for tracks, used to group downloads. */
    public String albumArtist;
    public int trackNumber;
    public int discNumber;
    public int year;
    /** Number of songs (albums, playlists) or albums (artists). */
    public int count;
    public long durationMs;
    /** Source-specific artwork key, or an absolute file path for downloaded/local art. */
    public String artKey;
    /** File extension / container, e.g. "flac". */
    public String suffix;
    /** Local file to play, when the item is on this phone. */
    public String path;
    /** Playlist description. */
    public String description;
    /** A track's entry in the playlist it was loaded from (used to remove it). */
    public String entryId;
    /** Track gain in dB reported by the server (ReplayGain, relative to -18 LUFS), or null. */
    public Float replayGain;
    /** When an album was added to the library or a playlist made (ms since 1970), or 0 if unknown. */
    public long added;
    /** A live stream (an internet radio station): no length, no seeking. */
    public boolean live;
    /** Where a live stream plays from. */
    public String streamUrl;

    public MediaItem() {
    }

    public MediaItem(int kind, String sourceId, String id, String title) {
        this.kind = kind;
        this.sourceId = sourceId;
        this.id = id;
        this.title = title;
    }

    public String key() {
        return sourceId + ":" + id;
    }

    public boolean sameAs(MediaItem other) {
        return other != null && kind == other.kind && key().equals(other.key());
    }
}
