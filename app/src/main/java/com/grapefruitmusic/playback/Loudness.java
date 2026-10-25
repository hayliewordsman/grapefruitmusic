package com.grapefruitmusic.playback;

import java.util.ArrayList;
import java.util.List;

/**
 * Integrated loudness of audio in LUFS, as defined by ITU-R BS.1770-4 / EBU R128 (the measure
 * ReplayGain 2 and streaming services use): K-weighting, 400 ms blocks overlapping by 75%, and
 * gating that leaves out silence and quiet passages. Pure Java (unit tested).
 *
 * <p>Feed interleaved 16-bit samples with {@link #add}; call {@link #discontinuity} between
 * separate stretches of the same song (the meter can measure a song from samples of it).
 */
public final class Loudness {
    /** Volume Match brings every song to this loudness (Apple Music's level). */
    public static final double TARGET_LUFS = -16;
    /** ReplayGain values are relative to this loudness. */
    public static final double REPLAYGAIN_REFERENCE_LUFS = -18;
    /** Never turn a song down further than this. */
    private static final double MAX_CUT_DB = -20;

    private static final double ABSOLUTE_GATE = -70;
    private static final double RELATIVE_GATE = -10;

    private int rate;
    private int channels;
    // Per channel: two biquads (high shelf, then high pass), direct form I state.
    private double[][] state;
    private double pb0, pb1, pb2, pa1, pa2;
    private double rb0, rb1, rb2, ra1, ra2;

    /** Samples per 100 ms step; a block is 4 steps. */
    private int step;
    private int inStep;
    private double stepSum;
    private final double[] lastSteps = new double[4];
    private int stepsFilled;
    /** Mean square (summed over channels) of each 400 ms block. */
    private final List<Double> blocks = new ArrayList<Double>();

    /** Sets the sample format; call before add() and whenever it changes. */
    public void setFormat(int sampleRate, int channelCount) {
        if (sampleRate == rate && channelCount == channels) return;
        rate = sampleRate;
        channels = Math.max(1, channelCount);
        // Coefficients for any sample rate (as in libebur128).
        double f0 = 1681.974450955533;
        double g = 3.999843853973347;
        double q = 0.7071752369554196;
        double k = Math.tan(Math.PI * f0 / rate);
        double vh = Math.pow(10, g / 20);
        double vb = Math.pow(vh, 0.4996667741545416);
        double a0 = 1 + k / q + k * k;
        pb0 = (vh + vb * k / q + k * k) / a0;
        pb1 = 2 * (k * k - vh) / a0;
        pb2 = (vh - vb * k / q + k * k) / a0;
        pa1 = 2 * (k * k - 1) / a0;
        pa2 = (1 - k / q + k * k) / a0;
        f0 = 38.13547087602444;
        q = 0.5003270373238773;
        k = Math.tan(Math.PI * f0 / rate);
        double d = 1 + k / q + k * k;
        rb0 = 1;
        rb1 = -2;
        rb2 = 1;
        ra1 = 2 * (k * k - 1) / d;
        ra2 = (1 - k / q + k * k) / d;
        step = Math.max(1, rate / 10);
        discontinuity();
    }

    /** The next samples don't follow the previous ones (a different part of the song). */
    public void discontinuity() {
        state = new double[channels][8];
        inStep = 0;
        stepSum = 0;
        stepsFilled = 0;
    }

    /** Interleaved 16-bit samples; count is the number of shorts (frames × channels). */
    public void add(short[] samples, int count) {
        if (rate == 0) throw new IllegalStateException("setFormat first");
        int frames = count / channels;
        int i = 0;
        for (int f = 0; f < frames; f++) {
            double sum = 0;
            for (int c = 0; c < channels; c++) {
                double y = filter(state[c], samples[i++] / 32768.0);
                sum += y * y;
            }
            stepSum += sum;
            if (++inStep == step) endStep();
        }
    }

    private double filter(double[] s, double x) {
        // High shelf: s[0..1] = x history, s[2..3] = y history.
        double y = pb0 * x + pb1 * s[0] + pb2 * s[1] - pa1 * s[2] - pa2 * s[3];
        s[1] = s[0];
        s[0] = x;
        s[3] = s[2];
        s[2] = y;
        // High pass: s[4..5] = input (the shelf's output) history, s[6..7] = output history.
        double z = rb0 * y + rb1 * s[4] + rb2 * s[5] - ra1 * s[6] - ra2 * s[7];
        s[5] = s[4];
        s[4] = y;
        s[7] = s[6];
        s[6] = z;
        return z;
    }

    private void endStep() {
        System.arraycopy(lastSteps, 1, lastSteps, 0, 3);
        lastSteps[3] = stepSum;
        stepSum = 0;
        inStep = 0;
        if (stepsFilled < 4) stepsFilled++;
        if (stepsFilled == 4) {
            blocks.add((lastSteps[0] + lastSteps[1] + lastSteps[2] + lastSteps[3]) / (4.0 * step));
        }
    }

    private static double lufs(double meanSquare) {
        return -0.691 + 10 * Math.log10(meanSquare);
    }

    /** Gated integrated loudness so far, or NaN if there's nothing above the absolute gate. */
    public double integrated() {
        double sum = 0;
        int n = 0;
        for (double b : blocks) {
            if (b > 0 && lufs(b) > ABSOLUTE_GATE) {
                sum += b;
                n++;
            }
        }
        if (n == 0) return Double.NaN;
        double gate = lufs(sum / n) + RELATIVE_GATE;
        sum = 0;
        n = 0;
        for (double b : blocks) {
            if (b > 0 && lufs(b) > ABSOLUTE_GATE && lufs(b) > gate) {
                sum += b;
                n++;
            }
        }
        return n == 0 ? Double.NaN : lufs(sum / n);
    }

    /** Number of 400 ms blocks measured. */
    public int blockCount() {
        return blocks.size();
    }

    /** Loudness a ReplayGain track gain implies (gain is relative to -18 LUFS). */
    public static double fromReplayGain(double gainDb) {
        return REPLAYGAIN_REFERENCE_LUFS - gainDb;
    }

    /**
     * How much to change a song of this loudness, in dB: down to the target, never up (Android's
     * player can only lower the volume), and never by more than 20 dB.
     */
    public static double adjustmentDb(double lufs) {
        if (Double.isNaN(lufs)) return 0;
        return Math.max(MAX_CUT_DB, Math.min(0, TARGET_LUFS - lufs));
    }

    /** The player volume (0 to 1) for an adjustment in dB. */
    public static float volume(double adjustmentDb) {
        return (float) Math.pow(10, adjustmentDb / 20);
    }
}
