package com.grapefruitmusic.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LrcParserTest {
    @Test
    public void parsesTimestampsAndSortsLines() {
        Lyrics l = LrcParser.parse("[ar:Someone]\n[00:12.50]Second\n[00:01.00]First\n[01:02.345]Third", "test");
        assertTrue(l.synced);
        assertEquals(3, l.lines.size());
        assertEquals(1000, l.lines.get(0).timeMs);
        assertEquals("First", l.lines.get(0).text);
        assertEquals(12500, l.lines.get(1).timeMs);
        assertEquals(62345, l.lines.get(2).timeMs);
        assertEquals("test", l.credit);
    }

    @Test
    public void repeatedTimestampsShareOneLine() {
        Lyrics l = LrcParser.parse("[00:10.00][00:30.00]Chorus\n[00:20.00]Verse", null);
        assertEquals(3, l.lines.size());
        assertEquals("Chorus", l.lines.get(0).text);
        assertEquals("Verse", l.lines.get(1).text);
        assertEquals("Chorus", l.lines.get(2).text);
        assertEquals(30000, l.lines.get(2).timeMs);
    }

    @Test
    public void appliesOffsetAndStripsWordTimings() {
        Lyrics l = LrcParser.parse("[offset:+500]\n[00:02.00]<00:02.00>Hello <00:02.50>world", null);
        assertEquals(1500, l.lines.get(0).timeMs);
        assertEquals("Hello world", l.lines.get(0).text);
    }

    @Test
    public void handlesOneAndTwoDigitFractions() {
        Lyrics l = LrcParser.parse("[00:01.5]a\n[00:02.05]b\n[00:03]c", null);
        assertEquals(1500, l.lines.get(0).timeMs);
        assertEquals(2050, l.lines.get(1).timeMs);
        assertEquals(3000, l.lines.get(2).timeMs);
    }

    @Test
    public void plainTextIsNotSynced() {
        Lyrics l = LrcParser.parse("\nFirst line\n\nSecond line\n\n", "server");
        assertFalse(l.synced);
        assertEquals(3, l.lines.size());
        assertEquals("First line", l.lines.get(0).text);
        assertEquals("", l.lines.get(1).text);
    }

    @Test
    public void blankInputIsNull() {
        assertNull(LrcParser.parse("  \n ", null));
        assertNull(LrcParser.parse(null, null));
    }

    @Test
    public void indexAtFindsTheLineBeingSung() {
        Lyrics l = LrcParser.parse("[00:05.00]a\n[00:10.00]b\n[00:15.00]c", null);
        assertEquals(-1, l.indexAt(0));
        assertEquals(0, l.indexAt(5000));
        assertEquals(0, l.indexAt(9999));
        assertEquals(1, l.indexAt(10000));
        assertEquals(2, l.indexAt(600000));
    }

    @Test
    public void roundTripsThroughText() {
        Lyrics l = LrcParser.parse("[00:05.10]a\n[01:10.00]b", "x");
        Lyrics again = LrcParser.parse(l.toText(), "x");
        assertTrue(again.synced);
        assertEquals(5100, again.lines.get(0).timeMs);
        assertEquals(70000, again.lines.get(1).timeMs);
        assertEquals("[01:23.45]", LrcParser.timestamp(83450));
    }
}
