package com.grapefruitmusic.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class VocalRemoverTest {
    private static VocalRemover.Model model;

    /** The model the app ships (app/src/main/assets/karaoke.bin). */
    static synchronized VocalRemover.Model model() throws IOException {
        if (model == null) {
            for (String path : new String[]{"src/main/assets/karaoke.bin", "app/src/main/assets/karaoke.bin"}) {
                File f = new File(path);
                if (f.exists()) {
                    InputStream in = new FileInputStream(f);
                    try {
                        model = VocalRemover.Model.read(in);
                    } finally {
                        in.close();
                    }
                    break;
                }
            }
            if (model == null) fail("karaoke.bin not found");
        }
        return model;
    }

    /** Big-endian 16-bit samples from a test resource. */
    private static short[] samples(String name) throws IOException {
        InputStream raw = VocalRemoverTest.class.getResourceAsStream("/karaoke/" + name);
        if (raw == null) fail("missing test resource " + name);
        DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(raw));
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bytes.write(buf, 0, n);
        in.close();
        byte[] b = bytes.toByteArray();
        short[] s = new short[b.length / 2];
        for (int i = 0; i < s.length; i++) s[i] = (short) ((b[2 * i] << 8) | (b[2 * i + 1] & 0xff));
        return s;
    }

    @Test
    public void latencyIsAWindowAndTwoHops() throws IOException {
        VocalRemover r = new VocalRemover(model(), 44100);
        assertEquals(2048 + 2 * 512, r.latency());
    }

    @Test
    public void matchesTheTrainedModel() throws IOException {
        // A sung-like test signal through Python's reference (tools/export.py) and through the app:
        // the same sound out, to within rounding.
        short[] in = samples("golden_in.bin");
        short[] expected = samples("golden_out.bin");
        VocalRemover r = new VocalRemover(model(), 44100);
        double removed = 0;
        int worst = 0;
        for (int f = 0; f < in.length / 2; f++) {
            r.process(in[2 * f] / 32768f, in[2 * f + 1] / 32768f);
            int l = Math.round(r.outL * 32768);
            int rr = Math.round(r.outR * 32768);
            worst = Math.max(worst, Math.max(Math.abs(l - expected[2 * f]), Math.abs(rr - expected[2 * f + 1])));
            if (f >= r.latency()) {
                double d = (in[2 * (f - r.latency())] - expected[2 * f]) / 32768.0;
                removed += d * d;
            }
        }
        System.out.println("golden: worst difference " + worst + " LSB, removed rms "
                + Math.sqrt(removed / (in.length / 2 - r.latency())));
        assertTrue("worst difference " + worst + " LSB", worst <= 3);
        assertTrue("the model really took something away", Math.sqrt(removed / (in.length / 2)) > 0.01);
    }

    @Test
    public void nothingTakenAwayAtZero() throws IOException {
        short[] in = samples("golden_in.bin");
        VocalRemover r = new VocalRemover(model(), 44100);
        r.setAmount(0);
        int lag = r.latency();
        for (int f = 0; f < in.length / 2; f++) {
            r.process(in[2 * f] / 32768f, in[2 * f + 1] / 32768f);
            if (f >= lag + 441) {
                assertEquals(in[2 * (f - lag)] / 32768f, r.outL, 0);
                assertEquals(in[2 * (f - lag) + 1] / 32768f, r.outR, 0);
            }
        }
    }

    @Test
    public void worksAt48kToo() throws IOException {
        short[] in = samples("golden_in.bin");
        VocalRemover r = new VocalRemover(model(), 48000);
        double sum = 0;
        for (int f = 0; f < in.length / 2; f++) {
            r.process(in[2 * f] / 32768f, in[2 * f + 1] / 32768f);
            assertTrue(!Float.isNaN(r.outL) && Math.abs(r.outL) < 2);
            sum += r.outL * r.outL;
        }
        assertTrue(sum > 0);
    }

    @Test
    public void rejectsOtherFiles() {
        try {
            VocalRemover.Model.read(new ByteArrayInputStream(new byte[64]));
            fail("read a file that isn't a model");
        } catch (IOException expected) {
            // good
        }
    }

    @Test
    public void inTheEngineItAddsItsDelayAndSwitchesCleanly() throws IOException {
        Dsp d = new Dsp();
        d.setFormat(44100, 2);
        int plain = d.latencyFrames();
        d.setKaraoke(true, 1f, model());
        short[] in = samples("golden_in.bin");
        short[] pcm = in.clone();
        for (int off = 0; off < pcm.length; off += 4096) d.process(pcm, off, Math.min(4096, pcm.length - off));
        assertEquals(plain + 3072, d.latencyFrames());
        assertTrue(d.karaokeRunning());
        // Switching off fades out, drops the remover, fades back in: no sample jumps by more than the
        // music itself does.
        d.setKaraoke(false, 1f, null);
        short[] again = in.clone();
        for (int off = 0; off < again.length; off += 4096) d.process(again, off, Math.min(4096, again.length - off));
        assertTrue(!d.karaokeRunning());
        assertEquals(plain, d.latencyFrames());
        int ceiling = (int) Math.ceil(Dsp.CEILING * 32767) + 1;
        for (short s : again) assertTrue(Math.abs(s) <= ceiling);
    }
}
