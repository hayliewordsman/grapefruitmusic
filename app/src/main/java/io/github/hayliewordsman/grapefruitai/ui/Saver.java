package io.github.hayliewordsman.grapefruitai.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import io.github.hayliewordsman.grapefruitai.util.Bg;

/**
 * Saves images and downloaded files where the user can find them: Pictures/Grapefruit AI and
 * Download/Grapefruit AI. Android 10+: MediaStore (no permission). Android 4.2-9: the public
 * folders, with the storage permission asked for at the time of saving on Android 6-9.
 */
public final class Saver {
    private static final int REQUEST = 7;
    private static Runnable pending;

    private Saver() {
    }

    /** Saves an image as PNG or JPEG, re-encoding when the format differs from the source. */
    public static void saveImage(final Activity a, final byte[] data, final String sourceMime, final boolean jpeg) {
        Bg.run(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] out = data;
                    String mime = jpeg ? "image/jpeg" : "image/png";
                    if (!mime.equals(sourceMime)) {
                        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
                        if (b == null) throw new IOException("Not an image Android can read");
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        b.compress(jpeg ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, jpeg ? 92 : 100, bytes);
                        b.recycle();
                        out = bytes.toByteArray();
                    }
                    String name = "grapefruit-ai-" + System.currentTimeMillis() + (jpeg ? ".jpg" : ".png");
                    save(a, out, name, mime, true);
                } catch (final Exception e) {
                    report(a, null, e);
                }
            }
        });
    }

    /** Saves any file to Download/Grapefruit AI. */
    public static void saveFile(final Activity a, final byte[] data, final String name, final String mime) {
        Bg.run(new Runnable() {
            @Override
            public void run() {
                try {
                    save(a, data, safeName(name), mime != null ? mime : "application/octet-stream", false);
                } catch (Exception e) {
                    report(a, null, e);
                }
            }
        });
    }

    static String safeName(String name) {
        String n = name == null ? "file" : name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_").trim();
        if (n.startsWith(".")) n = "_" + n;
        return n.length() == 0 ? "file" : n;
    }

    private static void save(final Activity a, final byte[] data, final String name, final String mime,
                             final boolean picture) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            saveMediaStore(a, data, name, mime, picture);
            report(a, (picture ? "Pictures" : "Download") + "/Grapefruit AI/" + name, null);
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && a.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Bg.ui(new Runnable() {
                @Override
                public void run() {
                    pending = new Runnable() {
                        @Override
                        public void run() {
                            Bg.run(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        saveLegacy(a, data, name, mime, picture);
                                    } catch (Exception e) {
                                        report(a, null, e);
                                    }
                                }
                            });
                        }
                    };
                    requestPermission(a);
                }
            });
            return;
        }
        saveLegacy(a, data, name, mime, picture);
    }

    @TargetApi(23)
    private static void requestPermission(Activity a) {
        a.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST);
    }

    static void onPermissionResult(Activity a, int request, int[] results) {
        if (request != REQUEST) return;
        Runnable r = pending;
        pending = null;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            if (r != null) r.run();
        } else {
            Ui.toast(a, "Saving needs storage access");
        }
    }

    @SuppressWarnings("deprecation")
    private static void saveLegacy(Activity a, byte[] data, String name, String mime, boolean picture)
            throws IOException {
        File root = Environment.getExternalStoragePublicDirectory(
                picture ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(root, "Grapefruit AI");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Can't create " + dir);
        File f = unique(dir, name);
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(data);
        } finally {
            out.close();
        }
        android.media.MediaScannerConnection.scanFile(a, new String[]{f.getAbsolutePath()}, new String[]{mime}, null);
        report(a, f.getAbsolutePath(), null);
    }

    private static File unique(File dir, String name) {
        File f = new File(dir, name);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; f.exists(); i++) f = new File(dir, base + " (" + i + ")" + ext);
        return f;
    }

    @TargetApi(29)
    private static void saveMediaStore(Activity a, byte[] data, String name, String mime, boolean picture)
            throws IOException {
        ContentResolver cr = a.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                (picture ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS) + "/Grapefruit AI");
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri collection = picture ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = cr.insert(collection, v);
        if (uri == null) throw new IOException("Could not create the file");
        OutputStream out = cr.openOutputStream(uri);
        if (out == null) throw new IOException("Could not write the file");
        try {
            out.write(data);
        } finally {
            out.close();
        }
        v.clear();
        v.put(MediaStore.MediaColumns.IS_PENDING, 0);
        cr.update(uri, v, null, null);
    }

    private static void report(final Activity a, final String where, final Exception e) {
        Bg.ui(new Runnable() {
            @Override
            public void run() {
                if (e != null) Ui.toast(a, "Couldn't save: " + e.getMessage());
                else Ui.toast(a, "Saved to " + where);
            }
        });
    }
}
