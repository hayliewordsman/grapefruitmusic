package com.grapefruitmusic.audiobooks;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;

import com.grapefruitmusic.data.Db;
import com.grapefruitmusic.model.MediaItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The audiobooks on this device. They're found in two places: the Audiobooks folder at the top of
 * the device's storage (put there from a computer, a file manager or the Libro.fm app's export),
 * and Grapefruit's own folder, where Add copies them (and unpacks Libro.fm's MP3 zips). In either,
 * a folder of audio files is one book (its files, in order, its parts), and an M4B, or any audio
 * file at the top of a folder of books, is a book of its own. What's read from the files (names,
 * lengths, chapters, the cover) is kept with where the listener left each book, and read again
 * only when its files change.
 */
public final class AudiobookStore {
    /** The audio a book can be in: anything Android plays (all DRM-free). */
    private static final String[] AUDIO = {"m4b", "m4a", "mp4", "aac", "mp3", "ogg", "oga", "opus", "flac", "wav",
            "mka", "3gp"};
    private static final String[] PICTURES = {"jpg", "jpeg", "png", "webp"};

    private static AudiobookStore instance;
    private final Context context;
    private final Db db;
    /** The books, as last read from the database or a scan. */
    private final Map<String, Audiobook> books = new LinkedHashMap<String, Audiobook>();
    private boolean loaded;

    public static synchronized AudiobookStore get(Context c) {
        if (instance == null) instance = new AudiobookStore(c.getApplicationContext());
        return instance;
    }

    private AudiobookStore(Context c) {
        context = c;
        db = Db.get(c);
    }

    /** The device's own Audiobooks folder (Android 10 and later know it as DIRECTORY_AUDIOBOOKS). */
    public static File sharedFolder() {
        return new File(Environment.getExternalStorageDirectory(), "Audiobooks");
    }

    /** Where Add puts books: Grapefruit's own folder, which goes when Grapefruit is removed. */
    public File ownFolder() {
        File files = context.getExternalFilesDir(null);
        if (files == null) return null;
        File f = new File(files, "Audiobooks");
        if (!f.isDirectory()) f.mkdirs();
        return f;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.US);
    }

    static boolean isAudio(String name) {
        return Arrays.asList(AUDIO).contains(extension(name));
    }

    private static boolean isPicture(String name) {
        return Arrays.asList(PICTURES).contains(extension(name));
    }

    // ---- The books ----

    /** The books known (without looking at the files: see scan()), the ones being listened to first. */
    public synchronized List<Audiobook> books() {
        if (!loaded) load();
        List<Audiobook> out = new ArrayList<Audiobook>(books.values());
        Collections.sort(out, new Comparator<Audiobook>() {
            @Override
            public int compare(Audiobook a, Audiobook b) {
                boolean ia = a.started() && !a.finished;
                boolean ib = b.started() && !b.finished;
                if (ia != ib) return ia ? -1 : 1;
                if (ia) return Long.valueOf(b.playedAt).compareTo(a.playedAt);
                if (a.finished != b.finished) return a.finished ? 1 : -1;
                return String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title);
            }
        });
        return out;
    }

    public synchronized Audiobook book(String id) {
        if (!loaded) load();
        return id == null ? null : books.get(id);
    }

    private void load() {
        books.clear();
        Cursor c = db.getReadableDatabase().query("audiobooks", null, null, null, null, null, null);
        try {
            while (c.moveToNext()) {
                Audiobook b = fromRow(c);
                if (b != null) books.put(b.id, b);
            }
        } finally {
            c.close();
        }
        loaded = true;
    }

    private Audiobook fromRow(Cursor c) {
        Audiobook b = new Audiobook(c.getString(c.getColumnIndexOrThrow("id")));
        b.title = c.getString(c.getColumnIndexOrThrow("title"));
        b.author = c.getString(c.getColumnIndexOrThrow("author"));
        b.art = c.getString(c.getColumnIndexOrThrow("art"));
        b.addedAt = c.getLong(c.getColumnIndexOrThrow("added_at"));
        b.playedAt = c.getLong(c.getColumnIndexOrThrow("played_at"));
        b.part = c.getInt(c.getColumnIndexOrThrow("part"));
        b.positionMs = c.getLong(c.getColumnIndexOrThrow("position_ms"));
        b.finished = c.getInt(c.getColumnIndexOrThrow("finished")) != 0;
        File own = ownFolder();
        b.imported = own != null && b.id.startsWith(own.getPath());
        try {
            JSONArray parts = new JSONArray(c.getString(c.getColumnIndexOrThrow("parts")));
            for (int i = 0; i < parts.length(); i++) {
                JSONObject p = parts.getJSONObject(i);
                b.parts.add(new Audiobook.Part(p.getString("path"), p.optString("title"), p.optLong("ms")));
            }
            JSONArray chapters = new JSONArray(c.getString(c.getColumnIndexOrThrow("chapters")));
            for (int i = 0; i < chapters.length(); i++) {
                JSONObject ch = chapters.getJSONObject(i);
                b.chapters.add(new Audiobook.Chapter(ch.optString("title"), ch.optInt("part"), ch.optLong("ms")));
            }
        } catch (JSONException | RuntimeException e) {
            return null;
        }
        return b.parts.isEmpty() ? null : b;
    }

    // ---- Finding them ----

    /** A book as found: its id, its files in order, and their signature. */
    private static final class Found {
        final String id;
        final List<File> files;
        final File folder;

        Found(String id, List<File> files, File folder) {
            this.id = id;
            this.files = files;
            this.folder = folder;
        }

        String signature() {
            StringBuilder s = new StringBuilder();
            for (File f : files) s.append(f.getName()).append('|').append(f.length()).append('|').append(f.lastModified()).append('\n');
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                byte[] d = md.digest(s.toString().getBytes("UTF-8"));
                StringBuilder hex = new StringBuilder();
                for (byte x : d) hex.append(String.format(Locale.US, "%02x", x));
                return hex.toString();
            } catch (Exception e) {
                return String.valueOf(s.toString().hashCode());
            }
        }
    }

    /**
     * Looks through both folders (blocking; reading a new book's files takes a moment each): adds new
     * books, reads changed ones again, forgets those whose files are gone. Returns books().
     */
    public List<Audiobook> scan() {
        Map<String, Found> found = new LinkedHashMap<String, Found>();
        File own = ownFolder();
        for (File root : new File[]{sharedFolder(), own}) {
            if (root != null && root.isDirectory()) walk(root, 0, found);
        }
        Map<String, String> known = new java.util.HashMap<String, String>();
        Cursor c = db.getReadableDatabase().rawQuery("SELECT id, signature FROM audiobooks", null);
        try {
            while (c.moveToNext()) known.put(c.getString(0), c.getString(1));
        } finally {
            c.close();
        }
        SQLiteDatabase w = db.getWritableDatabase();
        for (Found f : found.values()) {
            String signature = f.signature();
            if (signature.equals(known.get(f.id))) continue;
            Audiobook b = read(f);
            ContentValues v = new ContentValues();
            v.put("id", b.id);
            v.put("title", b.title);
            v.put("author", b.author);
            v.put("art", b.art);
            v.put("duration_ms", b.durationMs());
            v.put("parts", partsJson(b));
            v.put("chapters", chaptersJson(b));
            v.put("signature", signature);
            if (known.containsKey(f.id)) {
                // Changed: read again, keeping where the listener was (if that part is still there).
                w.update("audiobooks", v, "id = ?", new String[]{f.id});
                ContentValues keep = new ContentValues();
                keep.put("part", 0);
                w.update("audiobooks", keep, "id = ? AND part >= ?", new String[]{f.id, String.valueOf(b.parts.size())});
            } else {
                v.put("added_at", System.currentTimeMillis());
                w.insertWithOnConflict("audiobooks", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
        }
        for (String id : known.keySet()) {
            if (!found.containsKey(id)) w.delete("audiobooks", "id = ?", new String[]{id});
        }
        synchronized (this) {
            load();
        }
        return books();
    }

    private static final Comparator<File> NATURAL = new Comparator<File>() {
        @Override
        public int compare(File a, File b) {
            return natural(a.getName(), b.getName());
        }
    };

    /** "Part 2" before "Part 10": runs of digits compared as numbers. */
    static int natural(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i);
            char y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int si = i;
                int sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String na = a.substring(si, i).replaceFirst("^0+(?=\\d)", "");
                String nb = b.substring(sj, j).replaceFirst("^0+(?=\\d)", "");
                if (na.length() != nb.length()) return na.length() - nb.length();
                int cmp = na.compareTo(nb);
                if (cmp != 0) return cmp;
            } else {
                int cmp = Character.toLowerCase(x) - Character.toLowerCase(y);
                if (cmp != 0) return cmp;
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    /** depth 0 is a books folder itself: there, each audio file is a book. Deeper, a folder is one. */
    private void walk(File dir, int depth, Map<String, Found> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        Arrays.sort(list, NATURAL);
        List<File> audio = new ArrayList<File>();
        List<File> folders = new ArrayList<File>();
        for (File f : list) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                folders.add(f);
            } else if (isAudio(f.getName()) && f.length() > 0) {
                if (depth == 0 || "m4b".equals(extension(f.getName()))) {
                    out.put(f.getPath(), new Found(f.getPath(), Collections.singletonList(f), dir));
                } else {
                    audio.add(f);
                }
            }
        }
        if (!audio.isEmpty()) out.put(dir.getPath(), new Found(dir.getPath(), audio, dir));
        if (depth < 4) for (File d : folders) walk(d, depth + 1, out);
    }

    /** A book's names, lengths, chapters and cover, from its files. */
    private Audiobook read(Found f) {
        Audiobook b = new Audiobook(f.id);
        String album = null;
        String author = null;
        for (int i = 0; i < f.files.size(); i++) {
            File file = f.files.get(i);
            String title = null;
            long ms = 0;
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(file.getPath());
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                if (album == null) album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                if (author == null) author = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
                if (author == null) author = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                if (author == null) author = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR);
                String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (d != null) ms = Long.parseLong(d.trim());
            } catch (RuntimeException e) {
                // Not readable by Android's retriever: the file name, and the length found when played.
            } finally {
                try {
                    r.release();
                } catch (IOException | RuntimeException ignored) {
                    // Released already.
                }
            }
            String fromName = Chapters.titleFromFileName(file.getName());
            b.parts.add(new Audiobook.Part(file.getPath(), blank(title) ? fromName : title.trim(), ms));
            // Marks inside the file; otherwise the file is the chapter.
            List<Chapters.Chapter> inside = Chapters.read(file);
            if (inside.size() > 1 || (inside.size() == 1 && f.files.size() == 1)) {
                for (Chapters.Chapter c : inside) b.chapters.add(new Audiobook.Chapter(c.title, i, c.startMs));
            } else {
                b.chapters.add(new Audiobook.Chapter(b.parts.get(i).title, i, 0));
            }
        }
        boolean single = f.files.size() == 1;
        String name = single ? Chapters.titleFromFileName(f.files.get(0).getName()) : f.folder.getName();
        b.title = !blank(album) ? album.trim() : single && !blank(b.parts.get(0).title) ? b.parts.get(0).title : name;
        b.author = blank(author) ? null : author.trim();
        b.art = cover(f);
        File own = ownFolder();
        b.imported = own != null && b.id.startsWith(own.getPath());
        return b;
    }

    private static boolean blank(String s) {
        return s == null || s.trim().length() == 0;
    }

    /**
     * Its cover: for a folder book, a picture in the folder (cover, folder or front first); for a
     * book that's one file, a picture of the same name beside it (or the folder's, if it's alone
     * there); else the picture inside its first file.
     */
    private static String cover(Found f) {
        File first = f.files.get(0);
        boolean single = f.id.equals(first.getPath());
        File[] list = f.folder.listFiles();
        if (list != null) {
            int audio = 0;
            for (File p : list) if (isAudio(p.getName())) audio++;
            String base = baseName(first.getName());
            File any = null;
            for (File p : list) {
                if (!isPicture(p.getName())) continue;
                String n = p.getName().toLowerCase(Locale.US);
                if (single && baseName(n).equals(base)) return p.getPath();
                if (single && audio > 1) continue;
                if (n.startsWith("cover") || n.startsWith("folder") || n.startsWith("front")) return p.getPath();
                if (any == null) any = p;
            }
            if (any != null) return any.getPath();
        }
        return "embedded:" + first.getPath();
    }

    private static String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name).toLowerCase(Locale.US);
    }

    private static String partsJson(Audiobook b) {
        JSONArray a = new JSONArray();
        try {
            for (Audiobook.Part p : b.parts) a.put(new JSONObject().put("path", p.path).put("title", p.title).put("ms", p.durationMs));
        } catch (JSONException ignored) {
            // Strings and numbers only.
        }
        return a.toString();
    }

    private static String chaptersJson(Audiobook b) {
        JSONArray a = new JSONArray();
        try {
            for (Audiobook.Chapter c : b.chapters) a.put(new JSONObject().put("title", c.title).put("part", c.part).put("ms", c.startMs));
        } catch (JSONException ignored) {
            // Strings and numbers only.
        }
        return a.toString();
    }

    // ---- Where the listener is ----

    /** Where a part should start: where it was left, if that's this part. */
    public synchronized long positionFor(MediaItem item) {
        Audiobook b = book(item.albumId);
        if (b == null || b.part != item.trackNumber - 1) return 0;
        return b.positionMs;
    }

    /** Keeps the place (the player, every so often and on pause). Quick; any thread. */
    public synchronized void savePosition(String id, int part, long ms) {
        Audiobook b = book(id);
        if (b == null) return;
        // Just finished (the player back at the start of the last part): it stays finished until
        // the listener plays on.
        if (b.finished && part == b.parts.size() - 1 && ms < 2000) return;
        b.part = part;
        b.positionMs = Math.max(0, ms);
        b.playedAt = System.currentTimeMillis();
        b.finished = false;
        ContentValues v = new ContentValues();
        v.put("part", b.part);
        v.put("position_ms", b.positionMs);
        v.put("played_at", b.playedAt);
        v.put("finished", 0);
        db.getWritableDatabase().update("audiobooks", v, "id = ?", new String[]{id});
    }

    /** Finished (to the end, or marked so): next time it starts at the beginning. */
    public synchronized void setFinished(String id, boolean finished) {
        Audiobook b = book(id);
        if (b == null) return;
        b.finished = finished;
        b.part = 0;
        b.positionMs = 0;
        ContentValues v = new ContentValues();
        v.put("finished", finished ? 1 : 0);
        v.put("part", 0);
        v.put("position_ms", 0);
        db.getWritableDatabase().update("audiobooks", v, "id = ?", new String[]{id});
    }

    /** From the beginning, not finished. */
    public void startOver(String id) {
        setFinished(id, false);
    }

    /** Deletes a book Grapefruit added (its own copy); false for one of the listener's own files. */
    public synchronized boolean delete(String id) {
        Audiobook b = book(id);
        if (b == null || !b.imported) return false;
        File f = new File(id);
        deleteAll(f);
        db.getWritableDatabase().delete("audiobooks", "id = ?", new String[]{id});
        books.remove(id);
        return true;
    }

    private static void deleteAll(File f) {
        File[] inside = f.listFiles();
        if (inside != null) for (File x : inside) deleteAll(x);
        f.delete();
    }

    // ---- Adding books ----

    /** How an import is going. */
    public interface Progress {
        void progress(long done, long total);
    }

    /** "Title - Part 1 of 2", "Title (2 of 3)", "Title_Part02": "Title", so a book's zips go together. */
    static String bookName(String fileName) {
        String s = fileName;
        int dot = s.lastIndexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        s = s.replaceFirst("(?i)[\\s_\\-]*[(\\[]?\\s*(part|pt|disc|disk|cd)\\s*[_\\-.]?\\s*\\d+(\\s*(of|/)\\s*\\d+)?\\s*[)\\]]?\\s*$", "");
        s = s.replaceFirst("(?i)\\s*[(\\[]\\s*\\d+\\s*(of|/)\\s*\\d+\\s*[)\\]]\\s*$", "");
        s = s.replace('_', ' ').trim();
        s = s.replaceAll("[/\\\\:*?\"<>|]", "-");
        return s.length() > 0 ? s : "Audiobook";
    }

    private String displayName(Uri uri) {
        if ("file".equals(uri.getScheme())) return new File(uri.getPath()).getName();
        Cursor c = null;
        try {
            c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst() && c.getString(0) != null) return c.getString(0);
        } catch (RuntimeException ignored) {
            // No name to be had: the address's last part.
        } finally {
            if (c != null) c.close();
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "Audiobook";
    }

    private long size(Uri uri) {
        if ("file".equals(uri.getScheme())) return new File(uri.getPath()).length();
        Cursor c = null;
        try {
            c = context.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null);
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (RuntimeException ignored) {
            // Unknown.
        } finally {
            if (c != null) c.close();
        }
        return 0;
    }

    private InputStream open(Uri uri) throws IOException {
        if ("file".equals(uri.getScheme())) return new FileInputStream(uri.getPath());
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Couldn't open that file.");
        return in;
    }

    /**
     * Copies books into Grapefruit's own folder (blocking): an audio file on its own is a book; several
     * chosen together are one book's parts; a zip (Libro.fm's MP3 download) is unpacked into a
     * folder named after it, so a long book's several zips make one book. Then scans. Returns how many
     * audio files were added.
     */
    public int add(List<Uri> uris, Progress progress) throws IOException {
        File own = ownFolder();
        if (own == null) throw new IOException("Grapefruit's storage isn't available. Is the SD card mounted?");
        long total = 0;
        for (Uri u : uris) total += size(u);
        if (total > 0 && own.getUsableSpace() < total + 16 * 1024 * 1024) {
            throw new IOException("There isn't room for that here: it needs " + (total / (1024 * 1024)) + " MB.");
        }
        long[] done = {0};
        int added = 0;
        List<Uri> loose = new ArrayList<Uri>();
        for (Uri u : uris) {
            String name = displayName(u);
            if ("zip".equals(extension(name))) {
                File folder = new File(own, bookName(name));
                folder.mkdirs();
                added += unzip(u, folder, done, total, progress);
            } else if (isAudio(name) || isPicture(name)) {
                loose.add(u);
            }
        }
        int audio = 0;
        for (Uri u : loose) if (isAudio(displayName(u))) audio++;
        File into = own;
        if (audio > 1) {
            // Several files: one book.
            into = new File(own, bookName(displayName(loose.get(0))).replaceFirst("\\s*\\d+$", ""));
            into.mkdirs();
        }
        for (Uri u : loose) {
            String name = displayName(u);
            if (isPicture(name) && into == own) continue;
            copy(open(u), new File(into, safe(name)), done, total, progress);
            if (isAudio(name)) added++;
        }
        if (added == 0) throw new IOException("No audiobook in that: choose M4B, MP3 or other audio files, or a zip of them.");
        scan();
        return added;
    }

    private static String safe(String name) {
        String n = new File(name).getName().replaceAll("[/\\\\:*?\"<>|]", "-");
        return n.length() > 0 ? n : "audio";
    }

    private int unzip(Uri uri, File folder, long[] done, long total, Progress progress) throws IOException {
        ZipInputStream zip = new ZipInputStream(open(uri));
        int added = 0;
        try {
            ZipEntry e;
            byte[] buf = new byte[64 * 1024];
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                // Only the name: nothing in a zip may land outside the book's folder.
                String name = safe(e.getName());
                if (name.startsWith(".") || !(isAudio(name) || isPicture(name))) continue;
                OutputStream out = new FileOutputStream(new File(folder, name));
                try {
                    int n;
                    while ((n = zip.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                } finally {
                    out.close();
                }
                if (e.getCompressedSize() > 0) done[0] += e.getCompressedSize();
                if (progress != null) progress.progress(done[0], total);
                if (isAudio(name)) added++;
            }
        } finally {
            zip.close();
        }
        return added;
    }

    private static void copy(InputStream in, File to, long[] done, long total, Progress progress) throws IOException {
        OutputStream out = new FileOutputStream(to);
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            long last = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done[0] += n;
                if (progress != null && done[0] - last > 512 * 1024) {
                    last = done[0];
                    progress.progress(done[0], total);
                }
            }
        } finally {
            in.close();
            out.close();
        }
        if (progress != null) progress.progress(done[0], total);
    }
}
