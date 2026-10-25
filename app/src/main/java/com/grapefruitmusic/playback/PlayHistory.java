package com.grapefruitmusic.playback;

import com.grapefruitmusic.model.MediaItem;

import java.util.ArrayList;
import java.util.List;

/** Recently played songs, oldest first, capped. Pure Java (unit tested). */
public final class PlayHistory {
    private final int max;
    private final List<MediaItem> items = new ArrayList<MediaItem>();

    public PlayHistory(int max) {
        this.max = max;
    }

    /** Adds a song that was played; playing the same song twice in a row counts once. */
    public void add(MediaItem item) {
        if (item == null) return;
        if (!items.isEmpty() && items.get(items.size() - 1).key().equals(item.key())) return;
        items.add(item);
        while (items.size() > max) items.remove(0);
    }

    public void clear() {
        items.clear();
    }

    public void setAll(List<MediaItem> list) {
        items.clear();
        for (MediaItem m : list) add(m);
    }

    /** Oldest first. */
    public List<MediaItem> items() {
        return new ArrayList<MediaItem>(items);
    }
}
