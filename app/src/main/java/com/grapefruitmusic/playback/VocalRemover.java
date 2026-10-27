package com.grapefruitmusic.playback;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Karaoke Mode: turns a song's lead vocal down with a small neural network that runs on the
 * phone, offline.
 *
 * <p>The sound is cut into overlapping windows (2048 samples, a new one every 1024, under a sine
 * window). For each, the model looks at the loudness of 96 frequency bands of the middle (L+R)/2
 * and the sides (L-R)/2, from three windows back to one ahead, plus slowly-moving averages of
 * what it has heard (a longer memory), and estimates how much of each band is lead vocal, and how
 * strongly to favour sound right in the middle within the band (lead vocals sit in the middle of
 * a mix). That share of the middle is the vocal; it's subtracted from both channels, scaled by the
 * amount, so instruments and the stereo picture stay as they were. The sound comes out
 * {@link #latency()} samples late.
 *
 * <p>Version 2 (Grapefruit 3.2) does a quarter of version 1's work: half as many windows, a
 * smaller network, and no exp or log per frequency bin (a fast power on float bits instead).
 *
 * <p>The model is trained by tools/karaoke on the MUSDB18 7-second excerpts (CC BY 4.0) and on Free
 * Music Archive songs (CC BY, CC0 and public domain) split into voice and backing by Demucs;
 * tools/karaoke also holds a Python reference of this processing. One instance per stream; call
 * from one thread.
 */
public final class VocalRemover {
    /** A trained model, read from the app's karaoke.bin. Shared, read-only. */
    public static final class Model {
        final int n;
        final int hop;
        final int bands;
        final int past;
        final int future;
        final float fmin;
        final float fmax;
        final float eps;
        /** Per-frame decays of the encoding's moving averages. */
        final float[] emas;
        final float[] mean;
        final float[] std;
        final Layer enc;
        final Layer ctx;
        final Layer mid;
        final Layer out;

        private Model(DataInputStream in) throws IOException {
            if (in.readInt() != 0x47464b4d) throw new IOException("not a Grapefruit karaoke model");
            if (in.readInt() != 4) throw new IOException("unknown karaoke model version");
            n = in.readInt();
            hop = in.readInt();
            bands = in.readInt();
            past = in.readInt();
            future = in.readInt();
            fmin = in.readFloat();
            fmax = in.readFloat();
            eps = in.readFloat();
            emas = floats(in, in.readInt());
            mean = floats(in, 2 * bands);
            std = floats(in, 2 * bands);
            enc = new Layer(in);
            ctx = new Layer(in);
            mid = new Layer(in);
            out = new Layer(in);
            if (enc.ins != 2 * bands || ctx.ins != (past + 1 + future + emas.length) * enc.outs || mid.ins != ctx.outs
                    || out.ins != mid.outs || out.outs != 2 * bands) {
                throw new IOException("karaoke model layers don't fit together");
            }
        }

        public static Model read(InputStream in) throws IOException {
            return new Model(new DataInputStream(new java.io.BufferedInputStream(in)));
        }

        int context() {
            return past + 1 + future;
        }
    }

    /**
     * A dense layer, one scale per output: y = scale * (W x) + bias. The file holds 4-bit weights
     * (-7 to 7, two a byte, low nibble first); they're unpacked to a byte each here.
     */
    static final class Layer {
        final int outs;
        final int ins;
        final float[] scale;
        final float[] bias;
        final byte[] weights;

        Layer(DataInputStream in) throws IOException {
            outs = in.readInt();
            ins = in.readInt();
            scale = floats(in, outs);
            bias = floats(in, outs);
            weights = new byte[outs * ins];
            byte[] packed = new byte[(weights.length + 1) / 2];
            in.readFully(packed);
            for (int i = 0; i < weights.length; i++) {
                int nibble = (i & 1) == 0 ? packed[i >> 1] & 0xf : (packed[i >> 1] >> 4) & 0xf;
                weights[i] = (byte) (nibble >= 8 ? nibble - 16 : nibble);
            }
        }

        void apply(float[] x, float[] y, boolean relu) {
            byte[] w = weights;
            int n = ins;
            int four = n & ~3;
            int k = 0;
            for (int o = 0; o < outs; o++) {
                // Four at a time: fewer loop checks for Android 4's JIT.
                float a0 = 0;
                float a1 = 0;
                float a2 = 0;
                float a3 = 0;
                int i = 0;
                for (; i < four; i += 4, k += 4) {
                    a0 += w[k] * x[i];
                    a1 += w[k + 1] * x[i + 1];
                    a2 += w[k + 2] * x[i + 2];
                    a3 += w[k + 3] * x[i + 3];
                }
                for (; i < n; i++) a0 += w[k++] * x[i];
                float v = ((a0 + a1) + (a2 + a3)) * scale[o] + bias[o];
                y[o] = relu && v < 0 ? 0 : v;
            }
        }
    }

    private static float[] floats(DataInputStream in, int count) throws IOException {
        float[] f = new float[count];
        for (int i = 0; i < count; i++) f[i] = in.readFloat();
        return f;
    }

    /** How long the output takes to start after a reset: then it fades in over this many samples. */
    private static final int FADE = 441;

    private final Model m;
    private final int n;
    private final int hop;
    private final int latency;
    private final Fft fft;
    private final float[] window;
    // Triangle bands, sparse: band b covers bins first[b] .. first[b] + weight[b].length - 1.
    private final int[] first;
    private final float[][] weight;
    // Spreading band masks back to bins: each bin takes from bands spreadBand[k] (and the next).
    private final int[] spreadBand;
    private final float[] spreadLow;
    private final float[] spreadHigh;

    // Analysis: the last n samples of mid and side, filled a hop at a time.
    private final float[] anaMid;
    private final float[] anaSide;
    private int fill;
    private long frames;
    private final float[] re;
    private final float[] im;
    private final float[] synth;
    private final float[] powerMid;
    private final float[] powerSide;
    private final float[] features;
    private final float[] encoded;
    // The encoded frames from past back to future ahead (a ring), and each one's mid spectrum.
    private final float[] history;
    /** The encoding's moving averages, one after another (up to the newest frame). */
    private final float[] averages;
    private final float[][] spectraRe;
    private final float[][] spectraIm;
    /** Per bin, how much of the power is in the middle: |M|^2 / (|M|^2 + |S|^2). */
    private final float[][] spectraCentred;
    private final float[] context;
    private final float[] hidden1;
    private final float[] hidden2;
    private final float[] mask;
    private final float[] sharp;
    // Synthesis: the vocal estimate, overlap-added; vocal holds the hop being played.
    private final float[] ola;
    private final float[] vocal;
    private int vocalPos;
    // The dry sound, delayed by latency.
    private final float[] dryL;
    private final float[] dryR;
    private int dryPos;
    private long played;

    private volatile float amount = 1f;
    private float smoothed = -1;

    /** The two channels after process(). */
    public float outL;
    public float outR;

    public VocalRemover(Model model, int sampleRate) {
        m = model;
        n = model.n;
        hop = model.hop;
        latency = n + model.future * hop;
        fft = new Fft(n);
        // A sine window, in and out: at half-overlap the two together add up to exactly 1.
        window = new float[n];
        for (int i = 0; i < n; i++) window[i] = (float) Math.sin(Math.PI * i / n);

        // Mel-spaced band centres from fmin to fmax (or just under half the sample rate).
        int bins = n / 2 + 1;
        double top = Math.min(model.fmax, 0.48 * sampleRate);
        double lo = mel(model.fmin);
        double hi = mel(top);
        double[] centre = new double[model.bands];
        for (int b = 0; b < model.bands; b++) centre[b] = unmel(lo + (hi - lo) * b / (model.bands - 1));
        double[][] tri = new double[model.bands][bins];
        for (int b = 0; b < model.bands; b++) {
            double below = b > 0 ? centre[b - 1] : -1;
            double above = b < model.bands - 1 ? centre[b + 1] : 1e9;
            for (int k = 0; k < bins; k++) {
                double f = (double) k * sampleRate / n;
                if (f <= centre[b]) {
                    if (b == 0) tri[b][k] = 1;
                    else if (f > below) tri[b][k] = (f - below) / (centre[b] - below);
                } else {
                    if (b == model.bands - 1) tri[b][k] = 1;
                    else if (f < above) tri[b][k] = (above - f) / (above - centre[b]);
                }
            }
        }
        first = new int[model.bands];
        weight = new float[model.bands][];
        for (int b = 0; b < model.bands; b++) {
            int a = 0;
            while (a < bins && tri[b][a] == 0) a++;
            int z = bins - 1;
            while (z > a && tri[b][z] == 0) z--;
            first[b] = a;
            weight[b] = new float[a < bins ? z - a + 1 : 0];
            for (int k = a; k <= z && k < bins; k++) weight[b][k - a] = (float) tri[b][k];
        }
        // Each bin lies under at most two neighbouring triangles: remember which, normalized.
        spreadBand = new int[bins];
        spreadLow = new float[bins];
        spreadHigh = new float[bins];
        for (int k = 0; k < bins; k++) {
            int b0 = -1;
            double sum = 0;
            for (int b = 0; b < model.bands; b++) {
                if (tri[b][k] > 0) {
                    if (b0 < 0) b0 = b;
                    sum += tri[b][k];
                }
            }
            if (b0 < 0) b0 = 0;
            spreadBand[k] = b0;
            spreadLow[k] = sum > 0 ? (float) (tri[b0][k] / sum) : 1;
            spreadHigh[k] = sum > 0 && b0 + 1 < model.bands ? (float) (tri[b0 + 1][k] / sum) : 0;
        }

        anaMid = new float[n];
        anaSide = new float[n];
        re = new float[n];
        im = new float[n];
        synth = new float[n];
        powerMid = new float[bins];
        powerSide = new float[bins];
        features = new float[2 * model.bands];
        encoded = new float[model.enc.outs];
        history = new float[model.context() * model.enc.outs];
        averages = new float[model.emas.length * model.enc.outs];
        spectraRe = new float[model.future + 1][bins];
        spectraIm = new float[model.future + 1][bins];
        spectraCentred = new float[model.future + 1][bins];
        context = new float[model.ctx.ins];
        hidden1 = new float[model.ctx.outs];
        hidden2 = new float[model.mid.outs];
        mask = new float[2 * model.bands];
        sharp = new float[model.bands];
        ola = new float[n];
        vocal = new float[hop];
        dryL = new float[latency];
        dryR = new float[latency];
    }

    // x^s as 2^(s log2 x), on float bits: no exp or log per frequency bin. tools/karaoke/kdsp.py
    // has the same, and fitted these.
    private static final float L0 = -0.07915037f;
    private static final float L1 = 0.31221426f;
    private static final float L2 = -0.6695152f;
    private static final float L3 = 1.4360981f;
    private static final float L4 = 0.00020415891f;
    private static final float E0 = 0.013676531f;
    private static final float E1 = 0.051666877f;
    private static final float E2 = 0.24171026f;
    private static final float E3 = 0.6929313f;
    private static final float E4 = 1.0000073f;

    /** log2 of a positive float, to within 2e-4. */
    static float log2(float x) {
        int bits = Float.floatToRawIntBits(x);
        int e = ((bits >> 23) & 0xff) - 127;
        float m = Float.intBitsToFloat((bits & 0x7fffff) | 0x3f800000) - 1f;
        float p = (((L0 * m + L1) * m + L2) * m + L3) * m + L4;
        return e + p;
    }

    /** 2^y for y <= 0 (down to 2^-126), to within 7e-6. */
    static float exp2(float y) {
        if (y < -126f) y = -126f;
        int i = (int) Math.floor(y);
        float f = y - i;
        float p = (((E0 * f + E1) * f + E2) * f + E3) * f + E4;
        return Float.intBitsToFloat((i + 127) << 23) * p;
    }

    private static double mel(double f) {
        return 2595.0 * Math.log10(1.0 + f / 700.0);
    }

    private static double unmel(double m) {
        return 700.0 * (Math.pow(10.0, m / 2595.0) - 1.0);
    }

    /** Samples between a sample going in and coming out. */
    public int latency() {
        return latency;
    }

    /** How much of the vocal to take away, 0 to 1. Any thread. */
    public void setAmount(float a) {
        amount = Math.max(0, Math.min(1, a));
    }

    /** Takes one stereo sample (-1 to 1); the sample from latency() ago comes out in outL/outR. */
    public void process(float l, float r) {
        float target = amount;
        if (smoothed < 0) smoothed = target;
        // Glide to a new amount over a few milliseconds instead of jumping (no zipper noise).
        smoothed += (target - smoothed) * 0.002f;
        float v = vocal[vocalPos++] * smoothed;
        float gain = played < latency ? 0 : played < latency + FADE ? (played - latency) / (float) FADE : 1;
        played++;
        outL = (dryL[dryPos] - v) * gain;
        outR = (dryR[dryPos] - v) * gain;
        dryL[dryPos] = l;
        dryR[dryPos] = r;
        if (++dryPos == latency) dryPos = 0;

        anaMid[n - hop + fill] = (l + r) * 0.5f;
        anaSide[n - hop + fill] = (l - r) * 0.5f;
        if (++fill == hop) {
            frame();
            fill = 0;
            vocalPos = 0;
        }
    }

    /** A hop's worth of samples arrived: analyse the newest window, finish the one future frames ago. */
    private void frame() {
        int bands = m.bands;
        int bins = n / 2 + 1;
        long j = frames++;

        // Mid and side in one complex FFT: z = mid + i side.
        for (int i = 0; i < n; i++) {
            re[i] = anaMid[i] * window[i];
            im[i] = anaSide[i] * window[i];
        }
        System.arraycopy(anaMid, hop, anaMid, 0, n - hop);
        System.arraycopy(anaSide, hop, anaSide, 0, n - hop);
        fft.transform(re, im, false);
        int slot = (int) (j % (m.future + 1));
        float[] midRe = spectraRe[slot];
        float[] midIm = spectraIm[slot];
        float[] centred = spectraCentred[slot];
        for (int k = 0; k < bins; k++) {
            int c = (n - k) % n;
            float ar = re[k];
            float ai = im[k];
            float cr = re[c];
            float ci = im[c];
            float mr = (ar + cr) * 0.5f;
            float mi = (ai - ci) * 0.5f;
            float sr = (ai + ci) * 0.5f;
            float si = (cr - ar) * 0.5f;
            midRe[k] = mr;
            midIm[k] = mi;
            powerMid[k] = mr * mr + mi * mi;
            powerSide[k] = sr * sr + si * si;
            centred[k] = powerMid[k] / (powerMid[k] + powerSide[k] + m.eps);
        }
        for (int b = 0; b < bands; b++) {
            float[] w = weight[b];
            int a = first[b];
            double em = 0;
            double es = 0;
            for (int i = 0; i < w.length; i++) {
                em += w[i] * powerMid[a + i];
                es += w[i] * powerSide[a + i];
            }
            features[b] = ((float) Math.log(em + m.eps) - m.mean[b]) / m.std[b];
            features[bands + b] = ((float) Math.log(es + m.eps) - m.mean[bands + b]) / m.std[bands + b];
        }
        int enc = m.enc.outs;
        int ring = m.context();
        m.enc.apply(features, encoded, true);
        System.arraycopy(encoded, 0, history, (int) (j % ring) * enc, enc);
        for (int e = 0; e < m.emas.length; e++) {
            float a = m.emas[e];
            float b = 1 - a;
            int at = e * enc;
            for (int i = 0; i < enc; i++) {
                averages[at + i] = j == 0 ? encoded[i] : a * averages[at + i] + b * encoded[i];
            }
        }

        // The frame whose context is now complete.
        long t = j - m.future;
        if (t >= 0) {
            for (int c = 0; c < ring; c++) {
                long f = t - m.past + c;
                if (f < 0) {
                    java.util.Arrays.fill(context, c * enc, (c + 1) * enc, 0f);
                } else {
                    System.arraycopy(history, (int) (f % ring) * enc, context, c * enc, enc);
                }
            }
            // Then the moving averages, up to the newest frame.
            System.arraycopy(averages, 0, context, ring * enc, averages.length);
            m.ctx.apply(context, hidden1, true);
            m.mid.apply(hidden1, hidden2, true);
            m.out.apply(hidden2, mask, false);
            for (int b = 0; b < bands; b++) {
                float x = mask[bands + b];
                sharp[b] = x > 20 ? x : (float) Math.log(1 + Math.exp(x));
                mask[b] = (float) (1 / (1 + Math.exp(-mask[b])));
            }

            int ts = (int) (t % (m.future + 1));
            float[] sr = spectraRe[ts];
            float[] si = spectraIm[ts];
            float[] middle = spectraCentred[ts];
            for (int k = 0; k < bins; k++) {
                int b = spreadBand[k];
                float lo = spreadLow[k];
                float hi = spreadHigh[k];
                float g = mask[b] * lo + (hi != 0 ? mask[b + 1] * hi : 0);
                float s = sharp[b] * lo + (hi != 0 ? sharp[b + 1] * hi : 0);
                // Within the band, favour what's in the middle: centred^sharpness.
                float c = middle[k];
                g *= exp2(s * log2(c < 1e-12f ? 1e-12f : c));
                re[k] = sr[k] * g;
                im[k] = si[k] * g;
            }
            // A real signal back: half the work of a full inverse transform.
            fft.inverseReal(re, im, synth);
            float scale = 1f / n;
            for (int i = 0; i < n; i++) ola[i] += synth[i] * window[i] * scale;
        }
        System.arraycopy(ola, 0, vocal, 0, hop);
        System.arraycopy(ola, hop, ola, 0, n - hop);
        java.util.Arrays.fill(ola, n - hop, n, 0f);
    }
}
