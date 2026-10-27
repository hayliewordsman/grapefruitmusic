package com.grapefruitmusic.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListsTest {
    private static List<String> list(String... items) {
        return new ArrayList<String>(Arrays.asList(items));
    }

    @Test
    public void movesDownAndUp() {
        List<String> l = list("a", "b", "c", "d");
        Lists.move(l, 0, 2);
        assertEquals(list("b", "c", "a", "d"), l);
        Lists.move(l, 3, 0);
        assertEquals(list("d", "b", "c", "a"), l);
    }

    @Test
    public void ignoresOutOfRange() {
        List<String> l = list("a", "b");
        Lists.move(l, 0, 5);
        Lists.move(l, -1, 0);
        Lists.move(l, 1, 1);
        assertEquals(list("a", "b"), l);
    }

    private static com.grapefruitmusic.model.MediaItem item(int kind, String title, long added) {
        com.grapefruitmusic.model.MediaItem m = new com.grapefruitmusic.model.MediaItem(kind, "s", title, title);
        m.added = added;
        return m;
    }

    @Test
    public void recentlyAddedMixesInPlaylistsByDate() {
        int album = com.grapefruitmusic.model.MediaItem.ALBUM;
        int playlist = com.grapefruitmusic.model.MediaItem.PLAYLIST;
        java.util.List<com.grapefruitmusic.model.MediaItem> albums = java.util.Arrays.asList(
                item(album, "New Album", 5000), item(album, "Older Album", 3000), item(album, "Oldest Album", 1000));
        java.util.List<com.grapefruitmusic.model.MediaItem> playlists = java.util.Arrays.asList(
                item(playlist, "Gym", 4000), item(playlist, "Ancient", 10), item(playlist, "Undated", 0));
        java.util.List<String> titles = new java.util.ArrayList<String>();
        for (com.grapefruitmusic.model.MediaItem m : Lists.recentlyAdded(albums, playlists, 4)) titles.add(m.title);
        assertEquals(java.util.Arrays.asList("New Album", "Gym", "Older Album", "Oldest Album"), titles);
    }

    @Test
    public void recentlyAddedKeepsUndatedAlbumsInTheirOrder() {
        int album = com.grapefruitmusic.model.MediaItem.ALBUM;
        java.util.List<com.grapefruitmusic.model.MediaItem> albums = java.util.Arrays.asList(
                item(album, "A", 0), item(album, "B", 0));
        java.util.List<com.grapefruitmusic.model.MediaItem> playlists = java.util.Collections.singletonList(
                item(com.grapefruitmusic.model.MediaItem.PLAYLIST, "Gym", 4000));
        java.util.List<String> titles = new java.util.ArrayList<String>();
        for (com.grapefruitmusic.model.MediaItem m : Lists.recentlyAdded(albums, playlists, 20)) titles.add(m.title);
        assertEquals(java.util.Arrays.asList("Gym", "A", "B"), titles);
        assertEquals(2, Lists.recentlyAdded(albums, null, 20).size());
    }

    @Test
    public void readsServerDates() {
        assertEquals(1714566896789L, Dates.parseIso("2024-05-01T12:34:56.789Z"));
        // Jellyfin's seven-digit fractions.
        assertEquals(1714566896123L, Dates.parseIso("2024-05-01T12:34:56.1234567Z"));
        assertEquals(1714566896000L - 2 * 3600 * 1000L, Dates.parseIso("2024-05-01T12:34:56+02:00"));
        assertEquals(1714566896000L, Dates.parseIso("2024-05-01T12:34:56"));
        assertEquals(0, Dates.parseIso("yesterday"));
        assertEquals(0, Dates.parseIso(null));
    }
}
