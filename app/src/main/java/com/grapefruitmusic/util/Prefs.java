package com.grapefruitmusic.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/** App settings. */
public final class Prefs {
    /** No theme picked: Grapefruit Light, or Pitch Black while the phone is in dark mode. */
    public static final int THEME_SYSTEM = 0;
    public static final int THEME_STARLIGHT = 1;
    public static final int THEME_PITCH_BLACK = 2;
    public static final int THEME_GRAPEFRUIT_LIGHT = 3;
    public static final int THEME_GRAPEFRUIT_DARK = 4;

    /** Quality 0 means "original file when this phone can play it"; otherwise MP3 at that kbps. */
    public static final int QUALITY_ORIGINAL = 0;
    public static final int[] QUALITIES = {QUALITY_ORIGINAL, 320, 192, 128};

    private static SharedPreferences prefs;

    private Prefs() {
    }

    public static void init(Context context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static SharedPreferences raw() {
        return prefs;
    }

    public static int theme() {
        return prefs.getInt("theme", THEME_SYSTEM);
    }

    public static void setTheme(int theme) {
        prefs.edit().putInt("theme", theme).apply();
    }

    public static int streamQuality() {
        return prefs.getInt("stream_quality", QUALITY_ORIGINAL);
    }

    public static void setStreamQuality(int q) {
        prefs.edit().putInt("stream_quality", q).apply();
    }

    public static int downloadQuality() {
        return prefs.getInt("download_quality", QUALITY_ORIGINAL);
    }

    public static void setDownloadQuality(int q) {
        prefs.edit().putInt("download_quality", q).apply();
    }

    public static boolean wifiOnlyDownloads() {
        return prefs.getBoolean("wifi_only", true);
    }

    public static void setWifiOnlyDownloads(boolean v) {
        prefs.edit().putBoolean("wifi_only", v).apply();
    }

    public static boolean onlineLyrics() {
        return prefs.getBoolean("online_lyrics", true);
    }

    public static void setOnlineLyrics(boolean v) {
        prefs.edit().putBoolean("online_lyrics", v).apply();
    }

    /** Volume Match: play songs at about the same loudness. */
    public static boolean volumeMatch() {
        return prefs.getBoolean("volume_match", false);
    }

    public static void setVolumeMatch(boolean v) {
        prefs.edit().putBoolean("volume_match", v).apply();
    }

    /** Volume Boost level, 0 (off) to 4 (Extreme); see playback.Dsp. */
    public static int volumeBoost() {
        return prefs.getInt("volume_boost", 0);
    }

    public static void setVolumeBoost(int level) {
        prefs.edit().putInt("volume_boost", level).apply();
    }

    public static boolean bassBoost() {
        return prefs.getBoolean("bass_boost", false);
    }

    public static void setBassBoost(boolean on) {
        prefs.edit().putBoolean("bass_boost", on).apply();
    }

    /** Folders hidden from On This Phone (absolute paths, no trailing slash). */
    public static Set<String> excludedFolders() {
        return new TreeSet<String>(prefs.getStringSet("excluded_folders", new HashSet<String>()));
    }

    public static void setExcludedFolders(Set<String> folders) {
        prefs.edit().putStringSet("excluded_folders", new HashSet<String>(folders)).apply();
    }

    public static String currentSource() {
        return prefs.getString("current_source", null);
    }

    public static void setCurrentSource(String id) {
        prefs.edit().putString("current_source", id).apply();
    }

    public static String deviceId() {
        String id = prefs.getString("device_id", null);
        if (id == null) {
            id = Hashes.randomHex(16);
            prefs.edit().putString("device_id", id).apply();
        }
        return id;
    }

    public static String qualityLabel(int q) {
        return q == QUALITY_ORIGINAL ? "Original" : ("MP3 " + q + " kbps");
    }
}
