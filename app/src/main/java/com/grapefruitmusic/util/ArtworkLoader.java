package com.grapefruitmusic.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.LruCache;

import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.radio.RadioArt;
import com.grapefruitmusic.source.InternetRadioSource;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.ui.widget.ArtworkView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads album art from servers, files and embedded tags, with memory and disk caches. */
public final class ArtworkLoader {
    public interface Callback {
        /** Bitmap, or null when there's no artwork. Called on the main thread. */
        void onArtwork(Bitmap bitmap);
    }

    private static final long DISK_LIMIT = 60L * 1024 * 1024;
    private static ArtworkLoader instance;

    private final Context context;
    private final File diskDir;
    private final LruCache<String, Bitmap> memory;
    private final ExecutorService pool = Executors.newFixedThreadPool(3, Bg.named("gf-art"));
    private final Map<String, List<Callback>> inFlight = new HashMap<String, List<Callback>>();
    /** Keys known to have no artwork, so lists don't keep retrying them. */
    private final LruCache<String, Boolean> missing = new LruCache<String, Boolean>(500);

    public static synchronized ArtworkLoader get(Context context) {
        if (instance == null) instance = new ArtworkLoader(context.getApplicationContext());
        return instance;
    }

    private ArtworkLoader(Context context) {
        this.context = context;
        diskDir = new File(context.getCacheDir(), "art");
        diskDir.mkdirs();
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 6);
        memory = new LruCache<String, Bitmap>(maxKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight() / 1024;
            }
        };
        pool.execute(new Runnable() {
            @Override
            public void run() {
                trimDisk();
            }
        });
    }

    private static String key(MediaItem item, int size) {
        return item.sourceId + "|" + item.artKey + "|" + size;
    }

    public Bitmap cached(MediaItem item, int sizePx) {
        if (item == null || item.artKey == null) return null;
        return memory.get(key(item, sizePx));
    }

    /** Shows item's artwork in view (placeholder until loaded; safe in recycled list rows). */
    public void into(final ArtworkView view, MediaItem item, int sizePx) {
        if (item == null || item.artKey == null) {
            view.setTag(com.grapefruitmusic.R.id.art_request, null);
            view.setBitmap(null);
            return;
        }
        final String k = key(item, sizePx);
        view.setTag(com.grapefruitmusic.R.id.art_request, k);
        Bitmap b = memory.get(k);
        view.setBitmap(b);
        if (b != null) return;
        load(item, sizePx, new Callback() {
            @Override
            public void onArtwork(Bitmap bitmap) {
                if (k.equals(view.getTag(com.grapefruitmusic.R.id.art_request))) view.setBitmap(bitmap);
            }
        });
    }

    public void load(final MediaItem item, final int sizePx, Callback cb) {
        if (item == null || item.artKey == null) {
            cb.onArtwork(null);
            return;
        }
        final String k = key(item, sizePx);
        Bitmap b = memory.get(k);
        if (b != null || missing.get(k) != null) {
            cb.onArtwork(b);
            return;
        }
        synchronized (inFlight) {
            List<Callback> waiting = inFlight.get(k);
            if (waiting != null) {
                waiting.add(cb);
                return;
            }
            waiting = new ArrayList<Callback>();
            waiting.add(cb);
            inFlight.put(k, waiting);
        }
        final String sourceId = item.sourceId;
        final String artKey = item.artKey;
        final String title = item.title;
        pool.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap result = null;
                boolean definitive = false;
                boolean failed = false;
                try {
                    result = fetch(sourceId, artKey, sizePx);
                    definitive = Http.isOnline(context);
                } catch (Http.StatusException e) {
                    // The server says there's none (a station's logo link gone stale, say).
                    definitive = true;
                } catch (IOException ignored) {
                    // Treated as "no artwork" for now; retried next time.
                    failed = true;
                } catch (OutOfMemoryError e) {
                    memory.evictAll();
                    failed = true;
                }
                if (InternetRadioSource.ID.equals(sourceId)) {
                    // Stations' logos are often missing or tiny: always a full picture.
                    try {
                        result = RadioArt.station(artKey, title, result, sizePx);
                    } catch (OutOfMemoryError e) {
                        memory.evictAll();
                    }
                }
                final Bitmap r = result;
                // A made-up picture after a failed download isn't kept: the logo may come next time.
                if (r != null && !failed) memory.put(k, r);
                else if (definitive) missing.put(k, Boolean.TRUE);
                Bg.main(new Runnable() {
                    @Override
                    public void run() {
                        List<Callback> waiting;
                        synchronized (inFlight) {
                            waiting = inFlight.remove(k);
                        }
                        if (waiting != null) {
                            for (Callback c : waiting) c.onArtwork(r);
                        }
                    }
                });
            }
        });
    }

    private Bitmap fetch(String sourceId, String artKey, int size) throws IOException {
        String location;
        if (artKey.startsWith("/") || artKey.startsWith("embedded:")) {
            location = artKey;
        } else {
            MusicSource source = Sources.get(context).byId(sourceId);
            location = source != null ? source.artworkUrl(artKey, size) : null;
        }
        if (location == null) return null;
        if (location.startsWith("embedded:")) {
            byte[] data = embeddedPicture(location.substring("embedded:".length()));
            return data == null ? null : decode(data, size);
        }
        if (location.startsWith("http://") || location.startsWith("https://")) {
            File cached = new File(diskDir, Hashes.md5(sourceId + "|" + artKey + "|" + size));
            if (!cached.exists()) {
                if (!Http.isOnline(context)) return null;
                download(location, cached);
            } else {
                cached.setLastModified(System.currentTimeMillis());
            }
            return decodeFile(cached.getPath(), size);
        }
        return new File(location).exists() ? decodeFile(location, size) : null;
    }

    /** Downloads art to a file (also used to save cover art next to offline downloads). */
    public static void download(String url, File dest) throws IOException {
        HttpURLConnection c = Http.open("GET", url, null, null, null);
        File tmp = new File(dest.getPath() + ".part");
        try {
            InputStream in = c.getInputStream();
            OutputStream out = new FileOutputStream(tmp);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
            }
            if (!tmp.renameTo(dest)) throw new IOException("Couldn't save artwork");
        } finally {
            c.disconnect();
            tmp.delete();
        }
    }

    public static byte[] embeddedPicture(String path) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(path);
            return r.getEmbeddedPicture();
        } catch (RuntimeException e) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
                // Already released. (Declared to throw IOException since Android 10.)
            }
        }
    }

    private static int sampleSize(int w, int h, int size) {
        int sample = 1;
        while (Math.min(w, h) / (sample * 2) >= size) sample *= 2;
        return sample;
    }

    private static Bitmap decodeFile(String path, int size) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sampleSize(o.outWidth, o.outHeight, size);
        return BitmapFactory.decodeFile(path, o);
    }

    private static Bitmap decode(byte[] data, int size) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sampleSize(o.outWidth, o.outHeight, size);
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    private void trimDisk() {
        File[] files = diskDir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total <= DISK_LIMIT) return;
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.lastModified() < b.lastModified() ? -1 : (a.lastModified() == b.lastModified() ? 0 : 1);
            }
        });
        for (File f : files) {
            if (total <= DISK_LIMIT * 3 / 4) break;
            total -= f.length();
            f.delete();
        }
    }
}
