/*
 * This file is a part of Grapefruitgram Messenger, based on Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Follows the Grapefruit universal theme, the optional, read-only theme contract shared by the
 * Grapefruit apps (grapefruit-ui/src/main/java/com/grapefruitmusic/ui/UniversalTheme.java):
 * content://io.github.hayliewordsman.grapefruitsettings.theme/theme, with a general "theme" id
 * (Dark 0, Light 1, Black 2, Blue 3) and a "revision" token that changes on each new selection.
 *
 * A new revision switches the app to the matching Grapefruit theme; the same revision is never
 * applied twice, so a theme chosen in the app stays until the next universal selection. Without the
 * companion (or with an incompatible one) nothing changes. Nothing is ever written to the provider.
 */
package org.thunderdog.challegram.grapefruit;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.theme.ThemeSet;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

public final class UniversalTheme {
  public static final Uri URI = Uri.parse("content://io.github.hayliewordsman.grapefruitsettings.theme/theme");
  private static final String PREFS = "grapefruit_universal_theme";
  private static final String KEY_REVISION = "universal_revision";

  /** Canonical universal ids, in order, to Grapefruitgram's built-in themes. */
  private static final int[] THEMES = {
    ThemeId.GRAPEFRUIT_DARK,
    ThemeId.GRAPEFRUIT_LIGHT,
    ThemeId.PITCH_BLACK,
    ThemeId.VICTORY_BLUE
  };

  private static volatile boolean checking;

  private UniversalTheme () { }

  /** The built-in theme for a universal id, or ThemeId.NONE for an unknown one. */
  public static int themeFor (int universalId) {
    return universalId >= 0 && universalId < THEMES.length ? THEMES[universalId] : ThemeId.NONE;
  }

  /** A pending selection: the theme and its revision. */
  static final class Selection {
    final int themeId;
    final String revision;

    Selection (int themeId, String revision) {
      this.themeId = themeId;
      this.revision = revision;
    }
  }

  /** Reads the provider; null when there's nothing new (or no companion). Any thread. */
  @Nullable
  static Selection read (Context context) {
    SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    try (Cursor cursor = context.getContentResolver().query(URI, null, null, null, null)) {
      if (cursor == null || !cursor.moveToFirst())
        return null;
      String value = cursor.getString(cursor.getColumnIndexOrThrow("theme"));
      String revision = cursor.getString(cursor.getColumnIndexOrThrow("revision"));
      if (value == null || revision == null || revision.equals(prefs.getString(KEY_REVISION, "")))
        return null;
      int themeId = themeFor(Integer.parseInt(value.trim()));
      if (themeId == ThemeId.NONE)
        return null;
      return new Selection(themeId, revision);
    } catch (RuntimeException unavailable) {
      // Optional companion absent, inaccessible or incompatible: keep the local choice.
      return null;
    }
  }

  /** Checks for a new universal selection off the main thread, then applies it. */
  public static void check (final Context context) {
    if (checking)
      return;
    checking = true;
    final Context app = context.getApplicationContext();
    new Thread(() -> {
      final Selection selection;
      try {
        selection = read(app);
      } finally {
        checking = false;
      }
      if (selection != null) {
        UI.post(() -> apply(app, selection));
      }
    }, "GrapefruitUniversalTheme").start();
  }

  static void apply (Context context, Selection selection) {
    Tdlib tdlib = TdlibManager.instance().current();
    ThemeDelegate theme = ThemeSet.getBuiltinTheme(selection.themeId);
    ThemeManager manager = ThemeManager.instance();
    // A system-wide choice is the user's own: like picking a theme in Settings, it ends
    // automatic night mode when it changes between light and dark.
    if (manager.isCurrentThemeDark() != theme.isDark() && Settings.instance().getNightMode() != Settings.NIGHT_MODE_NONE) {
      Settings.instance().setAutoNightMode(Settings.NIGHT_MODE_NONE);
    }
    if (!manager.isCurrentTheme(selection.themeId)) {
      manager.changeGlobalTheme(tdlib, theme, true, null);
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_REVISION, selection.revision)
      .apply();
  }
}
