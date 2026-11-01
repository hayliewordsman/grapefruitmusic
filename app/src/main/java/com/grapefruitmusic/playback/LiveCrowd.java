package com.grapefruitmusic.playback;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Live Mode's audience (assets/live_crowd.bin, made by tools/live/render_crowd.py): the crowd under a
 * song, the cheer as one starts and the ovation as one ends. Read once and shared, read-only.
 * Stored as 8-bit mu-law stereo, so playing a frame is two table look-ups.
 */
final class LiveCrowd {
    /** One piece of the crowd: interleaved stereo mu-law codes. */
    static final class Clip {
        final byte[] data;
        final int rate;
        final int frames;
        /** OPEN: the frame where the song comes in. */
        final int marker;

        Clip(byte[] data, int rate, int frames, int marker) {
            this.data = data;
            this.rate = rate;
            this.frames = frames;
            this.marker = marker;
        }
    }

    /** Decoded value of each code byte, indexed by (code & 0xff). */
    static final float[] LEVELS = new float[256];

    static {
        for (int q = -127; q <= 127; q++) {
            double y = Math.abs(q) / 127.0;
            double x = (Math.pow(256, y) - 1) / 255; // mu = 255
            LEVELS[q & 0xff] = (float) (q < 0 ? -x : x);
        }
        LEVELS[0x80] = -1f; // -128 isn't written; decode it as full scale anyway
    }

    final Clip bed;
    final Clip opening;
    final Clip closing;

    private static volatile LiveCrowd loaded;

    private LiveCrowd(Clip bed, Clip opening, Clip closing) {
        this.bed = bed;
        this.opening = opening;
        this.closing = closing;
    }

    /** The crowd, once {@link #load} has read it; null before. */
    static LiveCrowd get() {
        return loaded;
    }

    /** Reads the crowd (once per app run; later calls just close the stream). */
    static synchronized LiveCrowd load(InputStream source) throws IOException {
        try {
            if (loaded == null) loaded = read(new DataInputStream(source));
            return loaded;
        } finally {
            source.close();
        }
    }

    static LiveCrowd read(DataInputStream in) throws IOException {
        if (in.readInt() != 0x47464c43 || in.readInt() != 1) throw new IOException("not a Grapefruit crowd file");
        int count = in.readInt();
        if (count != 3) throw new IOException("unexpected crowd sections");
        int[] tags = new int[count];
        int[] rates = new int[count];
        int[] frames = new int[count];
        int[] markers = new int[count];
        for (int i = 0; i < count; i++) {
            tags[i] = in.readInt();
            rates[i] = in.readInt();
            int channels = in.readInt();
            frames[i] = in.readInt();
            markers[i] = in.readInt();
            in.readFloat(); // the section's RMS, for tools
            if (channels != 2 || rates[i] < 8000 || rates[i] > 48000 || frames[i] < rates[i]
                    // Positions are 20.12 fixed point in an int: under 2^19 frames.
                    || frames[i] > rates[i] * 30 || frames[i] >= 1 << 19
                    || markers[i] < 0 || markers[i] >= frames[i]) {
                throw new IOException("invalid crowd section");
            }
        }
        Clip[] clips = new Clip[count];
        for (int i = 0; i < count; i++) {
            byte[] data = new byte[frames[i] * 2];
            in.readFully(data);
            clips[i] = new Clip(data, rates[i], frames[i], markers[i]);
        }
        if (in.read() != -1) throw new IOException("crowd file too long");
        if (tags[0] != 0x42454420 || tags[1] != 0x4f50454e || tags[2] != 0x434c4f53) {
            throw new IOException("crowd sections out of order");
        }
        return new LiveCrowd(clips[0], clips[1], clips[2]);
    }
}
