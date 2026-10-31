package com.grapefruitmusic.playback;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Short pre-rendered original cheers. Playback is two interpolated sample reads per frame. */
final class StadiumCheer {
    private static volatile byte[] clips;
    private static final int SAMPLE_RATE = 12000;
    private static final int FRAMES = SAMPLE_RATE * 3;
    private static final int END = FRAMES << 12;
    private final int step;
    private int cursor;
    private boolean scheduled, closing;
    float outL, outR;

    static synchronized void load(InputStream source) throws IOException {
        try {
            if (clips != null) return;
            DataInputStream in = new DataInputStream(source);
            if (in.readInt() != 0x47534331 || in.readInt() != SAMPLE_RATE || in.readInt() != FRAMES)
                throw new IOException("Invalid stadium cheer header");
            byte[] data = new byte[FRAMES * 2 * 2];
            in.readFully(data);
            if (in.read() != -1) throw new IOException("Oversized stadium cheer");
            clips = data;
        } finally {
            source.close();
        }
    }

    StadiumCheer(int rate) { step = (int)((SAMPLE_RATE * 4096L) / rate); }

    /** A seek or resumed position must not replay the beginning of a cheer. */
    void position(long ms) {
        closing = false;
        scheduled = true;
        cursor = ms >= 3000 ? END : (int)(Math.max(0, ms) * SAMPLE_RATE * 4096 / 1000);
    }

    void end() { scheduled = true; closing = true; cursor = 0; }

    void process(float amount) {
        outL = outR = 0;
        byte[] data = clips;
        if (!scheduled || cursor >= END) return;
        int at = cursor >>> 12;
        float part = (cursor & 4095) * (1f / 4096);
        int offset = (closing ? FRAMES * 2 : 0) + at * 2;
        if (data != null) {
            int next = at + 1 < FRAMES ? offset + 2 : offset;
            outL = (data[offset] + (data[next] - data[offset]) * part) * (amount * 0.0045f);
            outR = (data[offset+1] + (data[next+1] - data[offset+1]) * part) * (amount * 0.0045f);
        }
        // The timeline advances even with Live off, so enabling it mid-song cannot replay an intro.
        cursor += step;
    }
}
