package com.grapefruitmusic.playback;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * The real length of an MP3. Android takes it from the file's "Info" header when there is one, but
 * podcasts with ads put in as they're served keep the episode's header, which doesn't count the ads:
 * the time ran out minutes before the episode did. Here it comes from the audio itself: every frame
 * of a file, or, for a stream, the size and the bitrate (an MP3 with an Info header has a constant
 * bitrate). Pure Java (unit tested).
 */
public final class Mp3Length {
    /** estimate(): the bytes so far don't reach the first frames yet. */
    public static final long MORE = -2;

    private static final int[][] BITRATES = {
        // MPEG-1 layer I, II, III
        {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448},
        {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384},
        {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320},
        // MPEG-2 and 2.5 layer I, then II and III
        {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256},
        {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160},
    };
    private static final int[][] RATES = {{11025, 12000, 8000}, {0, 0, 0}, {22050, 24000, 16000}, {44100, 48000, 32000}};

    private Mp3Length() {
    }

    /** A frame header: its length in bytes, samples, sample rate and bitrate. */
    static final class Frame {
        int bytes;
        int samples;
        int rate;
        int kbps;
        boolean mpeg1;
        boolean mono;
        int layer;
        int version;
    }

    /** Reads the header at b[at..at+3] into f; false if it isn't one. */
    static boolean header(byte[] b, int at, Frame f) {
        int h1 = b[at] & 0xff, h2 = b[at + 1] & 0xff, h3 = b[at + 2] & 0xff, h4 = b[at + 3] & 0xff;
        if (h1 != 0xff || (h2 & 0xe0) != 0xe0) return false;
        int version = (h2 >> 3) & 3; // 0 MPEG-2.5, 2 MPEG-2, 3 MPEG-1
        int layer = 4 - ((h2 >> 1) & 3); // 1, 2, 3 (4: reserved)
        int bitrate = h3 >> 4;
        int rateIndex = (h3 >> 2) & 3;
        if (version == 1 || layer == 4 || bitrate == 0 || bitrate == 15 || rateIndex == 3) return false;
        boolean mpeg1 = version == 3;
        int kbps = BITRATES[mpeg1 ? layer - 1 : (layer == 1 ? 3 : 4)][bitrate];
        int rate = RATES[version][rateIndex];
        int padding = (h3 >> 1) & 1;
        int bytes;
        int samples;
        if (layer == 1) {
            bytes = (12000 * kbps / rate + padding) * 4;
            samples = 384;
        } else if (layer == 2 || mpeg1) {
            bytes = 144000 * kbps / rate + padding;
            samples = 1152;
        } else {
            bytes = 72000 * kbps / rate + padding;
            samples = 576;
        }
        if (bytes < 24) return false;
        f.bytes = bytes;
        f.samples = samples;
        f.rate = rate;
        f.kbps = kbps;
        f.mpeg1 = mpeg1;
        f.mono = (h4 >> 6) == 3;
        f.layer = layer;
        f.version = version;
        return true;
    }

    /** Bytes of ID3v2 tags at b[at..], or -1 if they run past len (0 for none). */
    static int id3(byte[] b, int at, int len) {
        int total = 0;
        while (at + total + 10 <= len && b[at + total] == 'I' && b[at + total + 1] == 'D' && b[at + total + 2] == '3') {
            int p = at + total;
            int size = ((b[p + 6] & 0x7f) << 21) | ((b[p + 7] & 0x7f) << 14) | ((b[p + 8] & 0x7f) << 7) | (b[p + 9] & 0x7f);
            total += 10 + size + ((b[p + 5] & 0x10) != 0 ? 10 : 0);
        }
        // A tag still coming (or one starting where these bytes stop): more is needed.
        if (at + total + 4 > len) return -1;
        return total;
    }

    /**
     * The first frame at or after from: a header followed by another of the same kind where it
     * says the next one starts. -1 if none before len (MORE if the bytes may simply stop too soon).
     */
    static long firstFrame(byte[] b, int from, int len, Frame f) {
        Frame next = new Frame();
        int limit = Math.min(len - 4, from + 64 * 1024);
        for (int i = from; i < limit; i++) {
            if (!header(b, i, f)) continue;
            int j = i + f.bytes;
            if (j + 4 > len) return MORE;
            if (header(b, j, next) && next.version == f.version && next.layer == f.layer && next.rate == f.rate) return i;
        }
        return len - from < 64 * 1024 ? MORE : -1;
    }

    /** "Xing" (variable bitrate), "Info" (constant) or "VBRI" in the first frame, else null. */
    static String tag(byte[] b, int at, int len, Frame f) {
        int side = f.mpeg1 ? (f.mono ? 17 : 32) : (f.mono ? 9 : 17);
        int p = at + 4 + side;
        if (p + 4 <= len) {
            String s = new String(b, p, 4, java.nio.charset.Charset.forName("ISO-8859-1"));
            if (s.equals("Xing") || s.equals("Info")) return s;
        }
        if (at + 40 <= len && b[at + 36] == 'V' && b[at + 37] == 'B' && b[at + 38] == 'R' && b[at + 39] == 'I') return "VBRI";
        return null;
    }

    /**
     * A stream's length in ms from its first bytes (head[0..len)) and its size in bytes: the size and
     * bitrate, if it has a constant bitrate (an Info header, or none). -1 if it can't be told this
     * way (not MP3, a variable bitrate, an unknown size), MORE if more of the start is needed.
     */
    public static long estimate(byte[] head, int len, long total) {
        if (total <= 0) return -1;
        int skip = id3(head, 0, len);
        if (skip < 0) return MORE;
        Frame f = new Frame();
        long at = firstFrame(head, skip, len, f);
        if (at < 0) return at;
        String tag = tag(head, (int) at, len, f);
        if ("Xing".equals(tag) || "VBRI".equals(tag)) return -1; // its frame count is all there is
        long audio = total - at - ("Info".equals(tag) ? f.bytes : 0);
        if (audio <= 0) return -1;
        return audio * 8 / f.kbps;
    }

    /** A file's length in ms, from every frame in it; -1 if it isn't an MP3. */
    public static long ofFile(File file) throws IOException {
        RandomAccessFile in = new RandomAccessFile(file, "r");
        try {
            long size = in.length();
            byte[] buf = new byte[256 * 1024];
            int len = read(in, 0, buf);
            int skip = id3(buf, 0, len);
            long pos;
            if (skip < 0) {
                // A big tag (cover art): read on from where it ends.
                int tagBytes = 0;
                byte[] h = new byte[10];
                while (true) {
                    if (read(in, tagBytes, h) < 10 || h[0] != 'I' || h[1] != 'D' || h[2] != '3') break;
                    int s = ((h[6] & 0x7f) << 21) | ((h[7] & 0x7f) << 14) | ((h[8] & 0x7f) << 7) | (h[9] & 0x7f);
                    tagBytes += 10 + s + ((h[5] & 0x10) != 0 ? 10 : 0);
                }
                pos = tagBytes;
                len = read(in, pos, buf);
                skip = 0;
            } else {
                pos = 0;
            }
            Frame f = new Frame();
            long first = firstFrame(buf, skip, len, f);
            if (first < 0) return -1;
            long at = pos + first;
            // The Info or Xing frame holds no sound.
            boolean header = tag(buf, (int) first, len, f) != null;
            long micros;
            long frames = 0;
            double samples = 0;
            long bufPos = pos;
            int bufLen = len;
            while (at + 4 <= size) {
                if (at + 4 > bufPos + bufLen) {
                    bufPos = at;
                    bufLen = read(in, bufPos, buf);
                    if (bufLen < 4) break;
                }
                int i = (int) (at - bufPos);
                if (header(buf, i, f)) {
                    if (!header || frames > 0 || at != pos + first) samples += f.samples / (double) f.rate;
                    frames++;
                    at += f.bytes;
                    continue;
                }
                // Lost the frames: an ID3v1 or APE tag at the end, or damage. Look for them again.
                long found = -1;
                Frame g = new Frame();
                for (long k = at + 1; k + 8 < Math.min(size, at + 16384); k++) {
                    if (k + 8 > bufPos + bufLen) {
                        bufPos = k;
                        bufLen = read(in, bufPos, buf);
                        if (bufLen < 8) break;
                    }
                    int x = (int) (k - bufPos);
                    if (header(buf, x, g) && x + g.bytes + 4 <= bufLen && header(buf, x + g.bytes, f)
                            && f.rate == g.rate) {
                        found = k;
                        break;
                    }
                }
                if (found < 0) break;
                at = found;
            }
            micros = Math.round(samples * 1e6);
            return frames > 0 ? micros / 1000 : -1;
        } finally {
            in.close();
        }
    }

    private static int read(RandomAccessFile in, long at, byte[] buf) throws IOException {
        in.seek(at);
        int n = 0;
        while (n < buf.length) {
            int r = in.read(buf, n, buf.length - n);
            if (r < 0) break;
            n += r;
        }
        return n;
    }
}
