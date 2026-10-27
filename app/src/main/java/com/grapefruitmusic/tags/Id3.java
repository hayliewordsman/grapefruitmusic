package com.grapefruitmusic.tags;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MP3 tags: ID3v2 (2.2, 2.3 and 2.4 read; 2.3 or 2.4 written) at the start of the file, and the
 * old ID3v1 at the end kept in step. Frames Grapefruit doesn't edit (lyrics, comments, ReplayGain)
 * are kept.
 */
final class Id3 {
    /** Padding left in a new tag, so later edits fit without rewriting the file. */
    private static final int PADDING = 2048;

    private static final String[] TEXT = {"TIT2", "TPE1", "TALB", "TPE2", "TCON", "TYER", "TDRC", "TRCK", "TPOS"};

    /** ID3v2.2's three-letter frames Grapefruit keeps, and their 2.3 names. */
    private static final String[][] V22 = {
            {"TT2", "TIT2"}, {"TP1", "TPE1"}, {"TAL", "TALB"}, {"TP2", "TPE2"}, {"TCO", "TCON"}, {"TYE", "TYER"},
            {"TRK", "TRCK"}, {"TPA", "TPOS"}, {"COM", "COMM"}, {"ULT", "USLT"}, {"TCM", "TCOM"}, {"PIC", "APIC"}};

    /** The ID3v1 genres a "(17)" refers to. */
    private static final String[] GENRES = {"Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge",
            "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno",
            "Industrial", "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient",
            "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game",
            "Sound Clip", "Gospel", "Noise", "Alternative Rock", "Bass", "Soul", "Punk", "Space", "Meditative",
            "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave", "Techno-Industrial", "Electronic",
            "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap",
            "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave", "Psychedelic", "Rave", "Showtunes",
            "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll",
            "Hard Rock", "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion", "Bebop", "Latin", "Revival",
            "Celtic", "Bluegrass", "Avantgarde", "Gothic Rock", "Progressive Rock", "Psychedelic Rock",
            "Symphonic Rock", "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour", "Speech",
            "Chanson", "Opera", "Chamber Music", "Sonata", "Symphony", "Booty Bass", "Primus", "Porn Groove", "Satire",
            "Slow Jam", "Club", "Tango", "Samba", "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul", "Freestyle",
            "Duet", "Punk Rock", "Drum Solo", "A Cappella", "Euro-House", "Dance Hall", "Goa", "Drum & Bass",
            "Club-House", "Hardcore", "Terror", "Indie", "BritPop", "Afro-Punk", "Polsk Punk", "Beat",
            "Christian Gangsta Rap", "Heavy Metal", "Black Metal", "Crossover", "Contemporary Christian",
            "Christian Rock", "Merengue", "Salsa", "Thrash Metal", "Anime", "JPop", "Synthpop"};

    static final class Frame {
        final String id;
        final byte[] data;

        Frame(String id, byte[] data) {
            this.id = id;
            this.data = data;
        }
    }

    /** The tag at the start of a file. */
    static final class Parsed {
        /** 2, 3 or 4; 0 when there's no tag. */
        int major;
        /** Bytes the tag (or tags) take at the start of the file. */
        long length;
        final List<Frame> frames = new ArrayList<Frame>();
    }

    private Id3() {
    }

    static boolean starts(byte[] head) {
        return head.length >= 3 && head[0] == 'I' && head[1] == 'D' && head[2] == '3';
    }

    static Parsed parse(File f) throws IOException {
        Parsed p = new Parsed();
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        try {
            long at = 0;
            // Some files have a second tag straight after the first: both go, the first one read.
            while (at + 10 <= raf.length()) {
                byte[] h = new byte[10];
                raf.seek(at);
                raf.readFully(h);
                if (!starts(h) || h[3] < 2 || h[3] > 4) break;
                int size = syncsafe(h, 6);
                long total = 10L + size + ((h[5] & 0x10) != 0 ? 10 : 0);
                if (at + total > raf.length()) throw new IOException("The song's tags are damaged");
                if (p.major == 0) {
                    byte[] body = new byte[size];
                    raf.readFully(body);
                    p.major = h[3];
                    frames(p, body, h[3], h[5]);
                }
                at += total;
            }
            p.length = at;
        } finally {
            raf.close();
        }
        return p;
    }

    private static void frames(Parsed p, byte[] body, int major, int flags) {
        if ((flags & 0x80) != 0 && major < 4) body = deunsync(body, 0, body.length);
        int at = 0;
        if ((flags & 0x40) != 0 && major >= 3) {
            // Extended header: its size leaves itself out in 2.3, counts itself in 2.4.
            if (body.length < 4) return;
            at = major == 3 ? 4 + Tags.be32(body, 0) : syncsafe(body, 0);
        }
        int head = major == 2 ? 6 : 10;
        while (at + head <= body.length) {
            if (body[at] == 0) break; // padding
            String id;
            int size;
            int frameFlags = 0;
            if (major == 2) {
                id = ascii(body, at, 3);
                size = ((body[at + 3] & 0xff) << 16) | ((body[at + 4] & 0xff) << 8) | (body[at + 5] & 0xff);
            } else {
                id = ascii(body, at, 4);
                size = major == 4 ? syncsafe(body, at + 4) : Tags.be32(body, at + 4);
                frameFlags = body[at + 9] & 0xff;
            }
            at += head;
            if (size < 0 || at + size > body.length) break;
            byte[] data = Arrays.copyOfRange(body, at, at + size);
            at += size;
            if (!id.matches("[A-Z0-9]{3,4}")) break;
            if (major == 2) {
                id = map22(id);
                if (id == null) continue;
                if (id.equals("APIC")) data = pic22(data);
            } else if (major == 3) {
                if ((frameFlags & 0xc0) != 0) continue; // compressed or encrypted
                if ((frameFlags & 0x20) != 0) data = Arrays.copyOfRange(data, Math.min(1, data.length), data.length);
            } else {
                if ((frameFlags & 0x0c) != 0) continue; // compressed or encrypted
                int skip = ((frameFlags & 0x40) != 0 ? 1 : 0) + ((frameFlags & 0x01) != 0 ? 4 : 0);
                data = Arrays.copyOfRange(data, Math.min(skip, data.length), data.length);
                if ((frameFlags & 0x02) != 0) data = deunsync(data, 0, data.length);
            }
            if (data != null) p.frames.add(new Frame(id, data));
        }
    }

    static Tags read(File f) throws IOException {
        Parsed p = parse(f);
        Tags t = new Tags();
        byte[] anyPicture = null;
        for (Frame fr : p.frames) {
            if (fr.id.equals("APIC")) {
                byte[][] pic = picture(fr.data);
                if (pic != null && (t.picture == null && pic[1][0] == 3)) t.picture = pic[0];
                if (pic != null && anyPicture == null) anyPicture = pic[0];
                continue;
            }
            if (!fr.id.startsWith("T")) continue;
            String v = text(fr.data);
            if (fr.id.equals("TIT2")) t.title = v;
            else if (fr.id.equals("TPE1")) t.artist = v;
            else if (fr.id.equals("TALB")) t.album = v;
            else if (fr.id.equals("TPE2")) t.albumArtist = v;
            else if (fr.id.equals("TCON")) t.genre = genre(v);
            else if (fr.id.equals("TYER") || (fr.id.equals("TDRC") && t.year == 0)) t.year = Tags.year(v);
            else if (fr.id.equals("TRCK")) {
                t.track = Tags.number(v);
                t.trackTotal = Tags.total(v);
            } else if (fr.id.equals("TPOS")) {
                t.disc = Tags.number(v);
                t.discTotal = Tags.total(v);
            }
        }
        if (t.picture == null) t.picture = anyPicture;
        v1(f, t);
        return t;
    }

    /** Fills what ID3v2 didn't have from an ID3v1 tag at the end, if there is one. */
    private static void v1(File f, Tags t) throws IOException {
        if (f.length() < 128) return;
        byte[] b = FileEdit.read(f, f.length() - 128, 128);
        if (b[0] != 'T' || b[1] != 'A' || b[2] != 'G') return;
        if (Tags.empty(t.title)) t.title = latin(b, 3, 30);
        if (Tags.empty(t.artist)) t.artist = latin(b, 33, 30);
        if (Tags.empty(t.album)) t.album = latin(b, 63, 30);
        if (t.year == 0) t.year = Tags.year(latin(b, 93, 4));
        if (t.track == 0 && b[125] == 0) t.track = b[126] & 0xff;
        int g = b[127] & 0xff;
        if (Tags.empty(t.genre) && g < GENRES.length) t.genre = GENRES[g];
    }

    /** Writes the tags (and the picture too when picture is true) into an MP3 file. */
    static void write(File f, Tags t, boolean picture) throws IOException {
        Parsed old = parse(f);
        int major = old.major == 4 ? 4 : 3;
        Set<String> edited = new HashSet<String>(Arrays.asList(TEXT));
        if (picture) edited.add("APIC");
        List<Frame> frames = new ArrayList<Frame>();
        text(frames, "TIT2", t.title, major);
        text(frames, "TPE1", t.artist, major);
        text(frames, "TALB", t.album, major);
        text(frames, "TPE2", t.albumArtist, major);
        text(frames, "TCON", t.genre, major);
        text(frames, major == 4 ? "TDRC" : "TYER", t.year > 0 ? String.valueOf(t.year) : "", major);
        text(frames, "TRCK", Tags.numberText(t.track, t.trackTotal), major);
        text(frames, "TPOS", Tags.numberText(t.disc, t.discTotal), major);
        if (picture && t.picture != null) frames.add(new Frame("APIC", apic(t.picture)));
        for (Frame fr : old.frames) {
            // TDAT and TIME go with the year they belonged to.
            if (!edited.contains(fr.id) && !fr.id.equals("TDAT") && !fr.id.equals("TIME")) frames.add(fr);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Frame fr : frames) {
            body.write(fr.id.getBytes("ISO-8859-1"));
            body.write(major == 4 ? syncsafe(fr.data.length) : be(fr.data.length));
            body.write(0);
            body.write(0);
            body.write(fr.data);
        }
        int used = 10 + body.size();
        // In place when it fits in the old tag; otherwise with room to spare next time.
        long length = old.length >= used ? old.length : used + PADDING;
        if (length - 10 > 0x0fffffffL) throw new IOException("The song's tags are too big");
        byte[] tag = new byte[(int) length];
        tag[0] = 'I';
        tag[1] = 'D';
        tag[2] = '3';
        tag[3] = (byte) major;
        System.arraycopy(syncsafe((int) length - 10), 0, tag, 6, 4);
        System.arraycopy(body.toByteArray(), 0, tag, 10, body.size());
        FileEdit.replace(f, 0, old.length, tag);
        writeV1(f, t);
    }

    /** Keeps an ID3v1 tag at the end (if the file has one) in step. */
    private static void writeV1(File f, Tags t) throws IOException {
        if (f.length() < 128) return;
        RandomAccessFile raf = new RandomAccessFile(f, "rw");
        try {
            long at = raf.length() - 128;
            byte[] b = new byte[128];
            raf.seek(at);
            raf.readFully(b);
            if (b[0] != 'T' || b[1] != 'A' || b[2] != 'G') return;
            putLatin(b, 3, 30, t.title);
            putLatin(b, 33, 30, t.artist);
            putLatin(b, 63, 30, t.album);
            putLatin(b, 93, 4, t.year > 0 ? String.valueOf(t.year) : "");
            if (b[125] == 0) b[126] = (byte) Math.max(0, Math.min(255, t.track));
            raf.seek(at);
            raf.write(b);
        } finally {
            raf.close();
        }
    }

    private static void text(List<Frame> frames, String id, String value, int major) throws IOException {
        if (Tags.empty(value)) return;
        String v = value.trim();
        boolean latin = true;
        for (int i = 0; i < v.length(); i++) {
            if (v.charAt(i) > 0xff) latin = false;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (latin) {
            out.write(0);
            out.write(v.getBytes("ISO-8859-1"));
        } else if (major == 4) {
            out.write(3);
            out.write(v.getBytes("UTF-8"));
        } else {
            out.write(1);
            out.write(0xff);
            out.write(0xfe);
            out.write(v.getBytes("UTF-16LE"));
        }
        frames.add(new Frame(id, out.toByteArray()));
    }

    /** A front-cover picture frame. */
    private static byte[] apic(byte[] image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        out.write(Tags.mimeOf(image).getBytes("ISO-8859-1"));
        out.write(0);
        out.write(3);
        out.write(0);
        out.write(image);
        return out.toByteArray();
    }

    /** A picture frame's image and {type}, or null if it's damaged. */
    private static byte[][] picture(byte[] d) {
        if (d.length < 4) return null;
        int enc = d[0];
        int at = 1;
        while (at < d.length && d[at] != 0) at++;
        at++; // mime's end
        if (at >= d.length) return null;
        int type = d[at++] & 0xff;
        at = skipText(d, at, enc);
        if (at > d.length) return null;
        return new byte[][]{Arrays.copyOfRange(d, at, d.length), {(byte) type}};
    }

    /** Past the zero-terminated text at at (two zero bytes for UTF-16). */
    private static int skipText(byte[] d, int at, int enc) {
        boolean wide = enc == 1 || enc == 2;
        while (at < d.length) {
            if (wide) {
                if (at + 1 < d.length && d[at] == 0 && d[at + 1] == 0) return at + 2;
                at += 2;
            } else {
                if (d[at] == 0) return at + 1;
                at++;
            }
        }
        return d.length + 1;
    }

    /** A text frame's first value. */
    static String text(byte[] d) {
        if (d.length < 1) return "";
        String s;
        try {
            switch (d[0]) {
                case 1:
                    s = new String(d, 1, d.length - 1, "UTF-16");
                    break;
                case 2:
                    s = new String(d, 1, d.length - 1, "UTF-16BE");
                    break;
                case 3:
                    s = new String(d, 1, d.length - 1, "UTF-8");
                    break;
                default:
                    s = new String(d, 1, d.length - 1, "ISO-8859-1");
            }
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        int nul = s.indexOf('\0');
        if (nul >= 0) s = s.substring(0, nul);
        return s.replace("﻿", "").trim();
    }

    /** "(17)" or "17" (an ID3v1 genre number) as its name; anything else as it is. */
    static String genre(String v) {
        String s = v.trim();
        if (s.startsWith("(")) {
            int close = s.indexOf(')');
            if (close > 1) {
                String rest = s.substring(close + 1).trim();
                if (rest.length() > 0) return rest;
                s = s.substring(1, close);
            }
        }
        if (s.matches("\\d{1,3}")) {
            int n = Integer.parseInt(s);
            return n < GENRES.length ? GENRES[n] : "";
        }
        return v.trim();
    }

    private static String map22(String id) {
        for (String[] m : V22) {
            if (m[0].equals(id)) return m[1];
        }
        return null;
    }

    /** ID3v2.2's PIC (a three-letter image format) as an APIC (a MIME type). */
    private static byte[] pic22(byte[] d) {
        if (d.length < 5) return null;
        String format = ascii(d, 1, 3);
        String mime = format.equalsIgnoreCase("PNG") ? "image/png" : "image/jpeg";
        byte[] m = mime.getBytes();
        byte[] out = new byte[d.length - 3 + m.length + 1];
        out[0] = d[0];
        System.arraycopy(m, 0, out, 1, m.length);
        out[1 + m.length] = 0;
        System.arraycopy(d, 4, out, 2 + m.length, d.length - 4);
        return out;
    }

    private static byte[] deunsync(byte[] b, int off, int len) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(len);
        for (int i = off; i < off + len; i++) {
            out.write(b[i]);
            if ((b[i] & 0xff) == 0xff && i + 1 < off + len && b[i + 1] == 0) i++;
        }
        return out.toByteArray();
    }

    static int syncsafe(byte[] b, int at) {
        return ((b[at] & 0x7f) << 21) | ((b[at + 1] & 0x7f) << 14) | ((b[at + 2] & 0x7f) << 7) | (b[at + 3] & 0x7f);
    }

    private static byte[] syncsafe(int n) {
        return new byte[]{(byte) ((n >> 21) & 0x7f), (byte) ((n >> 14) & 0x7f), (byte) ((n >> 7) & 0x7f),
                (byte) (n & 0x7f)};
    }

    private static byte[] be(int n) {
        return new byte[]{(byte) (n >> 24), (byte) (n >> 16), (byte) (n >> 8), (byte) n};
    }

    private static String ascii(byte[] b, int at, int len) {
        try {
            return new String(b, at, len, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String latin(byte[] b, int at, int len) {
        int end = at;
        while (end < at + len && b[end] != 0) end++;
        return ascii(b, at, end - at).trim();
    }

    private static void putLatin(byte[] b, int at, int len, String s) throws IOException {
        Arrays.fill(b, at, at + len, (byte) 0);
        if (s == null) return;
        byte[] v = s.trim().getBytes("ISO-8859-1");
        System.arraycopy(v, 0, b, at, Math.min(len, v.length));
    }
}
