package com.grapefruitmusic.audiobooks;

import com.grapefruitmusic.model.MediaItem;

import java.util.ArrayList;
import java.util.List;

/**
 * An audiobook: one file (an M4B, say) or a folder of them (an MP3 a chapter), its chapters, and
 * where the listener left it.
 */
public final class Audiobook {
    /** One of its files, in order. */
    public static final class Part {
        public final String path;
        public final String title;
        public final long durationMs;

        public Part(String path, String title, long durationMs) {
            this.path = path;
            this.title = title;
            this.durationMs = durationMs;
        }
    }

    /** A chapter: in which part, and where in it. */
    public static final class Chapter {
        public final String title;
        public final int part;
        public final long startMs;

        public Chapter(String title, int part, long startMs) {
            this.title = title;
            this.part = part;
            this.startMs = startMs;
        }
    }

    /** Its folder, or its one file: also how the listener's place in it is kept. */
    public final String id;
    public String title;
    public String author;
    /** For ArtworkLoader: a picture file beside it, or "embedded:" its first file. */
    public String art;
    public final List<Part> parts = new ArrayList<Part>();
    public final List<Chapter> chapters = new ArrayList<Chapter>();
    public long addedAt;
    public long playedAt;
    /** Where it was left: the part, and the time in it. */
    public int part;
    public long positionMs;
    public boolean finished;
    /** In Grapefruit's own folder (added with Add): Grapefruit may delete it. */
    public boolean imported;

    public Audiobook(String id) {
        this.id = id;
    }

    public long durationMs() {
        long total = 0;
        for (Part p : parts) total += Math.max(0, p.durationMs);
        return total;
    }

    /** How far in, in ms from the start of the book. */
    public long elapsedMs() {
        long t = 0;
        for (int i = 0; i < part && i < parts.size(); i++) t += Math.max(0, parts.get(i).durationMs);
        return t + positionMs;
    }

    public boolean started() {
        return part > 0 || positionMs > 0;
    }

    /** The chapter at this place (part, ms in it), or -1 before the first. */
    public int chapterAt(int inPart, long ms) {
        int found = -1;
        for (int i = 0; i < chapters.size(); i++) {
            Chapter c = chapters.get(i);
            if (c.part < inPart || (c.part == inPart && c.startMs <= ms)) found = i;
            else break;
        }
        return found;
    }

    /** The book's files as the player plays them (one queue, a part each). */
    public List<MediaItem> items() {
        List<MediaItem> out = new ArrayList<MediaItem>();
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            // Called by the book's title in the mini player, the notification and on the lock screen
            // (a part's own name is often only "Track 02"); Now Playing shows the chapter.
            MediaItem m = new MediaItem(MediaItem.TRACK, AudiobookSource.ID, p.path, title);
            m.album = title;
            m.albumId = id;
            m.artist = author;
            m.artKey = art;
            m.path = p.path;
            m.durationMs = p.durationMs;
            m.trackNumber = i + 1;
            out.add(m);
        }
        return out;
    }

    /** As a card on the Audiobooks page. */
    public MediaItem card() {
        MediaItem m = new MediaItem(MediaItem.ALBUM, AudiobookSource.ID, id, title);
        m.artist = author;
        m.artKey = art;
        m.durationMs = durationMs();
        return m;
    }
}
