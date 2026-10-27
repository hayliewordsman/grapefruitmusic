package com.grapefruitmusic.tags;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * FLAC tags: the Vorbis comment block (TITLE=..., ARTIST=...) and a PICTURE block, among the
 * metadata blocks before the audio. Comments Grapefruit doesn't edit (ReplayGain, lyrics) are kept.
 */
final class Flac {
    private static final int STREAMINFO = 0;
    private static final int PADDING = 1;
    private static final int COMMENT = 4;
    private static final int PICTURE = 6;
    private static final int SPARE = 4096;
    /** The comments the editor owns (others stay as they are). */
    private static final List<String> EDITED = Arrays.asList("TITLE", "ARTIST", "ALBUM", "ALBUMARTIST",
            "ALBUM ARTIST", "GENRE", "DATE", "YEAR", "TRACKNUMBER", "TRACKTOTAL", "TOTALTRACKS", "DISCNUMBER",
            "DISCTOTAL", "TOTALDISCS");

    private static final class Block {
        final int type;
        final byte[] data;

        Block(int type, byte[] data) {
            this.type = type;
            this.data = data;
        }
    }

    private Flac() {
    }

    static boolean starts(byte[] head) {
        return head.length >= 4 && head[0] == 'f' && head[1] == 'L' && head[2] == 'a' && head[3] == 'C';
    }

    /** The metadata blocks, and where the audio starts. */
    private static List<Block> blocks(File f, long[] audioStart) throws IOException {
        List<Block> out = new ArrayList<Block>();
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        try {
            long at = 0;
            byte[] magic = new byte[4];
            raf.readFully(magic);
            // An ID3 tag in front of a FLAC file (it happens): it goes when the tags are written.
            if (Id3.starts(magic)) {
                at = Id3.parse(f).length;
                raf.seek(at);
                raf.readFully(magic);
            }
            if (!starts(magic)) throw new IOException("Not a FLAC file");
            at += 4;
            boolean last = false;
            while (!last) {
                byte[] h = new byte[4];
                raf.seek(at);
                raf.readFully(h);
                last = (h[0] & 0x80) != 0;
                int type = h[0] & 0x7f;
                int len = ((h[1] & 0xff) << 16) | ((h[2] & 0xff) << 8) | (h[3] & 0xff);
                byte[] data = new byte[len];
                raf.readFully(data);
                out.add(new Block(type, data));
                at += 4 + len;
            }
            audioStart[0] = at;
        } finally {
            raf.close();
        }
        if (out.isEmpty() || out.get(0).type != STREAMINFO) throw new IOException("This FLAC file is damaged");
        return out;
    }

    static Tags read(File f) throws IOException {
        Tags t = new Tags();
        byte[] anyPicture = null;
        for (Block b : blocks(f, new long[1])) {
            if (b.type == COMMENT) {
                for (String c : comments(b.data)) {
                    int eq = c.indexOf('=');
                    if (eq <= 0) continue;
                    String key = c.substring(0, eq).toUpperCase(Locale.US);
                    String v = c.substring(eq + 1).trim();
                    if (key.equals("TITLE")) t.title = v;
                    else if (key.equals("ARTIST")) t.artist = v;
                    else if (key.equals("ALBUM")) t.album = v;
                    else if (key.equals("ALBUMARTIST") || key.equals("ALBUM ARTIST")) t.albumArtist = v;
                    else if (key.equals("GENRE")) t.genre = v;
                    else if (key.equals("DATE") || (key.equals("YEAR") && t.year == 0)) t.year = Tags.year(v);
                    else if (key.equals("TRACKNUMBER")) {
                        t.track = Tags.number(v);
                        if (Tags.total(v) > 0) t.trackTotal = Tags.total(v);
                    } else if (key.equals("TRACKTOTAL") || key.equals("TOTALTRACKS")) t.trackTotal = Tags.number(v);
                    else if (key.equals("DISCNUMBER")) {
                        t.disc = Tags.number(v);
                        if (Tags.total(v) > 0) t.discTotal = Tags.total(v);
                    } else if (key.equals("DISCTOTAL") || key.equals("TOTALDISCS")) t.discTotal = Tags.number(v);
                }
            } else if (b.type == PICTURE && b.data.length > 32) {
                int type = Tags.be32(b.data, 0);
                int at = 4;
                at += 4 + Tags.be32(b.data, at); // MIME type
                at += 4 + Tags.be32(b.data, at); // description
                at += 16; // width, height, depth, colours
                int len = Tags.be32(b.data, at);
                at += 4;
                if (len < 0 || at + len > b.data.length) continue;
                byte[] image = Arrays.copyOfRange(b.data, at, at + len);
                if (type == 3 && t.picture == null) t.picture = image;
                if (anyPicture == null) anyPicture = image;
            }
        }
        if (t.picture == null) t.picture = anyPicture;
        return t;
    }

    static void write(File f, Tags t, boolean picture) throws IOException {
        long[] audioStart = new long[1];
        List<Block> old = blocks(f, audioStart);
        List<Block> keep = new ArrayList<Block>();
        String vendor = "Grapefruit Music";
        List<String> comments = new ArrayList<String>();
        for (Block b : old) {
            if (b.type == COMMENT) {
                vendor = vendor(b.data);
                for (String c : comments(b.data)) {
                    int eq = c.indexOf('=');
                    String key = eq > 0 ? c.substring(0, eq).toUpperCase(Locale.US) : "";
                    if (!EDITED.contains(key)) comments.add(c);
                }
            } else if (b.type == PADDING || (picture && b.type == PICTURE)) {
                continue;
            } else {
                keep.add(b);
            }
        }
        List<String> mine = new ArrayList<String>();
        add(mine, "TITLE", t.title);
        add(mine, "ARTIST", t.artist);
        add(mine, "ALBUM", t.album);
        add(mine, "ALBUMARTIST", t.albumArtist);
        add(mine, "GENRE", t.genre);
        add(mine, "DATE", t.year > 0 ? String.valueOf(t.year) : "");
        add(mine, "TRACKNUMBER", t.track > 0 ? String.valueOf(t.track) : "");
        add(mine, "TRACKTOTAL", t.trackTotal > 0 ? String.valueOf(t.trackTotal) : "");
        add(mine, "DISCNUMBER", t.disc > 0 ? String.valueOf(t.disc) : "");
        add(mine, "DISCTOTAL", t.discTotal > 0 ? String.valueOf(t.discTotal) : "");
        mine.addAll(comments);
        // STREAMINFO stays first; the comments and picture follow what was there.
        keep.add(1, new Block(COMMENT, commentBlock(vendor, mine)));
        if (picture && t.picture != null) keep.add(2, new Block(PICTURE, pictureBlock(t.picture)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{'f', 'L', 'a', 'C'});
        for (Block b : keep) header(out, b.type, b.data.length, false).write(b.data);
        int used = out.size();
        long old0 = audioStart[0];
        // In place when it fits with room for a padding block (or exactly); otherwise with room to spare.
        long room = old0 - used;
        int pad = room == 0 ? -1 : room >= 4 && room - 4 <= 0xffffff ? (int) (room - 4) : SPARE;
        if (pad >= 0) {
            header(out, PADDING, pad, true).write(new byte[pad]);
        } else {
            markLast(out, keep);
        }
        FileEdit.replace(f, 0, old0, out.toByteArray());
    }

    /** With no padding block, the last block written carries the "last" flag. */
    private static void markLast(ByteArrayOutputStream out, List<Block> blocks) {
        byte[] all = out.toByteArray();
        int at = 4;
        for (int i = 0; i < blocks.size() - 1; i++) at += 4 + blocks.get(i).data.length;
        all[at] |= (byte) 0x80;
        out.reset();
        out.write(all, 0, all.length);
    }

    private static ByteArrayOutputStream header(ByteArrayOutputStream out, int type, int len, boolean last)
            throws IOException {
        if (len > 0xffffff) throw new IOException("The picture is too big for a FLAC file");
        out.write((last ? 0x80 : 0) | type);
        out.write(len >> 16);
        out.write(len >> 8);
        out.write(len);
        return out;
    }

    private static void add(List<String> list, String key, String value) {
        if (!Tags.empty(value)) list.add(key + "=" + value.trim());
    }

    private static String vendor(byte[] d) throws IOException {
        int len = le32(d, 0);
        return new String(d, 4, len, "UTF-8");
    }

    private static List<String> comments(byte[] d) throws IOException {
        List<String> out = new ArrayList<String>();
        int at = 4 + le32(d, 0);
        int count = le32(d, at);
        at += 4;
        for (int i = 0; i < count && at + 4 <= d.length; i++) {
            int len = le32(d, at);
            at += 4;
            if (len < 0 || at + len > d.length) break;
            out.add(new String(d, at, len, "UTF-8"));
            at += len;
        }
        return out;
    }

    private static byte[] commentBlock(String vendor, List<String> comments) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] v = vendor.getBytes("UTF-8");
        le32(out, v.length);
        out.write(v);
        le32(out, comments.size());
        for (String c : comments) {
            byte[] b = c.getBytes("UTF-8");
            le32(out, b.length);
            out.write(b);
        }
        return out.toByteArray();
    }

    private static byte[] pictureBlock(byte[] image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] mime = Tags.mimeOf(image).getBytes("US-ASCII");
        int[] size = Tags.sizeOf(image);
        be32(out, 3); // front cover
        be32(out, mime.length);
        out.write(mime);
        be32(out, 0); // no description
        be32(out, size[0]);
        be32(out, size[1]);
        be32(out, 24);
        be32(out, 0);
        be32(out, image.length);
        out.write(image);
        return out.toByteArray();
    }

    private static int le32(byte[] b, int at) throws IOException {
        if (at + 4 > b.length) throw new IOException("This FLAC file's tags are damaged");
        return (b[at] & 0xff) | ((b[at + 1] & 0xff) << 8) | ((b[at + 2] & 0xff) << 16) | ((b[at + 3] & 0xff) << 24);
    }

    private static void le32(ByteArrayOutputStream out, int n) {
        out.write(n);
        out.write(n >> 8);
        out.write(n >> 16);
        out.write(n >> 24);
    }

    private static void be32(ByteArrayOutputStream out, int n) {
        out.write(n >> 24);
        out.write(n >> 16);
        out.write(n >> 8);
        out.write(n);
    }
}
