package com.grapefruitmusic.playback;

/**
 * Mono Audio's mix of the two channels into one, keeping everything that's in either.
 *
 * <p>Plain (L+R)/2 loses sound: what's out of phase between the channels (reverb, room sound,
 * stereo-widened parts) cancels out, a time difference between them (a spaced pair of
 * microphones, the Haas effect) cuts notches into the sound, and an instrument only on one side
 * comes out 6 dB down. Here the sound is cut into overlapping windows (1024 samples, a new one
 * every 512, under a sine window, used again to put it back together: at half-overlap the two add
 * up to exactly 1), and each frequency gets the power of the two channels together,
 * (|L|^2 + |R|^2) / 2. Its phase is the plain mix's, L+R, pulled toward one channel's as the two
 * cancel, so it never falls into a hole: c = |L+R|^2 / (2 (|L|^2 + |R|^2)) is 1 for sound in the
 * middle, 1/2 for a side, 0 for opposite phases, and the phase is that of (L+R)/2 + (1 - c) X,
 * X the louder channel (held until the other is twice as strong, so it doesn't flicker). Sound in
 * the middle (c = 1) comes out exactly as (L+R)/2.
 *
 * <p>Pure Java (unit tested). Not thread-safe; one sample at a time, {@link #latency()} late.
 */
final class MonoMix {
    static final int N = 1024;
    static final int HOP = N / 2;
    private static final float TINY = 1e-20f;
    /** Fades in over 10 ms once the first sound comes through. */
    private static final int FADE = 441;

    private final Fft fft = new Fft(N);
    private final float[] window = new float[N];
    // Analysis: the last N samples of each channel, filled a hop at a time.
    private final float[] anaL = new float[N];
    private final float[] anaR = new float[N];
    private int fill;
    private final float[] re = new float[N];
    private final float[] im = new float[N];
    private final float[] synth = new float[N];
    /** Per frequency, is the right channel the one the phase leans toward? */
    private final boolean[] right = new boolean[N / 2 + 1];
    // Synthesis: overlap-added; mixed holds the hop being played.
    private final float[] ola = new float[N];
    private final float[] mixed = new float[HOP];
    private int pos;
    private long played;

    /** The mix after process(). */
    float out;

    MonoMix() {
        for (int i = 0; i < N; i++) window[i] = (float) Math.sin(Math.PI * i / N);
    }

    /** Samples between a sample going in and coming out. */
    int latency() {
        return N;
    }

    /** Takes one stereo sample (-1 to 1); the mix of the one from latency() ago comes out in out. */
    void process(float l, float r) {
        float gain = played < N ? 0 : played < N + FADE ? (played - N) / (float) FADE : 1;
        played++;
        out = mixed[pos++] * gain;
        anaL[N - HOP + fill] = l;
        anaR[N - HOP + fill] = r;
        if (++fill == HOP) {
            frame();
            fill = 0;
            pos = 0;
        }
    }

    private void frame() {
        // Both channels in one complex FFT: z = l + i r.
        for (int i = 0; i < N; i++) {
            re[i] = anaL[i] * window[i];
            im[i] = anaR[i] * window[i];
        }
        System.arraycopy(anaL, HOP, anaL, 0, N - HOP);
        System.arraycopy(anaR, HOP, anaR, 0, N - HOP);
        fft.transform(re, im, false);
        // Bin k is written in place: its mirror N-k (above N/2) is never written, only read.
        int bins = N / 2 + 1;
        for (int k = 0; k < bins; k++) {
            int c = (N - k) % N;
            float ar = re[k];
            float ai = im[k];
            float cr = re[c];
            float ci = im[c];
            float lr = (ar + cr) * 0.5f;
            float li = (ai - ci) * 0.5f;
            float rr = (ai + ci) * 0.5f;
            float ri = (cr - ar) * 0.5f;
            float pl = lr * lr + li * li;
            float pr = rr * rr + ri * ri;
            float target = (pl + pr) * 0.5f;
            float sr = (lr + rr) * 0.5f;
            float si = (li + ri) * 0.5f;
            if (right[k] ? pl > 2 * pr : pr > 2 * pl) right[k] = !right[k];
            // 1 - c: 0 in the middle, up to 1 as the channels cancel.
            float lean = target > TINY ? 1 - (sr * sr + si * si) / target : 0;
            float dr = sr + lean * (right[k] ? rr : lr);
            float di = si + lean * (right[k] ? ri : li);
            float d = dr * dr + di * di;
            float g = d > TINY ? (float) Math.sqrt(target / d) : 0;
            re[k] = dr * g;
            im[k] = di * g;
        }
        fft.inverseReal(re, im, synth);
        float scale = 1f / N;
        for (int i = 0; i < N; i++) ola[i] += synth[i] * window[i] * scale;
        System.arraycopy(ola, 0, mixed, 0, HOP);
        System.arraycopy(ola, HOP, ola, 0, N - HOP);
        java.util.Arrays.fill(ola, N - HOP, N, 0f);
    }
}
