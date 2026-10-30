package com.grapefruitmusic.radio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.PowerManager;

import com.grapefruitmusic.playback.PlaybackService;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Notice;
import com.grapefruitmusic.util.Prefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FM radio: the station, its RDS information, presets, and the chip (a Tuner), which works on a
 * thread of its own. Wired headphones are the antenna; the sound can still go to the speaker. Not
 * every tablet tells Android when headphones are plugged in, so the radio plays either way, and
 * only stops for an unplug Android actually reports. Main thread only, apart from the worker and
 * RDS threads here.
 *
 * Frequencies are in units of 10 kHz: 9870 is 98.7 MHz.
 */
public final class Radio implements Player.Listener, AudioManager.OnAudioFocusChangeListener {
    public static final int LOWEST = 8750;
    public static final int HIGHEST = 10800;
    /** The dial and the fine-tuning buttons move in 0.1 MHz steps. */
    public static final int STEP = 10;

    public static final int OFF = 0;
    public static final int STARTING = 1;
    public static final int PLAYING = 2;
    public static final int SEEKING = 3;
    public static final int SCANNING = 4;

    public interface Listener {
        /** Station, state, presets or headphones changed. */
        void onRadioChanged();
    }

    /** Finds and opens the tablet's radio chip, or returns null if there's none it can use. */
    public interface TunerFactory {
        Tuner open(Context context, RadioLog log);
    }

    /** A saved station. */
    public static final class Preset {
        /** Longest label a listener can give a preset. */
        public static final int MAX_LABEL = 40;

        public final int frequency;
        /** The label the listener gave it, or null. */
        public String userLabel;
        /** The station's own name, from RDS; null until it's said it. */
        public String stationName;

        Preset(int frequency, String userLabel, String stationName) {
            this.frequency = frequency;
            this.userLabel = clean(userLabel);
            this.stationName = clean(stationName);
        }

        /** The listener's label, else the station's name; null if it has neither. */
        public String name() {
            return userLabel != null ? userLabel : stationName;
        }

        /** What the preset is called: its name, or its frequency ("98.7 FM"). */
        public String label() {
            String n = name();
            return n != null ? n : format(frequency) + " FM";
        }

        /** A label as typed: on one line, trimmed, null if empty. */
        static String clean(String label) {
            if (label == null) return null;
            String l = label.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
            if (l.length() > MAX_LABEL) l = l.substring(0, MAX_LABEL).trim();
            return l.length() > 0 ? l : null;
        }
    }

    private static Radio instance;
    private static TunerFactory factory = new TunerFactory() {
        @Override
        public Tuner open(Context context, RadioLog log) {
            // Rockchip tablets (the JP5S) through their own radio app's service; MediaTek's otherwise.
            if (RockchipTuner.present(context)) {
                Tuner t = RockchipTuner.open(context, log);
                if (t != null) return t;
            }
            return MediatekTuner.open(context, log);
        }
    };

    private final Context context;
    private final AudioManager audio;
    private final PowerManager.WakeLock wakeLock;
    /** Talks to the chip, one call at a time (they block). */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Bg.named("gf-radio"));
    private final RadioLog log = new RadioLog();
    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private final List<Preset> presets = new ArrayList<Preset>();

    // The worker thread's.
    private volatile Tuner tuner;
    private boolean poweredUp;

    // The main thread's.
    /** The radio is what Now Playing shows (playing or not). */
    private boolean session;
    private int state = OFF;
    private int frequency;
    private String programName;
    private String radioText;
    private int programType = -1;
    private boolean speaker;
    /** Android says headphones are plugged in (some tablets never say). */
    private boolean headphones;
    /** What Android last said about the headphone jack, for Radio Details. */
    private String headsetReport = "no plug broadcast";
    /** Shown the "headphones are the antenna" tip this time the app runs. */
    private boolean tippedHeadphones;
    /** No radio this app can use on this tablet (see report()). */
    private boolean unsupported;
    /** Looked for the radio (see find()), or started to. */
    private boolean looked;
    /** Whether a radio Grapefruit can use has been found, or found missing. */
    private boolean known;
    /** Stations from the last scan, or null. */
    private int[] found;
    private boolean pausedByFocusLoss;
    /** Bumped by stop(): results of work started before it are dropped. */
    private int generation;
    /** Bumped on every change of station (a seek finding the dial turned meanwhile goes back to it). */
    private volatile int station;
    /** The thread reading RDS; an old one notices it's been replaced and ends. */
    private volatile Thread rdsThread;
    private int volumeStream = AudioManager.STREAM_MUSIC;
    /** The radio's own volume, 0 to ownVolumeMax (0: it follows volumeStream instead). */
    private int ownVolumeMax;
    private int ownVolume;
    /** The sound can go to the speaker (until the radio is known, a guess from what's installed). */
    private boolean canUseSpeaker;

    /** The frequency the worker tunes to next (tunes while dragging the dial are merged). */
    private volatile int tuneTo;
    private final AtomicBoolean tunePending = new AtomicBoolean();
    private final Runnable tuneTask = new Runnable() {
        @Override
        public void run() {
            tunePending.set(false);
            if (tuner != null && poweredUp) tuneChip(tuneTo);
        }
    };

    /**
     * The frequency the chip is on (set on the worker; -1 while it moves through the band), so RDS
     * is put down to the station it came from, even if the screen hasn't caught up yet.
     */
    private volatile int chipFrequency = -1;
    /** RDS for the frequency a seek is just landing on, before the screen has moved there. */
    private Tuner.Rds earlyRds;
    private int earlyRdsFor = -1;

    /** Tunes the chip (worker thread). */
    private void tuneChip(int freq) {
        tuner.tune(freq);
        chipFrequency = freq;
    }

    private final BroadcastReceiver headset = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            int plug = intent.getIntExtra("state", -1);
            headsetReport = "state " + plug + ", name " + intent.getStringExtra("name") + ", microphone "
                    + intent.getIntExtra("microphone", -1);
            // 1 on most tablets; some use other numbers for headphones without a microphone.
            boolean in = plug > 0 || detectHeadphones();
            if (in == headphones) return;
            boolean wasIn = headphones;
            headphones = in;
            if (wasIn && !in && state != OFF && state != SCANNING) {
                stop();
                Notice.showLong(context, "The radio stopped: headphones are its antenna. Plug them back in to listen.");
            }
            changed();
        }
    };

    public static synchronized Radio get(Context context) {
        if (instance == null) instance = new Radio(context.getApplicationContext());
        return instance;
    }

    /** Uses a different radio chip (tests). Call before the first get(). */
    public static void setTunerFactory(TunerFactory f) {
        factory = f;
    }

    private Radio(Context context) {
        this.context = context;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "grapefruit:radio");
        wakeLock.setReferenceCounted(false);
        frequency = clamp(Prefs.radioFrequency());
        loadPresets();
        headphones = detectHeadphones();
        canUseSpeaker = !RockchipTuner.present(context);
        context.registerReceiver(headset, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
        Player.get(context).addListener(this);
    }

    /** Android's own idea, or the kernel's headphone switch (h2w), says headphones are in. */
    @SuppressWarnings("deprecation")
    private boolean detectHeadphones() {
        return audio.isWiredHeadsetOn() || RadioReport.headphoneSwitch() > 0;
    }

    /** Headphones aren't known to be in: a tip, once a run, that they're the antenna. */
    private void tipHeadphones() {
        if (headphones || (headphones = detectHeadphones()) || tippedHeadphones) return;
        tippedHeadphones = true;
        Notice.showLong(context, "Headphones are the radio's antenna: if there's only static, check they're "
                + "plugged in.");
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        for (Listener l : listeners) l.onRadioChanged();
    }

    // ---- What's on ----

    public boolean inSession() {
        return session;
    }

    public int state() {
        return state;
    }

    /** "Playing" from the listener's point of view: on, or on its way. */
    public boolean wantsToPlay() {
        return state == STARTING || state == PLAYING || state == SEEKING;
    }

    public int frequency() {
        return frequency;
    }

    public String radioText() {
        return radioText;
    }

    public boolean headphones() {
        return headphones;
    }

    public boolean speaker() {
        return speaker;
    }

    public boolean unsupported() {
        return unsupported;
    }

    /** Whether Grapefruit knows yet if this tablet has a radio it can use (see find()). */
    public boolean known() {
        return known;
    }

    /**
     * Looks for this tablet's radio (without switching it on), once, so the Radio tab can say
     * straight away when there's none Grapefruit can use. Listeners hear when it's known.
     */
    public void find() {
        if (looked || known()) return;
        looked = true;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                if (tuner == null) {
                    log.add("looking for the radio");
                    tuner = factory.open(context, log);
                }
                final boolean none = tuner == null;
                Bg.main(new Runnable() {
                    @Override
                    public void run() {
                        known = true;
                        unsupported = none;
                        changed();
                    }
                });
            }
        });
    }

    public int[] found() {
        return found;
    }

    /** The volume stream the radio's sound follows (the volume slider and keys). */
    public int volumeStream() {
        return volumeStream;
    }

    /** The radio has a volume of its own from 0 to this (the chip's), or 0 if it follows volumeStream(). */
    public int ownVolumeMax() {
        return ownVolumeMax;
    }

    public int ownVolume() {
        return ownVolume;
    }

    /** Sets the radio's own volume (see ownVolumeMax()). */
    public void setOwnVolume(int v) {
        if (ownVolumeMax <= 0) return;
        final int volume = Math.max(0, Math.min(ownVolumeMax, v));
        if (volume == ownVolume) return;
        ownVolume = volume;
        changed();
        worker.execute(new Runnable() {
            @Override
            public void run() {
                if (tuner != null && poweredUp) tuner.setVolume(volume);
            }
        });
    }

    /** The volume keys, when the radio has a volume of its own. */
    public void stepVolume(boolean up) {
        setOwnVolume(ownVolume + (up ? 1 : -1));
    }

    /** Whether the sound can be sent to the speaker on this tablet (see setSpeaker()). */
    public boolean canUseSpeaker() {
        return canUseSpeaker;
    }

    /**
     * The station's name: the label the listener gave its preset, else the name it's sending, else
     * the name it sent last time; null if none.
     */
    public String name() {
        Preset p = preset(frequency);
        if (p != null && p.userLabel != null) return p.userLabel;
        if (programName != null && programName.length() > 0) return programName;
        return p != null ? p.stationName : null;
    }

    /** The station's name, or its frequency ("98.7 FM"). */
    public String title() {
        String n = name();
        return n != null ? n : format(frequency) + " FM";
    }

    /** What the station says is on, or its frequency and kind of programme. */
    public String subtitle() {
        if (radioText != null && radioText.length() > 0) return radioText;
        String type = programTypeName(programType, Locale.getDefault().getCountry());
        String freq = format(frequency) + " FM";
        boolean named = !title().equals(freq);
        if (type != null) return named ? freq + " · " + type : type;
        return named ? freq : "FM Radio";
    }

    // ---- Playing ----

    /** Plays the station at freq. False (with a message) if it can't: another app has the sound, say. */
    public boolean play(int freq) {
        if (state == SCANNING) return false;
        Player.get(context).pause();
        pausedByFocusLoss = false;
        boolean retune = state != OFF;
        session = true;
        setFrequency(freq);
        if (retune) {
            queueTune();
            changed();
            return true;
        }
        state = STARTING;
        tipHeadphones();
        changed();
        com.grapefruitmusic.compat.Platform.service(context, new Intent(context, PlaybackService.class));
        final int gen = ++generation;
        final int at = frequency;
        final boolean toSpeaker = speaker;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                final String error = startOnWorker(at, toSpeaker);
                final Tuner t = tuner;
                final int stream = t != null ? t.volumeStream() : AudioManager.STREAM_MUSIC;
                final int max = t != null ? t.volumeMax() : 0;
                final int volume = max > 0 && error == null ? t.volume() : 0;
                Bg.main(new Runnable() {
                    @Override
                    public void run() {
                        known = true;
                        if (gen != generation) return;
                        volumeStream = stream;
                        ownVolumeMax = max;
                        ownVolume = volume;
                        if (t != null) canUseSpeaker = t.canUseSpeaker();
                        if (!canUseSpeaker) speaker = false;
                        // Taken now, not before: Rockchip's radio service takes focus itself as the
                        // radio comes on, which would otherwise switch Grapefruit's radio off.
                        if (error == null && audio.requestAudioFocus(Radio.this, AudioManager.STREAM_MUSIC,
                                AudioManager.AUDIOFOCUS_GAIN) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            state = OFF;
                            worker.execute(new Runnable() {
                                @Override
                                public void run() {
                                    powerDownOnWorker();
                                }
                            });
                            Notice.show(context, "Another app is playing sound");
                            changed();
                            return;
                        }
                        if (error == null) {
                            unsupported = false;
                            if (state == STARTING) state = PLAYING;
                            wakeLock.acquire();
                            startRds();
                        } else {
                            state = OFF;
                            audio.abandonAudioFocus(Radio.this);
                            if (tuner == null) {
                                unsupported = true;
                                session = false;
                            }
                            Notice.showLong(context, error);
                        }
                        changed();
                    }
                });
            }
        });
        return true;
    }

    /** Switches the chip on at freq and starts its sound; an error message, or null. */
    private String startOnWorker(int freq, boolean toSpeaker) {
        if (tuner == null) {
            log.add("looking for the radio");
            tuner = factory.open(context, log);
            if (tuner == null) return "This tablet's FM radio isn't one Grapefruit can use yet. See Radio for details.";
        }
        if (!poweredUp) {
            chipFrequency = -1;
            if (!tuner.powerUp(freq)) {
                tuner.powerDown();
                return "The radio didn't switch on. Try again in a moment.";
            }
            poweredUp = true;
            chipFrequency = freq;
            tuner.useHeadphoneAntenna(true);
            boolean rds = tuner.setRds(true);
            log.add("RDS: " + (rds ? "on" : "not available"));
        } else {
            tuneChip(freq);
        }
        if (!tuner.startAudio(toSpeaker)) {
            powerDownOnWorker();
            return "The radio switched on, but its sound couldn't be played on this tablet. See Radio for details.";
        }
        tuner.setMute(false);
        return null;
    }

    private void powerDownOnWorker() {
        if (tuner == null || !poweredUp) return;
        tuner.stopAudio();
        tuner.setRds(false);
        tuner.powerDown();
        poweredUp = false;
    }

    public boolean play() {
        return play(frequency);
    }

    /** Switches the radio off; Now Playing keeps showing the station (to play again). */
    public void stop() {
        pausedByFocusLoss = false;
        halt(false);
    }

    /** Switches the chip off; keepFocus while another app talks briefly (to come back after). */
    private void halt(boolean keepFocus) {
        if (state == OFF) return;
        generation++;
        state = OFF;
        stopRds();
        if (wakeLock.isHeld()) wakeLock.release();
        if (!keepFocus) audio.abandonAudioFocus(this);
        changed();
        worker.execute(new Runnable() {
            @Override
            public void run() {
                powerDownOnWorker();
            }
        });
    }

    public void togglePlay() {
        if (wantsToPlay()) stop();
        else play();
    }

    /** Switches the radio off and out of Now Playing. */
    public void close() {
        stop();
        if (!session) return;
        session = false;
        changed();
    }

    /** Tunes to freq (playing or not; when off, it's where the radio starts). */
    public void tune(int freq) {
        int f = clamp(snap(freq));
        if (f == frequency) return;
        setFrequency(f);
        if (state != OFF) queueTune();
        changed();
    }

    /** Fine tuning: one step (0.1 MHz) up or down. */
    public void step(boolean up) {
        tune(frequency + (up ? STEP : -STEP));
    }

    private void setFrequency(int freq) {
        int f = clamp(freq);
        if (f != frequency) {
            programName = null;
            radioText = null;
            programType = -1;
            station++;
        }
        frequency = f;
        Prefs.setRadioFrequency(f);
        Tuner.Rds early = earlyRdsFor == f ? earlyRds : null;
        earlyRds = null;
        earlyRdsFor = -1;
        if (early != null) applyRds(early);
    }

    private void queueTune() {
        tuneTo = frequency;
        if (tunePending.compareAndSet(false, true)) worker.execute(tuneTask);
    }

    /** Finds the next station up or down (switching the radio on first if it's off). */
    public void seek(final boolean up) {
        if (state == SEEKING || state == SCANNING) return;
        if (state == OFF && !play()) return;
        state = SEEKING;
        final int from = frequency;
        final int gen = generation;
        final int at = station;
        changed();
        worker.execute(new Runnable() {
            @Override
            public void run() {
                int f = -1;
                if (tuner != null && poweredUp) {
                    tuner.setMute(true);
                    chipFrequency = -1;
                    f = tuner.seek(from, up);
                    // Some chips stay on the station they found; others need telling.
                    tuneChip(f > 0 ? f : from);
                    tuner.setMute(false);
                }
                final int foundAt = f;
                Bg.main(new Runnable() {
                    @Override
                    public void run() {
                        // Stopped meanwhile, or the radio didn't switch on.
                        if (gen != generation || state == OFF) return;
                        if (state == SEEKING) state = PLAYING;
                        if (at == station) {
                            if (foundAt > 0) setFrequency(foundAt);
                            else Notice.show(context, "No other station found");
                        } else {
                            // Tuned by hand meanwhile: go back there.
                            queueTune();
                        }
                        changed();
                    }
                });
            }
        });
    }

    /** Looks for every station on the band (the radio stays as it was). */
    public void scan() {
        if (state == SCANNING || state == SEEKING || state == STARTING) return;
        tipHeadphones();
        final boolean wasPlaying = state == PLAYING;
        final int back = frequency;
        state = SCANNING;
        stopRds();
        changed();
        final int gen = ++generation;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                int[] result = null;
                boolean ok = true;
                if (tuner == null) {
                    log.add("looking for the radio");
                    tuner = factory.open(context, log);
                }
                if (tuner != null) {
                    boolean wasUp = poweredUp;
                    if (!wasUp && tuner.powerUp(back)) {
                        poweredUp = true;
                        tuner.useHeadphoneAntenna(true);
                    }
                    if (poweredUp) {
                        tuner.setMute(true);
                        chipFrequency = -1;
                        result = tuner.scan();
                        if (wasUp) {
                            tuneChip(back);
                            tuner.setMute(false);
                        } else {
                            powerDownOnWorker();
                        }
                    }
                    ok = result != null;
                }
                final int[] stations = result;
                final boolean worked = ok;
                final boolean none = tuner == null;
                Bg.main(new Runnable() {
                    @Override
                    public void run() {
                        known = true;
                        unsupported = none;
                        if (worked && stations != null) found = stations;
                        if (gen == generation) {
                            state = wasPlaying ? PLAYING : OFF;
                            if (wasPlaying) startRds();
                        }
                        if (none) {
                            Notice.showLong(context, "This tablet's FM radio isn't one Grapefruit can use yet.");
                        } else if (!worked) {
                            Notice.show(context, "The radio couldn't look for stations");
                        } else {
                            int n = stations.length;
                            Notice.show(context, n == 0 ? "No stations found" : n == 1 ? "Found 1 station"
                                    : "Found " + n + " stations");
                        }
                        changed();
                    }
                });
            }
        });
    }

    /** The sound through the speaker (true) or the headphones. The headphones stay the antenna. */
    public void setSpeaker(final boolean on) {
        if (on == speaker || (on && !canUseSpeaker)) return;
        speaker = on;
        changed();
        worker.execute(new Runnable() {
            @Override
            public void run() {
                if (tuner != null && poweredUp) tuner.setSpeaker(on);
            }
        });
    }

    // ---- RDS ----

    private void startRds() {
        stopRds();
        final Tuner t = tuner;
        if (t == null) return;
        Thread thread = new Thread("gf-radio-rds") {
            @Override
            public void run() {
                while (rdsThread == this) {
                    final int at = chipFrequency;
                    Tuner.Rds r = null;
                    try {
                        r = t.pollRds();
                    } catch (RuntimeException e) {
                        log.add("RDS: " + e);
                    }
                    if (r != null && rdsThread == this) {
                        final Tuner.Rds rds = r;
                        Bg.main(new Runnable() {
                            @Override
                            public void run() {
                                if (at < 0 || state == OFF) return;
                                if (at == frequency) {
                                    applyRds(rds);
                                } else {
                                    // A seek landing: the screen moves there in a moment.
                                    earlyRds = rds;
                                    earlyRdsFor = at;
                                }
                            }
                        });
                    }
                    try {
                        Thread.sleep(r == null ? 200 : 50);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        };
        thread.setDaemon(true);
        rdsThread = thread;
        thread.start();
    }

    private void stopRds() {
        rdsThread = null;
    }

    private void applyRds(Tuner.Rds rds) {
        boolean change = false;
        if (rds.programName != null && rds.programName.length() > 0 && !rds.programName.equals(programName)) {
            programName = rds.programName;
            Preset p = preset(frequency);
            String station = Preset.clean(programName);
            if (p != null && station != null && !station.equals(p.stationName)) {
                p.stationName = station;
                savePresets();
            }
            change = true;
        }
        if (rds.radioText != null && !rds.radioText.equals(radioText)) {
            radioText = rds.radioText;
            change = true;
        }
        if (rds.programType >= 0 && rds.programType != programType) {
            programType = rds.programType;
            change = true;
        }
        if (change) changed();
    }

    // ---- Presets ----

    public List<Preset> presets() {
        return Collections.unmodifiableList(presets);
    }

    public Preset preset(int freq) {
        return findIn(presets, freq);
    }

    /**
     * Saves freq as a preset with the listener's label (null or empty for none), remembering the
     * station's own name if it's on and has said it.
     */
    public void addPreset(int freq, String userLabel) {
        if (preset(freq) != null) return;
        presets.add(new Preset(freq, userLabel, freq == frequency ? programName : null));
        savePresets();
        changed();
    }

    public void removePreset(int freq) {
        Preset p = preset(freq);
        if (p == null) return;
        presets.remove(p);
        savePresets();
        changed();
    }

    /** Labels a preset; an empty label goes back to the station's own name (or its frequency). */
    public void setPresetLabel(int freq, String userLabel) {
        Preset p = preset(freq);
        if (p == null) return;
        p.userLabel = Preset.clean(userLabel);
        savePresets();
        changed();
    }

    private void loadPresets() {
        presets.clear();
        presets.addAll(parsePresets(Prefs.radioPresets()));
    }

    private void savePresets() {
        Prefs.setRadioPresets(serializePresets(presets));
    }

    /**
     * Presets saved as lines of "frequency<tab>label<tab>station name" (either may be empty, and a
     * station with neither is just its frequency). Before 3.0 beta 6 a line had one name, after
     * one tab: kept as the label, as the listener saw it.
     */
    static List<Preset> parsePresets(String saved) {
        List<Preset> out = new ArrayList<Preset>();
        if (saved == null || saved.length() == 0) return out;
        for (String line : saved.split("\n")) {
            String[] parts = line.split("\t", 3);
            try {
                int f = Integer.parseInt(parts[0].trim());
                if (inBand(f) && findIn(out, f) == null) {
                    out.add(new Preset(f, parts.length > 1 ? parts[1] : null, parts.length > 2 ? parts[2] : null));
                }
            } catch (NumberFormatException e) {
                // Not a preset: skip the line.
            }
        }
        return out;
    }

    static String serializePresets(List<Preset> list) {
        StringBuilder sb = new StringBuilder();
        for (Preset p : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(p.frequency);
            if (p.userLabel != null || p.stationName != null) {
                sb.append('\t').append(p.userLabel != null ? p.userLabel : "")
                        .append('\t').append(p.stationName != null ? p.stationName : "");
            }
        }
        return sb.toString();
    }

    private static Preset findIn(List<Preset> list, int freq) {
        for (Preset p : list) {
            if (p.frequency == freq) return p;
        }
        return null;
    }

    // ---- Diagnostics ----

    /** What Grapefruit found out about this tablet's radio, for the details page. Slow: not main thread. */
    public String report() {
        return RadioReport.collect(context, log.text(), headsetReport);
    }

    // ---- Music and other apps ----

    @Override
    public void onPlayerChanged() {
        // Music started: it takes over from the radio.
        if (session && Player.get(context).wantsToPlay()) close();
    }

    @Override
    public void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                stop();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // A call or another app talking for a moment: back on when it's done.
                if (wantsToPlay()) {
                    halt(true);
                    pausedByFocusLoss = true;
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (pausedByFocusLoss && session) {
                    pausedByFocusLoss = false;
                    play();
                }
                break;
            default:
                break;
        }
    }

    // ---- Frequencies ----

    public static boolean inBand(int freq) {
        return freq >= LOWEST && freq <= HIGHEST;
    }

    public static int clamp(int freq) {
        return Math.max(LOWEST, Math.min(HIGHEST, freq));
    }

    /** To the nearest 0.1 MHz. */
    public static int snap(int freq) {
        return Math.round(freq / (float) STEP) * STEP;
    }

    /** "98.7" (or "98.75" between tenths). */
    public static String format(int freq) {
        int tenths = freq / 10;
        int hundredths = freq % 10;
        String s = (tenths / 10) + "." + (tenths % 10);
        return hundredths != 0 ? s + hundredths : s;
    }

    /**
     * The kind of programme, from the station's RDS code: the North American (RBDS) list in the US,
     * Canada and Mexico, Europe's elsewhere. Null when there's no code.
     */
    public static String programTypeName(int code, String country) {
        if (code <= 0 || code > 31) return null;
        boolean rbds = "US".equals(country) || "CA".equals(country) || "MX".equals(country);
        String name = (rbds ? RBDS : RDS)[code];
        return name.length() > 0 ? name : null;
    }

    private static final String[] RBDS = {"", "News", "Information", "Sports", "Talk", "Rock", "Classic Rock",
            "Adult Hits", "Soft Rock", "Top 40", "Country", "Oldies", "Soft", "Nostalgia", "Jazz", "Classical",
            "Rhythm and Blues", "Soft Rhythm and Blues", "Language", "Religious Music", "Religious Talk",
            "Personality", "Public", "College", "Spanish Talk", "Spanish Music", "Hip Hop", "", "", "Weather",
            "Emergency Test", "Emergency"};

    private static final String[] RDS = {"", "News", "Current Affairs", "Information", "Sport", "Education",
            "Drama", "Culture", "Science", "Varied", "Pop Music", "Rock Music", "Easy Listening", "Light Classical",
            "Serious Classical", "Other Music", "Weather", "Finance", "Children's", "Social Affairs", "Religion",
            "Phone-In", "Travel", "Leisure", "Jazz", "Country", "National Music", "Oldies", "Folk", "Documentary",
            "Alarm Test", "Alarm"};
}
