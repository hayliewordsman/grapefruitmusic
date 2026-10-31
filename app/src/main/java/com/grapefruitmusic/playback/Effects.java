package com.grapefruitmusic.playback;

/** What Grapefruit's own engine does to the sound, as set in Settings. Immutable. */
public final class Effects {
    /** Volume Boost level, Dsp.BOOST_*. */
    public final int boost;
    /** Equalizer curve, dB per band, or null. */
    public final float[] eq;
    public final boolean mono;
    public final boolean karaoke;
    /** How much of the vocal Karaoke Mode takes away, 0 to 1. */
    public final float karaokeAmount;

    public final boolean live;
    public final float liveAmount;

    public Effects(int boost, float[] eq, boolean mono, boolean karaoke, float karaokeAmount) {
        this(boost, eq, mono, karaoke, karaokeAmount, false, 0);
    }

    public Effects(int boost, float[] eq, boolean mono, boolean karaoke, float karaokeAmount,
            boolean live, float liveAmount) {
        this.live = live;
        this.liveAmount = liveAmount;
        this.boost = boost;
        this.eq = eq;
        this.mono = mono;
        this.karaoke = karaoke;
        this.karaokeAmount = karaokeAmount;
    }

    /** Does any of it change the sound? If not, Android's own player plays the music untouched. */
    public boolean anyOn() {
        return boost > Dsp.BOOST_OFF || Equalizer.isOn(eq) || mono || karaoke || live;
    }
}
