// SPDX-License-Identifier: GPL-3.0-or-later
package com.android.settings.grapefruit;

/** Adapted from Grapefruit Settings 9211bba. All four IDs now apply to media too. */
final class ThemeState {
    final int theme, mediaTheme;
    final String revision, mediaRevision;
    ThemeState(int t, int m, String r, String mr) { theme=t; mediaTheme=m; revision=r; mediaRevision=mr; }
    ThemeState select(int id, String token) {
        if (id < 0 || id > 3) throw new IllegalArgumentException("Unknown Grapefruit theme");
        return new ThemeState(id,id,token,token);
    }
}
