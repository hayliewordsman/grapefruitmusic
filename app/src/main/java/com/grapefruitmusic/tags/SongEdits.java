package com.grapefruitmusic.tags;

import com.grapefruitmusic.model.MediaItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Songs whose tags were changed this session, so pages already showing them (and the play queue)
 * can show the new title, artist and so on without loading everything again.
 */
public final class SongEdits {
    private static final Map<String, MediaItem> EDITED = new HashMap<String, MediaItem>();

    private SongEdits() {
    }

    /** Notes the song's new details (in tags) under its key. */
    public static synchronized void record(MediaItem song, Tags tags) {
        MediaItem m = new MediaItem(song.kind, song.sourceId, song.id, tags.title);
        m.artist = tags.artist;
        m.album = tags.album;
        m.albumArtist = tags.albumArtist;
        m.year = tags.year;
        m.trackNumber = tags.track;
        m.discNumber = tags.disc;
        EDITED.put(song.key(), m);
        apply(song);
    }

    /** Brings any edited songs in items up to date; true if something changed. */
    public static synchronized boolean apply(List<MediaItem> items) {
        if (items == null || EDITED.isEmpty()) return false;
        boolean changed = false;
        for (MediaItem m : items) changed |= apply(m);
        return changed;
    }

    private static boolean apply(MediaItem m) {
        if (m == null || m.kind != MediaItem.TRACK) return false;
        MediaItem e = EDITED.get(m.key());
        if (e == null || same(e, m)) return false;
        m.title = e.title.length() > 0 ? e.title : m.title;
        m.artist = blankToNull(e.artist);
        m.album = blankToNull(e.album);
        m.albumArtist = blankToNull(e.albumArtist);
        m.year = e.year;
        m.trackNumber = e.trackNumber;
        m.discNumber = e.discNumber;
        return true;
    }

    private static boolean same(MediaItem e, MediaItem m) {
        return eq(e.title, m.title) && eq(blankToNull(e.artist), m.artist) && eq(blankToNull(e.album), m.album)
                && eq(blankToNull(e.albumArtist), m.albumArtist) && e.year == m.year
                && e.trackNumber == m.trackNumber && e.discNumber == m.discNumber;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().length() == 0 ? null : s.trim();
    }
}
