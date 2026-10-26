package com.mediatek.FMRadio;

/**
 * Grapefruit's handle on the FM radio chip of MediaTek-based tablets (and tablets whose radio
 * chip copies MediaTek's interface).
 *
 * Android has no public FM radio API. On these tablets the radio is driven by the system library
 * libfmjni.so, which, as it loads, attaches its functions to a class with exactly this name: the
 * one in MediaTek's own FM Radio app. Declaring the same class here lets Grapefruit load the
 * library and drive the radio itself. Loading fails if the library attaches a function that isn't
 * declared here, so this declares every one known, under the Jelly Bean names and the later
 * camelCase ones; functions a tablet's library doesn't have are simply never called (see
 * radio.MediatekTuner).
 *
 * Frequencies are in MHz (98.7f).
 */
public final class FMRadioNative {
    private FMRadioNative() {
    }

    /** Loads libfmjni.so. Throws UnsatisfiedLinkError if it's missing or doesn't fit this class. */
    public static void load() {
        System.loadLibrary("fmjni");
    }

    // ---- Jelly Bean (MediaTek's FMRadio app, 2012 to 2013) ----

    public static native boolean opendev();

    public static native boolean closedev();

    public static native boolean powerup(float frequency);

    /** type: 0 receive, 1 transmit. */
    public static native boolean powerdown(int type);

    public static native boolean tune(float frequency);

    /** The next station up or down from frequency, in MHz. */
    public static native float seek(float frequency, boolean isUp);

    /** Every station on the band, in units of 100 kHz (987) or 10 kHz (9870). */
    public static native short[] autoscan();

    public static native boolean stopscan();

    public static native int readRssi();

    public static native int rdsset(boolean rdson);

    /** Which RDS information changed (bit flags), waiting a little for some. */
    public static native short readrds();

    public static native short getPI();

    public static native byte getPTY();

    public static native byte[] getPS();

    public static native byte[] getLRText();

    public static native short activeAF();

    public static native short[] getAFList();

    public static native short activeTA();

    public static native short deactiveTA();

    public static native int setmute(boolean mute);

    public static native int getchipid();

    public static native int isRDSsupport();

    public static native int isFMPoweredUp();

    /** 0: the headphone wire, 1: the short built-in antenna (if there is one). */
    public static native int switchAntenna(int antenna);

    public static native boolean stereoMono();

    public static native boolean setStereoMono(boolean isMono);

    public static native short readCapArray();

    public static native short readRdsBler();

    public static native boolean setFMViaBTController(boolean bEnable);

    public static native int[] getHardwareVersion();

    public static native short[] scannew(int upper, int lower, int space);

    public static native int seeknew(int upper, int lower, int space, int freq, int dir, int lev);

    public static native boolean tunenew(int upper, int lower, int space, int freq);

    public static native short[] emcmd(short[] val);

    public static native boolean emsetth(int index, int value);

    public static native int getFMStatus(int which);

    public static native boolean setFMStatus(int which, boolean on);

    // ---- The same functions as named from KitKat on ----

    public static native boolean openDev();

    public static native boolean closeDev();

    public static native boolean powerUp(float frequency);

    public static native boolean powerDown(int type);

    public static native short[] autoScan();

    public static native boolean stopScan();

    public static native int setRds(boolean rdson);

    public static native short readRds();

    public static native byte[] getPs();

    public static native byte[] getLrText();

    public static native short activeAf();

    public static native int setMute(boolean mute);

    public static native int isRdsSupport();

    public static native boolean setFmStatus(int which, boolean on);

    public static native boolean getFmStatus(int which);
}
