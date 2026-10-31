package com.grapefruitmusic.playback;

/** Original procedural audience, song-derived vocal ensemble and small venue. No sample playback. */
final class LiveAmbience {
    private final int rate;
    private final double[][] voices;
    private final double[][] room;
    private final double[][] ar = new double[2][LiveCrowdModel.AR.length];
    private final double[] noise = new double[2];
    private final double[] phase = {0.03, 0.21, 0.47, 0.79, 0.13, 0.39, 0.63, 0.91};
    private long pos;
    private int arPos, random = 0x41b1, clock;
    private final double smooth, attack, release;
    private double clap;
    private double envelope, amount;
    double outL, outR;

    LiveAmbience(int sampleRate) {
        rate = sampleRate;
        smooth = 1 - Math.exp(-1.0 / (rate * 0.025));
        attack = 1 - Math.exp(-1.0 / (rate * 0.05));
        release = 1 - Math.exp(-1.0 / (rate * 0.35));
        voices = new double[2][Math.max(4, rate / 3)];
        room = new double[2][Math.max(4, rate / 2)];
    }

    private double random() {
        random ^= random << 13; random ^= random >>> 17; random ^= random << 5;
        return random / 2147483648.0;
    }

    private double read(double[] buffer, double delay) {
        double at = (pos % buffer.length - delay + buffer.length * 2) % buffer.length;
        int i = (int) at;
        double fraction = at - i;
        return buffer[i] * (1 - fraction) + buffer[(i + 1) % buffer.length] * fraction;
    }

    void process(double left, double right, double vocalL, double vocalR, float strength) {
        double target = Float.isNaN(strength) ? 0 : Math.max(0, Math.min(1, strength));
        amount += (target - amount) * smooth;
        // Audience follows musical activity, then fades with the venue tail in silence.
        double energy = Math.min(1, (Math.abs(left) + Math.abs(right)) * 2);
        envelope += (energy - envelope) * (energy > envelope ? attack : release);
        voices[0][(int) (pos % voices[0].length)] = vocalL;
        voices[1][(int) (pos % voices[1].length)] = vocalR;
        if (clock <= 0) {
            if (random() > 0.995) clap = 1;
            clap *= 0.985;
            for (int c = 0; c < 2; c++) {
                double x = random() * Math.sqrt(3) * LiveCrowdModel.INNOVATION;
                for (int k = 0; k < LiveCrowdModel.AR.length; k++)
                    x += LiveCrowdModel.AR[k] * ar[c][(arPos + ar[c].length - k - 1) % ar[c].length];
                ar[c][arPos] = x;
                noise[c] = Math.tanh(x * 0.6) + random() * clap * 0.7;
            }
            arPos = (arPos + 1) % ar[0].length;
            clock += rate;
        }
        clock -= LiveCrowdModel.RATE;
        double choirL = 0, choirR = 0;
        for (int i = 0; i < phase.length; i++) {
            phase[i] += (0.31 + i * 0.047) / rate;
            if (phase[i] >= 1) phase[i] -= 1;
            double triangle = 4 * Math.abs(phase[i] - 0.5) - 1;
            double delay = rate * (0.065 + i * 0.013 + 0.003 * triangle);
            double voice = read(voices[i & 1], delay);
            choirL += voice * ((i & 1) == 0 ? 0.18 : 0.07);
            choirR += voice * ((i & 1) == 0 ? 0.07 : 0.18);
        }
        double roomL = read(room[0], rate * 0.113) * 0.32 + read(room[1], rate * 0.271) * 0.21;
        double roomR = read(room[1], rate * 0.127) * 0.32 + read(room[0], rate * 0.293) * 0.21;
        room[0][(int) (pos % room[0].length)] = left + roomL * 0.45;
        room[1][(int) (pos % room[1].length)] = right + roomR * 0.45;
        // A slowly varying applause/cheer bed with a different pattern in each ear.
        double swell = 0.6 + 0.4 * Math.sin(pos / (double) rate * 0.83);
        outL = left + amount * (choirL * 0.6 + roomL * 0.65 + noise[0] * envelope * swell * 0.12);
        outR = right + amount * (choirR * 0.6 + roomR * 0.65 + noise[1] * envelope * swell * 0.12);
        pos++;
    }
}
