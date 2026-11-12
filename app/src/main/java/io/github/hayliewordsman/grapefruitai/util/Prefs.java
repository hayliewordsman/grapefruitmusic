package io.github.hayliewordsman.grapefruitai.util;

import android.content.Context;
import android.content.SharedPreferences;

import io.github.hayliewordsman.grapefruitai.App;

/** Non-secret settings. Keys and passwords are in secure/Secrets, never here. */
public final class Prefs {
    public static final String THEME = "theme";
    public static final String CHAT_MODEL = "chat_model";
    public static final String IMAGE_MODEL = "image_model";
    public static final String WEB_SEARCH = "web_search";
    public static final String OC_URL = "opencode_url";
    public static final String OC_USER = "opencode_user";
    public static final String OC_DIRECTORY = "opencode_directory";
    public static final String OC_MODEL = "opencode_model";
    public static final String OC_PROTECTED = "opencode_protected";
    public static final String MODE = "mode";

    private Prefs() {
    }

    public static SharedPreferences get() {
        return App.get().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static String str(String key, String def) {
        return get().getString(key, def);
    }

    public static void put(String key, String value) {
        get().edit().putString(key, value).apply();
    }

    public static boolean bool(String key, boolean def) {
        return get().getBoolean(key, def);
    }

    public static void put(String key, boolean value) {
        get().edit().putBoolean(key, value).apply();
    }

    public static int integer(String key, int def) {
        return get().getInt(key, def);
    }

    public static void put(String key, int value) {
        get().edit().putInt(key, value).apply();
    }
}
