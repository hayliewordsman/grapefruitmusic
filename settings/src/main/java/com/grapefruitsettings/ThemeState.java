package com.grapefruitsettings;

/** Atomic universal selection, including the last supported media selection and its revision. */
final class ThemeState {
    final int theme, mediaTheme;
    final String revision, mediaRevision;
    ThemeState(int t, int m, String r, String mr) { theme=t; mediaTheme=m; revision=r; mediaRevision=mr; }
    ThemeState select(int id, String token) {
        if (id < 0 || id > 3) throw new IllegalArgumentException("Unknown Grapefruit theme");
        return new ThemeState(id, id == 3 ? mediaTheme : id, token, id == 3 ? mediaRevision : token);
    }
}
