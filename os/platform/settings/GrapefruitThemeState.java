// SPDX-License-Identifier: Apache-2.0
package com.android.settings.grapefruit;

import android.app.UiModeManager;
import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/** State belongs to the calling Android user. Updates preserve unrelated theme JSON fields. */
final class GrapefruitThemeState {
    static final Uri URI = Uri.parse("content://io.github.hayliewordsman.grapefruitsettings.theme/theme");
    static final String ID = "grapefruit.theme", REVISION = "grapefruit.revision";
    private static final String[] SEEDS = {"FF7F6B", "E83850", "FF7F6B", "67C7FF"};
    static JSONObject read(Context context) {
        String raw = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES);
        try { return raw == null ? new JSONObject() : new JSONObject(raw); }
        catch (JSONException invalid) { return new JSONObject(); }
    }
    static final class Snapshot {
        final int theme;
        final String revision;
        Snapshot(int theme, String revision) { this.theme = theme; this.revision = revision; }
    }
    static Snapshot snapshot(Context context) {
        JSONObject state = read(context);
        int fallback = context.getResources().getInteger(com.android.internal.R.integer.config_grapefruitDefaultTheme);
        int id = state.optInt(ID, fallback);
        if (id < 0 || id >= 4) id = 0;
        return new Snapshot(id, state.optString(REVISION, "default-" + id));
    }
    static int selected(Context context) { return snapshot(context).theme; }
    static void select(Context context, int id) {
        if (id < 0 || id >= 4) throw new IllegalArgumentException("Unknown Grapefruit theme");
        JSONObject state = read(context);
        try {
            state.put(ID, id);
            state.put(REVISION, UUID.randomUUID().toString());
            state.put("android.theme.customization.system_palette", SEEDS[id]);
            state.put("android.theme.customization.accent_color", SEEDS[id]);
            state.put("android.theme.customization.color_source", "preset");
            state.put("android.theme.customization.theme_style", "TONAL_SPOT");
            state.remove("android.theme.customization.dynamic_color");
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        if (!Settings.Secure.putString(context.getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, state.toString())) {
            throw new IllegalStateException("Could not save the theme");
        }
        context.getSystemService(UiModeManager.class).setNightMode(
                id == 1 ? UiModeManager.MODE_NIGHT_NO : UiModeManager.MODE_NIGHT_YES);
        context.getContentResolver().notifyChange(URI, null);
    }
}
