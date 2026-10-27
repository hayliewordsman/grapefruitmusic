package com.grapefruitmusic.util;

import com.grapefruitmusic.model.MediaItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Pure-Java list helpers (unit tested). */
public final class Lists {
    private Lists() {
    }

    /** Moves the element at from so that it ends up at index to. */
    public static <T> void move(List<T> list, int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= list.size() || to >= list.size()) return;
        T item = list.remove(from);
        list.add(to, item);
    }

    /**
     * Recently Added: the newest albums with the playlists made in the same time, newest first,
     * up to limit. Albums the source can't date keep their order (the source's own, newest first)
     * after the dated ones; undated playlists are left out.
     */
    public static List<MediaItem> recentlyAdded(List<MediaItem> albums, List<MediaItem> playlists, int limit) {
        List<MediaItem> dated = new ArrayList<MediaItem>();
        List<MediaItem> undated = new ArrayList<MediaItem>();
        for (MediaItem a : albums) (a.added > 0 ? dated : undated).add(a);
        if (playlists != null) {
            for (MediaItem p : playlists) {
                if (p.added > 0) dated.add(p);
            }
        }
        // Stable: albums added at the same moment stay in the source's order.
        Collections.sort(dated, new Comparator<MediaItem>() {
            @Override
            public int compare(MediaItem a, MediaItem b) {
                return a.added == b.added ? 0 : a.added > b.added ? -1 : 1;
            }
        });
        dated.addAll(undated);
        return dated.size() > limit ? new ArrayList<MediaItem>(dated.subList(0, limit)) : dated;
    }
}
