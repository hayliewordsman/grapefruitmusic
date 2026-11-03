package com.grapefruitmusic.playback;

/**
 * Plays sound faster or slower without changing its pitch: WSOLA (waveform-similarity overlap-add),
 * the method SoundTouch's TDStretch uses. The sound is cut into 40 ms pieces; each is laid 30 ms
 * after the last, overlapping it by 10 ms, but taken from the input speed × 30 ms on, at the spot
 * within the next 15 ms whose waveform best continues the last piece, so the joins don't click or
 * warble. Speech (podcasts, audiobooks) sounds natural from 0.5× to 2.5×; music, a little smeared
 * on sustained notes at the far ends.
 *
 * <p>Light enough for Android 4.2 phones: the search compares every fourth sample of the two
 * channels' sum at every fourth offset (about 0.6 million integer multiply-adds a second of
 * sound), then tries the offsets either side of the best one. Interleaved 16-bit PCM in and out,
 * mono or stereo. One thread.
 */
final class TimeStretch {
    private static final double SEQUENCE_S = 0.040;
    private static final double OVERLAP_S = 0.010;
    private static final double SEEK_S = 0.015;

    private int channels = 2;
    private float speed = 1f;
    /** In frames. */
    private int sequence;
    private int overlap;
    private int seek;

    /** Input not yet used, interleaved. */
    private short[] in = new short[0];
    private int inFrames;
    /** The end of the last piece, to blend into the next. */
    private short[] mid = new short[0];
    private boolean haveMid;
    private double skipFraction;
    /** At high speed a piece can be taken from past what's buffered: input still to drop. */
    private int skipAhead;
    /** Output made by the last put() or flush(), interleaved. */
    private short[] out = new short[0];
    /** The overlap's channel sum, every other sample, for the search. */
    private int[] midSum = new int[0];

    /** Starts afresh for this format and speed (after a seek, a new song, a new speed). */
    void configure(int sampleRate, int channels, float speed) {
        this.channels = Math.max(1, Math.min(2, channels));
        this.speed = Math.max(0.5f, Math.min(2.5f, speed));
        sequence = Math.max(64, (int) Math.round(sampleRate * SEQUENCE_S));
        overlap = Math.max(16, (int) Math.round(sampleRate * OVERLAP_S)) & ~1;
        seek = Math.max(16, (int) Math.round(sampleRate * SEEK_S));
        if (mid.length < overlap * this.channels) mid = new short[overlap * this.channels];
        if (midSum.length < overlap / STRIDE + 1) midSum = new int[overlap / STRIDE + 1];
        inFrames = 0;
        haveMid = false;
        skipFraction = 0;
        skipAhead = 0;
    }

    float speed() {
        return speed;
    }

    short[] output() {
        return out;
    }

    /** Adds samples (interleaved, a whole number of frames); returns how many samples output() holds. */
    int put(short[] pcm, int offset, int samples) {
        int frames = samples / channels;
        if (skipAhead > 0) {
            int drop = Math.min(frames, skipAhead);
            skipAhead -= drop;
            frames -= drop;
            offset += drop * channels;
        }
        ensureIn(inFrames + frames);
        System.arraycopy(pcm, offset, in, inFrames * channels, frames * channels);
        inFrames += frames;
        return run();
    }

    /** The end of the sound: what's left, as it is. Returns how many samples output() holds. */
    int flush() {
        int frames = (haveMid ? overlap : 0) + inFrames;
        ensureOut(frames * channels);
        int o = 0;
        if (haveMid) {
            System.arraycopy(mid, 0, out, 0, overlap * channels);
            o = overlap * channels;
        }
        System.arraycopy(in, 0, out, o, inFrames * channels);
        int total = o + inFrames * channels;
        inFrames = 0;
        haveMid = false;
        return total;
    }

    private int run() {
        int ch = channels;
        int step = sequence - overlap;
        int body = sequence - 2 * overlap;
        // At most this many pieces fit in what's buffered (each takes at least speed × step).
        int most = inFrames / Math.max(1, (int) (speed * step)) + 1;
        ensureOut(most * step * ch);
        int o = 0;
        int at = 0;
        // Each piece needs the seek range and a whole sequence after where it may start.
        while (inFrames - at >= seek + sequence && o + step * ch <= out.length) {
            int start = at + (haveMid ? bestOffset(at) : 0);
            if (haveMid) {
                // Blend the last piece's end into this one's start.
                for (int i = 0; i < overlap; i++) {
                    int a = overlap - i;
                    for (int c = 0; c < ch; c++) {
                        out[o++] = (short) ((mid[i * ch + c] * a + in[(start + i) * ch + c] * i) / overlap);
                    }
                }
            } else {
                System.arraycopy(in, start * ch, out, o, overlap * ch);
                o += overlap * ch;
            }
            System.arraycopy(in, (start + overlap) * ch, out, o, body * ch);
            o += body * ch;
            System.arraycopy(in, (start + sequence - overlap) * ch, mid, 0, overlap * ch);
            haveMid = true;
            // The next piece is taken from speed × step further on (fractions carried).
            double advance = speed * step + skipFraction;
            int skip = (int) advance;
            skipFraction = advance - skip;
            at += skip;
        }
        // Keep what the next pieces need.
        if (at > inFrames) {
            skipAhead = at - inFrames;
            at = inFrames;
        }
        int keep = inFrames - at;
        System.arraycopy(in, at * ch, in, 0, keep * ch);
        inFrames = keep;
        return o;
    }

    /** Samples compared, and offsets tried first: every fourth (speech sits well below 5 kHz). */
    private static final int STRIDE = 4;

    /** The offset within the seek range whose start best matches the last piece's end. */
    private int bestOffset(int at) {
        int ch = channels;
        int n = overlap / STRIDE;
        for (int k = 0; k < n; k++) {
            int i = STRIDE * k * ch;
            midSum[k] = ch == 2 ? mid[i] + mid[i + 1] : mid[i];
        }
        int best = 0;
        double bestScore = -Double.MAX_VALUE;
        for (int d = 0; d < seek; d += STRIDE) {
            double score = score(at + d, n);
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        // Then each offset either side of the coarse best.
        int from = Math.max(0, best - STRIDE + 1);
        int to = Math.min(seek - 1, best + STRIDE - 1);
        int coarse = best;
        for (int d = from; d <= to; d++) {
            if (d == coarse) continue;
            double score = score(at + d, n);
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    /** Cross-correlation of the last piece's end with the input from frame start, over its energy. */
    private double score(int start, int n) {
        int ch = channels;
        long dot = 0;
        long energy = 1;
        int step = STRIDE * ch;
        int i = start * ch;
        for (int k = 0; k < n; k++, i += step) {
            int s = ch == 2 ? in[i] + in[i + 1] : in[i];
            dot += (long) midSum[k] * s;
            energy += (long) s * s;
        }
        return dot / Math.sqrt(energy);
    }

    private void ensureIn(int frames) {
        if (in.length < frames * channels) {
            short[] bigger = new short[Math.max(frames * channels, in.length * 2)];
            System.arraycopy(in, 0, bigger, 0, inFrames * channels);
            in = bigger;
        }
    }

    private void ensureOut(int samples) {
        if (out.length < samples) out = new short[samples];
    }
}
