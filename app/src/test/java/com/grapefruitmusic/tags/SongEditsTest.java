package com.grapefruitmusic.tags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.grapefruitmusic.model.MediaItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class SongEditsTest {
    @Test
    public void listsPickUpEditedSongs() {
        MediaItem edited = new MediaItem(MediaItem.TRACK, "src", "t9", "Old");
        edited.artist = "Old Artist";
        Tags t = new Tags();
        t.title = "New";
        t.artist = "";
        t.album = "Album";
        t.track = 4;
        SongEdits.record(edited, t);
        assertEquals("the edited item itself", "New", edited.title);

        MediaItem copy = new MediaItem(MediaItem.TRACK, "src", "t9", "Old");
        copy.artist = "Old Artist";
        MediaItem other = new MediaItem(MediaItem.TRACK, "src", "t10", "Other");
        MediaItem album = new MediaItem(MediaItem.ALBUM, "src", "t9", "An album with the same id");
        List<MediaItem> list = Arrays.asList(copy, other, album);
        assertTrue(SongEdits.apply(list));
        assertEquals("New", copy.title);
        assertNull("cleared", copy.artist);
        assertEquals("Album", copy.album);
        assertEquals(4, copy.trackNumber);
        assertEquals("Other", other.title);
        assertEquals("An album with the same id", album.title);
        assertFalse("nothing more to change", SongEdits.apply(list));
    }
}
