package com.grapefruitmusic.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TimeStretchTest {
    private static final int RATE = 44100;

    /** seconds of a tone at hz, interleaved for channels. */
    private static short[] tone(double seconds, double hz, int channels) {
        int frames = (int) (seconds * RATE);
        short[] pcm = new short[frames * channels];
        for (int f = 0; f < frames; f++) {
            short v = (short) (12000 * Math.sin(2 * Math.PI * hz * f / RATE));
            for (int c = 0; c < channels; c++) pcm[f * channels + c] = v;
        }
        return pcm;
    }

    /** Runs pcm through in blocks the size the engine uses; returns the output. */
    private static short[] stretch(short[] pcm, int channels, float speed) {
        TimeStretch t = new TimeStretch();
        t.configure(RATE, channels, speed);
        short[] out = new short[pcm.length * 3];
        int o = 0;
        int block = 1152 * channels;
        for (int i = 0; i < pcm.length; i += block) {
            int n = t.put(pcm, i, Math.min(block, pcm.length - i));
            System.arraycopy(t.output(), 0, out, o, n);
            o += n;
        }
        int n = t.flush();
        System.arraycopy(t.output(), 0, out, o, n);
        o += n;
        short[] trimmed = new short[o];
        System.arraycopy(out, 0, trimmed, 0, o);
        return trimmed;
    }

    /** The tone's frequency, from upward zero crossings of the first channel. */
    private static double frequency(short[] pcm, int channels) {
        int crossings = 0;
        int frames = pcm.length / channels;
        int first = -1;
        int last = -1;
        for (int f = 1; f < frames; f++) {
            if (pcm[(f - 1) * channels] < 0 && pcm[f * channels] >= 0) {
                if (first < 0) first = f;
                last = f;
                crossings++;
            }
        }
        return (crossings - 1) * (double) RATE / (last - first);
    }

    @Test
    public void fasterIsShorterAtTheSamePitch() {
        short[] in = tone(10, 440, 2);
        for (float speed : new float[]{0.8f, 1.2f, 1.5f, 2f}) {
            short[] out = stretch(in, 2, speed);
            double expected = in.length / speed;
            assertEquals("length at " + speed, 1.0, out.length / expected, 0.01);
            assertEquals("pitch at " + speed, 440, frequency(out, 2), 440 * 0.01);
        }
    }

    @Test
    public void monoToo() {
        short[] in = tone(5, 300, 1);
        short[] out = stretch(in, 1, 1.75f);
        assertEquals(1.0, out.length / (in.length / 1.75), 0.015);
        assertEquals(300, frequency(out, 1), 3);
    }

    @Test
    public void shortSoundsComeOutWhole() {
        // Less than one piece: put() holds it all, flush() gives it back unchanged.
        short[] in = tone(0.02, 440, 2);
        TimeStretch t = new TimeStretch();
        t.configure(RATE, 2, 1.5f);
        assertEquals(0, t.put(in, 0, in.length));
        assertEquals(in.length, t.flush());
        assertEquals(0, t.flush());
    }

    @Test
    public void howFast() {
        short[] in = tone(60, 220, 2);
        long start = System.nanoTime();
        short[] out = stretch(in, 2, 1.5f);
        double seconds = (System.nanoTime() - start) / 1e9;
        System.out.println("TimeStretch: 60 s of stereo at 1.5x in " + Math.round(seconds * 1000) + " ms ("
                + Math.round(60 / seconds) + "x real time here)");
        assertTrue(out.length > 0);
    }
}
