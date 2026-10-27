package com.grapefruitmusic.tags;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;

/** Tag reading and writing on real files (made with ffmpeg; see the resources folder). */
public class TagFileTest {
    /** Where edited copies are left, to look at with other tools if a test fails. */
    private static final File OUT = new File(System.getProperty("java.io.tmpdir"), "grapefruit-tag-test");

    private static byte[] resource(String name) throws IOException {
        InputStream in = TagFileTest.class.getResourceAsStream("/tags/" + name);
        if (in == null) throw new IOException("no test file " + name);
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static File copy(String name) throws IOException {
        OUT.mkdirs();
        File f = new File(OUT, name);
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(resource(name));
        } finally {
            out.close();
        }
        return f;
    }

    private static byte[] bytes(File f) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        try {
            byte[] b = new byte[(int) raf.length()];
            raf.readFully(b);
            return b;
        } finally {
            raf.close();
        }
    }

    private static Tags edits() throws IOException {
        Tags t = new Tags();
        t.title = "Halo (Live) — Beyoncé";
        t.artist = "坂本九";
        t.album = "New Album";
        t.albumArtist = "Various Artists";
        t.genre = "Pop";
        t.year = 2008;
        t.track = 7;
        t.trackTotal = 14;
        t.disc = 2;
        t.discTotal = 2;
        t.picture = resource("cover.jpg");
        return t;
    }

    private static void assertSame(Tags want, Tags got) {
        assertEquals(want.title, got.title);
        assertEquals(want.artist, got.artist);
        assertEquals(want.album, got.album);
        assertEquals(want.albumArtist, got.albumArtist);
        assertEquals(want.genre, got.genre);
        assertEquals(want.year, got.year);
        assertEquals(want.track, got.track);
        assertEquals(want.trackTotal, got.trackTotal);
        assertEquals(want.disc, got.disc);
        assertEquals(want.discTotal, got.discTotal);
        assertArrayEquals(want.picture, got.picture);
    }

    /** Writes, reads back, writes again (in place), then takes the picture away. */
    private static void roundTrip(File f) throws IOException {
        Tags t = edits();
        TagFile.write(f, t, true);
        assertSame(t, TagFile.read(f));
        long length = f.length();
        t.title = "Shorter";
        t.genre = "";
        TagFile.write(f, t, false);
        assertEquals("the second edit fits where the first was", length, f.length());
        Tags back = TagFile.read(f);
        assertEquals("Shorter", back.title);
        assertEquals("", back.genre);
        assertArrayEquals("the picture stays", t.picture, back.picture);
        t.picture = null;
        TagFile.write(f, t, true);
        assertNull(TagFile.read(f).picture);
        t.title = "Final Title";
        t.picture = resource("cover.jpg");
        TagFile.write(f, t, true);
    }

    private static byte[] tail(byte[] all, long from) {
        return Arrays.copyOfRange(all, (int) from, all.length);
    }

    @Test
    public void readsWhatFfmpegWrote() throws Exception {
        Tags t = TagFile.read(copy("tagged23.mp3"));
        assertEquals("Old Title", t.title);
        assertEquals("Old Artist", t.artist);
        assertEquals("Old Album", t.album);
        assertEquals("Old AA", t.albumArtist);
        assertEquals("Rock", t.genre);
        assertEquals(1999, t.year);
        assertEquals(3, t.track);
        assertEquals(12, t.trackTotal);
        assertEquals(1, t.disc);
        assertEquals(2, t.discTotal);
        Tags v4 = TagFile.read(copy("tagged24.mp3"));
        assertEquals("Ünïcode Tïtle", v4.title);
        assertArrayEquals(resource("cover.jpg"), v4.picture);
        Tags m4a = TagFile.read(copy("moovend.m4a"));
        assertEquals("Old Title", m4a.title);
        assertEquals("Old Album", m4a.album);
        assertEquals(1999, m4a.year);
        assertEquals(3, m4a.track);
        assertEquals(12, m4a.trackTotal);
        Tags flac = TagFile.read(copy("tagged.flac"));
        assertEquals("Old Title", flac.title);
        assertEquals(3, flac.track);
        assertArrayEquals(resource("cover.jpg"), flac.picture);
    }

    @Test
    public void mp3KeepsTheSoundAndOtherFrames() throws Exception {
        for (String name : new String[]{"tagged23.mp3", "tagged24.mp3", "bare.mp3"}) {
            File f = copy(name);
            byte[] before = bytes(f);
            long soundBefore = Id3.parse(f).length;
            roundTrip(f);
            Id3.Parsed after = Id3.parse(f);
            assertArrayEquals(name + ": the sound is untouched", tail(before, soundBefore), tail(bytes(f), after.length));
            assertEquals(name, name.equals("tagged24.mp3") ? 4 : 3, after.major);
            // ffmpeg writes its comment and ReplayGain as TXXX frames.
            String kept = "";
            for (Id3.Frame fr : after.frames) {
                if (fr.id.equals("TXXX")) kept += new String(fr.data, "ISO-8859-1");
            }
            if (name.equals("tagged23.mp3")) assertTrue("the comment stays", kept.contains("keep me"));
            if (name.equals("tagged24.mp3")) assertTrue("ReplayGain stays", kept.contains("-6.5 dB"));
        }
    }

    @Test
    public void flacKeepsTheSoundAndOtherComments() throws Exception {
        File f = copy("tagged.flac");
        byte[] before = bytes(f);
        long audio = audioStart(before);
        roundTrip(f);
        byte[] after = bytes(f);
        assertArrayEquals(tail(before, audio), tail(after, audioStart(after)));
        assertTrue("ReplayGain stays", new String(after, "UTF-8").contains("REPLAYGAIN_TRACK_GAIN=-3.2 dB"));
    }

    /** Where a FLAC file's audio frames start (after its metadata blocks). */
    private static long audioStart(byte[] b) {
        int at = 4;
        while (true) {
            boolean last = (b[at] & 0x80) != 0;
            int len = ((b[at + 1] & 0xff) << 16) | ((b[at + 2] & 0xff) << 8) | (b[at + 3] & 0xff);
            at += 4 + len;
            if (last) return at;
        }
    }

    @Test
    public void m4aMovesChunkOffsetsWithTheAudio() throws Exception {
        for (String name : new String[]{"moovend.m4a", "faststart.m4a"}) {
            File f = copy(name);
            byte[] before = bytes(f);
            long[] chunkBefore = firstChunk(before);
            byte[] audioBefore = Arrays.copyOfRange(before, (int) chunkBefore[0], (int) chunkBefore[0] + 64);
            roundTrip(f);
            byte[] after = bytes(f);
            long[] chunkAfter = firstChunk(after);
            assertArrayEquals(name + ": the first chunk offset still points at the first chunk", audioBefore,
                    Arrays.copyOfRange(after, (int) chunkAfter[0], (int) chunkAfter[0] + 64));
            if (name.equals("faststart.m4a")) assertTrue("the audio moved", chunkAfter[0] != chunkBefore[0]);
        }
    }

    /** The first stco offset in a file (moov/trak/mdia/minf/stbl/stco). */
    private static long[] firstChunk(byte[] b) throws IOException {
        int at = 0;
        while (at < b.length) {
            int size = Tags.be32(b, at);
            if (new String(b, at + 4, 4, "ISO-8859-1").equals("moov")) {
                Mp4.Box moov = Mp4.parse("moov", b, at + 8, size - 8);
                Mp4.Box stco = find(moov, "stco");
                return new long[]{Tags.be32(stco.data, 8) & 0xffffffffL};
            }
            at += size;
        }
        throw new AssertionError("no moov");
    }

    private static Mp4.Box find(Mp4.Box box, String type) {
        if (box.type.equals(type)) return box;
        if (box.children == null) return null;
        for (Mp4.Box c : box.children) {
            Mp4.Box found = find(c, type);
            if (found != null) return found;
        }
        return null;
    }

    @Test
    public void readsPictureSizes() throws Exception {
        assertArrayEquals(new int[]{24, 24}, Tags.sizeOf(resource("cover.jpg")));
        assertEquals("image/jpeg", Tags.mimeOf(resource("cover.jpg")));
        assertEquals(3, Tags.number("3/12"));
        assertEquals(12, Tags.total("3/12"));
        assertEquals(2019, Tags.year("2019-05-01"));
        assertEquals("Rock", Id3.genre("(17)"));
        assertEquals("Trip-Hop", Id3.genre("27"));
        assertEquals("My Genre", Id3.genre("(17)My Genre"));
    }
}
