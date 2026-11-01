package com.grapefruitmusic.playback;

/**
 * Live Mode: the song as if played to a stadium. The crowd cheers before the first note, is there
 * through the song (and cheers when a big moment arrives), sings along with the lead vocal, and gives
 * an ovation when the song ends. The song and the crowd's singing ring in the stadium.
 *
 * <p>The crowd itself is pre-rendered ({@link LiveCrowd}). The singing and the stadium's
 * reverberation run at about 11kHz (a quarter of the sample rate, usually): a distant crowd and a
 * stadium's echo have little treble to lose, and it keeps the per-frame work small enough for
 * Android 4 tablets. Allocation-free once made. Pure Java (unit tested).
 */
final class LiveStage {
    // Levels at full strength, for a song of typical loudness. In live_crowd.bin the opening and
    // closing sections peak at about -19dBFS (over a second) and the bed runs at -18dBFS.
    private static final float BED_GAIN = 0.22f;     // the crowd under the song, about -31dBFS
    private static final float OPEN_GAIN = 1.6f;     // the opening cheer peaks about -15dBFS
    private static final float CLOSE_GAIN = 3.0f;    // the ovation peaks about -10dBFS
    private static final float REACT_GAIN = 0.55f;   // a cheer for a big moment
    private static final float SONG_SEND = 0.2f;     // how much of the song rings in the stadium
    private static final float CHOIR_DIRECT = 0.35f; // the crowd's singing, heard directly...
    private static final float CHOIR_SEND = 0.8f;    // ...and in the stadium
    /** RMS of a typical song's mid channel; the crowd is louder for louder songs (and the reverse). */
    private static final float TYPICAL_RMS = 0.15f;
    private static final int CONTROL = 64;

    /** The crowd's loudness as the last song ended, for the next song's opening cheer. */
    private static volatile float lastScale = 1f;

    private static final float[] SINE = new float[257];

    static {
        for (int i = 0; i <= 256; i++) SINE[i] = (float) Math.sin(2 * Math.PI * i / 256);
    }

    private final int rate;
    private final LiveCrowd crowd;
    double outL, outR;

    // Song time in frames: negative while the crowd cheers before the first note.
    private long songFrame;
    private long durationFrames = -1;
    private boolean outro;
    /** A long fade-out was noticed: the song frame to start the ovation at, so it lasts past the end. */
    private long outroAt = Long.MAX_VALUE;

    private float amount;
    private final float glide;
    private float power;
    private float clipL, clipR;

    // The song's loudness (mean square of the mid channel), slow and fast, and its recent peak.
    private float slowMs = TYPICAL_RMS * TYPICAL_RMS;
    private float fastMs = TYPICAL_RMS * TYPICAL_RMS;
    private float longMs = TYPICAL_RMS * TYPICAL_RMS;
    private float loudPeak;
    // The lead vocal's loudness (fast, and its recent peak), summed at the stadium's rate.
    private float voicePower;
    private int voiceCount;
    private float voiceMs, voicePeak;
    private final float voiceCoef, voiceFall;
    private final float slowCoef, fastCoef, longCoef, peakFall;
    private float scale = lastScale;
    private final float scaleCoef;
    private int control;
    private int quietFrames;
    /** The fast loudness every half second, the last two seconds: is the song fading out? */
    private final float[] history = new float[4];
    private int historyAt;
    private int historyCount;
    private final int endWindow, endHold, endHoldEarly;
    /** For tests: the song frame the ovation started at (or Long.MIN_VALUE), and cheers so far. */
    long ovationFrame = Long.MIN_VALUE;
    int reactions;
    private int reactCooldown;
    private int random = 0x2545f491;

    // Crowd sounds: positions in 20.12 fixed point, in their own frames.
    private int bedCursor;
    private final int bedStep;
    private final int bedEnd;
    private float bedLevel, bedTarget = 1;
    private final float bedCoef;
    private int openCursor = Integer.MAX_VALUE;
    private final int openStep;
    private final int openEnd;
    private int closeCursor = Integer.MAX_VALUE;
    private final int closeStep;
    private final int closeEnd;
    private int reactCursor = Integer.MAX_VALUE;
    private int reactEnd;
    private int reactFadeAt;
    private float reactLevel, reactTarget;
    private final float reactAttack, reactRelease;

    // From the song to the stadium: decimated by factor (one pole, then averaged).
    private final int factor;
    private final float invFactor;
    private final float decimate;
    private int phase;
    private float lpSong, lpVoice, sumSong, sumVoice;
    private float wetL, wetR, lastWetL, lastWetR;

    // Stadium time (slowRate).
    private final float hp, band, envCoef, fastEnvCoef;
    private float songLow, voiceLow, voiceHigh, sideHigh, sideLow2, songHigh2;
    private float eMid, eSide, eFast, eSlow;
    private float choirGain, choirTarget = 1;
    private final float choirCoef;

    // The crowd's singing: six voices, each a little late and a little out of tune, in a ring.
    private final float[] ring;
    private final int ringMask;
    private int ringPos;
    private final float[] base = new float[6], depth = new float[6], lfo = new float[6], lfoStep = new float[6];
    private static final float[] PAN_L = {0.38f, 0.18f, 0.33f, 0.24f, 0.40f, 0.09f};
    private static final float[] PAN_R = {0.15f, 0.36f, 0.25f, 0.34f, 0.04f, 0.40f};

    // The stadium: two diffusers into an eight-line feedback delay network.
    private final float[] ap1, ap2;
    private int ap1Pos, ap2Pos;
    private final float[][] lines = new float[8][];
    private final int[] linePos = new int[8];
    private final float[] lineGain = new float[8], lineLow = new float[8];
    private final float damp;

    LiveStage(int rate, LiveCrowd crowd, long songFrame, long durationFrames) {
        this.rate = rate;
        this.crowd = crowd;
        glide = coef(rate, 0.025);
        slowCoef = coef(rate / (float) CONTROL, 1.5);
        fastCoef = coef(rate / (float) CONTROL, 0.3);
        longCoef = coef(rate / (float) CONTROL, 8);
        // The peak falls half a dB a second (power): slow enough that a fade-out shows.
        peakFall = (float) Math.pow(10, -0.05 * CONTROL / rate);
        voiceCoef = coef(rate / (float) CONTROL, 0.15);
        voiceFall = (float) Math.pow(10, -0.025 * CONTROL / rate);
        scaleCoef = coef(rate / (float) CONTROL, 3);
        bedCoef = coef(rate / (float) CONTROL, 1.5);
        reactAttack = coef(rate / (float) CONTROL, 0.25);
        reactRelease = coef(rate / (float) CONTROL, 0.35);
        choirCoef = coef(rate / (float) CONTROL, 0.4);
        endWindow = rate * 15;
        endHold = rate * 3 / 10;
        endHoldEarly = rate * 6 / 5;
        reactCooldown = rate * 15;

        if (crowd != null) {
            bedStep = step(crowd.bed.rate, rate);
            bedEnd = crowd.bed.frames << 12;
            openStep = step(crowd.opening.rate, rate);
            openEnd = crowd.opening.frames << 12;
            closeStep = step(crowd.closing.rate, rate);
            closeEnd = crowd.closing.frames << 12;
        } else {
            bedStep = openStep = closeStep = bedEnd = 0;
            openEnd = closeEnd = 0;
        }

        factor = Math.max(1, Math.round(rate / 11025f));
        invFactor = 1f / factor;
        float slowRate = rate / (float) factor;
        decimate = (float) (1 - Math.exp(-2 * Math.PI * Math.min(4000, slowRate * 0.4) / rate));
        hp = (float) (1 - Math.exp(-2 * Math.PI * 200 / slowRate));
        band = (float) (1 - Math.exp(-2 * Math.PI * Math.min(3000, slowRate * 0.35) / slowRate));
        envCoef = coef(slowRate, 0.04);
        fastEnvCoef = coef(slowRate, 0.004);

        int size = 1;
        while (size < slowRate * 0.2f) size <<= 1;
        ring = new float[size];
        ringMask = size - 1;
        float[] baseMs = {31, 44, 58, 76, 95, 121};
        float[] lfoHz = {0.23f, 0.29f, 0.35f, 0.19f, 0.41f, 0.27f};
        for (int i = 0; i < 6; i++) {
            base[i] = baseMs[i] * slowRate / 1000;
            // About 15 cents of pitch wander: the delay's slope peaks at 2*pi*f*depth.
            depth[i] = (float) (0.0087 / (2 * Math.PI * lfoHz[i]) * slowRate);
            lfo[i] = i * 41f;
            lfoStep[i] = lfoHz[i] * 256 / slowRate;
        }

        ap1 = new float[Math.max(1, Math.round(slowRate * 0.0047f))];
        ap2 = new float[Math.max(1, Math.round(slowRate * 0.0071f))];
        float[] lineMs = {37.3f, 42.9f, 51.1f, 58.7f, 66.9f, 77.3f, 87.1f, 99.7f};
        float decaySeconds = 2.4f;
        for (int i = 0; i < 8; i++) {
            int n = Math.max(1, Math.round(lineMs[i] * slowRate / 1000));
            lines[i] = new float[n];
            // Each trip round a line loses its share of 60dB per decaySeconds; 1/sqrt(8) keeps the
            // Hadamard mix lossless.
            lineGain[i] = (float) (Math.pow(10, -3.0 * n / (decaySeconds * slowRate)) / Math.sqrt(8));
        }
        damp = (float) (1 - Math.exp(-2 * Math.PI * Math.min(3200, slowRate * 0.38) / slowRate));

        seek(songFrame);
        setDuration(durationFrames);
    }

    private static float coef(float rate, double seconds) {
        return (float) (1 - Math.exp(-1.0 / (rate * seconds)));
    }

    private static int step(int from, int to) {
        return (int) (((long) from << 12) / to);
    }

    /** Frames of cheering to play before the song, when it starts from the beginning. */
    int preRollFrames() {
        return crowd == null ? 0 : (int) ((long) crowd.opening.marker * rate / crowd.opening.rate);
    }

    /** The crowd cheers for preRollFrames() before the song (which then starts at frame 0). */
    void startPreRoll() {
        if (crowd == null) return;
        songFrame = -preRollFrames();
        openCursor = 0;
        bedLevel = 0;
    }

    /** Song position in frames, at the start and after a seek. */
    void seek(long frame) {
        songFrame = frame;
        outro = false;
        outroAt = Long.MAX_VALUE;
        closeCursor = Integer.MAX_VALUE;
        reactCursor = Integer.MAX_VALUE;
        quietFrames = 0;
        loudPeak = 0;
        voiceMs = voicePeak = 0;
        java.util.Arrays.fill(history, 0);
        bedTarget = 1;
        openCursor = Integer.MAX_VALUE;
        if (crowd != null && frame >= 0) {
            long at = ((long) crowd.opening.marker << 12) + (frame * crowd.opening.rate << 12) / rate;
            if (at < openEnd) openCursor = (int) at;
        }
    }

    void setDuration(long frames) {
        durationFrames = frames;
    }

    /** The song's last sample has gone in: the ovation (if it hasn't started). Frames left of it. */
    int finish() {
        if (crowd == null) return 0;
        if (!outro) startOutro();
        if (closeCursor >= closeEnd) return 0;
        return (int) (((long) (closeEnd - closeCursor) + closeStep - 1) / closeStep) + rate / 50;
    }

    boolean ovation() {
        return outro;
    }

    /**
     * The song is ending, left frames from the end of the file. The ovation starts now, or (in a long
     * fade-out) late enough to go on past the end by a couple of seconds.
     */
    private void end(long left) {
        if (outro || outroAt != Long.MAX_VALUE) return;
        long ovation = crowd != null ? ((long) crowd.closing.frames * rate / crowd.closing.rate) : 0;
        long latest = ovation - rate * 5L / 2;
        if (left > latest && latest > 0) outroAt = durationFrames - latest;
        else startOutro();
    }

    private void startOutro() {
        outro = true;
        ovationFrame = songFrame;
        closeCursor = 0;
        bedTarget = 0;
        reactTarget = 0;
        lastScale = scale;
    }

    private int nextRandom() {
        random ^= random << 13;
        random ^= random >>> 17;
        random ^= random << 5;
        return random;
    }

    /**
     * One frame of the song (left, right) and, with separated true, the lead vocal from Karaoke Mode's
     * separator (else the song's centre is used as a rough stand-in). strength is Live Mode's, 0 to 1.
     */
    void process(double left, double right, float vocal, boolean separated, float strength) {
        amount += (strength - amount) * glide;
        float l = (float) left;
        float r = (float) right;
        float mid = (l + r) * 0.5f;
        power += mid * mid;
        if (--control <= 0) control();

        // Into the stadium, a factor-th of the time.
        lpSong += (mid - lpSong) * decimate;
        lpVoice += ((separated ? vocal : (l - r) * 0.5f) - lpVoice) * decimate;
        sumSong += lpSong;
        sumVoice += lpVoice;
        if (++phase == factor) {
            phase = 0;
            lastWetL = wetL;
            lastWetR = wetR;
            stadium(sumSong * invFactor, sumVoice * invFactor, separated);
            sumSong = sumVoice = 0;
        }
        float t = phase * invFactor;
        float addL = lastWetL + (wetL - lastWetL) * t;
        float addR = lastWetR + (wetR - lastWetR) * t;

        // The crowd.
        if (crowd != null) {
            float level = bedLevel * BED_GAIN * scale;
            if (level > 0) {
                byte[] d = crowd.bed.data;
                int at = bedCursor >>> 12;
                int next = at + 1 == crowd.bed.frames ? 0 : at + 1;
                float f = (bedCursor & 4095) * (1f / 4096);
                float a = LiveCrowd.LEVELS[d[at * 2] & 0xff];
                float b = LiveCrowd.LEVELS[d[at * 2 + 1] & 0xff];
                addL += (a + (LiveCrowd.LEVELS[d[next * 2] & 0xff] - a) * f) * level;
                addR += (b + (LiveCrowd.LEVELS[d[next * 2 + 1] & 0xff] - b) * f) * level;
            }
            bedCursor += bedStep;
            if (bedCursor >= bedEnd) bedCursor -= bedEnd;
            if (openCursor < openEnd) {
                addClip(crowd.opening, openCursor, OPEN_GAIN * scale);
                openCursor += openStep;
            }
            if (closeCursor < closeEnd) {
                addClip(crowd.closing, closeCursor, CLOSE_GAIN * scale);
                closeCursor += closeStep;
            }
            if (reactCursor < reactEnd) {
                addClip(crowd.opening, reactCursor, REACT_GAIN * reactLevel * scale);
                reactCursor += openStep;
            }
        }
        outL = left + amount * (addL + clipL);
        outR = right + amount * (addR + clipR);
        clipL = clipR = 0;
        songFrame++;
    }

    private void addClip(LiveCrowd.Clip clip, int cursor, float gain) {
        byte[] d = clip.data;
        int at = cursor >>> 12;
        int next = at + 1 < clip.frames ? at + 1 : at;
        float f = (cursor & 4095) * (1f / 4096);
        float a = LiveCrowd.LEVELS[d[at * 2] & 0xff];
        float b = LiveCrowd.LEVELS[d[at * 2 + 1] & 0xff];
        clipL += (a + (LiveCrowd.LEVELS[d[next * 2] & 0xff] - a) * f) * gain;
        clipR += (b + (LiveCrowd.LEVELS[d[next * 2 + 1] & 0xff] - b) * f) * gain;
    }

    /** Every CONTROL frames: loudness, the crowd's mood, and whether the song is ending. */
    private void control() {
        control = CONTROL;
        float block = power * (1f / CONTROL);
        power = 0;
        fastMs += (block - fastMs) * fastCoef;
        float voiceBlock = voiceCount > 0 ? voicePower / voiceCount : 0;
        voicePower = 0;
        voiceCount = 0;
        voiceMs += (voiceBlock - voiceMs) * voiceCoef;
        float voiceDecayed = voicePeak * voiceFall;
        voicePeak = voiceMs > voiceDecayed ? voiceMs : voiceDecayed;
        // Still singing: within 10dB of the vocal's recent peak. The band dropping out under the last
        // line isn't the end of the song.
        boolean singing = voiceMs > voicePeak * 0.1f && voiceMs > 1e-6f;
        if (songFrame >= 0 && !outro) {
            slowMs += (block - slowMs) * slowCoef;
            longMs += (block - longMs) * longCoef;
            float peak = loudPeak * peakFall;
            loudPeak = fastMs > peak ? fastMs : peak;
            if (slowMs > 1e-5f) {
                float target = (float) Math.sqrt(slowMs) / TYPICAL_RMS;
                target = target < 0.5f ? 0.5f : target > 1.5f ? 1.5f : target;
                scale += (target - scale) * scaleCoef;
            }
            // Louder parts (a chorus) get more of the crowd singing.
            float section = slowMs > 1e-6f ? fastMs / slowMs : 0;
            choirTarget = section < 0.6f ? 0.6f : section > 1.6f ? 1.6f : section;

            if (++historyCount >= rate / 2 / CONTROL) {
                historyCount = 0;
                history[historyAt] = fastMs;
                historyAt = (historyAt + 1) & 3;
            }
            if (durationFrames > 0) {
                long left = durationFrames - songFrame;
                // Fading out: falling all through the last two seconds, 4dB in all. (A sudden drop
                // into a break isn't: it doesn't keep falling.)
                float twoAgo = history[historyAt];
                float oneAgo = history[(historyAt + 2) & 3];
                boolean fading = oneAgo < twoAgo * 0.8f && fastMs < oneAgo * 0.8f && fastMs < twoAgo * 0.4f;
                if (left <= endWindow && fading && !singing) end(left);
                if (left <= endWindow && !outro && outroAt == Long.MAX_VALUE) {
                    // A fade-out, a last chord dying away, or silence before the file ends: 9dB down
                    // on the song's recent peak, with the singing stopped, for a moment near the end,
                    // or for over a second before that (so a break before the last chorus doesn't end
                    // it).
                    boolean quiet = (fastMs < loudPeak * 0.125f || fastMs < 1e-5f) && !singing;
                    quietFrames = quiet ? quietFrames + CONTROL : 0;
                    int hold = left <= rate * 3L ? endHold : endHoldEarly;
                    if (quietFrames >= hold || left <= rate / 5) end(left);
                }
                if (songFrame >= outroAt) startOutro();
            }
            reactCooldown -= CONTROL;
            if (reactCooldown <= 0 && crowd != null && reactCursor >= reactEnd && songFrame > rate * 15L
                    && (durationFrames <= 0 || durationFrames - songFrame > endWindow + rate * 5L)
                    && longMs > 1e-4f && slowMs > longMs * 2) {
                react();
            }
        }
        bedLevel += (bedTarget - bedLevel) * bedCoef;
        if (reactCursor < reactEnd) {
            if (reactCursor >= reactFadeAt) reactTarget = 0;
            reactLevel += (reactTarget - reactLevel) * (reactTarget > reactLevel ? reactAttack : reactRelease);
        }
        choirGain += (choirTarget - choirGain) * choirCoef;
    }

    /** A big moment (the song 3dB louder than over the last few seconds, held): a cheer from part of the crowd. */
    private void react() {
        reactions++;
        reactCooldown = rate * 35;
        LiveCrowd.Clip c = crowd.opening;
        // Part of the opening cheer, from around the moment the song was recognised.
        int[] starts = {c.marker - c.rate * 4 / 5, c.marker - c.rate / 5, c.marker + c.rate * 2 / 5};
        int start = Math.max(0, starts[(nextRandom() >>> 1) % 3]);
        int length = Math.min(c.frames - start, c.rate * 14 / 5);
        reactCursor = start << 12;
        reactEnd = (start + length) << 12;
        reactFadeAt = (start + length - c.rate * 3 / 2) << 12;
        reactLevel = 0;
        reactTarget = 1;
    }

    /** One sample at the stadium's rate: the crowd's singing and the reverberation. */
    private void stadium(float song, float other, boolean separated) {
        // The song, without its lowest bass (which would only muddy the stadium).
        songLow += (song - songLow) * hp;
        float songHigh = song - songLow;
        float voice;
        if (separated) {
            voiceLow += (other - voiceLow) * hp;
            voice = other - voiceLow;
        } else {
            // No separator: the centre of the vocal band, where the side channel is quiet, and not on
            // a drum hit.
            songHigh2 += (songHigh - songHigh2) * band;
            float bandMid = songHigh2;
            voiceLow += (other - voiceLow) * hp;
            sideLow2 += ((other - voiceLow) - sideLow2) * band;
            eMid += (bandMid * bandMid - eMid) * envCoef;
            eSide += (sideLow2 * sideLow2 - eSide) * envCoef;
            eFast += (bandMid * bandMid - eFast) * fastEnvCoef;
            float keep = eMid > 1e-9f ? 1 - 1.5f * eSide / eMid : 0;
            if (eFast > eMid * 4) keep *= 0.2f;
            voice = keep > 0 ? bandMid * (keep < 1 ? keep : 1) : 0;
        }
        voiceHigh += (voice - voiceHigh) * band; // dull, as from far away
        voicePower += voiceHigh * voiceHigh;
        voiceCount++;
        ring[ringPos] = voiceHigh * choirGain;

        float choirL = 0, choirR = 0;
        for (int i = 0; i < 6; i++) {
            float p = lfo[i] + lfoStep[i];
            if (p >= 256) p -= 256;
            lfo[i] = p;
            int k = (int) p;
            float s = SINE[k] + (SINE[k + 1] - SINE[k]) * (p - k);
            float back = ringPos - base[i] - depth[i] * s + ring.length;
            int at = (int) back;
            float f = back - at;
            float a = ring[at & ringMask];
            float v = a + (ring[(at + 1) & ringMask] - a) * f;
            choirL += v * PAN_L[i];
            choirR += v * PAN_R[i];
        }
        ringPos = (ringPos + 1) & ringMask;

        // Diffusers, then the network.
        float in = songHigh * SONG_SEND + (choirL + choirR) * CHOIR_SEND + 1e-18f;
        float d1 = ap1[ap1Pos];
        float w1 = in + d1 * 0.6f;
        ap1[ap1Pos] = w1;
        in = d1 - w1 * 0.6f;
        if (++ap1Pos == ap1.length) ap1Pos = 0;
        float d2 = ap2[ap2Pos];
        float w2 = in + d2 * 0.6f;
        ap2[ap2Pos] = w2;
        in = d2 - w2 * 0.6f;
        if (++ap2Pos == ap2.length) ap2Pos = 0;

        float y0 = lines[0][linePos[0]], y1 = lines[1][linePos[1]], y2 = lines[2][linePos[2]], y3 = lines[3][linePos[3]];
        float y4 = lines[4][linePos[4]], y5 = lines[5][linePos[5]], y6 = lines[6][linePos[6]], y7 = lines[7][linePos[7]];
        float revL = y0 - y2 + y4 - y6;
        float revR = y1 - y3 + y5 - y7;
        // Fast Hadamard transform.
        float a0 = y0 + y1, a1 = y0 - y1, a2 = y2 + y3, a3 = y2 - y3;
        float a4 = y4 + y5, a5 = y4 - y5, a6 = y6 + y7, a7 = y6 - y7;
        float b0 = a0 + a2, b1 = a1 + a3, b2 = a0 - a2, b3 = a1 - a3;
        float b4 = a4 + a6, b5 = a5 + a7, b6 = a4 - a6, b7 = a5 - a7;
        feed(0, b0 + b4, in);
        feed(1, b1 + b5, -in);
        feed(2, b2 + b6, in);
        feed(3, b3 + b7, -in);
        feed(4, b0 - b4, in);
        feed(5, b1 - b5, -in);
        feed(6, b2 - b6, in);
        feed(7, b3 - b7, -in);

        wetL = choirL * CHOIR_DIRECT + revL * 0.5f;
        wetR = choirR * CHOIR_DIRECT + revR * 0.5f;
    }

    private void feed(int i, float mixed, float in) {
        float low = lineLow[i] + (mixed * lineGain[i] - lineLow[i]) * damp;
        lineLow[i] = low;
        float[] line = lines[i];
        int p = linePos[i];
        line[p] = low + in * 0.35f;
        linePos[i] = p + 1 == line.length ? 0 : p + 1;
    }
}
