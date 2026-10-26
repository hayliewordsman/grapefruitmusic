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

    private static Dsp dsp(int boost, int preset) {
        Dsp d = new Dsp();
        d.setFormat(RATE, 2);
        d.setBoost(boost);
        d.setEq(Equalizer.curve(preset));
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

    /** Interleaved stereo: a different tone in each ear, both at full scale if amplitude is 1. */
    private static short[] stereo(double seconds, double amplitude, double leftHz, double rightHz) {
        int frames = (int) (RATE * seconds);
        short[] out = new short[frames * 2];
        for (int f = 0; f < frames; f++) {
            out[2 * f] = (short) Math.max(-32768, Math.min(32767,
                    Math.round(Math.sin(2 * Math.PI * leftHz * f / RATE) * amplitude * 32768)));
            out[2 * f + 1] = (short) Math.max(-32768, Math.min(32767,
                    Math.round(Math.sin(2 * Math.PI * rightHz * f / RATE) * amplitude * 32768)));
        }
        return out;
    }

    @Test
    public void everythingOffPassesTheSoundThroughBitForBit() {
        // Even at full scale: nothing to limit, so every sample comes out exactly as it went in.
        Dsp d = dsp(Dsp.BOOST_OFF, Equalizer.OFF);
        short[] in = stereo(1, 1, 440, 660);
        short[] out = run(d, in);
        int lag = d.latencyFrames() * 2;
        for (int i = lag; i < in.length; i++) assertEquals("sample " + i, in[i - lag], out[i]);
    }

    @Test
    public void monoPlaysBothChannelsInBothEarsAtFullQuality() {
        // Different full-scale tones left and right: each ear gets their exact average, unlimited.
        Dsp d = dsp(Dsp.BOOST_OFF, Equalizer.OFF);
        d.setMono(true);
        short[] in = stereo(1, 1, 440, 660);
        short[] out = run(d, in);
        int lag = d.latencyFrames() * 2;
        for (int f = lag / 2; f < in.length / 2; f++) {
            int left = in[2 * f - lag];
            int right = in[2 * f + 1 - lag];
            long mix = Math.round((left + right) / 2.0);
            assertEquals("left at " + f, mix, out[2 * f]);
            assertEquals("right at " + f, mix, out[2 * f + 1]);
        }
    }

    @Test
    public void monoCostsNothingWithTheEqualizerOn() {
        // Mono with Rock sounds exactly like stereo Rock fed the same mix in both channels.
        short[] in = stereo(1, 0.4, 440, 3000);
        short[] mixed = new short[in.length];
        for (int i = 0; i < in.length; i += 2) {
            mixed[i] = mixed[i + 1] = (short) Math.round((in[i] + in[i + 1]) / 2.0);
        }
        Dsp mono = dsp(Dsp.BOOST_OFF, Equalizer.ROCK);
        mono.setMono(true);
        short[] a = run(mono, in);
        short[] b = run(dsp(Dsp.BOOST_OFF, Equalizer.ROCK), mixed);
        for (int i = 0; i < a.length; i += 2) {
            assertEquals("both ears the same", a[i], a[i + 1]);
            assertEquals("as good as stereo at " + i, b[i], a[i], 1);
        }
    }

    @Test
    public void turningMonoOnAndOffWhilePlayingStaysClean() {
        Dsp d = dsp(Dsp.BOOST_OFF, Equalizer.BASS_BOOSTER);
        short[] in = stereo(0.5, 0.4, 100, 150);
        int ceiling = (int) Math.ceil(Dsp.CEILING * 32767) + 1;
        for (boolean on : new boolean[]{true, false, true}) {
            d.setMono(on);
            assertTrue(peak(run(d, in)) <= ceiling);
        }
    }

    @Test
    public void eachBoostLevelRaisesQuietMusicByItsGain() {
        short[] quiet = sine(2, 0.05, 1000); // about -26 dBFS, far from the ceiling
        double before = rmsDb(quiet);
        double[] expected = {0, 4, 7, 10, 14};
        for (int level = Dsp.BOOST_OFF; level <= Dsp.BOOST_EXTREME; level++) {
            double after = rmsDb(run(dsp(level, Equalizer.OFF), quiet));
            assertEquals("level " + Dsp.BOOST_NAMES[level], expected[level], after - before, 0.1);
        }
    }

    @Test
    public void boostedLoudMusicStaysUnderTheCeiling() {
        int ceiling = (int) Math.ceil(Dsp.CEILING * 32767) + 1;
        for (int level = Dsp.BOOST_MILD; level <= Dsp.BOOST_EXTREME; level++) {
            short[] out = run(dsp(level, Equalizer.BASS_BOOSTER), sine(2, 0.95, 60));
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
        short[] out = run(dsp(Dsp.BOOST_EXTREME, Equalizer.OFF), in);
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

    /** How many dB the equalizer preset changes a quiet tone at hz by. */
    private static double eqGainDb(int preset, double hz) {
        short[] tone = sine(2, 0.05, hz);
        return rmsDb(run(dsp(Dsp.BOOST_OFF, preset), tone)) - rmsDb(tone);
    }

    @Test
    public void presetsShapeTheSoundToTheirCurves() {
        // Played through the real filters, each band's centre comes out at the preset's value.
        for (int preset : new int[]{Equalizer.BASS_BOOSTER, Equalizer.BASS_REDUCER, Equalizer.VOCAL_BOOSTER,
                Equalizer.ROCK, Equalizer.SPOKEN_WORD}) {
            float[] curve = Equalizer.curve(preset);
            for (int b = 1; b < Equalizer.BANDS - 1; b += 2) {
                assertEquals(Equalizer.name(preset) + " at " + Equalizer.FREQUENCIES[b] + " Hz", curve[b],
                        eqGainDb(preset, Equalizer.FREQUENCIES[b]), 0.3);
            }
        }
    }

    @Test
    public void offLeavesEveryFrequencyAlone() {
        for (double hz : new double[]{40, 200, 1500, 8000}) assertEquals(0.0, eqGainDb(Equalizer.OFF, hz), 0.05);
    }

    @Test
    public void changingTheCurveWhilePlayingStaysClean() {
        // Changed mid-song (a preset tapped, a slider dragged): no burst, and the new curve applies.
        Dsp d = dsp(Dsp.BOOST_OFF, Equalizer.BASS_BOOSTER);
        short[] first = run(d, sine(1, 0.5, 62));
        d.setEq(Equalizer.curve(Equalizer.BASS_REDUCER));
        short[] second = run(d, sine(1, 0.5, 62));
        int ceiling = (int) Math.ceil(Dsp.CEILING * 32767) + 1;
        assertTrue(peak(first) <= ceiling && peak(second) <= ceiling);
        assertEquals(Equalizer.curve(Equalizer.BASS_REDUCER)[1], rmsDb(second) - rmsDb(sine(1, 0.5, 62)), 0.5);
    }

    @Test
    public void volumeMatchVolumeStillApplies() {
        Dsp d = dsp(Dsp.BOOST_MEDIUM, Equalizer.OFF);
        d.setVolume(0.5f); // -6 dB
        short[] quiet = sine(2, 0.05, 1000);
        assertEquals(7 - 6.02, rmsDb(run(d, quiet)) - rmsDb(quiet), 0.1);
    }

    @Test
    public void anyOn() {
        assertTrue(Dsp.anyOn(Dsp.BOOST_MILD, null, false));
        assertTrue(Dsp.anyOn(Dsp.BOOST_OFF, Equalizer.curve(Equalizer.ROCK), false));
        assertTrue(Dsp.anyOn(Dsp.BOOST_OFF, null, true));
        assertTrue(!Dsp.anyOn(Dsp.BOOST_OFF, Equalizer.curve(Equalizer.OFF), false));
        assertTrue(!Dsp.anyOn(Dsp.BOOST_OFF, null, false));
    }
}
