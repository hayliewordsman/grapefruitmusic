package com.grapefruitmusic.audiobooks;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The chapters marked inside an audio file, found by its contents (not its name), for audiobooks in
 * any DRM-free format:
 * <ul>
 * <li>M4B, M4A and MP4 (Libro.fm's M4B, Apple's, and most tools'): a QuickTime chapter track (text
 * samples a track points to with "chap"), or Nero's chapter list ("chpl"). Read by seeking, so a
 * long book's sample tables are never read into memory.</li>
 * <li>MP3: ID3v2.3 and v2.4 CHAP frames, each with its title (TIT2).</li>
 * <li>FLAC, Ogg Vorbis and Opus: CHAPTERnnn=HH:MM:SS.mmm and CHAPTERnnnNAME= comments.</li>
 * </ul>
 * A book in several files (Libro.fm's MP3 download: a file a chapter) has no marks needed: each file
 * is a chapter (AudiobookStore). Pure Java (unit tested).
 */
public final class Chapters {
    /** One chapter: its title and where it starts in the file, in ms. */
    public static final class Chapter {
        public final String title;
        public final long startMs;

        public Chapter(String title, long startMs) {
            this.title = title;
            this.startMs = startMs;
        }
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");
    private static final Charset UTF16 = Charset.forName("UTF-16");
    private static final Charset UTF16BE = Charset.forName("UTF-16BE");
    /** Larger than any real chapter list or comment header; protects against odd files. */
    private static final int MAX_READ = 8 * 1024 * 1024;

    private Chapters() {
    }

    /** The file's chapters, in order; empty if it marks none, or can't be read. */
    public static List<Chapter> read(File file) {
        RandomAccessFile f = null;
        try {
            f = new RandomAccessFile(file, "r");
            byte[] head = new byte[12];
            int n = f.read(head);
            if (n < 12) return new ArrayList<Chapter>();
            List<Chapter> found;
            if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') found = id3(f);
            else if (head[0] == 'f' && head[1] == 'L' && head[2] == 'a' && head[3] == 'C') found = flac(f);
            else if (head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S') found = ogg(f);
            else if (head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p'
                    || isAtom(head, "moov") || isAtom(head, "mdat") || isAtom(head, "wide")) found = mp4(f);
            else found = new ArrayList<Chapter>();
            return tidy(found);
        } catch (IOException | RuntimeException e) {
            return new ArrayList<Chapter>();
        } finally {
            if (f != null) {
                try {
                    f.close();
                } catch (IOException ignored) {
                    // Read already.
                }
            }
        }
    }

    private static boolean isAtom(byte[] head, String type) {
        return head[4] == type.charAt(0) && head[5] == type.charAt(1) && head[6] == type.charAt(2)
                && head[7] == type.charAt(3);
    }

    /** In order of start, one per start time, with a name for any untitled ones. */
    static List<Chapter> tidy(List<Chapter> chapters) {
        Collections.sort(chapters, new Comparator<Chapter>() {
            @Override
            public int compare(Chapter a, Chapter b) {
                return Long.valueOf(a.startMs).compareTo(b.startMs);
            }
        });
        List<Chapter> out = new ArrayList<Chapter>();
        long last = -1;
        for (Chapter c : chapters) {
            if (c.startMs < 0 || c.startMs == last) continue;
            last = c.startMs;
            String title = c.title == null ? "" : c.title.replace('\u0000', ' ').trim();
            out.add(new Chapter(title.length() > 0 ? title : "Chapter " + (out.size() + 1), c.startMs));
        }
        return out;
    }

    // ---- MP4 (M4B, M4A) ----

    /** Where an atom's content starts and ends, and its type. */
    private static final class Atom {
        final String type;
        final long body;
        final long end;

        Atom(String type, long body, long end) {
            this.type = type;
            this.body = body;
            this.end = end;
        }
    }

    /** A trak, as far as chapters need it. */
    private static final class Track {
        int id;
        String handler = "";
        long timescale = 1000;
        final List<Integer> chapterTracks = new ArrayList<Integer>();
        Atom stts;
        Atom stsz;
        Atom stsc;
        Atom stco;
        Atom co64;
    }

    private static List<Atom> children(RandomAccessFile f, long start, long end) throws IOException {
        List<Atom> out = new ArrayList<Atom>();
        long at = start;
        while (at + 8 <= end) {
            f.seek(at);
            long size = f.readInt() & 0xffffffffL;
            byte[] t = new byte[4];
            f.readFully(t);
            long body = at + 8;
            if (size == 1) {
                size = f.readLong();
                body = at + 16;
            } else if (size == 0) {
                size = end - at;
            }
            if (size < body - at || at + size > end) break;
            out.add(new Atom(new String(t, LATIN1), body, at + size));
            at += size;
        }
        return out;
    }

    private static Atom child(List<Atom> atoms, String type) {
        for (Atom a : atoms) if (a.type.equals(type)) return a;
        return null;
    }

    private static List<Chapter> mp4(RandomAccessFile f) throws IOException {
        Atom moov = child(children(f, 0, f.length()), "moov");
        if (moov == null) return new ArrayList<Chapter>();
        List<Atom> inMoov = children(f, moov.body, moov.end);
        List<Track> tracks = new ArrayList<Track>();
        for (Atom a : inMoov) if (a.type.equals("trak")) tracks.add(track(f, a));
        // A QuickTime chapter track: the one an audio track points to, or else any text track.
        Track chapters = null;
        for (Track t : tracks) {
            for (int id : t.chapterTracks) {
                for (Track c : tracks) if (c.id == id) chapters = c;
            }
        }
        if (chapters == null) {
            for (Track t : tracks) if (t.handler.equals("text") && t.stts != null) chapters = t;
        }
        if (chapters != null) {
            List<Chapter> found = textTrack(f, chapters);
            if (!found.isEmpty()) return found;
        }
        // Nero's list, in moov/udta.
        Atom udta = child(inMoov, "udta");
        if (udta != null) {
            Atom chpl = child(children(f, udta.body, udta.end), "chpl");
            if (chpl != null) return chpl(f, chpl);
        }
        return new ArrayList<Chapter>();
    }

    private static Track track(RandomAccessFile f, Atom trak) throws IOException {
        Track t = new Track();
        List<Atom> in = children(f, trak.body, trak.end);
        Atom tkhd = child(in, "tkhd");
        if (tkhd != null) {
            f.seek(tkhd.body);
            int version = f.readUnsignedByte();
            f.skipBytes(3 + (version == 1 ? 16 : 8));
            t.id = f.readInt();
        }
        Atom tref = child(in, "tref");
        if (tref != null) {
            Atom chap = child(children(f, tref.body, tref.end), "chap");
            if (chap != null) {
                f.seek(chap.body);
                for (long at = chap.body; at + 4 <= chap.end; at += 4) t.chapterTracks.add(f.readInt());
            }
        }
        Atom mdia = child(in, "mdia");
        if (mdia == null) return t;
        List<Atom> inMdia = children(f, mdia.body, mdia.end);
        Atom mdhd = child(inMdia, "mdhd");
        if (mdhd != null) {
            f.seek(mdhd.body);
            int version = f.readUnsignedByte();
            f.skipBytes(3 + (version == 1 ? 16 : 8));
            t.timescale = f.readInt() & 0xffffffffL;
        }
        Atom hdlr = child(inMdia, "hdlr");
        if (hdlr != null) {
            f.seek(hdlr.body + 8);
            byte[] h = new byte[4];
            f.readFully(h);
            t.handler = new String(h, LATIN1);
        }
        Atom minf = child(inMdia, "minf");
        if (minf == null) return t;
        Atom stbl = child(children(f, minf.body, minf.end), "stbl");
        if (stbl == null) return t;
        List<Atom> inStbl = children(f, stbl.body, stbl.end);
        t.stts = child(inStbl, "stts");
        t.stsz = child(inStbl, "stsz");
        t.stsc = child(inStbl, "stsc");
        t.stco = child(inStbl, "stco");
        t.co64 = child(inStbl, "co64");
        return t;
    }

    /** Each text sample is a chapter: its title (16-bit length, then the text) and its time. */
    private static List<Chapter> textTrack(RandomAccessFile f, Track t) throws IOException {
        List<Chapter> out = new ArrayList<Chapter>();
        if (t.stts == null || t.stsz == null || t.stsc == null || (t.stco == null && t.co64 == null)) return out;
        if (t.timescale <= 0) return out;
        // Sample start times, from the durations.
        f.seek(t.stts.body + 4);
        int entries = f.readInt();
        List<Long> starts = new ArrayList<Long>();
        long time = 0;
        for (int i = 0; i < entries && starts.size() < 10000; i++) {
            int count = f.readInt();
            long delta = f.readInt() & 0xffffffffL;
            for (int k = 0; k < count && starts.size() < 10000; k++) {
                starts.add(time);
                time += delta;
            }
        }
        int samples = starts.size();
        // Sizes.
        f.seek(t.stsz.body + 4);
        int fixed = f.readInt();
        int count = f.readInt();
        int[] sizes = new int[Math.min(samples, count > 0 ? count : samples)];
        for (int i = 0; i < sizes.length; i++) sizes[i] = fixed != 0 ? fixed : f.readInt();
        // Chunks: where each starts, and how many samples each holds.
        Atom offsets = t.co64 != null ? t.co64 : t.stco;
        f.seek(offsets.body + 4);
        int chunks = f.readInt();
        long[] chunkAt = new long[Math.min(chunks, 10000)];
        for (int i = 0; i < chunkAt.length; i++) chunkAt[i] = t.co64 != null ? f.readLong() : f.readInt() & 0xffffffffL;
        f.seek(t.stsc.body + 4);
        int runs = f.readInt();
        int[] firstChunk = new int[Math.min(runs, 10000)];
        int[] perChunk = new int[firstChunk.length];
        for (int i = 0; i < firstChunk.length; i++) {
            firstChunk[i] = f.readInt();
            perChunk[i] = f.readInt();
            f.readInt(); // sample description
        }
        int sample = 0;
        for (int chunk = 0; chunk < chunkAt.length && sample < sizes.length; chunk++) {
            int inChunk = 0;
            for (int r = 0; r < firstChunk.length; r++) if (firstChunk[r] - 1 <= chunk) inChunk = perChunk[r];
            long at = chunkAt[chunk];
            for (int k = 0; k < inChunk && sample < sizes.length; k++, sample++) {
                if (sizes[sample] >= 2 && sizes[sample] < 65536 + 2) {
                    f.seek(at);
                    int length = f.readUnsignedShort();
                    length = Math.min(length, sizes[sample] - 2);
                    byte[] text = new byte[Math.max(0, length)];
                    f.readFully(text);
                    out.add(new Chapter(text(text), starts.get(sample) * 1000 / t.timescale));
                }
                at += sizes[sample];
            }
        }
        return out;
    }

    /** UTF-16 if it starts with a byte order mark, else UTF-8. */
    private static String text(byte[] b) {
        if (b.length >= 2 && ((b[0] & 0xff) == 0xfe && (b[1] & 0xff) == 0xff
                || (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xfe)) {
            return new String(b, UTF16);
        }
        return new String(b, UTF8);
    }

    /** Nero: version, flags, (a reserved word in version 1), a count, then start (100 ns) and title. */
    private static List<Chapter> chpl(RandomAccessFile f, Atom chpl) throws IOException {
        List<Chapter> out = new ArrayList<Chapter>();
        f.seek(chpl.body);
        int version = f.readUnsignedByte();
        f.skipBytes(3);
        if (version == 1) f.skipBytes(4);
        int count = f.readUnsignedByte();
        for (int i = 0; i < count && f.getFilePointer() + 9 <= chpl.end; i++) {
            long start = f.readLong();
            int length = f.readUnsignedByte();
            byte[] title = new byte[length];
            f.readFully(title);
            out.add(new Chapter(new String(title, UTF8), start / 10000));
        }
        return out;
    }

    // ---- MP3 (ID3v2 CHAP) ----

    private static List<Chapter> id3(RandomAccessFile f) throws IOException {
        f.seek(3);
        int major = f.readUnsignedByte();
        f.readUnsignedByte();
        int flags = f.readUnsignedByte();
        int size = syncsafe(f.readInt());
        if (major < 3 || major > 4 || size <= 0 || size > MAX_READ) return new ArrayList<Chapter>();
        byte[] tag = new byte[size];
        f.readFully(tag);
        if ((flags & 0x80) != 0 && major == 3) tag = unsync(tag);
        int at = 0;
        if ((flags & 0x40) != 0) {
            // An extended header first.
            int ext = major == 4 ? syncsafe(int32(tag, 0)) : int32(tag, 0) + 4;
            at = Math.max(0, ext);
        }
        List<Chapter> out = new ArrayList<Chapter>();
        frames(tag, at, tag.length, major, out, null);
        return out;
    }

    /** Reads frames from tag[from, to): CHAP frames into out, or (inside one) its title into title[0]. */
    private static void frames(byte[] tag, int from, int to, int major, List<Chapter> out, String[] title) {
        int at = from;
        while (at + 10 <= to) {
            if (tag[at] == 0) break; // padding
            String id = new String(tag, at, 4, LATIN1);
            int size = major == 4 ? syncsafe(int32(tag, at + 4)) : int32(tag, at + 4);
            int body = at + 10;
            if (size < 0 || body + size > to) break;
            if (id.equals("CHAP") && out != null) {
                int p = body;
                while (p < body + size && tag[p] != 0) p++; // element id
                p++;
                if (p + 16 <= body + size) {
                    long start = int32(tag, p) & 0xffffffffL;
                    String[] t = new String[1];
                    frames(tag, p + 16, body + size, major, null, t);
                    out.add(new Chapter(t[0], start));
                }
            } else if (id.equals("TIT2") && title != null && size > 1) {
                title[0] = id3Text(tag, body, size);
            }
            at = body + size;
        }
    }

    private static String id3Text(byte[] b, int at, int size) {
        int encoding = b[at];
        int start = at + 1;
        int length = size - 1;
        Charset cs = encoding == 1 ? UTF16 : encoding == 2 ? UTF16BE : encoding == 3 ? UTF8 : LATIN1;
        String s = new String(b, start, length, cs);
        int nul = s.indexOf('\u0000');
        return nul >= 0 ? s.substring(0, nul) : s;
    }

    private static int int32(byte[] b, int at) {
        return (b[at] & 0xff) << 24 | (b[at + 1] & 0xff) << 16 | (b[at + 2] & 0xff) << 8 | (b[at + 3] & 0xff);
    }

    private static int syncsafe(int v) {
        return (v & 0x7f) | ((v >> 8) & 0x7f) << 7 | ((v >> 16) & 0x7f) << 14 | ((v >> 24) & 0x7f) << 21;
    }

    /** ID3's unsynchronisation undone: FF 00 back to FF. */
    private static byte[] unsync(byte[] b) {
        byte[] out = new byte[b.length];
        int o = 0;
        for (int i = 0; i < b.length; i++) {
            out[o++] = b[i];
            if ((b[i] & 0xff) == 0xff && i + 1 < b.length && b[i + 1] == 0) i++;
        }
        byte[] trimmed = new byte[o];
        System.arraycopy(out, 0, trimmed, 0, o);
        return trimmed;
    }

    // ---- FLAC, Ogg (Vorbis comments) ----

    private static List<Chapter> flac(RandomAccessFile f) throws IOException {
        f.seek(4);
        while (true) {
            int header = f.readUnsignedByte();
            int length = f.readUnsignedByte() << 16 | f.readUnsignedByte() << 8 | f.readUnsignedByte();
            if ((header & 0x7f) == 4) {
                if (length > MAX_READ) break;
                byte[] block = new byte[length];
                f.readFully(block);
                return comments(block, 0);
            }
            if ((header & 0x80) != 0) break;
            f.seek(f.getFilePointer() + length);
        }
        return new ArrayList<Chapter>();
    }

    /** The second Ogg packet: Vorbis's or Opus's comment header. */
    private static List<Chapter> ogg(RandomAccessFile f) throws IOException {
        f.seek(0);
        java.io.ByteArrayOutputStream packet = new java.io.ByteArrayOutputStream();
        int packets = 0;
        long at = 0;
        while (at + 27 <= f.length() && packet.size() < MAX_READ) {
            f.seek(at);
            byte[] h = new byte[27];
            f.readFully(h);
            if (h[0] != 'O' || h[1] != 'g' || h[2] != 'g' || h[3] != 'S') break;
            int segments = h[26] & 0xff;
            byte[] table = new byte[segments];
            f.readFully(table);
            for (int s = 0; s < segments; s++) {
                int len = table[s] & 0xff;
                byte[] data = new byte[len];
                f.readFully(data);
                if (packets == 1) packet.write(data, 0, len);
                if (len < 255) {
                    // A packet ends here.
                    if (packets == 1) {
                        byte[] p = packet.toByteArray();
                        if (p.length > 7 && p[0] == 3 && new String(p, 1, 6, LATIN1).equals("vorbis")) {
                            return comments(p, 7);
                        }
                        if (p.length > 8 && new String(p, 0, 8, LATIN1).equals("OpusTags")) return comments(p, 8);
                        return new ArrayList<Chapter>();
                    }
                    packets++;
                }
            }
            at = f.getFilePointer();
        }
        return new ArrayList<Chapter>();
    }

    private static final Pattern CHAPTER = Pattern.compile("CHAPTER(\\d+)(NAME)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME = Pattern.compile("(\\d+):(\\d{1,2}):(\\d{1,2})(?:[.,](\\d+))?");

    /** Vorbis comments (little-endian lengths) from at: CHAPTERnnn and CHAPTERnnnNAME. */
    static List<Chapter> comments(byte[] b, int at) {
        Map<Integer, Long> starts = new TreeMap<Integer, Long>();
        Map<Integer, String> names = new TreeMap<Integer, String>();
        int vendor = le32(b, at);
        int p = at + 4 + vendor;
        if (vendor < 0 || p + 4 > b.length) return new ArrayList<Chapter>();
        int count = le32(b, p);
        p += 4;
        for (int i = 0; i < count && p + 4 <= b.length; i++) {
            int len = le32(b, p);
            p += 4;
            if (len < 0 || p + len > b.length) break;
            String c = new String(b, p, len, UTF8);
            p += len;
            int eq = c.indexOf('=');
            if (eq <= 0) continue;
            Matcher m = CHAPTER.matcher(c.substring(0, eq));
            if (!m.matches()) continue;
            int n = Integer.parseInt(m.group(1));
            String value = c.substring(eq + 1);
            if (m.group(2) != null) {
                names.put(n, value);
            } else {
                Matcher t = TIME.matcher(value.trim());
                if (t.matches()) {
                    String frac = t.group(4) == null ? "0" : (t.group(4) + "000").substring(0, 3);
                    long ms = ((Long.parseLong(t.group(1)) * 60 + Long.parseLong(t.group(2))) * 60
                            + Long.parseLong(t.group(3))) * 1000 + Long.parseLong(frac);
                    starts.put(n, ms);
                }
            }
        }
        List<Chapter> out = new ArrayList<Chapter>();
        for (Map.Entry<Integer, Long> e : starts.entrySet()) out.add(new Chapter(names.get(e.getKey()), e.getValue()));
        return out;
    }

    private static int le32(byte[] b, int at) {
        if (at + 4 > b.length) return -1;
        return (b[at] & 0xff) | (b[at + 1] & 0xff) << 8 | (b[at + 2] & 0xff) << 16 | (b[at + 3] & 0xff) << 24;
    }

    /** "Chapter 3", "03 - The Road", "Part 1 - 04" from a file name: what to call a file that's a chapter. */
    public static String titleFromFileName(String name) {
        String s = name;
        int dot = s.lastIndexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        s = s.replace('_', ' ').trim();
        // "01 - Title" / "01. Title" / "Track 01 Title": the number is the order, not the title.
        String stripped = s.replaceFirst("^(?i:track\\s*)?\\d{1,4}\\s*[-.):]?\\s+", "");
        return stripped.length() > 0 ? stripped : s;
    }
}
