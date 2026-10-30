package com.grapefruitmusic.playback;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Stereo vocal reduction with a small, quantized GPL-3.0-or-later neural separator. Four spectral
 * views (left, right, mid and side), short context and running memories identify vocal energy;
 * independent channel masks retain each channel's original phase. Processing is bounded and
 * allocation-free per sample/frame after initialization. Trained only on attributed CC BY 4.0
 * seven-second stem excerpts; see tools/karaoke/README-v4.md.
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
        final float maskPower;

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
            if (in.readInt() != 5) throw new IOException("unknown karaoke model version");
            n = in.readInt();
            hop = in.readInt();
            bands = in.readInt();
            past = in.readInt();
            future = in.readInt();
            fmin = in.readFloat();
            fmax = in.readFloat();
            eps = in.readFloat();
            maskPower = in.readFloat();
            if (n != 2048
                    || hop != 1024
                    || bands != 128
                    || past != 3
                    || future != 1
                    || !(eps > 0 && eps < 1)
                    || !(maskPower >= 1 && maskPower <= 3))
                throw new IOException("invalid karaoke model dimensions");
            int memories = in.readInt();
            if (memories != 2) throw new IOException("invalid karaoke context");
            emas = floats(in, memories);
            mean = floats(in, 4 * bands);
            std = floats(in, 4 * bands);
            enc = new Layer(in);
            ctx = new Layer(in);
            mid = new Layer(in);
            out = new Layer(in);
            for (float d : emas)
                if (!(d > 0 && d < 1)) throw new IOException("invalid karaoke memory");
            for (int i = 0; i < std.length; i++)
                if (!(std[i] > 0)
                        || Float.isInfinite(std[i])
                        || Float.isNaN(mean[i])
                        || Float.isInfinite(mean[i]))
                    throw new IOException("invalid karaoke normalization");
            if (enc.ins != 4 * bands
                    || ctx.ins != (past + 1 + future + emas.length) * enc.outs
                    || mid.ins != ctx.outs
                    || out.ins != mid.outs
                    || out.outs != 2 * bands) {
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
            if (outs <= 0 || outs > 512 || ins <= 0 || ins > 1024)
                throw new IOException("invalid karaoke layer dimensions");
            scale = floats(in, outs);
            bias = floats(in, outs);
            for (int i = 0; i < outs; i++)
                if (!(scale[i] > 0)
                        || Float.isInfinite(scale[i])
                        || Float.isNaN(bias[i])
                        || Float.isInfinite(bias[i]))
                    throw new IOException("invalid karaoke layer values");
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

    /**
     * How long the output takes to start after a reset: then it fades in over this many samples.
     */
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
    private boolean previousHalfMono = true, currentHalfMono = true;
    private final boolean[] monoSpectra;
    private long frames;
    private final float[] re;
    private final float[] im;
    private final float[] synth;
    private final float[] powerMid;
    private final float[] powerSide;
    private final float[] powerLeft, powerRight;
    private final float[] features;
    private final float[] encoded;
    // The encoded frames from past back to future ahead (a ring), and each one's mid spectrum.
    private final float[] history;

    /** The encoding's moving averages, one after another (up to the newest frame). */
    private final float[] averages;

    private final float[][] leftRe, leftIm, rightRe, rightIm;
    private final float[] context;
    private final float[] hidden1;
    private final float[] hidden2;
    private final float[] mask;
    // Synthesis: the vocal estimate, overlap-added; vocal holds the hop being played.
    private final float[][] ola;
    private final float[][] vocal;
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
        for (int b = 0; b < model.bands; b++)
            centre[b] = unmel(lo + (hi - lo) * b / (model.bands - 1));
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
        powerLeft = new float[bins];
        powerRight = new float[bins];
        features = new float[4 * model.bands];
        encoded = new float[model.enc.outs];
        history = new float[model.context() * model.enc.outs];
        averages = new float[model.emas.length * model.enc.outs];
        monoSpectra = new boolean[model.future + 1];
        leftRe = new float[model.future + 1][bins];
        leftIm = new float[model.future + 1][bins];
        rightRe = new float[model.future + 1][bins];
        rightIm = new float[model.future + 1][bins];
        context = new float[model.ctx.ins];
        hidden1 = new float[model.ctx.outs];
        hidden2 = new float[model.mid.outs];
        mask = new float[2 * model.bands];
        ola = new float[2][n];
        vocal = new float[2][hop];
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
        float vl = vocal[0][vocalPos] * smoothed;
        float vr = vocal[1][vocalPos++] * smoothed;
        float gain =
                played < latency
                        ? 0
                        : played < latency + FADE ? (played - latency) / (float) FADE : 1;
        played++;
        outL = (dryL[dryPos] - vl) * gain;
        outR = (dryR[dryPos] - vr) * gain;
        dryL[dryPos] = l;
        dryR[dryPos] = r;
        if (++dryPos == latency) dryPos = 0;

        anaMid[n - hop + fill] = (l + r) * 0.5f;
        anaSide[n - hop + fill] = (l - r) * 0.5f;
        if (l != r) currentHalfMono = false;
        if (++fill == hop) {
            frame();
            fill = 0;
            vocalPos = 0;
        }
    }

    /**
     * A hop's worth of samples arrived: analyse the newest window, finish the one future frames
     * ago.
     */
    private void frame() {
        int bands = m.bands, bins = n / 2 + 1;
        long j = frames++;
        for (int i = 0; i < n; i++) {
            re[i] = anaMid[i] * window[i];
            im[i] = anaSide[i] * window[i];
        }
        System.arraycopy(anaMid, hop, anaMid, 0, n - hop);
        System.arraycopy(anaSide, hop, anaSide, 0, n - hop);
        fft.transform(re, im, false);
        int slot = (int) (j % (m.future + 1));
        monoSpectra[slot] = previousHalfMono && currentHalfMono;
        previousHalfMono = currentHalfMono;
        currentHalfMono = true;
        for (int k = 0; k < bins; k++) {
            int c = (n - k) % n;
            float mr = (re[k] + re[c]) * .5f, mi = (im[k] - im[c]) * .5f;
            float sr = (im[k] + im[c]) * .5f, si = (re[c] - re[k]) * .5f;
            float lr = mr + sr, li = mi + si, rr = mr - sr, ri = mi - si;
            leftRe[slot][k] = lr;
            leftIm[slot][k] = li;
            if (monoSpectra[slot]) {
                rr = lr;
                ri = li;
            }
            rightRe[slot][k] = rr;
            rightIm[slot][k] = ri;
            powerLeft[k] = lr * lr + li * li;
            powerRight[k] = rr * rr + ri * ri;
            powerMid[k] = mr * mr + mi * mi;
            powerSide[k] = sr * sr + si * si;
        }
        for (int b = 0; b < bands; b++) {
            double el = 0, er = 0, em = 0, es = 0;
            float[] w = weight[b];
            int start = first[b];
            for (int i = 0; i < w.length; i++) {
                int k = start + i;
                el += w[i] * powerLeft[k];
                er += w[i] * powerRight[k];
                em += w[i] * powerMid[k];
                es += w[i] * powerSide[k];
            }
            features[b] = ((float) Math.log(el + m.eps) - m.mean[b]) / m.std[b];
            features[bands + b] =
                    ((float) Math.log(er + m.eps) - m.mean[bands + b]) / m.std[bands + b];
            features[2 * bands + b] =
                    ((float) Math.log(em + m.eps) - m.mean[2 * bands + b]) / m.std[2 * bands + b];
            features[3 * bands + b] =
                    ((float) Math.log(es + m.eps) - m.mean[3 * bands + b]) / m.std[3 * bands + b];
        }
        int enc = m.enc.outs, ring = m.context();
        m.enc.apply(features, encoded, true);
        System.arraycopy(encoded, 0, history, (int) (j % ring) * enc, enc);
        for (int e = 0; e < m.emas.length; e++) {
            float a = m.emas[e], b = 1 - a;
            int at = e * enc;
            for (int i = 0; i < enc; i++)
                averages[at + i] = j == 0 ? encoded[i] : a * averages[at + i] + b * encoded[i];
        }
        long t = j - m.future;
        if (t >= 0) {
            for (int c = 0; c < ring; c++) {
                long f = t - m.past + c;
                if (f < 0) java.util.Arrays.fill(context, c * enc, (c + 1) * enc, 0);
                else System.arraycopy(history, (int) (f % ring) * enc, context, c * enc, enc);
            }
            System.arraycopy(averages, 0, context, ring * enc, averages.length);
            m.ctx.apply(context, hidden1, true);
            m.mid.apply(hidden1, hidden2, true);
            m.out.apply(hidden2, mask, false);
            for (int b = 0; b < mask.length; b++) mask[b] = (float) (1 / (1 + Math.exp(-mask[b])));
            int ts = (int) (t % (m.future + 1));
            for (int channel = 0; channel < 2; channel++) {
                float[] real = channel == 0 ? leftRe[ts] : rightRe[ts];
                float[] imaginary = channel == 0 ? leftIm[ts] : rightIm[ts];
                int offset = channel * bands;
                for (int k = 0; k < bins; k++) {
                    int b = spreadBand[k];
                    float lo = spreadLow[k], hi = spreadHigh[k];
                    float g = mask[offset + b] * lo + (hi != 0 ? mask[offset + b + 1] * hi : 0);
                    if (monoSpectra[ts]) {
                        float other =
                                mask[(1 - channel) * bands + b] * lo
                                        + (hi != 0 ? mask[(1 - channel) * bands + b + 1] * hi : 0);
                        g = (g + other) * .5f;
                    }
                    if (m.maskPower == 2) g *= g;
                    else if (m.maskPower == 1.5f) g *= Math.sqrt(g);
                    else if (m.maskPower != 1) g = (float) Math.pow(g, m.maskPower);
                    re[k] = real[k] * g;
                    im[k] = imaginary[k] * g;
                }
                fft.inverseReal(re, im, synth);
                for (int i = 0; i < n; i++) ola[channel][i] += synth[i] * window[i] / n;
            }
        }
        for (int channel = 0; channel < 2; channel++) {
            System.arraycopy(ola[channel], 0, vocal[channel], 0, hop);
            System.arraycopy(ola[channel], hop, ola[channel], 0, n - hop);
            java.util.Arrays.fill(ola[channel], n - hop, n, 0);
        }
    }
}
