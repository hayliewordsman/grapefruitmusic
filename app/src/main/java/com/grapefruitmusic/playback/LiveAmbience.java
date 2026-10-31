package com.grapefruitmusic.playback;

/** Lightweight live mix: baked FOSS crowd texture, vocal-band ensemble and venue reflections. */
final class LiveAmbience {
    private final float[] voice, roomL, roomR;
    private final int mask;
    private final int[] roomDelay;
    private final float[] delay = new float[4], step = new float[4], center = new float[4];
    private final float excursion;
    private final float smooth, attack, release, lowCoefficient, highCoefficient;
    private final int textureStep;
    private final float transientAttack, transientSlow, sendAttack, sendRelease;
    private final int transientHold;
    private float fastEnergy, slowEnergy, echoSend = 1;
    private int hold;
    private int pos, texturePos, random = 0x41b1, control;
    private float amount, envelope, low, high, clap, crowdL, crowdR;
    double outL, outR;

    LiveAmbience(int rate) {
        int size = 1;
        while (size < rate / 2 + 2) size <<= 1;
        mask = size - 1;
        voice = new float[size]; roomL = new float[size]; roomR = new float[size];
        roomDelay = new int[]{(int)(rate*0.113), (int)(rate*0.271),
                (int)(rate*0.127), (int)(rate*0.293)};
        excursion = rate * 0.003f;
        for (int i = 0; i < 4; i++) {
            center[i] = rate * (0.065f + i * 0.029f);
            delay[i] = center[i];
            step[i] = (i % 2 == 0 ? 1 : -1) * (0.0037f + i * 0.0005f);
        }
        smooth = (float)(1 - Math.exp(-1.0 / (rate * 0.025)));
        attack = (float)(1 - Math.exp(-1.0 / (rate * 0.05)));
        release = (float)(1 - Math.exp(-1.0 / (rate * 0.35)));
        lowCoefficient = (float)(1 - Math.exp(-2*Math.PI*3000/rate));
        highCoefficient = (float)(1 - Math.exp(-2*Math.PI*240/rate));
        transientAttack = (float)(1 - Math.exp(-1.0 / (rate * 0.002)));
        transientSlow = (float)(1 - Math.exp(-1.0 / (rate * 0.060)));
        sendAttack = (float)(1 - Math.exp(-1.0 / (rate * 0.001)));
        sendRelease = (float)(1 - Math.exp(-1.0 / (rate * 0.030)));
        transientHold = rate / 30;
        textureStep = (int)((LiveCrowdTexture.RATE * 65536L) / rate);
    }

    /** No neural separator or model warm-up needed for Live Mode alone. */
    void processSong(double left, double right, float strength) {
        float mid = (float)((left + right) * 0.5);
        low += lowCoefficient * (mid - low);
        high += highCoefficient * (low - high);
        float vocalBand = low - high;
        process(left, right, vocalBand, vocalBand, strength);
    }

    private float random() {
        random ^= random << 13; random ^= random >>> 17; random ^= random << 5;
        return random / 2147483648f;
    }

    private float readVoice(float d) {
        int whole = (int)d;
        float part = d - whole;
        return voice[(pos - whole) & mask] * (1-part) + voice[(pos-whole-1) & mask]*part;
    }

    void process(double left, double right, double vocalL, double vocalR, float strength) {
        float target = Float.isNaN(strength) ? 0 : Math.max(0, Math.min(1, strength));
        amount += (target - amount) * smooth;
        float magnitude = (float)((Math.abs(left)+Math.abs(right))*0.5);
        float energy = Math.min(1, magnitude*4);
        envelope += (energy-envelope) * (energy > envelope ? attack : release);
        // Keep dry drum attacks intact, but do not turn each hit into repeated crowd/room taps.
        fastEnergy += (magnitude-fastEnergy)*transientAttack;
        slowEnergy += (magnitude-slowEnergy)*transientSlow;
        if (fastEnergy > 0.025f && fastEnergy > slowEnergy*1.8f) hold = transientHold;
        float sendTarget = hold > 0 ? 0.12f : 1;
        if (hold > 0) hold--;
        echoSend += (sendTarget-echoSend)*(sendTarget < echoSend ? sendAttack : sendRelease);
        voice[pos] = (float)((vocalL+vocalR)*0.5)*echoSend;
        float choirL = 0, choirR = 0;
        for (int i = 0; i < 4; i++) {
            delay[i] += step[i];
            if (delay[i] > center[i]+excursion || delay[i] < center[i]-excursion) step[i] = -step[i];
            float v = readVoice(delay[i]);
            choirL += v * (i % 2 == 0 ? 0.26f : 0.10f);
            choirR += v * (i % 2 == 0 ? 0.10f : 0.26f);
        }
        float reverbL = roomL[(pos-roomDelay[0])&mask]*0.32f + roomR[(pos-roomDelay[1])&mask]*0.21f;
        float reverbR = roomR[(pos-roomDelay[2])&mask]*0.32f + roomL[(pos-roomDelay[3])&mask]*0.21f;
        roomL[pos] = (float)left*echoSend + reverbL*0.45f;
        roomR[pos] = (float)right*echoSend + reverbR*0.45f;
        // Read a pre-rendered trained texture; no AR, tanh, sin, division or long modulo per sample.
        short[] texture = LiveCrowdTexture.PCM;
        int at = texturePos >>> 16;
        float part = (texturePos & 65535) * (1f/65536);
        float a = texture[at] + (texture[(at+1)&4095]-texture[at])*part;
        int other = (at+1919)&4095;
        float b = texture[other] + (texture[(other+1)&4095]-texture[other])*part;
        if (--control <= 0) {
            control = 64;
            if (random() > 0.94f) clap = 1;
            clap *= 0.82f;
            crowdL = random()*clap*0.18f;
            crowdR = random()*clap*0.18f;
        }
        float bedL = a*(1f/32768)*0.35f + crowdL;
        float bedR = b*(1f/32768)*0.35f + crowdR;
        outL = left + amount*(choirL*0.45f + reverbL*0.30f + bedL*envelope);
        outR = right + amount*(choirR*0.45f + reverbR*0.30f + bedR*envelope);
        texturePos = (texturePos+textureStep)&0x0fffffff;
        pos = (pos+1)&mask;
    }
}
