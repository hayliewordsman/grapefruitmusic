package com.grapefruitmusic.tags;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * M4A (MP4) tags: iTunes-style items in moov/udta/meta/ilst. When the tags grow and the moov box
 * sits before the audio, the audio moves along, and the chunk offsets (stco/co64) that point into
 * it move with it.
 */
final class Mp4 {
    private static final int SPARE = 2048;
    private static final String NAME = "©nam";
    private static final String ARTIST = "©ART";
    private static final String ALBUM = "©alb";
    private static final String ALBUM_ARTIST = "aART";
    private static final String GENRE = "©gen";
    private static final String GENRE_ID = "gnre";
    private static final String DAY = "©day";
    private static final String TRACK = "trkn";
    private static final String DISC = "disk";
    private static final String COVER = "covr";
    private static final List<String> TEXT_ITEMS = Arrays.asList(NAME, ARTIST, ALBUM, ALBUM_ARTIST, GENRE, GENRE_ID,
            DAY, TRACK, DISC);
    /** Boxes Grapefruit looks inside on the way to the tags and the chunk offsets. */
    private static final List<String> CONTAINERS = Arrays.asList("moov", "trak", "mdia", "minf", "stbl", "udta",
            "meta", "ilst", "edts", "dinf");

    /** A box: its type and either its raw contents or its children. */
    static final class Box {
        final String type;
        byte[] data;
        List<Box> children;
        /** meta's version and flags (4 bytes before its children), or null. */
        byte[] fullHeader;

        Box(String type, byte[] data) {
            this.type = type;
            this.data = data;
        }
    }

    private Mp4() {
    }

    static boolean starts(byte[] head) {
        return head.length >= 8 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p';
    }

    /** Where moov is in the file: {start, end}, and whether audio (mdat) comes after it. */
    private static long[] findMoov(File f, boolean[] mdatAfter) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        try {
            long at = 0;
            long len = raf.length();
            long[] moov = null;
            while (at + 8 <= len) {
                raf.seek(at);
                long size = raf.readInt() & 0xffffffffL;
                byte[] t = new byte[4];
                raf.readFully(t);
                String type = new String(t, "ISO-8859-1");
                if (size == 1) size = raf.readLong();
                else if (size == 0) size = len - at;
                if (size < 8) throw new IOException("This M4A file is damaged");
                if (type.equals("moov")) moov = new long[]{at, at + size};
                if (type.equals("mdat") && moov != null) mdatAfter[0] = true;
                if (type.equals("moof")) throw new IOException("Grapefruit can't change tags in this kind of M4A file");
                at += size;
            }
            if (moov == null) throw new IOException("This M4A file has no song details to change");
            return moov;
        } finally {
            raf.close();
        }
    }

    static Box parse(String type, byte[] b, int off, int len) throws IOException {
        Box box = new Box(type, null);
        if (!CONTAINERS.contains(type)) {
            box.data = Arrays.copyOfRange(b, off, off + len);
            return box;
        }
        int at = off;
        if (type.equals("meta") && !(len >= 8 && new String(b, off + 4, 4, "ISO-8859-1").equals("hdlr"))) {
            // iTunes' meta is a "full box": version and flags before its children.
            box.fullHeader = Arrays.copyOfRange(b, off, off + 4);
            at += 4;
        }
        box.children = new ArrayList<Box>();
        int end = off + len;
        while (at + 8 <= end) {
            long size = Tags.be32(b, at) & 0xffffffffL;
            String t = new String(b, at + 4, 4, "ISO-8859-1");
            int head = 8;
            if (size == 1) {
                size = ((long) Tags.be32(b, at + 8) << 32) | (Tags.be32(b, at + 12) & 0xffffffffL);
                head = 16;
            } else if (size == 0) {
                size = end - at;
            }
            if (size < head || at + size > end) throw new IOException("This M4A file is damaged");
            // Inside ilst every item holds data boxes: keep items whole.
            box.children.add(type.equals("ilst") ? new Box(t, Arrays.copyOfRange(b, at + head, at + (int) size))
                    : parse(t, b, at + head, (int) size - head));
            at += (int) size;
        }
        return box;
    }

    static byte[] serialize(Box box) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (box.children == null) {
            body.write(box.data);
        } else {
            if (box.fullHeader != null) body.write(box.fullHeader);
            for (Box c : box.children) body.write(serialize(c));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(body.size() + 8);
        be32(out, body.size() + 8);
        out.write(box.type.getBytes("ISO-8859-1"));
        body.writeTo(out);
        return out.toByteArray();
    }

    private static Box moov(File f, long[] where) throws IOException {
        byte[] b = FileEdit.read(f, where[0], (int) (where[1] - where[0]));
        int head = (Tags.be32(b, 0) & 0xffffffffL) == 1 ? 16 : 8;
        return parse("moov", b, head, b.length - head);
    }

    private static Box child(Box parent, String type) {
        if (parent == null || parent.children == null) return null;
        for (Box c : parent.children) {
            if (c.type.equals(type)) return c;
        }
        return null;
    }

    static Tags read(File f) throws IOException {
        Box ilst = child(child(child(moov(f, findMoov(f, new boolean[1])), "udta"), "meta"), "ilst");
        Tags t = new Tags();
        if (ilst == null) return t;
        for (Box item : ilst.children) {
            byte[] d = value(item.data);
            if (d == null) continue;
            String type = item.type;
            if (type.equals(NAME)) t.title = utf8(d);
            else if (type.equals(ARTIST)) t.artist = utf8(d);
            else if (type.equals(ALBUM)) t.album = utf8(d);
            else if (type.equals(ALBUM_ARTIST)) t.albumArtist = utf8(d);
            else if (type.equals(GENRE)) t.genre = utf8(d);
            else if (type.equals(GENRE_ID) && d.length >= 2 && Tags.empty(t.genre)) {
                t.genre = Id3.genre(String.valueOf((((d[0] & 0xff) << 8) | (d[1] & 0xff)) - 1));
            } else if (type.equals(DAY)) t.year = Tags.year(utf8(d));
            else if (type.equals(TRACK) && d.length >= 6) {
                t.track = ((d[2] & 0xff) << 8) | (d[3] & 0xff);
                t.trackTotal = ((d[4] & 0xff) << 8) | (d[5] & 0xff);
            } else if (type.equals(DISC) && d.length >= 6) {
                t.disc = ((d[2] & 0xff) << 8) | (d[3] & 0xff);
                t.discTotal = ((d[4] & 0xff) << 8) | (d[5] & 0xff);
            } else if (type.equals(COVER) && t.picture == null) {
                t.picture = d;
            }
        }
        return t;
    }

    /** The value in an item's first data box (after its type and locale). */
    private static byte[] value(byte[] item) {
        int at = 0;
        while (at + 16 <= item.length) {
            int size = Tags.be32(item, at);
            if (size < 16 || at + size > item.length) return null;
            if (item[at + 4] == 'd' && item[at + 5] == 'a' && item[at + 6] == 't' && item[at + 7] == 'a') {
                return Arrays.copyOfRange(item, at + 16, at + size);
            }
            at += size;
        }
        return null;
    }

    static void write(File f, Tags t, boolean picture) throws IOException {
        boolean[] mdatAfter = new boolean[1];
        long[] where = findMoov(f, mdatAfter);
        Box moov = moov(f, where);
        Box udta = child(moov, "udta");
        if (udta == null) {
            udta = new Box("udta", null);
            udta.children = new ArrayList<Box>();
            moov.children.add(udta);
        }
        Box meta = child(udta, "meta");
        if (meta == null) {
            meta = new Box("meta", null);
            meta.fullHeader = new byte[4];
            meta.children = new ArrayList<Box>();
            meta.children.add(new Box("hdlr", hdlr()));
            udta.children.add(meta);
        }
        Box ilst = child(meta, "ilst");
        if (ilst == null) {
            ilst = new Box("ilst", null);
            ilst.children = new ArrayList<Box>();
            meta.children.add(ilst);
        }
        // Padding inside meta is dropped: the file gets its own after moov instead.
        for (int i = meta.children.size() - 1; i >= 0; i--) {
            if (meta.children.get(i).type.equals("free")) meta.children.remove(i);
        }
        List<Box> items = new ArrayList<Box>();
        text(items, NAME, t.title);
        text(items, ARTIST, t.artist);
        text(items, ALBUM, t.album);
        text(items, ALBUM_ARTIST, t.albumArtist);
        text(items, GENRE, t.genre);
        text(items, DAY, t.year > 0 ? String.valueOf(t.year) : "");
        if (t.track > 0) items.add(item(TRACK, 0, new byte[]{0, 0, (byte) (t.track >> 8), (byte) t.track,
                (byte) (t.trackTotal >> 8), (byte) t.trackTotal, 0, 0}));
        if (t.disc > 0) items.add(item(DISC, 0, new byte[]{0, 0, (byte) (t.disc >> 8), (byte) t.disc,
                (byte) (t.discTotal >> 8), (byte) t.discTotal}));
        if (picture && t.picture != null) {
            items.add(item(COVER, "image/png".equals(Tags.mimeOf(t.picture)) ? 14 : 13, t.picture));
        }
        for (Box old : ilst.children) {
            if (TEXT_ITEMS.contains(old.type) || (picture && old.type.equals(COVER))) continue;
            items.add(old);
        }
        ilst.children = items;

        long oldStart = where[0];
        long oldEnd = where[1];
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        long freeAfter = 0;
        try {
            // Padding straight after moov can take up the difference.
            if (oldEnd + 8 <= raf.length()) {
                raf.seek(oldEnd);
                long size = raf.readInt() & 0xffffffffL;
                byte[] type = new byte[4];
                raf.readFully(type);
                String s = new String(type, "ISO-8859-1");
                if ((s.equals("free") || s.equals("skip")) && size >= 8) freeAfter = size;
            }
        } finally {
            raf.close();
        }
        byte[] fresh = serialize(moov);
        long oldTotal = oldEnd - oldStart + freeAfter;
        if (fresh.length == oldTotal || fresh.length + 8 <= oldTotal) {
            // Fits where the old moov and its padding were: nothing else moves.
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(fresh);
            if (fresh.length < oldTotal) free(out, (int) (oldTotal - fresh.length));
            FileEdit.replace(f, oldStart, oldStart + oldTotal, out.toByteArray());
            return;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (mdatAfter[0]) {
            // The audio moves by delta: the chunk offsets pointing at it move too.
            long delta = fresh.length + 8 + SPARE - oldTotal;
            shift(moov, delta, oldEnd);
            fresh = serialize(moov);
            out.write(fresh);
            free(out, SPARE + 8);
        } else {
            out.write(fresh);
        }
        FileEdit.replace(f, oldStart, oldStart + oldTotal, out.toByteArray());
    }

    /** Moves every chunk offset at or after from by delta (in every track's stco or co64). */
    private static void shift(Box box, long delta, long from) throws IOException {
        if (box.children != null) {
            for (Box c : box.children) shift(c, delta, from);
            return;
        }
        boolean wide = box.type.equals("co64");
        if (!wide && !box.type.equals("stco")) return;
        byte[] d = box.data;
        int count = Tags.be32(d, 4);
        int width = wide ? 8 : 4;
        if (8 + (long) count * width > d.length) throw new IOException("This M4A file is damaged");
        for (int i = 0; i < count; i++) {
            int at = 8 + i * width;
            long v = wide ? ((long) Tags.be32(d, at) << 32) | (Tags.be32(d, at + 4) & 0xffffffffL)
                    : Tags.be32(d, at) & 0xffffffffL;
            if (v < from) continue;
            v += delta;
            if (wide) {
                put32(d, at, (int) (v >>> 32));
                put32(d, at + 4, (int) v);
            } else {
                if (v > 0xffffffffL) throw new IOException("This M4A file is too big to change");
                put32(d, at, (int) v);
            }
        }
    }

    private static void free(ByteArrayOutputStream out, int size) throws IOException {
        be32(out, size);
        out.write(new byte[]{'f', 'r', 'e', 'e'});
        out.write(new byte[size - 8]);
    }

    private static void text(List<Box> items, String type, String value) throws IOException {
        if (!Tags.empty(value)) items.add(item(type, 1, value.trim().getBytes("UTF-8")));
    }

    /** An ilst item holding one data box of the given well-known type (1 text, 13 JPEG, 14 PNG). */
    private static Box item(String type, int dataType, byte[] value) throws IOException {
        ByteArrayOutputStream d = new ByteArrayOutputStream();
        be32(d, 16 + value.length);
        d.write(new byte[]{'d', 'a', 't', 'a'});
        be32(d, dataType);
        be32(d, 0);
        d.write(value);
        return new Box(type, d.toByteArray());
    }

    /** The handler meta needs: "mdir" metadata, "appl" as iTunes writes it. */
    private static byte[] hdlr() {
        return new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 'm', 'd', 'i', 'r', 'a', 'p', 'p', 'l', 0, 0, 0, 0, 0, 0, 0, 0, 0};
    }

    private static String utf8(byte[] d) throws IOException {
        return new String(d, "UTF-8").trim();
    }

    private static void be32(ByteArrayOutputStream out, int n) {
        out.write(n >> 24);
        out.write(n >> 16);
        out.write(n >> 8);
        out.write(n);
    }

    private static void put32(byte[] b, int at, int n) {
        b[at] = (byte) (n >> 24);
        b[at + 1] = (byte) (n >> 16);
        b[at + 2] = (byte) (n >> 8);
        b[at + 3] = (byte) n;
    }
}
