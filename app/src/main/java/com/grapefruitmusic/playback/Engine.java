package com.grapefruitmusic.playback;

import java.io.IOException;

/**
 * Plays one song: Android's MediaPlayer ({@link MediaPlayerEngine}), or Grapefruit's own
 * ({@link DspEngine}) when Volume Boost, the Equalizer or Mono Audio is on. Called on the main thread; the
 * callbacks arrive there too.
 */
interface Engine {
    interface Callback {
        void onPrepared(Engine engine);

        void onCompletion(Engine engine);

        void onError(Engine engine, String what);
    }

    void setCallback(Callback callback);

    /** A file path, or an http URL (the stream relay's). */
    void setDataSource(String source) throws IOException;

    void prepareAsync();

    void start();

    void pause();

    boolean isPlaying();

    void seekTo(int ms);

    int getCurrentPosition();

    /** In ms, or a value <= 0 if unknown. */
    int getDuration();

    /** Volume Match and ducking, 0 to 1. */
    void setVolume(float volume);

    /** Volume Boost level, the equalizer curve (or null) and Mono Audio (ignored by MediaPlayer). */
    void setEffects(int boostLevel, float[] eq, boolean mono);

    /** Whether this engine applies setEffects (and so needs reloading to turn effects on/off). */
    boolean hasEffects();

    void release();
}
