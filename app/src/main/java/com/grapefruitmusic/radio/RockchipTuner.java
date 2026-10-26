package com.grapefruitmusic.radio;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The FM radio of Rockchip tablets (the JP5S: an RK3188 with a QN8035-type receiver at
 * /dev/RADIO_FM8035), driven through Rockchip's own FM Radio app (com.rk.FmRadio). Only that app's
 * service may open the radio device (it runs as the system user), and it's exported for other
 * apps to use: Grapefruit binds to it and calls its IfmService interface the way the app's own
 * screen does. The radio's sound takes its own path to the headphones; its volume is the chip's.
 *
 * Frequencies are in units of 10 kHz, the same as Rockchip's.
 */
final class RockchipTuner implements Tuner {
    static final String PACKAGE = "com.rk.FmRadio";
    static final String SERVICE = "com.rk.FmRadio.FmRadioService";
    static final String DESCRIPTOR = "com.rk.FmRadio.IfmService";

    // IfmService's methods, numbered as its Stub numbers them.
    static final int SET_AREA = 1;
    static final int SET_FREQUENCY = 2;
    static final int STATION_HERE = 7;
    static final int ON = 8;
    static final int OFF = 9;
    static final int MUTE = 10;
    static final int IS_ON = 11;
    static final int SET_VOLUME = 14;
    static final int GET_VOLUME = 15;

    /** Rockchip's band setting for 87.0 to 108.0 MHz in 0.1 MHz steps (most of the world). */
    static final int AREA_87_108 = 86;
    /** The chip's volume goes from 0 to 15. */
    static final int MAX_VOLUME = 15;
    /** Rockchip's seek waits this long between steps. */
    private static final long SEEK_PAUSE_MS = 20;

    private final RadioLog log;
    private final Context context;
    private volatile IBinder binder;
    private ServiceConnection connection;
    /** Where the chip was last tuned: the direction of the next tune tells it which way it moved. */
    private int tuned = -1;

    /** Is Rockchip's FM Radio app on this tablet? */
    static boolean present(Context c) {
        try {
            return c.getPackageManager().getServiceInfo(new ComponentName(PACKAGE, SERVICE), 0) != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Connects to the radio service (waiting up to 5 seconds), or null. Not on the main thread. */
    static RockchipTuner open(Context c, RadioLog log) {
        if (!present(c)) return null;
        RockchipTuner t = new RockchipTuner(c, log);
        return t.connect() ? t : null;
    }

    /** A tuner talking to an already-connected service (tests). */
    RockchipTuner(Context context, RadioLog log, IBinder binder) {
        this.context = context;
        this.log = log;
        this.binder = binder;
    }

    private RockchipTuner(Context context, RadioLog log) {
        this(context.getApplicationContext(), log, null);
    }

    private boolean connect() {
        final CountDownLatch connected = new CountDownLatch(1);
        connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binder = service;
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                binder = null;
                log.add("Rockchip radio service went away");
            }
        };
        Intent intent = new Intent().setComponent(new ComponentName(PACKAGE, SERVICE));
        boolean bound;
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            log.add("Rockchip radio service: " + e);
            return false;
        }
        if (!bound) {
            log.add("Rockchip radio service: can't connect");
            return false;
        }
        try {
            connected.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        IBinder b = binder;
        String descriptor = null;
        try {
            if (b != null) descriptor = b.getInterfaceDescriptor();
        } catch (RemoteException e) {
            log.add("Rockchip radio service: " + e);
        }
        if (!DESCRIPTOR.equals(descriptor)) {
            log.add("Rockchip radio service: " + (b == null ? "didn't answer" : "unexpected interface " + descriptor));
            context.unbindService(connection);
            binder = null;
            return false;
        }
        log.add("Rockchip radio service: connected");
        return true;
    }

    /**
     * Calls one of IfmService's methods with int arguments (an AIDL boolean is an int too) and
     * returns its reply, positioned after the exception header; null if it failed. Recycle it.
     */
    private Parcel call(int method, int... args) {
        IBinder b = binder;
        if (b == null) return null;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            for (int a : args) data.writeInt(a);
            b.transact(method, data, reply, 0);
            reply.readException();
            return reply;
        } catch (RemoteException e) {
            log.add("Rockchip radio call " + method + ": " + e);
        } catch (RuntimeException e) {
            log.add("Rockchip radio call " + method + ": " + e);
        } finally {
            data.recycle();
        }
        reply.recycle();
        return null;
    }

    private boolean callVoid(int method, int... args) {
        Parcel reply = call(method, args);
        if (reply == null) return false;
        reply.recycle();
        return true;
    }

    private int callInt(int method, int fallback, int... args) {
        Parcel reply = call(method, args);
        if (reply == null) return fallback;
        try {
            return reply.readInt();
        } finally {
            reply.recycle();
        }
    }

    private boolean callBoolean(int method) {
        return callInt(method, 0) != 0;
    }

    @Override
    public String name() {
        return "Rockchip";
    }

    @Override
    public boolean powerUp(int freq) {
        if (!callVoid(ON)) return false;
        // The service keeps the band the radio app last used; Grapefruit's dial is 87.5 to 108.
        callVoid(SET_AREA, AREA_87_108);
        tuned = -1;
        tune(freq);
        boolean on = callBoolean(IS_ON);
        log.add("power up at " + Radio.format(freq) + ": " + (on ? "ok" : "the service says it's off"));
        return on;
    }

    @Override
    public void powerDown() {
        callVoid(OFF);
    }

    @Override
    public boolean tune(int freq) {
        // 1 moving up the band, 0 moving down (Rockchip's app passes the direction).
        boolean ok = callVoid(SET_FREQUENCY, freq, freq >= tuned ? 1 : 0);
        if (ok) tuned = freq;
        return ok;
    }

    /** Is there a station at the frequency the chip is on? */
    private boolean stationHere() {
        return callBoolean(STATION_HERE);
    }

    @Override
    public int seek(int freq, boolean up) {
        // As Rockchip's app seeks: a step at a time until the chip hears a station, all the way round.
        int f = freq;
        int steps = (Radio.HIGHEST - Radio.LOWEST) / Radio.STEP + 1;
        for (int i = 0; i < steps; i++) {
            f += up ? Radio.STEP : -Radio.STEP;
            if (f > Radio.HIGHEST) f = Radio.LOWEST;
            if (f < Radio.LOWEST) f = Radio.HIGHEST;
            if (f == freq) break;
            if (!callVoid(SET_FREQUENCY, f, up ? 1 : 0)) return -1;
            tuned = f;
            if (stationHere()) return f;
            try {
                Thread.sleep(SEEK_PAUSE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }
        return -1;
    }

    @Override
    public int[] scan() {
        List<Integer> found = new ArrayList<Integer>();
        tuned = -1;
        for (int f = Radio.LOWEST; f <= Radio.HIGHEST; f += Radio.STEP) {
            if (!callVoid(SET_FREQUENCY, f, 1)) return null;
            tuned = f;
            if (stationHere()) found.add(f);
        }
        int[] out = new int[found.size()];
        for (int i = 0; i < out.length; i++) out[i] = found.get(i);
        log.add("scan: " + out.length + " stations");
        return out;
    }

    @Override
    public void setMute(boolean mute) {
        callVoid(MUTE, mute ? 1 : 0);
    }

    @Override
    public void useHeadphoneAntenna(boolean headphones) {
        // The chip only has the headphone wire.
    }

    @Override
    public boolean setRds(boolean on) {
        // No RDS on this chip (nor in Rockchip's interface).
        return false;
    }

    @Override
    public Rds pollRds() {
        return null;
    }

    @Override
    public boolean startAudio(boolean speaker) {
        // The chip's sound goes straight to the headphones once it's on.
        return true;
    }

    @Override
    public void setSpeaker(boolean speaker) {
    }

    @Override
    public void stopAudio() {
    }

    @Override
    public boolean canUseSpeaker() {
        return false;
    }

    @Override
    public int volumeStream() {
        return AudioManager.STREAM_MUSIC;
    }

    @Override
    public int volumeMax() {
        return MAX_VOLUME;
    }

    @Override
    public int volume() {
        return callInt(GET_VOLUME, MAX_VOLUME);
    }

    @Override
    public void setVolume(int volume) {
        callInt(SET_VOLUME, 0, Math.max(0, Math.min(MAX_VOLUME, volume)));
    }
}
