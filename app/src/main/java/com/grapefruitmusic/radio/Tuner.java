package com.grapefruitmusic.radio;

/**
 * An FM radio chip and the path its sound takes. Frequencies are in units of 10 kHz (9870 is
 * 98.7 MHz). Calls block, some for seconds, so Radio makes them on its own threads; pollRds()
 * comes from a thread of its own, at the same time as the others.
 */
public interface Tuner {
    /** What changed in the station's RDS information. Null fields didn't change. */
    final class Rds {
        /** Program Service name, the station's 8-character name ("KISS FM"). */
        public String programName;
        /** RadioText: what's on ("Now: Adele - Hello"). */
        public String radioText;
        /** Program Type code, or -1. */
        public int programType = -1;
    }

    /** Short name for diagnostics ("MediaTek"). */
    String name();

    /** Switches the chip on, tuned to freq. */
    boolean powerUp(int freq);

    /** Switches the chip off (and closes it). */
    void powerDown();

    boolean tune(int freq);

    /** The next station above (or below) freq, or -1 if there's none. The chip may end up anywhere. */
    int seek(int freq, boolean up);

    /** Every station across the band. The chip may end up anywhere. Empty if none, null if it failed. */
    int[] scan();

    void setMute(boolean mute);

    /** Listens through the headphone wire (true) or a built-in antenna, if there is one. */
    void useHeadphoneAntenna(boolean headphones);

    /** Switches RDS (station name and text) reception on or off; false if the chip has none. */
    boolean setRds(boolean on);

    /** Waits a little for RDS information; null if nothing changed. */
    Rds pollRds();

    /** Starts the radio's sound, through the speaker or the headphones. */
    boolean startAudio(boolean speaker);

    /** Moves the sound to the speaker or back to the headphones. */
    void setSpeaker(boolean speaker);

    void stopAudio();

    /** Whether setSpeaker() can move the sound to the speaker on this tablet. */
    boolean canUseSpeaker();

    /** The AudioManager stream whose volume the radio follows (when it has no volume of its own). */
    int volumeStream();

    /** The radio's own volume goes from 0 to this; 0 if it follows volumeStream() instead. */
    int volumeMax();

    /** The radio's own volume (see volumeMax()). */
    int volume();

    void setVolume(int volume);
}
