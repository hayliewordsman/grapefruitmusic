package com.grapefruitmusic.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Test;

public class Mp3LengthTest {
    /** Padding owed, so frames average exactly the bitrate, as an encoder pads them. */
    private static long owed;

    /** One MPEG-1 layer III stereo frame at kbps and 44.1kHz, with "Info" or "Xing" in it if tag isn't null. */
    private static byte[] frame(int kbps, String tag, int frames) {
        int index = java.util.Arrays.asList(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320).indexOf(kbps);
        owed += 144000L * kbps % 44100;
        int padding = owed >= 44100 ? 1 : 0;
        owed -= padding * 44100L;
        int bytes = 144000 * kbps / 44100 + padding;
        byte[] f = new byte[bytes];
        f[0] = (byte) 0xff;
        f[1] = (byte) 0xfb; // MPEG-1, layer III, no CRC
        f[2] = (byte) (index << 4 | padding << 1); // 44.1kHz
        f[3] = 0x00; // stereo
        if (tag != null) {
            byte[] t = tag.getBytes();
            System.arraycopy(t, 0, f, 36, 4);
            f[43] = 1; // frames field present
            f[44] = (byte) (frames >>> 24);
            f[45] = (byte) (frames >>> 16);
            f[46] = (byte) (frames >>> 8);
            f[47] = (byte) frames;
        }
        return f;
    }

    private static byte[] id3(int size) {
        byte[] b = new byte[10 + size];
        b[0] = 'I';
        b[1] = 'D';
        b[2] = '3';
        b[3] = 4;
        b[6] = (byte) ((size >> 21) & 0x7f);
        b[7] = (byte) ((size >> 14) & 0x7f);
        b[8] = (byte) ((size >> 7) & 0x7f);
        b[9] = (byte) (size & 0x7f);
        return b;
    }

    /** An episode: a tag, an Info header that says headerFrames, then frames of sound at kbps. */
    private static byte[] episode(int tagBytes, int kbps, int headerFrames, int frames, String tag) throws IOException {
        owed = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (tagBytes > 0) out.write(id3(tagBytes));
        if (tag != null) out.write(frame(kbps, tag, headerFrames));
        for (int i = 0; i < frames; i++) out.write(frame(kbps, null, 0));
        return out.toByteArray();
    }

    private static long ms(int frames) {
        return Math.round(frames * 1152 * 1000.0 / 44100);
    }

    @Test
    public void aStreamWithAdsAddedIsMeasuredBySizeNotItsInfoHeader() throws IOException {
        // The header says 10 minutes (22968 frames); 2 minutes of ads were added after it.
        int said = 22968, real = 22968 + 4594;
        byte[] whole = episode(4000, 128, said, real, "Info");
        byte[] start = java.util.Arrays.copyOf(whole, 64 * 1024);
        long length = Mp3Length.estimate(start, start.length, whole.length);
        assertEquals(ms(real), length, 30);
    }

    @Test
    public void moreIsNeededWhileTheTagIsStillComing() throws IOException {
        byte[] whole = episode(200000, 128, 100, 2000, "Info");
        assertEquals(Mp3Length.MORE, Mp3Length.estimate(whole, 60000, whole.length));
        long length = Mp3Length.estimate(whole, 260000, whole.length);
        assertEquals(ms(2000), length, 30);
    }

    @Test
    public void aVariableBitrateHeaderIsLeftToTheDecoder() throws IOException {
        byte[] whole = episode(0, 128, 500, 500, "Xing");
        assertEquals(-1, Mp3Length.estimate(whole, whole.length, whole.length));
    }

    @Test
    public void withoutAHeaderItIsTheSizeAndBitrate() throws IOException {
        byte[] whole = episode(0, 64, 0, 3000, null);
        assertEquals(ms(3000), Mp3Length.estimate(whole, 65536, whole.length), 30);
    }

    @Test
    public void notAnMp3() {
        byte[] noise = new byte[100000];
        new java.util.Random(3).nextBytes(noise);
        for (int i = 0; i < noise.length; i++) if ((noise[i] & 0xff) == 0xff) noise[i] = 0;
        assertEquals(-1, Mp3Length.estimate(noise, noise.length, noise.length));
        assertEquals(-1, Mp3Length.estimate(new byte[100], 100, -1));
    }

    @Test
    public void aFileIsMeasuredFrameByFrame() throws IOException {
        // A big cover-art tag, a stale Info header, frames at two bitrates (an ad at 128kbps in an
        // episode at 64kbps), and an ID3v1 tag at the end.
        owed = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(id3(300000));
        out.write(frame(64, "Info", 1000));
        for (int i = 0; i < 1000; i++) out.write(frame(64, null, 0));
        for (int i = 0; i < 2000; i++) out.write(frame(128, null, 0));
        for (int i = 0; i < 1000; i++) out.write(frame(64, null, 0));
        byte[] v1 = new byte[128];
        v1[0] = 'T';
        v1[1] = 'A';
        v1[2] = 'G';
        out.write(v1);
        File f = File.createTempFile("episode", ".mp3");
        try {
            FileOutputStream w = new FileOutputStream(f);
            w.write(out.toByteArray());
            w.close();
            assertEquals(ms(4000), Mp3Length.ofFile(f), 30);
        } finally {
            f.delete();
        }
    }

    @Test
    public void aFileThatIsntAnMp3() throws IOException {
        File f = File.createTempFile("episode", ".m4a");
        try {
            FileOutputStream w = new FileOutputStream(f);
            w.write("....ftypM4A some other format".getBytes());
            w.close();
            assertEquals(-1, Mp3Length.ofFile(f));
        } finally {
            f.delete();
        }
    }

    @Test
    public void headersAreRead() {
        Mp3Length.Frame f = new Mp3Length.Frame();
        owed = 0;
        byte[] b = frame(128, null, 0);
        assertTrue(Mp3Length.header(b, 0, f));
        assertEquals(417, f.bytes);
        assertEquals(1152, f.samples);
        assertEquals(44100, f.rate);
        assertEquals(128, f.kbps);
    }
}
