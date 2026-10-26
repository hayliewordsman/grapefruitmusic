package com.grapefruitmusic.radio;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;

import com.mediatek.FMRadio.FMRadioNative;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.TreeSet;

/**
 * The FM radio of MediaTek-based tablets (and radio chips that copy MediaTek's interface), driven
 * the way MediaTek's own FM Radio app drives it: the chip through libfmjni.so (see
 * com.mediatek.FMRadio.FMRadioNative), the sound through a special MediaPlayer that routes the
 * chip's audio to the headphones or the speaker.
 */
final class MediatekTuner implements Tuner {
    // RDS information that changed, as readrds() reports it.
    private static final int RDS_PTY = 0x0004;
    private static final int RDS_PS = 0x0008;
    private static final int RDS_RT = 0x0040;

    /** MediaTek's MediaPlayer plays the radio chip's sound for these: Jelly Bean's, then KitKat's. */
    private static final String SOUND_JB = "MEDIATEK://MEDIAPLAYER_PLAYERTYPE_FM";
    private static final String SOUND_KK = "THIRDPARTY://MEDIAPLAYER_PLAYERTYPE_FM";

    // Operations, each called by its Jelly Bean or KitKat name (whichever this tablet has).
    private static final int OPEN = 0;
    private static final int CLOSE = 1;
    private static final int POWER_UP = 2;
    private static final int POWER_DOWN = 3;
    private static final int TUNE = 4;
    private static final int SEEK = 5;
    private static final int SCAN = 6;
    private static final int SET_RDS = 7;
    private static final int READ_RDS = 8;
    private static final int PROGRAM_NAME = 9;
    private static final int RADIO_TEXT = 10;
    private static final int PROGRAM_TYPE = 11;
    private static final int MUTE = 12;
    private static final int ANTENNA = 13;
    private static final int OPS = 14;
    private static final String[] OP_NAMES = {"open", "close", "power up", "power down", "tune", "seek", "scan",
            "RDS on/off", "read RDS", "station name", "radio text", "program type", "mute", "antenna"};

    /** Whether libfmjni.so loaded (null: not tried yet); a library only loads once per process. */
    private static Boolean loaded;
    private static String loadError;

    private final AudioManager audio;
    private final RadioLog log;
    /** Per operation: answered by the KitKat name (not Jelly Bean's). */
    private final boolean[] kitkat = new boolean[OPS];
    /** Per operation: neither name exists in this tablet's library. */
    private final boolean[] missing = new boolean[OPS];
    /** The radio's own volume stream, on Android versions that have one (MediaTek Jelly Bean). */
    private final int fmStream;
    private boolean open;
    private MediaPlayer player;
    private String sound;
    private boolean speaker;

    /** The radio, opened, or null if this tablet's FM library isn't there or doesn't fit. */
    static MediatekTuner open(Context context, RadioLog log) {
        synchronized (MediatekTuner.class) {
            if (loaded == null) {
                try {
                    FMRadioNative.load();
                    loaded = true;
                } catch (UnsatisfiedLinkError e) {
                    loaded = false;
                    loadError = e.getMessage();
                } catch (SecurityException e) {
                    loaded = false;
                    loadError = e.toString();
                }
            }
        }
        if (!loaded) {
            log.add("libfmjni: " + loadError);
            return null;
        }
        log.add("libfmjni: loaded");
        MediatekTuner t = new MediatekTuner(context, log);
        return t.openDevice() ? t : null;
    }

    private MediatekTuner(Context context, RadioLog log) {
        this.log = log;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int stream = AudioManager.STREAM_MUSIC;
        try {
            stream = AudioManager.class.getField("STREAM_FM").getInt(null);
        } catch (Exception e) {
            // Not MediaTek's Jelly Bean: the radio follows the music volume.
        }
        fmStream = stream;
    }

    @Override
    public String name() {
        return "MediaTek";
    }

    private boolean openDevice() {
        open = ok(call(OPEN, 0, false, 0));
        log.add("open /dev/fm: " + (open ? "ok" : "failed"));
        return open;
    }

    // ---- Calls into the library ----

    /** Calls op by whichever of its names this tablet's library has; null if neither. */
    private Object call(int op, float f, boolean b, int i) {
        if (missing[op]) return null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return kitkat[op] ? kitkatCall(op, f, b, i) : jellyBeanCall(op, f, b, i);
            } catch (UnsatisfiedLinkError e) {
                kitkat[op] = !kitkat[op];
            }
        }
        missing[op] = true;
        log.add(OP_NAMES[op] + ": not in this tablet's library");
        return null;
    }

    private static Object jellyBeanCall(int op, float f, boolean b, int i) {
        switch (op) {
            case OPEN:
                return FMRadioNative.opendev();
            case CLOSE:
                return FMRadioNative.closedev();
            case POWER_UP:
                return FMRadioNative.powerup(f);
            case POWER_DOWN:
                return FMRadioNative.powerdown(0);
            case TUNE:
                return FMRadioNative.tune(f);
            case SEEK:
                return FMRadioNative.seek(f, b);
            case SCAN:
                return FMRadioNative.autoscan();
            case SET_RDS:
                return FMRadioNative.rdsset(b);
            case READ_RDS:
                return FMRadioNative.readrds();
            case PROGRAM_NAME:
                return FMRadioNative.getPS();
            case RADIO_TEXT:
                return FMRadioNative.getLRText();
            case PROGRAM_TYPE:
                return FMRadioNative.getPTY();
            case MUTE:
                return FMRadioNative.setmute(b);
            case ANTENNA:
                return FMRadioNative.switchAntenna(i);
            default:
                return null;
        }
    }

    private static Object kitkatCall(int op, float f, boolean b, int i) {
        switch (op) {
            case OPEN:
                return FMRadioNative.openDev();
            case CLOSE:
                return FMRadioNative.closeDev();
            case POWER_UP:
                return FMRadioNative.powerUp(f);
            case POWER_DOWN:
                return FMRadioNative.powerDown(0);
            case TUNE:
                return FMRadioNative.tune(f);
            case SEEK:
                return FMRadioNative.seek(f, b);
            case SCAN:
                return FMRadioNative.autoScan();
            case SET_RDS:
                return FMRadioNative.setRds(b);
            case READ_RDS:
                return FMRadioNative.readRds();
            case PROGRAM_NAME:
                return FMRadioNative.getPs();
            case RADIO_TEXT:
                return FMRadioNative.getLrText();
            case MUTE:
                return FMRadioNative.setMute(b);
            case ANTENNA:
                return FMRadioNative.switchAntenna(i);
            default:
                // No KitKat name (the program type): try Jelly Bean's again next time.
                throw new UnsatisfiedLinkError();
        }
    }

    private static boolean ok(Object result) {
        return Boolean.TRUE.equals(result);
    }

    private static float mhz(int freq) {
        return freq / 100f;
    }

    // ---- The chip ----

    @Override
    public boolean powerUp(int freq) {
        if (!open && !openDevice()) return false;
        boolean ok = ok(call(POWER_UP, mhz(freq), false, 0));
        log.add("power up at " + Radio.format(freq) + ": " + (ok ? "ok" : "failed"));
        return ok;
    }

    @Override
    public void powerDown() {
        call(MUTE, 0, true, 0);
        if (open) {
            call(POWER_DOWN, 0, false, 0);
            call(CLOSE, 0, false, 0);
            open = false;
        }
    }

    @Override
    public boolean tune(int freq) {
        return ok(call(TUNE, mhz(freq), false, 0));
    }

    @Override
    public int seek(int freq, boolean up) {
        Object r = call(SEEK, mhz(freq), up, 0);
        if (!(r instanceof Float)) return -1;
        int found = fromChip((Float) r);
        return found != freq && Radio.inBand(found) ? found : -1;
    }

    /** A frequency from the chip: in MHz, or in units of 100 kHz or 10 kHz on some chips. */
    static int fromChip(float value) {
        if (value > 2000) return Math.round(value);
        if (value > 200) return Math.round(value * 10);
        return Math.round(value * 100);
    }

    @Override
    public int[] scan() {
        Object r = call(SCAN, 0, false, 0);
        if (!(r instanceof short[])) return null;
        int[] found = stations((short[]) r);
        log.add("scan: " + Arrays.toString((short[]) r) + " -> " + found.length + " stations");
        return found;
    }

    /** The stations in a scan result (frequencies in 100 kHz or 10 kHz units), in order, in the band. */
    static int[] stations(short[] values) {
        TreeSet<Integer> set = new TreeSet<Integer>();
        for (short s : values) {
            int v = s & 0xffff;
            int freq = v >= 2000 ? v : v * 10;
            if (Radio.inBand(freq)) set.add(freq);
        }
        int[] out = new int[set.size()];
        int n = 0;
        for (int f : set) out[n++] = f;
        return out;
    }

    @Override
    public void setMute(boolean mute) {
        call(MUTE, 0, mute, 0);
    }

    @Override
    public void useHeadphoneAntenna(boolean headphones) {
        call(ANTENNA, 0, false, headphones ? 0 : 1);
    }

    @Override
    public boolean setRds(boolean on) {
        Object r = call(SET_RDS, 0, on, 0);
        return r instanceof Integer && (Integer) r == 0;
    }

    @Override
    public Rds pollRds() {
        Object r = call(READ_RDS, 0, false, 0);
        if (!(r instanceof Short)) return null;
        int events = (Short) r & 0xffff;
        Rds rds = null;
        if ((events & RDS_PS) != 0) {
            Object ps = call(PROGRAM_NAME, 0, false, 0);
            if (ps instanceof byte[]) {
                rds = new Rds();
                rds.programName = text((byte[]) ps);
            }
        }
        if ((events & RDS_RT) != 0) {
            Object rt = call(RADIO_TEXT, 0, false, 0);
            if (rt instanceof byte[]) {
                if (rds == null) rds = new Rds();
                rds.radioText = text((byte[]) rt);
            }
        }
        if ((events & RDS_PTY) != 0) {
            Object pty = call(PROGRAM_TYPE, 0, false, 0);
            if (pty instanceof Byte) {
                if (rds == null) rds = new Rds();
                rds.programType = (Byte) pty & 0x1f;
            }
        }
        return rds;
    }

    /** RDS text: mostly ASCII, padded with spaces or zeros. */
    static String text(byte[] bytes) {
        String s = new String(bytes, Charset.forName("ISO-8859-1"));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 0) break;
            out.append(c < 32 ? ' ' : c);
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    // ---- The sound ----

    @Override
    public boolean startAudio(boolean toSpeaker) {
        if (player == null) {
            for (String source : new String[]{SOUND_JB, SOUND_KK}) {
                MediaPlayer mp = new MediaPlayer();
                try {
                    mp.setDataSource(source);
                    mp.setAudioStreamType(SOUND_JB.equals(source) ? fmStream : AudioManager.STREAM_MUSIC);
                    mp.prepare();
                    mp.start();
                    player = mp;
                    sound = source;
                    log.add("sound: " + source);
                    break;
                } catch (IOException e) {
                    log.add("sound " + source + ": " + e);
                } catch (RuntimeException e) {
                    log.add("sound " + source + ": " + e);
                }
                mp.release();
            }
        }
        if (player == null) return false;
        setSpeaker(toSpeaker);
        return true;
    }

    @Override
    public void setSpeaker(boolean on) {
        if (on == speaker && player != null) return;
        if (SOUND_KK.equals(sound)) {
            // KitKat: force the "proprietary" (FM) route to the speaker, as MediaTek's app does.
            forceUse(1, on ? 1 : 0);
        } else {
            audio.setParameters("AudioSetForceToSpeaker=" + (on ? 1 : 0));
        }
        speaker = on;
        log.add("speaker: " + on);
    }

    /** AudioSystem.setForceUse (not public): usage 1, config 1 = speaker, 0 = normal. */
    private void forceUse(int usage, int config) {
        try {
            Class<?> system = Class.forName("android.media.AudioSystem");
            Method m = system.getMethod("setForceUse", int.class, int.class);
            m.invoke(null, usage, config);
        } catch (Exception e) {
            log.add("setForceUse: " + e);
        }
    }

    @Override
    public void stopAudio() {
        if (speaker) setSpeaker(false);
        if (player == null) return;
        if (SOUND_KK.equals(sound)) audio.setParameters("AudioFmPreStop=1");
        try {
            player.stop();
        } catch (IllegalStateException e) {
            // Already stopped.
        }
        player.release();
        player = null;
    }

    @Override
    public int volumeStream() {
        return SOUND_KK.equals(sound) ? AudioManager.STREAM_MUSIC : fmStream;
    }
}
