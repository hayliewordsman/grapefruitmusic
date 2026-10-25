package com.grapefruitmusic.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FoldersTest {
    @Test
    public void normalizes() {
        assertEquals("/sdcard/Music", Folders.normalize(" /sdcard/Music// "));
        assertNull(Folders.normalize("Music"));
        assertNull(Folders.normalize("/"));
        assertNull(Folders.normalize(null));
    }

    @Test
    public void insideMatchesWholeFolderNamesOnly() {
        assertTrue(Folders.isInside("/sdcard/Music/a.mp3", "/sdcard/Music"));
        assertTrue(Folders.isInside("/sdcard/Music/Rock/a.mp3", "/sdcard/Music"));
        assertFalse(Folders.isInside("/sdcard/Music2/a.mp3", "/sdcard/Music"));
    }

    @Test
    public void likePatternEscapesWildcards() {
        assertEquals("/sdcard/My\\_Songs\\%/%", Folders.likeInside("/sdcard/My_Songs%"));
    }

    @Test
    public void namesAndRelativePaths() {
        assertEquals("/sdcard/Music", Folders.parent("/sdcard/Music/a.mp3"));
        assertEquals("Rock", Folders.name("/sdcard/Music/Rock"));
        assertEquals("Music/Rock", Folders.relative("/sdcard/Music/Rock", "/sdcard"));
        assertEquals("/storage/sdcard1/Music", Folders.relative("/storage/sdcard1/Music", "/sdcard"));
    }
}
