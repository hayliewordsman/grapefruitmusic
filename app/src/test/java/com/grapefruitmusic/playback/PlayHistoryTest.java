package com.grapefruitmusic.playback;

import static org.junit.Assert.assertEquals;

import com.grapefruitmusic.model.MediaItem;

import org.junit.Test;

public class PlayHistoryTest {
    private static MediaItem song(String id) {
        return new MediaItem(MediaItem.TRACK, "s", id, "Song " + id);
    }

    @Test
    public void keepsOrderSkipsRepeatsAndCaps() {
        PlayHistory h = new PlayHistory(3);
        h.add(song("1"));
        h.add(song("1"));
        h.add(song("2"));
        h.add(song("1"));
        assertEquals(3, h.items().size());
        h.add(song("3"));
        assertEquals(3, h.items().size());
        assertEquals("2", h.items().get(0).id);
        assertEquals("3", h.items().get(2).id);
        h.clear();
        assertEquals(0, h.items().size());
    }
}
