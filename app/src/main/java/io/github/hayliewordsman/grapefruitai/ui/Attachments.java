package io.github.hayliewordsman.grapefruitai.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.github.hayliewordsman.grapefruitai.chat.Attachment;
import io.github.hayliewordsman.grapefruitai.chat.Store;
import io.github.hayliewordsman.grapefruitai.or.OpenRouter;

/** Picking files with the system picker and copying them into private storage. */
public final class Attachments {
    private Attachments() {
    }

    public static Intent pickIntent(String mime) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType(mime);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        return Intent.createChooser(i, "Attach");
    }

    /** Copies uri into the store; large photos are scaled to 2048 px to keep requests small. */
    public static Attachment importUri(Activity a, Store store, Uri uri) throws IOException {
        ContentResolver cr = a.getContentResolver();
        String name = "file";
        long size = -1;
        Cursor cursor = null;
        try {
            cursor = cr.query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int s = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (n >= 0 && cursor.getString(n) != null) name = cursor.getString(n);
                if (s >= 0 && !cursor.isNull(s)) size = cursor.getLong(s);
            }
        } catch (RuntimeException ignored) {
            // Some providers don't answer queries.
        } finally {
            if (cursor != null) cursor.close();
        }
        if (size > OpenRouter.MAX_ATTACHMENT * 3) throw new IOException(name + " is too large");
        String mime = cr.getType(uri);
        if (mime == null || mime.equals("application/octet-stream")) mime = guessMime(name);
        InputStream in = cr.openInputStream(uri);
        if (in == null) throw new IOException("Can't read " + name);
        byte[] bytes;
        try {
            bytes = io.github.hayliewordsman.grapefruitai.net.Http.readAll(in, (int) OpenRouter.MAX_ATTACHMENT * 3);
        } finally {
            in.close();
        }
        if (mime.startsWith("image/") && (bytes.length > 1500000 || !(mime.equals("image/png")
                || mime.equals("image/jpeg") || mime.equals("image/webp") || mime.equals("image/gif")))) {
            byte[] scaled = scaleImage(bytes, 2048);
            if (scaled != null) {
                bytes = scaled;
                mime = "image/jpeg";
                if (!name.toLowerCase(java.util.Locale.US).endsWith(".jpg")) name = name + ".jpg";
            }
        }
        if (bytes.length > OpenRouter.MAX_ATTACHMENT) throw new IOException(name + " is too large (max 12 MB)");
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        File f = store.newMedia(ext != null ? ext : "bin");
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
        return new Attachment(name, mime, f.getAbsolutePath(), bytes.length);
    }

    static String guessMime(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String ext = name.substring(dot + 1).toLowerCase(java.util.Locale.US);
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (m != null) return m;
            String[] text = {"md", "txt", "java", "kt", "kts", "gradle", "py", "js", "ts", "json", "xml", "yml",
                    "yaml", "toml", "c", "h", "cpp", "rs", "go", "sh", "csv", "html", "css", "properties"};
            for (String t : text) if (t.equals(ext)) return "text/plain";
        }
        return "application/octet-stream";
    }

    public static byte[] scaleImage(byte[] data, int max) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= max || o.outHeight / (sample * 2) >= max) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (b == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 88, out);
        b.recycle();
        return out.toByteArray();
    }

    /** A downscaled bitmap for showing (old tablets have little memory). */
    public static Bitmap thumbnail(String path, int maxPx) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= maxPx || o.outHeight / (sample * 2) >= maxPx) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, o);
    }

    public static byte[] read(String path) throws IOException {
        java.io.FileInputStream in = new java.io.FileInputStream(path);
        try {
            return io.github.hayliewordsman.grapefruitai.net.Http.readAll(in, 64 * 1024 * 1024);
        } finally {
            in.close();
        }
    }

    public static Uri fileUri(File f) {
        return Uri.fromFile(f);
    }
}
