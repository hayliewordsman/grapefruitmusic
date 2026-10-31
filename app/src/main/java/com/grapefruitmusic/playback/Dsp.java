package com.grapefruitmusic.playback;

/**
 * Grapefruit's own sound processing, for Karaoke Mode, Volume Boost, the Equalizer and Mono Audio:
 * vocal removal, mono mixing, the 10-band equalizer, gain, and a look-ahead limiter that turns peaks down just before they'd clip, so boosted music gets
 * louder instead of distorting (the idea behind Viper4Android's eXtra-Loud). Pure Java (unit tested).
 *
 * <p>The settings may change from any thread while audio is being processed.
 */
public final class Dsp {
    public static final int BOOST_OFF = 0;
    public static final int BOOST_MILD = 1;
    public static final int BOOST_MEDIUM = 2;
    public static final int BOOST_STRONG = 3;
    public static final int BOOST_EXTREME = 4;
    public static final String[] BOOST_NAMES = {"Off", "Mild", "Medium", "Strong", "Extreme"};
    /** Gain in dB for each Volume Boost level. */
    static final double[] BOOST_DB = {0, 4, 7, 10, 14};

    /** Peaks are held under this (about -0.5 dBFS). */
    static final double CEILING = 0.944;
    private static final double LOOKAHEAD_S = 0.004;
    private static final double RELEASE_S = 0.15;

    private volatile int boostLevel;
    private volatile float[] eq;
    private volatile boolean mono;
    private volatile float volume = 1f;
    private volatile boolean live;
    private volatile float liveAmount = 0.5f;
    private LiveAmbience audience;
    private double liveKaraokeMix;
    private boolean liveRunning;
    private volatile boolean karaoke;
    private volatile float karaokeAmount = 1f;
    private volatile VocalRemover.Model karaokeModel;

    private int rate;
    private int channels;

    // Equalizer: a biquad per band (from Equalizer.design; null for a band with nothing to do), run
    // in direct form I with per-band state x1, x2, y1, y2 for each channel.
    private double[][] eqFilters = new double[Equalizer.BANDS][];
    private double[][] eqState;
    /** The curve eqFilters were made for; redesigned when it changes. */
    private float[] eqDesigned;
    private boolean eqStale = true;
    /** Mono: both channels are mixed (keeping all of each), then filtered once on the left channel's state. */
    private MonoMix mixer;
    private double[] frame;
    private double[] input;

    // Karaoke Mode and Mono Audio. Switching either on or off fades the sound out, swaps, then fades
    // back in.
    private VocalRemover remover;
    private float switchGain = 1;
    /** 0 steady, 1 fading out to switch, 2 fading back in. */
    private int switchPhase;

    // Limiter. The sound is delayed by lookahead frames, so the gain can come down before a peak
    // reaches the output. needs holds the gain each delayed frame needs to stay under the ceiling.
    private int lookahead;
    private double[] delay;
    private double[] needs;
    private int pos;
    private long frameNo;
    private double gain = 1;
    private double attack;
    private double release;
    private double[] out;
    // Sliding minimum of the needs of the frames in the delay line (a monotonic queue).
    private double[] qValue;
    private long[] qFrame;
    private int qHead;
    private int qSize;

    public void setBoost(int level) {
        boostLevel = Math.max(BOOST_OFF, Math.min(BOOST_EXTREME, level));
    }

    /** The equalizer curve, dB for each of Equalizer.BANDS (null or flat for none). */
    public void setEq(float[] curve) {
        eq = curve != null ? curve.clone() : null;
    }

    /** Karaoke Mode on or off, how much of the vocal to take away (0 to 1), and its model. */
    public void setKaraoke(boolean on, float amount, VocalRemover.Model model) {
        karaokeAmount = amount;
        if (model != null) karaokeModel = model;
        karaoke = on;
        VocalRemover r = remover;
        if (r != null) r.setAmount(live ? 1f : amount);
    }

    public void setLive(boolean on, float amount) {
        liveAmount = Float.isNaN(amount) ? 0 : Math.max(0, Math.min(1, amount));
        live = on;
    }

    /** Is Karaoke Mode taking effect right now (its model loaded and running)? */
    public boolean karaokeRunning() {
        return remover != null;
    }

    /** Mono Audio: both ears hear the left and right channels mixed together ({@link MonoMix}). */
    public void setMono(boolean on) {
        mono = on;
    }

    /** Volume Match and ducking, 0 to 1, applied before the boost. */
    public void setVolume(float v) {
        volume = v;
    }

    public void setFormat(int sampleRate, int channelCount) {
        rate = sampleRate;
        channels = Math.max(1, channelCount);
        eqFilters = new double[Equalizer.BANDS][];
        eqState = new double[Equalizer.BANDS][channels * 4];
        eqStale = true;
        // Nothing's playing through the old state, so the mix starts as wanted, without a fade.
        mixer = mono && channels == 2 ? new MonoMix() : null;
        audience = new LiveAmbience(rate);
        liveRunning = live && karaokeModel != null;
        liveKaraokeMix = karaoke ? karaokeAmount : 0;
        frame = new double[channels];
        input = new double[channels];
        remover = (karaoke || live) && karaokeModel != null ? newRemover() : null;
        switchGain = 1;
        switchPhase = 0;

        lookahead = Math.max(1, (int) Math.round(LOOKAHEAD_S * rate));
        delay = new double[lookahead * channels];
        needs = new double[lookahead];
        java.util.Arrays.fill(needs, 1);
        out = new double[channels];
        qValue = new double[lookahead + 1];
        qFrame = new long[lookahead + 1];
        qHead = 0;
        qSize = 0;
        pos = 0;
        frameNo = 0;
        gain = 1;
        // Down within the look-ahead; back up over RELEASE_S.
        attack = Math.exp(-3.0 / lookahead);
        release = Math.exp(-1.0 / (RELEASE_S * rate));
    }

    /** Clears filters and limiter (after a seek). */
    public void reset() {
        if (rate > 0) setFormat(rate, channels);
    }

    private VocalRemover newRemover() {
        VocalRemover r = new VocalRemover(karaokeModel, rate);
        r.setAmount(live ? 1f : karaokeAmount);
        return r;
    }

    /** The delay the limiter (and Karaoke Mode and Mono Audio) adds, in frames. */
    public int latencyFrames() {
        VocalRemover r = remover;
        MonoMix m = mixer;
        return lookahead + (r != null ? r.latency() : 0) + (m != null ? m.latency() : 0);
    }

    /** Processes interleaved 16-bit samples in place (delayed by latencyFrames()). */
    public void process(short[] pcm, int offset, int count) {
        if (rate == 0) throw new IllegalStateException("setFormat first");
        float[] curve = eq;
        if (eqStale || curve != eqDesigned) {
            double[][] next = Equalizer.isOn(curve) ? Equalizer.design(curve, rate) : new double[Equalizer.BANDS][];
            for (int b = 0; b < Equalizer.BANDS; b++) {
                // A band coming in starts from silence; one being adjusted carries on smoothly.
                if (eqFilters[b] == null || next[b] == null) java.util.Arrays.fill(eqState[b], 0);
            }
            eqFilters = next;
            eqDesigned = curve;
            eqStale = false;
        }
        int bands = 0;
        double[][] k = new double[Equalizer.BANDS][];
        double[][] ks = new double[Equalizer.BANDS][];
        for (int b = 0; b < Equalizer.BANDS; b++) {
            if (eqFilters[b] == null) continue;
            k[bands] = eqFilters[b];
            ks[bands++] = eqState[b];
        }
        int boost = boostLevel;
        double g = volume * Math.pow(10, BOOST_DB[boost] / 20);
        boolean wantKaraoke = (karaoke || live) && karaokeModel != null;
        boolean wantMono = mono && channels == 2;
        if ((wantKaraoke != (remover != null) || wantMono != (mixer != null)
                || (live && karaokeModel != null) != liveRunning) && switchPhase == 0) switchPhase = 1;
        float fade = 1f / Math.max(1, rate / 100); // 10 ms
        // With no boost, equalizer or vocal removal nothing can get louder than it came in, so it
        // passes through as is. (The mono mix very rarely peaks a little higher than the music
        // did: the limiter still catches anything over full scale.)
        double ceiling = boost > BOOST_OFF || bands > 0 || live || remover != null || switchPhase != 0 ? CEILING : 1;
        int frames = count / channels;
        int i = offset;
        for (int f = 0; f < frames; f++) {
            int base = pos * channels;
            double peak = 0;
            for (int c = 0; c < channels; c++) input[c] = pcm[i + c] / 32768.0;
            if (remover != null) {
                remover.process((float) input[0], (float) input[channels - 1]);
                if (liveRunning) {
                    double vocalL = remover.dryOutL - remover.outL;
                    double vocalR = remover.dryOutR - remover.outR;
                    liveKaraokeMix += ((karaoke ? karaokeAmount : 0) - liveKaraokeMix) * 0.002;
                    double amount = liveKaraokeMix;
                    audience.process(remover.dryOutL - vocalL * amount,
                            remover.dryOutR - vocalR * amount, vocalL, vocalR, liveAmount);
                    input[0] = audience.outL;
                    if (channels > 1) input[1] = audience.outR;
                } else {
                    input[0] = remover.outL;
                    if (channels > 1) input[1] = remover.outR;
                }
            }
            if (mixer != null) {
                mixer.process((float) input[0], (float) input[1]);
                input[0] = mixer.out;
            }
            if (switchPhase != 0) {
                if (switchPhase == 1) {
                    switchGain -= fade;
                    if (switchGain <= 0) {
                        switchGain = 0;
                        boolean want = (karaoke || live) && karaokeModel != null;
                        if (want != (remover != null)) remover = want ? newRemover() : null;
                        liveRunning = live && karaokeModel != null;
                        audience = new LiveAmbience(rate);
                        liveKaraokeMix = karaoke ? karaokeAmount : 0;
                        boolean together = mono && channels == 2;
                        if (together != (mixer != null)) {
                            mixer = together ? new MonoMix() : null;
                            // Mono runs the filters on the left channel's state; the right one picks up from it.
                            for (double[] s : eqState) System.arraycopy(s, 0, s, 4, 4);
                        }
                        switchPhase = 2;
                    }
                } else {
                    switchGain += fade;
                    if (switchGain >= 1) {
                        switchGain = 1;
                        switchPhase = 0;
                    }
                }
                for (int c = 0; c < channels; c++) input[c] *= switchGain;
            }
            boolean together = mixer != null;
            int filtered = together ? 1 : channels;
            for (int c = 0; c < filtered; c++) {
                double x = input[c];
                int o = c * 4;
                for (int b = 0; b < bands; b++) {
                    double[] kb = k[b];
                    double[] s = ks[b];
                    double y = kb[0] * x + kb[1] * s[o] + kb[2] * s[o + 1] - kb[3] * s[o + 2] - kb[4] * s[o + 3];
                    // Fading to silence, the filters would otherwise crawl through tiny (slow) numbers.
                    if (y < 1e-20 && y > -1e-20) y = 0;
                    s[o + 1] = s[o];
                    s[o] = x;
                    s[o + 3] = s[o + 2];
                    s[o + 2] = y;
                    x = y;
                }
                frame[c] = x * g;
            }
            if (together) frame[1] = frame[0];
            for (int c = 0; c < channels; c++) {
                double abs = Math.abs(frame[c]);
                if (abs > peak) peak = abs;
                out[c] = delay[base + c];
                delay[base + c] = frame[c];
            }
            double outNeed = needs[pos];
            double need = peak > ceiling ? ceiling / peak : 1;
            needs[pos] = need;
            pos = pos + 1 == lookahead ? 0 : pos + 1;

            // The lowest need among the frames about to come out.
            while (qSize > 0 && qValue[(qHead + qSize - 1) % qValue.length] >= need) qSize--;
            int back = (qHead + qSize) % qValue.length;
            qValue[back] = need;
            qFrame[back] = frameNo;
            qSize++;
            while (qFrame[qHead] <= frameNo - lookahead) {
                qHead = (qHead + 1) % qValue.length;
                qSize--;
            }
            double target = Math.min(qValue[qHead], outNeed);

            gain = target < gain ? target + (gain - target) * attack : target + (gain - target) * release;
            if (gain > outNeed) gain = outNeed; // never let the frame going out clip
            for (int c = 0; c < channels; c++) {
                long y = Math.round(out[c] * gain * 32768);
                pcm[i + c] = (short) Math.max(-32768, Math.min(32767, y));
            }
            frameNo++;
            i += channels;
        }
    }
}
