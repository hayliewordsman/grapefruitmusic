#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Recreate patches from a directory of the pinned upstream input files in upstream.json."""
import argparse
import difflib
import hashlib
import json
import re
from pathlib import Path
from font_config import transform

ROOT = Path(__file__).resolve().parents[2]


def once(text, old, new):
    if text.count(old) != 1:
        raise ValueError("Upstream anchor changed: " + old[:100])
    return text.replace(old, new, 1)


def build(upstream):
    lock = json.loads((ROOT / "os/upstream.json").read_text())
    patches = {key: [] for key in lock["projects"]}

    def edit(project, name, replacement):
        info = lock["projects"][project]["files"][name]
        old = (upstream / name).read_text()
        if hashlib.sha256(old.encode()).hexdigest() != info["sha256"]:
            raise ValueError("Pinned input mismatch: " + name)
        new = replacement(old)
        patches[project].extend(difflib.unified_diff(old.splitlines(True), new.splitlines(True),
                                                  "a/" + info["path"], "b/" + info["path"]))

    def add(project, path, content):
        patches[project].extend(difflib.unified_diff([], content.splitlines(True), "/dev/null", "b/" + path))

    def systemui(s):
        s = once(s, "import android.app.ActivityManager;", "import com.grapefruit.os.theme.GrapefruitPalette;\nimport android.app.ActivityManager;")
        start = s.index("        final WallpaperColors currentColors =", s.index("private void reevaluateSystemTheme"))
        end = s.index("\n        if (mMainWallpaperColor", start)
        s = s[:start] + "        final int mainColor = GrapefruitPalette.seed(grapefruitTheme());\n" + s[end:]
        s = once(s, "    private void createOverlays(int color) {", """    private int grapefruitTheme() {
        int fallback = mResources.getInteger(com.android.internal.R.integer.config_grapefruitDefaultTheme);
        String raw = mSecureSettings.getStringForUser(
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, mUserTracker.getUserId());
        try {
            return GrapefruitPalette.valid(raw == null ? fallback
                    : new JSONObject(raw).optInt("grapefruit.theme", fallback));
        } catch (JSONException invalid) { return GrapefruitPalette.valid(fallback); }
    }

    private void createOverlays(int color) {
        // Grapefruit selection is authoritative; wallpaper extraction never changes the palette.
        color = GrapefruitPalette.seed(grapefruitTheme());
        mThemeStyle = ThemeStyle.TONAL_SPOT;""")
        for mode in ["Light", "Dark"]:
            orig = f"p.second.getArgb(m{mode}ColorScheme.getMaterialScheme())"
            s = once(s, orig, f'GrapefruitPalette.role(grapefruitTheme(), p.first, {str(mode == "Dark").lower()}, mContrast, {orig})')
        # Match the same custom roles in AOSP's optimization that checks installed overlays.
        for method, role in [("outlineVariant", "outline_variant"), ("primaryContainer", "primary_container")]:
            for mode in ["Light", "Dark"]:
                pattern = rf"dynamicColors\.{method}\(\)\.getArgb\(\s*m{mode}ColorScheme\.getMaterialScheme\(\)\)"
                s, count = re.subn(pattern, lambda m: f'GrapefruitPalette.role(grapefruitTheme(), "{role}", {str(mode == "Dark").lower()}, mContrast, {m[0]})', s)
                if count != 1:
                    raise ValueError("Overlay comparison anchor changed")
        return s

    edit("frameworks/base", "ThemeOverlayController.java", systemui)
    add("frameworks/base", "core/res/res/values/grapefruit_config.xml",
        '<resources><integer name="config_grapefruitDefaultTheme">0</integer></resources>\n')
    add("frameworks/base", "packages/SystemUI/src/com/grapefruit/os/theme/GrapefruitPalette.java",
        (ROOT / "os/generated/GrapefruitPalette.java").read_text())

    def fontbp(s):
        s = once(s, 'tools: [":generate_fonts_xml"],', 'tools: [":generate_fonts_xml", "grapefruit_font_config"],')
        return once(s, '"-o $(out)",', '"-o $(out) && $(location grapefruit_font_config) $(out)",')
    edit("frameworks/base", "base_Android.bp", fontbp)
    def pills(s):
        # Keep AOSP semantics, animation, focus, detail targets and minimum touch sizes.
        for resource in ["active_tile", "inactive_tile"]:
            old = "get() = dimensionResource(id = R.dimen.common_tile_default_" + resource + "_corner_radius)"
            s = once(s, old, "get() = TileHeight / 2")
        for resource in ["active_icon", "inactive_icon"]:
            old = "get() = dimensionResource(id = R.dimen.common_tile_default_" + resource + "_corner_radius)"
            s = once(s, old, "get() = ToggleTargetSize / 2")
        return s
    edit("frameworks/base", "S_CommonTile.kt", pills)


    def legacyfonts(s):
        replacement = re.search(r'<family name="sans-serif">.*?</family>', transform(s), re.S)[0]
        return re.sub(r'<family name="sans-serif">.*?</family>', lambda _: replacement, s, count=1, flags=re.S)
    edit("frameworks/base", "fonts.xml", legacyfonts)

    def settingsmanifest(s):
        return once(s, "    </application>", """        <!-- Read-only compatibility contract for the Grapefruit app family. -->
        <provider android:name="com.android.settings.grapefruit.GrapefruitThemeProvider"
            android:authorities="io.github.hayliewordsman.grapefruitsettings.theme"
            android:exported="true" android:directBootAware="true" />
    </application>""")
    edit("packages/apps/Settings", "settings_AndroidManifest.xml", settingsmanifest)
    edit("packages/apps/Settings", "settings_display.xml", lambda s: once(s, '    <PreferenceCategory\n        android:key="category_brightness"', '''    <Preference android:key="grapefruit_theme"
        android:title="@string/grapefruit_theme_title" android:order="-210"
        android:fragment="com.android.settings.grapefruit.GrapefruitThemeSettings" />

    <PreferenceCategory\n        android:key="category_brightness"'''))
    edit("packages/apps/Settings", "SettingsGateway.java", lambda s: once(s,
        "            DisplaySettings.class.getName(),",
        "            DisplaySettings.class.getName(),\n            com.android.settings.grapefruit.GrapefruitThemeSettings.class.getName(),"))
    for p in (ROOT / "os/platform/settings").iterdir():
        if p.suffix == ".java":
            dest = "src/com/android/settings/grapefruit/" + p.name
        else:
            dest = ("res/xml/" if p.name.endswith("settings.xml") else "res/values/") + p.name
        add("packages/apps/Settings", dest, p.read_text())

    edit("packages/apps/Launcher3", "L_QuickstepLauncher.java", lambda s: once(s,
        "    protected void onResume() {\n        super.onResume();",
        """    protected void onResume() {
        super.onResume();
        com.android.launcher3.grapefruit.GrapefruitLauncherStyle.apply(
                this, getDragLayer(), getHotseat().getShortcutsAndWidgets());"""))

    def bubble(s):
        s = once(s, "    private FastBitmapDrawable mIcon;", """    private FastBitmapDrawable mIcon;
    private com.android.launcher3.grapefruit.GrapefruitLauncherStyle mGrapefruitStyle;
    private final Rect mGrapefruitIconBounds = new Rect();""")
        s = once(s, '    @SuppressWarnings("wrongcall")', ''''    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mGrapefruitStyle = null;
        invalidate();
    }

    @SuppressWarnings("wrongcall")''')
        return once(s, "    public void onDraw(Canvas canvas) {", """    public void onDraw(Canvas canvas) {
        if (mIcon != null && (mDisplay == DISPLAY_WORKSPACE || mDisplay == DISPLAY_ALL_APPS || mDisplay == DISPLAY_FOLDER)) {
            if (mGrapefruitStyle == null) {
                mGrapefruitStyle = new com.android.launcher3.grapefruit.GrapefruitLauncherStyle(getContext());
                mGrapefruitStyle.applyText(this);
            }
            getIconBounds(mGrapefruitIconBounds);
            mGrapefruitStyle.draw(canvas, this, mGrapefruitIconBounds, !mLayoutHorizontal);
        }""")
    edit("packages/apps/Launcher3", "L_BubbleTextView.java", bubble)
    edit("packages/apps/Launcher3", "L_strings.xml", lambda s: once(s,
        '<string name="app_name">Launcher3</string>', '<string name="app_name">Grapefruit Launcher</string>'))
    edit("packages/apps/Launcher3", "L_quickstep_strings.xml", lambda s: once(s,
        '<string name="derived_app_name" translatable="false">Quickstep</string>',
        '<string name="derived_app_name" translatable="false">Grapefruit Launcher</string>'))
    add("packages/apps/Launcher3", "src/com/android/launcher3/grapefruit/GrapefruitLauncherStyle.java",
        (ROOT / "os/platform/GrapefruitLauncherStyle.java").read_text())
    def ime_theme(s):
        return once(s, '''        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final KeyboardTheme[] availableThemeArray = getAvailableThemeArray(context);
        return getKeyboardTheme(prefs, BuildCompatUtils.EFFECTIVE_SDK_INT, availableThemeArray);''', '''        // The OS theme controls keyboard appearance; keep AOSP's native light/dark icon sets.
        final boolean night = (context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return searchKeyboardThemeById(night ? THEME_ID_LXX_DARK : THEME_ID_LXX_LIGHT,
                KEYBOARD_THEMES);''')
    edit("packages/inputmethods/LatinIME", "I_KeyboardTheme.java", ime_theme)

    def ime_switcher(s):
        s = once(s, "    private KeyboardTheme mKeyboardTheme;", "    private KeyboardTheme mKeyboardTheme;\n    private int mGrapefruitPaletteFingerprint;")
        s = once(s, '''            final KeyboardTheme keyboardTheme) {
        if (mThemeContext == null''', '''            final KeyboardTheme keyboardTheme) {
        final boolean night = (context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        final int palette = 31 * context.getColor(night
                ? android.R.color.system_background_dark : android.R.color.system_background_light)
                + context.getColor(night
                ? android.R.color.system_primary_dark : android.R.color.system_primary_light);
        if (palette != mGrapefruitPaletteFingerprint || mThemeContext == null''')
        return once(s, "            mKeyboardTheme = keyboardTheme;", "            mGrapefruitPaletteFingerprint = palette;\n            mKeyboardTheme = keyboardTheme;")
    edit("packages/inputmethods/LatinIME", "I_KeyboardSwitcher.java", ime_switcher)
    edit("packages/inputmethods/LatinIME", "I_AppearanceSettingsFragment.java", lambda s: once(s,
        "        ThemeSettingsFragment.updateKeyboardThemeSummary(findPreference(Settings.SCREEN_THEME));",
        "        findPreference(Settings.SCREEN_THEME).setSummary(R.string.grapefruit_keyboard_follows_system);"))
    edit("packages/inputmethods/LatinIME", "I_prefs_appearance.xml", lambda s: once(s, '''    <PreferenceScreen
        android:fragment="com.android.inputmethod.latin.settings.ThemeSettingsFragment"
        android:key="screen_theme"
        android:title="@string/settings_screen_theme" />''', '''    <Preference android:key="screen_theme"
        android:title="@string/grapefruit_keyboard_theme"
        android:summary="@string/grapefruit_keyboard_follows_system">
        <intent android:action="android.settings.DISPLAY_SETTINGS" />
    </Preference>'''))
    edit("packages/inputmethods/LatinIME", "I_appname.xml", lambda s:
         s.replace("Android Keyboard (AOSP)", "Grapefruit Keyboard")
          .replace("Android Keyboard Settings (AOSP)", "Grapefruit Keyboard Settings"))

    def ime_colors(s):
        def replace(m):
            name = m[1]
            if not (name.endswith("_lxx_light") or name.endswith("_lxx_dark")):
                return m[0]
            if "background_pressed" in name or "background_selected" in name:
                target = "grapefruit_key_down"
            elif "background" in name or "floating_preview_color" in name:
                target = "grapefruit_keyboard_surface"
            elif "highlight" in name or "trail" in name or "sliding" in name:
                target = "grapefruit_key_accent"
            elif "inactive" in name or "hint" in name or "suggested_word" in name or "spacebar" in name:
                target = "grapefruit_key_secondary"
            else:
                target = "grapefruit_key_text"
            return '<color name="' + name + '">@color/' + target + '</color>'
        return re.sub(r'<color name="([^"]+)">[^<]+</color>', replace, s)
    edit("packages/inputmethods/LatinIME", "I_colors.xml", ime_colors)
    for mode in ["dark", "light"]:
        def ime_styles(s, mode=mode):
            resources = {
                "btn_keyboard_key_lxx_": "@drawable/grapefruit_key",
                "btn_keyboard_key_functional_lxx_": "@drawable/grapefruit_action_key",
                "btn_keyboard_spacebar_lxx_": "@drawable/grapefruit_action_key",
                "btn_keyboard_key_popup_lxx_": "@drawable/grapefruit_key",
                "btn_keyboard_key_popup_action_lxx_": "@drawable/grapefruit_action_key",
                "keyboard_background_lxx_": "@color/grapefruit_keyboard_background",
                "keyboard_suggest_strip_lxx_": "@color/grapefruit_keyboard_surface",
                "keyboard_popup_panel_background_lxx_": "@color/grapefruit_keyboard_surface",
                "keyboard_key_feedback_lxx_": "@drawable/grapefruit_key_selected",
                "btn_suggestion_lxx_": "@drawable/grapefruit_key",
            }
            for resource, target in resources.items():
                s = s.replace("@drawable/" + resource + mode, target)
            return s
        edit("packages/inputmethods/LatinIME", "I_themes_" + mode + ".xml", ime_styles)
    for p in (ROOT / "os/platform/latinime/res").rglob("*.xml"):
        add("packages/inputmethods/LatinIME", "java/res/" + str(p.relative_to(ROOT / "os/platform/latinime/res")), p.read_text())

    for project, lines in patches.items():
        (ROOT / "os/patches" / (project.replace("/", "_") + ".patch")).write_text("".join(lines))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream", type=Path, required=True)
    build(parser.parse_args().upstream)
