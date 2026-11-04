package com.grapefruitmusic.audiobooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ChaptersTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    // ---- builders ----

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.write(p, 0, p.length);
        return out.toByteArray();
    }

    private static byte[] be32(long v) {
        return new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }

    private static byte[] be64(long v) {
        return cat(be32(v >> 32), be32(v));
    }

    private static byte[] le32(int v) {
        return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] atom(String type, byte[]... body) {
        byte[] b = cat(body);
        return cat(be32(8 + b.length), ascii(type), b);
    }

    private File write(String name, byte[] data) throws IOException {
        File f = folder.newFile(name);
        FileOutputStream out = new FileOutputStream(f);
        out.write(data);
        out.close();
        return f;
    }

    private static final byte[] FTYP = atom("ftyp", ascii("M4B "), be32(0), ascii("M4B mp42isom"));

    // ---- tests ----

    @Test
    public void m4bWithNeroChapters() throws IOException {
        byte[] chpl = atom("chpl", new byte[]{1, 0, 0, 0}, be32(0), new byte[]{3},
                be64(0), new byte[]{(byte) utf8("Opening Credits").length}, utf8("Opening Credits"),
                be64(9_000_000_000L), new byte[]{(byte) utf8("Chapter One").length}, utf8("Chapter One"),
                be64(36_000_000_000L), new byte[]{(byte) utf8("Café at Night").length}, utf8("Café at Night"));
        byte[] file = cat(FTYP, atom("moov", atom("mvhd", new byte[100]), atom("udta", chpl)), atom("mdat", new byte[64]));
        List<Chapters.Chapter> c = Chapters.read(write("nero.m4b", file));
        assertEquals(3, c.size());
        assertEquals("Opening Credits", c.get(0).title);
        assertEquals(0, c.get(0).startMs);
        assertEquals(900_000, c.get(1).startMs);
        assertEquals("Café at Night", c.get(2).title);
        assertEquals(3_600_000, c.get(2).startMs);
    }

    /** moov with an audio track that points ("chap") to a text track of three titles in mdat. */
    private static byte[] quickTime(long mdatBodyAt, byte[][] titles) {
        byte[] audio = atom("trak",
                atom("tkhd", new byte[]{0, 0, 0, 7}, be32(0), be32(0), be32(1), new byte[68]),
                atom("tref", atom("chap", be32(2))),
                atom("mdia", atom("mdhd", new byte[]{0, 0, 0, 0}, be32(0), be32(0), be32(44100), be32(0), new byte[4]),
                        atom("hdlr", be32(0), be32(0), ascii("soun"), new byte[12])));
        byte[] sizes = new byte[0];
        for (byte[] t : titles) sizes = cat(sizes, be32(t.length));
        byte[] text = atom("trak",
                atom("tkhd", new byte[]{0, 0, 0, 7}, be32(0), be32(0), be32(2), new byte[68]),
                atom("mdia", atom("mdhd", new byte[]{0, 0, 0, 0}, be32(0), be32(0), be32(1000), be32(0), new byte[4]),
                        atom("hdlr", be32(0), be32(0), ascii("text"), new byte[12]),
                        atom("minf", atom("stbl",
                                atom("stts", be32(0), be32(2), be32(1), be32(65_000), be32(2), be32(120_500)),
                                atom("stsz", be32(0), be32(0), be32(titles.length), sizes),
                                atom("stsc", be32(0), be32(1), be32(1), be32(titles.length), be32(1)),
                                atom("stco", be32(0), be32(1), be32(mdatBodyAt))))));
        return atom("moov", atom("mvhd", new byte[100]), audio, text);
    }

    private static byte[] sample(String title, boolean utf16) {
        byte[] t = utf16 ? cat(new byte[]{(byte) 0xfe, (byte) 0xff}, title.getBytes(StandardCharsets.UTF_16BE))
                : utf8(title);
        return cat(new byte[]{(byte) (t.length >> 8), (byte) t.length}, t);
    }

    @Test
    public void m4bWithAQuickTimeChapterTrack() throws IOException {
        byte[][] titles = {sample("Prologue", false), sample("The Long Way — Home", true), sample("Epilogue", false)};
        byte[] moov = quickTime(0, titles);
        long mdatBody = FTYP.length + moov.length + 8;
        moov = quickTime(mdatBody, titles);
        byte[] file = cat(FTYP, moov, atom("mdat", cat(titles), new byte[256]));
        List<Chapters.Chapter> c = Chapters.read(write("apple.m4b", file));
        assertEquals(3, c.size());
        assertEquals("Prologue", c.get(0).title);
        assertEquals(0, c.get(0).startMs);
        assertEquals("The Long Way — Home", c.get(1).title);
        assertEquals(65_000, c.get(1).startMs);
        assertEquals("Epilogue", c.get(2).title);
        assertEquals(185_500, c.get(2).startMs);
    }

    private static byte[] syncsafe(int v) {
        return new byte[]{(byte) ((v >> 21) & 0x7f), (byte) ((v >> 14) & 0x7f), (byte) ((v >> 7) & 0x7f), (byte) (v & 0x7f)};
    }

    private static byte[] id3Frame(int major, String id, byte[] body) {
        return cat(ascii(id), major == 4 ? syncsafe(body.length) : be32(body.length), new byte[2], body);
    }

    private static byte[] chap(int major, String element, long start, long end, String title, int encoding) {
        byte[] text = encoding == 3 ? utf8(title) : encoding == 1
                ? cat(new byte[]{(byte) 0xff, (byte) 0xfe}, title.getBytes(StandardCharsets.UTF_16LE))
                : title.getBytes(StandardCharsets.ISO_8859_1);
        byte[] tit2 = id3Frame(major, "TIT2", cat(new byte[]{(byte) encoding}, text));
        return id3Frame(major, "CHAP", cat(ascii(element), new byte[1], be32(start), be32(end), be32(0xffffffffL),
                be32(0xffffffffL), tit2));
    }

    private static byte[] id3(int major, byte[]... frames) {
        byte[] body = cat(cat(frames), new byte[32]);
        return cat(ascii("ID3"), new byte[]{(byte) major, 0, 0}, syncsafe(body.length), body,
                new byte[]{(byte) 0xff, (byte) 0xfb, 0x50, 0}, new byte[400]);
    }

    @Test
    public void mp3WithId3Chapters() throws IOException {
        for (int major : new int[]{3, 4}) {
            byte[] file = id3(major,
                    id3Frame(major, "TIT2", cat(new byte[]{3}, utf8("A Book"))),
                    chap(major, "ch1", 420_000, 900_000, "Two", 1),
                    chap(major, "ch0", 0, 420_000, "One", 0),
                    chap(major, "ch2", 900_000, 1_300_000, "Three ☃", 3));
            List<Chapters.Chapter> c = Chapters.read(write("book" + major + ".mp3", file));
            assertEquals("v2." + major, 3, c.size());
            assertEquals("One", c.get(0).title);
            assertEquals("Two", c.get(1).title);
            assertEquals(420_000, c.get(1).startMs);
            assertEquals("Three ☃", c.get(2).title);
        }
    }

    private static byte[] vorbisComments(String... comments) {
        byte[] out = cat(le32(5), ascii("tests"), le32(comments.length));
        for (String c : comments) out = cat(out, le32(utf8(c).length), utf8(c));
        return out;
    }

    private static final String[] TAGS = {"TITLE=A Book", "CHAPTER001=00:00:00.000", "CHAPTER001NAME=Start",
            "CHAPTER002=00:12:30.5", "CHAPTER002NAME=Middle", "chapter003=01:02:03.250", "CHAPTER003NAME=End"};

    @Test
    public void flacWithVorbisChapters() throws IOException {
        byte[] comments = vorbisComments(TAGS);
        byte[] file = cat(ascii("fLaC"), new byte[]{0, 0, 0, 34}, new byte[34],
                new byte[]{(byte) 0x84, (byte) (comments.length >> 16), (byte) (comments.length >> 8), (byte) comments.length},
                comments, new byte[100]);
        List<Chapters.Chapter> c = Chapters.read(write("book.flac", file));
        assertEquals(3, c.size());
        assertEquals("Middle", c.get(1).title);
        assertEquals(750_500, c.get(1).startMs);
        assertEquals(3_723_250, c.get(2).startMs);
    }

    private static byte[] oggPage(int sequence, byte[] packet) {
        ByteArrayOutputStream table = new ByteArrayOutputStream();
        int left = packet.length;
        while (left >= 255) {
            table.write(255);
            left -= 255;
        }
        table.write(left);
        byte[] t = table.toByteArray();
        return cat(ascii("OggS"), new byte[]{0, (byte) (sequence == 0 ? 2 : 0)}, new byte[8], le32(1234),
                le32(sequence), new byte[4], new byte[]{(byte) t.length}, t, packet);
    }

    @Test
    public void oggWithVorbisChapters() throws IOException {
        byte[] ident = cat(new byte[]{1}, ascii("vorbis"), new byte[23]);
        // A long comment header (several 255-byte segments) to check packets are joined.
        String[] tags = new String[TAGS.length + 1];
        System.arraycopy(TAGS, 0, tags, 0, TAGS.length);
        tags[TAGS.length] = "DESCRIPTION=" + new String(new char[700]).replace('\0', 'x');
        byte[] comment = cat(new byte[]{3}, ascii("vorbis"), vorbisComments(tags), new byte[]{1});
        byte[] file = cat(oggPage(0, ident), oggPage(1, comment), new byte[100]);
        List<Chapters.Chapter> c = Chapters.read(write("book.ogg", file));
        assertEquals(3, c.size());
        assertEquals("Start", c.get(0).title);
        assertEquals("End", c.get(2).title);
    }

    @Test
    public void filesWithoutChapters() throws IOException {
        assertTrue(Chapters.read(write("plain.mp3", cat(new byte[]{(byte) 0xff, (byte) 0xfb, 0x50, 0}, new byte[2000]))).isEmpty());
        assertTrue(Chapters.read(write("short.m4a", cat(FTYP, atom("moov", atom("mvhd", new byte[100]))))).isEmpty());
        assertTrue(Chapters.read(write("tiny.bin", new byte[3])).isEmpty());
        assertTrue(Chapters.read(new File(folder.getRoot(), "missing.m4b")).isEmpty());
        // A broken atom size: no chapters, no exception.
        assertTrue(Chapters.read(write("broken.m4b", cat(FTYP, be32(9999), ascii("moov"), new byte[20]))).isEmpty());
    }

    @Test
    public void namesFromFiles() {
        assertEquals("The Road", Chapters.titleFromFileName("03 - The Road.mp3"));
        assertEquals("Chapter 12", Chapters.titleFromFileName("Chapter 12.mp3"));
        assertEquals("Prologue", Chapters.titleFromFileName("Track 01 Prologue.m4a"));
        assertEquals("01", Chapters.titleFromFileName("01.mp3"));
        assertEquals("Part One", Chapters.titleFromFileName("02_Part_One.ogg"));
    }
}
