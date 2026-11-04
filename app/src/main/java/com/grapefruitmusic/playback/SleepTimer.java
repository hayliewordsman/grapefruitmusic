package com.grapefruitmusic.playback;

import android.content.Context;
import android.os.SystemClock;

import com.grapefruitmusic.audiobooks.Audiobook;
import com.grapefruitmusic.audiobooks.AudiobookStore;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.radio.Radio;
import com.grapefruitmusic.util.Bg;

import java.util.ArrayList;
import java.util.List;

/**
 * The sleep timer: stops what's playing (music, a podcast, an audiobook, an internet station or the
 * FM radio) after a while, fading it out over the last seconds, or at the end of the song, episode
 * or chapter. Set from Now Playing's ⋯ menu; it lasts until it goes off or is turned off.
 */
public final class SleepTimer {
    public interface Listener {
        void onSleepTimerChanged();
    }

    public static final int OFF = 0;
    /** After a set time. */
    public static final int TIMED = 1;
    /** When the song or episode playing ends (an audiobook's file). */
    public static final int END_OF_ITEM = 2;
    /** When the audiobook's chapter playing ends. */
    public static final int END_OF_CHAPTER = 3;

    /** The minutes offered. */
    public static final int[] MINUTES = {5, 10, 15, 30, 45, 60};

    /** A timed stop fades out over this long, so it doesn't wake anyone. */
    private static final long FADE_MS = 8000;
    /** A chapter's end is looked for this often. */
    private static final long CHAPTER_TICK_MS = 1000;

    private static SleepTimer instance;

    private final Context context;
    private final List<Listener> listeners = new ArrayList<Listener>();
    private int mode = OFF;
    /** TIMED: when it goes off (SystemClock.elapsedRealtime), and the minutes chosen. */
    private long endsAt;
    private int minutes;
    /** END_OF_CHAPTER: the book, the chapter playing, and how far into the book the last look was. */
    private String bookId;
    private int chapter = -1;
    private long lastBookMs = -1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            tick();
        }
    };

    public static synchronized SleepTimer get(Context c) {
        if (instance == null) instance = new SleepTimer(c.getApplicationContext());
        return instance;
    }

    private SleepTimer(Context context) {
        this.context = context;
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public int mode() {
        return mode;
    }

    public boolean active() {
        return mode != OFF;
    }

    /** TIMED: how long is left (0 otherwise). */
    public long remainingMs() {
        return mode == TIMED ? Math.max(0, endsAt - SystemClock.elapsedRealtime()) : 0;
    }

    /** TIMED: the minutes it was set for (0 otherwise). */
    public int minutes() {
        return mode == TIMED ? minutes : 0;
    }

    /** Goes off in this many minutes. */
    public void setMinutes(int minutes) {
        reset();
        mode = TIMED;
        this.minutes = minutes;
        endsAt = SystemClock.elapsedRealtime() + minutes * 60000L;
        schedule();
        changed();
    }

    /** Goes off when the song, episode or audiobook file playing ends. */
    public void setEndOfItem() {
        reset();
        mode = END_OF_ITEM;
        changed();
    }

    /** Goes off when the audiobook's chapter playing ends. */
    public void setEndOfChapter(Audiobook book) {
        reset();
        mode = END_OF_CHAPTER;
        bookId = book.id;
        Player p = Player.get(context);
        MediaItem m = p.current();
        int part = m != null ? Math.max(0, m.trackNumber - 1) : 0;
        long ms = p.positionMs();
        chapter = book.chapterAt(part, ms);
        lastBookMs = bookMs(book, part, ms);
        Bg.mainDelayed(tick, CHAPTER_TICK_MS);
        changed();
    }

    public void cancel() {
        if (mode == OFF) return;
        reset();
        changed();
    }

    /**
     * The player has come to the end of item: whether to stop there (and if so, the timer is done).
     * An audiobook's chapters almost always end with its files, so a file's end is a chapter's end.
     */
    boolean stopsAfter(MediaItem item) {
        if (item == null || item.live) return false;
        boolean stop = mode == END_OF_ITEM
                || (mode == END_OF_CHAPTER && Player.isAudiobook(item) && item.albumId != null
                && item.albumId.equals(bookId));
        if (stop) {
            reset();
            changed();
        }
        return stop;
    }

    private void reset() {
        Bg.cancelMain(tick);
        mode = OFF;
        bookId = null;
        chapter = -1;
        lastBookMs = -1;
        Player.get(context).setFade(1f);
    }

    private void schedule() {
        Bg.cancelMain(tick);
        long left = endsAt - SystemClock.elapsedRealtime();
        // One wait until the fade begins, then small steps through it.
        Bg.mainDelayed(tick, left > FADE_MS ? left - FADE_MS : 100);
    }

    private void tick() {
        if (mode == TIMED) {
            long left = endsAt - SystemClock.elapsedRealtime();
            if (left <= 0) {
                goOff();
                return;
            }
            Player.get(context).setFade(left < FADE_MS ? left / (float) FADE_MS : 1f);
            schedule();
        } else if (mode == END_OF_CHAPTER) {
            watchChapter();
        }
    }

    /** Stops at the start of the next chapter, if the book plays on into it (not if it was skipped to). */
    private void watchChapter() {
        Player p = Player.get(context);
        MediaItem m = p.current();
        Audiobook book = AudiobookStore.get(context).book(bookId);
        if (book == null) {
            cancel();
            return;
        }
        if (m != null && Player.isAudiobook(m) && bookId.equals(m.albumId)) {
            int part = Math.max(0, m.trackNumber - 1);
            long ms = p.positionMs();
            long at = bookMs(book, part, ms);
            int now = book.chapterAt(part, ms);
            if (now != chapter) {
                // Played on into it: a few seconds on (at most, at 2×) since the last look.
                boolean playedOn = lastBookMs >= 0 && at >= lastBookMs && at - lastBookMs < 6000;
                if (playedOn && p.wantsToPlay() && now >= 0) {
                    Audiobook.Chapter next = book.chapters.get(now);
                    if (next.part == part) p.seekTo(next.startMs);
                    goOff();
                    return;
                }
                // Skipped or chosen: the chapter it's in now is the one to finish.
                chapter = now;
            }
            lastBookMs = at;
        } else {
            lastBookMs = -1;
        }
        Bg.mainDelayed(tick, CHAPTER_TICK_MS);
    }

    private static long bookMs(Audiobook book, int part, long ms) {
        long t = 0;
        for (int i = 0; i < part && i < book.parts.size(); i++) t += Math.max(0, book.parts.get(i).durationMs);
        return t + ms;
    }

    /** Time's up: stops whatever is playing. */
    private void goOff() {
        Bg.cancelMain(tick);
        mode = OFF;
        bookId = null;
        Player p = Player.get(context);
        if (p.wantsToPlay()) p.pause();
        p.setFade(1f);
        Radio radio = Radio.peek();
        if (radio != null && radio.inSession() && radio.wantsToPlay()) radio.stop();
        changed();
    }

    private void changed() {
        for (Listener l : new ArrayList<Listener>(listeners)) l.onSleepTimerChanged();
    }
}
