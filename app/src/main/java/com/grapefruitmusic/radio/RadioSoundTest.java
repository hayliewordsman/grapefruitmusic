package com.grapefruitmusic.radio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Can Grapefruit hear this tablet's radio? On the JP5S the radio's sound goes from the chip to the
 * headphone jack in hardware, past Android, so Grapefruit can't filter it there. But Rockchip's own
 * FM Radio app records the radio through the microphone input, so its sound may reach Android
 * that way, which is what doing anything about a station's hum would need first.
 *
 * <p>With the radio playing, each audio input is recorded twice, the chip playing and muted; where
 * muting makes the input clearly quieter, that input carries the radio. Only then is a clip of the
 * station recorded (otherwise it would be the room), saved for listening and analysed for hum.
 */
public final class RadioSoundTest {
    /** Told what the test is doing (on the test's thread). */
    public interface Progress {
        void step(String what);
    }

    public static final class Result {
        /** An input carries the radio's sound. */
        public final boolean heard;
        /** For the page and Radio Details. */
        public final String summary;
        /** The station clip, or null. */
        public final File clip;

        Result(boolean heard, String summary, File clip) {
            this.heard = heard;
            this.summary = summary;
            this.clip = clip;
        }
    }

    /** The clip, in the Grapefruit folder (and in Save Radio Files' zip). */
    public static final String CLIP_NAME = "radio-sound-test.wav";
    /** MediaRecorder.AudioSource numbers: microphone, default, camcorder, voice recognition, and
     * the FM tuner input some later Android versions have (not 4.2's). */
    private static final int[] SOURCES = {1, 0, 5, 6, 1998};
    private static final String[] SOURCE_NAMES = {"Microphone", "Default", "Camcorder", "Voice recognition",
            "FM tuner"};
    private static final int[] RATES = {44100, 48000, 22050, 16000, 8000};
    /** Muting the radio makes an input carrying it at least this much quieter. */
    static final double HEARD_DB = 6;
    private static final float CLIP_SECONDS = 10;

    private RadioSoundTest() {
    }

    /** The clip's place on the storage. */
    public static File clipFile() {
        return new File(new File(Environment.getExternalStorageDirectory(), "Grapefruit"), CLIP_NAME);
    }

    /** Runs the test with the radio playing. Takes about 20 seconds: not on the main thread. */
    public static Result run(Radio radio, Progress progress) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "On %s FM:%n", Radio.format(radio.frequency())));
        int best = -1;
        int bestRate = 0;
        double bestDrop = 0;
        try {
            for (int i = 0; i < SOURCES.length; i++) {
                progress.step("Listening through the " + SOURCE_NAMES[i].toLowerCase(Locale.US) + " input…");
                int rate = workingRate(SOURCES[i]);
                if (rate == 0) {
                    sb.append(SOURCE_NAMES[i]).append(" input: can't record from it.\n");
                    continue;
                }
                radio.muteChip(false);
                short[] playing = record(SOURCES[i], rate, 1.5f);
                radio.muteChip(true);
                short[] muted = record(SOURCES[i], rate, 1.5f);
                radio.muteChip(false);
                if (playing == null || muted == null) {
                    sb.append(SOURCE_NAMES[i]).append(" input: recording stopped short.\n");
                    continue;
                }
                double on = SoundAnalysis.levelDb(playing);
                double off = SoundAnalysis.levelDb(muted);
                double drop = on - off;
                boolean heard = drop >= HEARD_DB && on > -60;
                sb.append(String.format(Locale.US, "%s input (%d Hz): %.1f dBFS with the radio playing, %.1f muted: %s.%n",
                        SOURCE_NAMES[i], rate, on, off, heard ? "it hears the radio" : "not the radio"));
                if (heard && drop > bestDrop) {
                    best = i;
                    bestRate = rate;
                    bestDrop = drop;
                }
            }
        } finally {
            radio.muteChip(false);
        }
        if (best < 0) {
            sb.append("No input carries the radio's sound, so Grapefruit can't clean up stations on this tablet.\n");
            return new Result(false, sb.toString(), null);
        }
        progress.step("Recording 10 seconds of the station…");
        short[] station = record(SOURCES[best], bestRate, CLIP_SECONDS);
        if (station == null) {
            sb.append("The station clip couldn't be recorded.\n");
            return new Result(true, sb.toString(), null);
        }
        progress.step("Looking for hum…");
        sb.append(SoundAnalysis.of(station, bestRate).describe());
        File clip = clipFile();
        try {
            File dir = clip.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("can't make " + dir);
            SoundAnalysis.writeWav(clip, station, bestRate);
            sb.append("Clip saved: ").append(clip.getPath()).append('\n');
        } catch (IOException e) {
            sb.append("The clip couldn't be saved: ").append(e.getMessage()).append('\n');
            clip = null;
        }
        return new Result(true, sb.toString(), clip);
    }

    /** A sample rate the input records at, or 0 if it can't be opened. */
    private static int workingRate(int source) {
        for (int rate : RATES) {
            AudioRecord r = open(source, rate);
            if (r != null) {
                r.release();
                return rate;
            }
        }
        return 0;
    }

    private static AudioRecord open(int source, int rate) {
        int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return null;
        AudioRecord r;
        try {
            r = new AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(min, rate));
        } catch (IllegalArgumentException e) {
            return null;
        } catch (SecurityException e) {
            return null;
        }
        if (r.getState() != AudioRecord.STATE_INITIALIZED) {
            r.release();
            return null;
        }
        return r;
    }

    /** Seconds of sound from the input, after a quarter second for it to settle; null if it fails. */
    private static short[] record(int source, int rate, float seconds) {
        AudioRecord r = open(source, rate);
        if (r == null) return null;
        short[] out = new short[(int) (rate * seconds)];
        short[] settle = new short[rate / 4];
        try {
            r.startRecording();
            if (!fill(r, settle)) return null;
            return fill(r, out) ? out : null;
        } catch (IllegalStateException e) {
            return null;
        } finally {
            try {
                r.stop();
            } catch (IllegalStateException e) {
                // Never started.
            }
            r.release();
        }
    }

    private static boolean fill(AudioRecord r, short[] into) {
        int at = 0;
        while (at < into.length) {
            int n = r.read(into, at, into.length - at);
            if (n <= 0) return false;
            at += n;
        }
        return true;
    }
}
