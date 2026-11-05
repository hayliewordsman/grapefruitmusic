package com.grapefruitmusic.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.podcast.Opml;
import com.grapefruitmusic.podcast.PodcastStore;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Notice;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Podcast subscriptions in and out as an OPML file (Opml), from Subscriptions. Android 4.4 and later
 * use the system's file picker to save and open it. Android 4.2 and 4.3 have none: Export saves to
 * the Download folder, and Import lists the OPML files there, or asks a file manager, if there is
 * one.
 */
final class OpmlTransfer {
    static final int REQUEST_EXPORT = 71;
    static final int REQUEST_IMPORT = 72;
    private static final String NAME = com.grapefruitmusic.util.Brand.text("Grapefruit podcasts");
    /** Bigger than any real subscription list; protects against opening the wrong file. */
    private static final int MAX_BYTES = 4 * 1024 * 1024;

    private OpmlTransfer() {
    }

    private static List<Opml.Feed> feeds(Context c) {
        List<Opml.Feed> feeds = new ArrayList<Opml.Feed>();
        for (MediaItem show : PodcastStore.get(c).subscriptions()) feeds.add(Opml.feed(show.title, show.id));
        return feeds;
    }

    // ---- Export ----

    static void export(BaseFragment page) {
        Activity a = page.getActivity();
        if (PodcastStore.get(a).subscriptions().isEmpty()) {
            Notice.show(a, "No subscriptions to export yet.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 19) {
            Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/x-opml").putExtra(Intent.EXTRA_TITLE, NAME + ".opml");
            try {
                page.startActivityForResult(save, REQUEST_EXPORT);
                return;
            } catch (android.content.ActivityNotFoundException e) {
                // No picker (some TV boxes): the Download folder, as on Android 4.2.
            }
        }
        saveToDownloads(a);
    }

    /** Android 4.2 and 4.3: straight to Download, under a name not yet taken. */
    private static void saveToDownloads(final Activity a) {
        final Context app = a.getApplicationContext();
        page(a, new Bg.Work<File>() {
            @Override
            public File run() throws Exception {
                File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                        || (!folder.isDirectory() && !folder.mkdirs())) {
                    throw new IOException("Can't reach the Download folder. Is the storage mounted?");
                }
                String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                File file = new File(folder, NAME + ".opml");
                for (int n = 1; file.exists(); n++) {
                    file = new File(folder, NAME + " " + day + (n > 1 ? " " + n : "") + ".opml");
                }
                write(new FileOutputStream(file), feeds(app));
                // So it shows on a computer over USB, too.
                MediaScannerConnection.scanFile(app, new String[]{file.getPath()}, null, null);
                return file;
            }
        }, new Bg.Done<File>() {
            @Override
            public void done(File file, Exception error) {
                if (error != null) {
                    Notice.showLong(app, describe(error, "Couldn't export the subscriptions."));
                    return;
                }
                int n = PodcastStore.get(app).subscriptions().size();
                Ui.dialog(a).setTitle("Subscriptions Exported")
                        .setMessage(count(n) + " saved to Download/" + file.getName()
                                + ". Another podcast app can import this file.")
                        .setPositiveButton("OK", null).show();
            }
        });
    }

    private static void write(OutputStream out, List<Opml.Feed> feeds) throws IOException {
        try {
            out.write(Opml.write(feeds, System.currentTimeMillis()).getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    // ---- Import ----

    static void importFile(final BaseFragment page, final Runnable reload) {
        final Activity a = page.getActivity();
        if (Build.VERSION.SDK_INT >= 19) {
            Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            try {
                page.startActivityForResult(open, REQUEST_IMPORT);
                return;
            } catch (android.content.ActivityNotFoundException e) {
                // As on Android 4.2, then.
            }
        }
        // Android 4.2 and 4.3: the OPML files in Download (and the storage's top folder), newest first.
        final List<File> files = opmlFiles();
        final Intent other = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        final boolean canAsk = other.resolveActivity(a.getPackageManager()) != null;
        if (files.isEmpty() && !canAsk) {
            Ui.dialog(a).setTitle("Import Subscriptions")
                    .setMessage("Put the .opml file another podcast app exported in the Download folder (from a"
                            + " computer over USB, or saved from an email), then try again.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String[] items = new String[files.size() + (canAsk ? 1 : 0)];
        for (int i = 0; i < files.size(); i++) items[i] = files.get(i).getName();
        if (canAsk) items[items.length - 1] = "Choose with another app…";
        Ui.dialog(a).setTitle(files.isEmpty() ? "Import Subscriptions" : "Import from Download")
                .setItems(items, (d, which) -> {
                    if (which < files.size()) {
                        read(page, Uri.fromFile(files.get(which)), reload);
                    } else {
                        page.startActivityForResult(other, REQUEST_IMPORT);
                    }
                })
                .show();
    }

    private static List<File> opmlFiles() {
        List<File> out = new ArrayList<File>();
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(Environment.getExternalStorageState())) {
            return out;
        }
        File[] places = {Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStorageDirectory()};
        for (File place : places) {
            File[] found = place.listFiles();
            if (found == null) continue;
            for (File f : found) {
                String name = f.getName().toLowerCase(Locale.US);
                if (f.isFile() && (name.endsWith(".opml") || name.endsWith(".opml.xml")) && !out.contains(f)) out.add(f);
            }
        }
        java.util.Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File x, File y) {
                return Long.valueOf(y.lastModified()).compareTo(x.lastModified());
            }
        });
        return out;
    }

    /** The picker's answer (Subscriptions' onActivityResult). */
    static void onResult(BaseFragment page, int request, int result, Intent data, Runnable reload) {
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        if (request == REQUEST_EXPORT) save(page.getActivity(), data.getData());
        else if (request == REQUEST_IMPORT) read(page, data.getData(), reload);
    }

    private static void save(final Activity a, final Uri where) {
        final Context app = a.getApplicationContext();
        page(a, new Bg.Work<Integer>() {
            @Override
            public Integer run() throws Exception {
                List<Opml.Feed> feeds = feeds(app);
                OutputStream out = app.getContentResolver().openOutputStream(where);
                if (out == null) throw new IOException("Couldn't write the file.");
                write(out, feeds);
                return feeds.size();
            }
        }, new Bg.Done<Integer>() {
            @Override
            public void done(Integer n, Exception error) {
                if (error != null) Notice.showLong(app, describe(error, "Couldn't export the subscriptions."));
                else Notice.show(app, count(n) + " exported.");
            }
        });
    }

    /** Reads a file and subscribes to what's new in it; then the new shows' feeds load, quietly. */
    private static void read(final BaseFragment page, final Uri from, final Runnable reload) {
        final Activity a = page.getActivity();
        final Context app = a.getApplicationContext();
        page(a, new Bg.Work<int[]>() {
            @Override
            public int[] run() throws Exception {
                List<Opml.Feed> feeds = Opml.parse(text(app, from));
                if (feeds.isEmpty()) return new int[]{0, 0};
                final List<MediaItem> added = PodcastStore.get(app).subscribeAll(feeds);
                if (!added.isEmpty()) {
                    // Names, artwork and episodes from each feed (offline: the regular refresh does it later).
                    Bg.execute(new Runnable() {
                        @Override
                        public void run() {
                            for (MediaItem show : added) {
                                try {
                                    PodcastStore.get(app).refresh(show);
                                } catch (Exception ignored) {
                                    // Not reachable now; it's retried with the others.
                                }
                            }
                            Bg.main(reload);
                        }
                    });
                }
                return new int[]{added.size(), feeds.size() - added.size()};
            }
        }, new Bg.Done<int[]>() {
            @Override
            public void done(int[] counts, Exception error) {
                if (error != null) {
                    Notice.showLong(app, describe(error, "Couldn't read that file."));
                    return;
                }
                if (counts[0] + counts[1] == 0) {
                    Notice.showLong(app, "No podcasts in that file. Is it an OPML file?");
                    return;
                }
                if (page.getActivity() != null) reload.run();
                String message = counts[0] == 0 ? "Nothing new: you're already subscribed to all "
                        + count(counts[1]) + " in that file."
                        : "Subscribed to " + count(counts[0]) + "."
                        + (counts[1] > 0 ? " " + count(counts[1]) + (counts[1] == 1 ? " was" : " were")
                        + " already in your subscriptions." : "");
                Ui.dialog(a).setTitle("Subscriptions Imported").setMessage(message)
                        .setPositiveButton("OK", null).show();
            }
        });
    }

    private static String text(Context c, Uri from) throws IOException {
        InputStream in = "file".equals(from.getScheme()) ? new FileInputStream(from.getPath())
                : c.getContentResolver().openInputStream(from);
        if (in == null) throw new IOException("Couldn't open that file.");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new IOException("That file is too big to be a subscription list.");
            }
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    private static String count(int n) {
        return n == 1 ? "1 podcast" : n + " podcasts";
    }

    private static String describe(Exception e, String fallback) {
        return e instanceof IOException && e.getMessage() != null ? e.getMessage() : fallback;
    }

    /** Background work whose result only matters while the activity is there. */
    private static <T> void page(final Activity a, Bg.Work<T> work, final Bg.Done<T> done) {
        Bg.run(work, new Bg.Done<T>() {
            @Override
            public void done(T result, Exception error) {
                if (!a.isFinishing()) done.done(result, error);
            }
        });
    }
}
