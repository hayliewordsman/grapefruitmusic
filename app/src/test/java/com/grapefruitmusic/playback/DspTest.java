package com.grapefruitmusic.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DspTest {
    private static final int RATE = 44100;

    private static short[] sine(double seconds, double amplitude, double freq) {
        int frames = (int) (RATE * seconds);
        short[] out = new short[frames * 2];
        for (int f = 0; f < frames; f++) {
            short v = (short) Math.round(Math.sin(2 * Math.PI * freq * f / RATE) * amplitude * 32767);
            out[2 * f] = v;
            out[2 * f + 1] = v;
        }
        return out;
    }

    private static Dsp dsp(int boost, boolean bass) {
        Dsp d = new Dsp();
        d.setFormat(RATE, 2);
        d.setBoost(boost);
        d.setBass(bass);
        return d;
    }

    /** RMS in dBFS of the second half (after filters and limiter settle). */
    private static double rmsDb(short[] pcm) {
        double sum = 0;
        int n = 0;
        for (int i = pcm.length / 2; i < pcm.length; i++) {
            double x = pcm[i] / 32768.0;
            sum += x * x;
            n++;
        }
        return 10 * Math.log10(sum / n);
    }

    private static int peak(short[] pcm) {
        int p = 0;
        for (short s : pcm) p = Math.max(p, Math.abs((int) s));
        return p;
    }

    private static short[] run(Dsp d, short[] in) {
        short[] pcm = in.clone();
        // In chunks, as the player feeds it.
        for (int off = 0; off < pcm.length; off += 4096) d.process(pcm, off, Math.min(4096, pcm.length - off));
        return pcm;
    }

    @Test
    public void everythingOffPassesTheSoundThroughDelayed() {
        Dsp d = dsp(Dsp.BOOST_OFF, false);
        short[] in = sine(1, 0.5, 440);
        short[] out = run(d, in);
        int lag = d.latencyFrames() * 2;
        for (int i = lag; i < in.length; i++) assertEquals(in[i - lag], out[i], 1);
    }

    @Test
    public void eachBoostLevelRaisesQuietMusicByItsGain() {
        short[] quiet = sine(2, 0.05, 1000); // about -26 dBFS, far from the ceiling
        double before = rmsDb(quiet);
        double[] expected = {0, 4, 7, 10, 14};
        for (int level = Dsp.BOOST_OFF; level <= Dsp.BOOST_EXTREME; level++) {
            double after = rmsDb(run(dsp(level, false), quiet));
            assertEquals("level " + Dsp.BOOST_NAMES[level], expected[level], after - before, 0.1);
        }
    }

    @Test
    public void boostedLoudMusicStaysUnderTheCeiling() {
        int ceiling = (int) Math.ceil(Dsp.CEILING * 32767) + 1;
        for (int level = Dsp.BOOST_MILD; level <= Dsp.BOOST_EXTREME; level++) {
            short[] out = run(dsp(level, true), sine(2, 0.95, 60));
            assertTrue("peak " + peak(out) + " at " + Dsp.BOOST_NAMES[level], peak(out) <= ceiling);
        }
    }

    @Test
    public void suddenLoudBurstsDontClip() {
        // Quiet, then a full-scale burst out of nowhere: the look-ahead catches it.
        short[] quiet = sine(1, 0.02, 500);
        short[] burst = sine(0.2, 0.99, 3000);
        short[] in = new short[quiet.length * 2 + burst.length];
        System.arraycopy(quiet, 0, in, 0, quiet.length);
        System.arraycopy(burst, 0, in, quiet.length, burst.length);
        System.arraycopy(quiet, 0, in, quiet.length + burst.length, quiet.length);
        short[] out = run(dsp(Dsp.BOOST_EXTREME, false), in);
        int ceiling = (int) Math.ceil(Dsp.CEILING * 32767) + 1;
        assertTrue("peak " + peak(out), peak(out) <= ceiling);
        // And the quiet part afterwards comes back up (the limiter lets go).
        short[] tail = new short[quiet.length / 2];
        System.arraycopy(out, out.length - tail.length, tail, 0, tail.length);
        assertEquals(14.0, 20 * Math.log10(rms(tail) / rms(quiet)), 0.5);
    }

    private static double rms(short[] pcm) {
        double sum = 0;
        for (short s : pcm) sum += (s / 32768.0) * (s / 32768.0);
        return Math.sqrt(sum / pcm.length);
    }

    @Test
    public void bassBoostRaisesTheBassAndLeavesTheRest() {
        double lowBefore = rmsDb(sine(2, 0.05, 40));
        double lowAfter = rmsDb(run(dsp(Dsp.BOOST_OFF, true), sine(2, 0.05, 40)));
        double midBefore = rmsDb(sine(2, 0.05, 2000));
        double midAfter = rmsDb(run(dsp(Dsp.BOOST_OFF, true), sine(2, 0.05, 2000)));
        assertEquals(8.0, lowAfter - lowBefore, 0.8);
        assertEquals(0.0, midAfter - midBefore, 0.3);
    }

    @Test
    public void volumeMatchVolumeStillApplies() {
        Dsp d = dsp(Dsp.BOOST_MEDIUM, false);
        d.setVolume(0.5f); // -6 dB
        short[] quiet = sine(2, 0.05, 1000);
        assertEquals(7 - 6.02, rmsDb(run(d, quiet)) - rmsDb(quiet), 0.1);
    }

    @Test
    public void anyOn() {
        assertTrue(Dsp.anyOn(Dsp.BOOST_MILD, false));
        assertTrue(Dsp.anyOn(Dsp.BOOST_OFF, true));
        assertTrue(!Dsp.anyOn(Dsp.BOOST_OFF, false));
    }
}
