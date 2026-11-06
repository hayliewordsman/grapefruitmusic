// SPDX-License-Identifier: Apache-2.0
package com.android.settings.grapefruit;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import java.util.Arrays;
import java.util.List;

/** Exposes only a theme ID and revision; never accepts writes or returns Settings data. */
public final class GrapefruitThemeProvider extends ContentProvider {
    private static final List<String> COLUMNS = Arrays.asList(
            "theme", "revision", "media_theme", "media_revision", "os");
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        check(uri);
        if (selection != null || selectionArgs != null || sortOrder != null)
            throw new IllegalArgumentException("Only a single theme row is available");
        String[] columns = projection == null ? COLUMNS.toArray(new String[0]) : projection;
        GrapefruitThemeState.Snapshot state = GrapefruitThemeState.snapshot(getContext());
        int theme = state.theme;
        String revision = state.revision;
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (!COLUMNS.contains(column)) throw new IllegalArgumentException("Unknown column: " + column);
            switch (column) {
                case "theme": case "media_theme": row.add(theme); break;
                case "revision": case "media_revision": row.add(revision); break;
                case "os": row.add(1); break;
            }
        }
        cursor.setNotificationUri(getContext().getContentResolver(), GrapefruitThemeState.URI);
        return cursor;
    }
    private static void check(Uri uri) {
        if (!GrapefruitThemeState.URI.equals(uri)) throw new IllegalArgumentException("Unknown URI");
    }
    @Override public String getType(Uri uri) { check(uri); return "vnd.android.cursor.item/vnd.grapefruit.theme"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw readOnly(); }
    @Override public int update(Uri uri, ContentValues values, String s, String[] a) { throw readOnly(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw readOnly(); }
    private static SecurityException readOnly() { return new SecurityException("Choose themes in system Settings"); }
}
