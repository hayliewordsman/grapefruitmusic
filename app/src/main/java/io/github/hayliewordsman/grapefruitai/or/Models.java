package io.github.hayliewordsman.grapefruitai.or;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.hayliewordsman.grapefruitai.net.Http;
import io.github.hayliewordsman.grapefruitai.util.Bg;

/**
 * OpenRouter's catalog, fetched live and cached in the app's files so the picker opens at once on
 * an old tablet; refreshed when older than six hours or on request.
 */
public final class Models {
    public interface Listener {
        void onModels(List<OrModel> models, String error);
    }

    private static final long MAX_AGE = 6L * 3600 * 1000;
    private static List<OrModel> models = Collections.emptyList();
    private static Map<String, ImageParams> imageParams = new HashMap<String, ImageParams>();
    private static long fetched;
    private static boolean loading;
    private static String lastError;
    private static final List<Listener> waiting = new ArrayList<Listener>();

    private Models() {
    }

    public static synchronized List<OrModel> all() {
        return models;
    }

    public static synchronized ImageParams imageParams(String id) {
        return imageParams.get(id);
    }

    public static synchronized OrModel find(String id) {
        return Catalog.find(models, id);
    }

    public static synchronized String lastError() {
        return lastError;
    }

    public static synchronized long fetchedAt() {
        return fetched;
    }

    /** Calls back on the main thread with the cached list, then (if stale or forced) a fresh one. */
    public static void load(final Context c, final boolean force, final Listener listener) {
        synchronized (Models.class) {
            if (models.isEmpty()) readCache(c);
            if (!models.isEmpty() && listener != null) {
                final List<OrModel> now = models;
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        listener.onModels(now, null);
                    }
                });
            }
            boolean fresh = System.currentTimeMillis() - fetched < MAX_AGE;
            if (fresh && !force && !models.isEmpty()) return;
            if (listener != null) waiting.add(listener);
            if (loading) return;
            loading = true;
        }
        Bg.run(new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    String json = OpenRouter.fetchModelsJson();
                    List<OrModel> list = OrModel.parseList(json);
                    Map<String, ImageParams> params;
                    try {
                        params = OpenRouter.fetchImageModels();
                    } catch (Exception e) {
                        params = null;
                    }
                    synchronized (Models.class) {
                        models = Collections.unmodifiableList(list);
                        if (params != null) imageParams = params;
                        fetched = System.currentTimeMillis();
                        lastError = null;
                    }
                    writeCache(c, json, params);
                } catch (Exception e) {
                    error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    synchronized (Models.class) {
                        lastError = error;
                    }
                }
                final List<Listener> done;
                final List<OrModel> result;
                synchronized (Models.class) {
                    loading = false;
                    done = new ArrayList<Listener>(waiting);
                    waiting.clear();
                    result = models;
                }
                final String err = error;
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        for (Listener l : done) l.onModels(result, err);
                    }
                });
            }
        });
    }

    private static void readCache(Context c) {
        try {
            File f = new File(c.getCacheDir(), "models.json");
            if (!f.exists()) return;
            models = Collections.unmodifiableList(OrModel.parseList(read(f)));
            fetched = f.lastModified();
            File img = new File(c.getCacheDir(), "image-models.json");
            if (img.exists()) {
                org.json.JSONArray data = new org.json.JSONObject(read(img)).getJSONArray("data");
                Map<String, ImageParams> m = new HashMap<String, ImageParams>();
                for (int i = 0; i < data.length(); i++) {
                    ImageParams p = ImageParams.parse(data.getJSONObject(i));
                    m.put(p.id, p);
                }
                imageParams = m;
            }
        } catch (Exception ignored) {
            // A damaged cache is refetched.
        }
    }

    private static void writeCache(Context c, String json, Map<String, ImageParams> params) {
        try {
            write(new File(c.getCacheDir(), "models.json"), json);
            if (params != null) {
                write(new File(c.getCacheDir(), "image-models.json"), ImageParamsCache.serialize(params));
            }
        } catch (Exception ignored) {
            // Cache is optional.
        }
    }

    private static String read(File f) throws java.io.IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            return new String(Http.readAll(in, 32 * 1024 * 1024), "UTF-8");
        } finally {
            in.close();
        }
    }

    private static void write(File f, String s) throws java.io.IOException {
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(s.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    /** Writes ImageParams back in the API's own shape, so readCache can parse it. */
    static final class ImageParamsCache {
        static String serialize(Map<String, ImageParams> params) throws org.json.JSONException {
            org.json.JSONArray data = new org.json.JSONArray();
            for (ImageParams p : params.values()) {
                org.json.JSONObject sp = new org.json.JSONObject();
                if (!p.aspectRatios.isEmpty()) {
                    sp.put("aspect_ratio", new org.json.JSONObject().put("type", "enum")
                            .put("values", new org.json.JSONArray(p.aspectRatios)));
                }
                if (!p.resolutions.isEmpty()) {
                    sp.put("resolution", new org.json.JSONObject().put("type", "enum")
                            .put("values", new org.json.JSONArray(p.resolutions)));
                }
                if (p.maxReferences > 0) {
                    sp.put("input_references", new org.json.JSONObject().put("type", "range")
                            .put("min", 0).put("max", p.maxReferences));
                }
                data.put(new org.json.JSONObject().put("id", p.id).put("supported_parameters", sp)
                        .put("supports_streaming", p.supportsStreaming));
            }
            return new org.json.JSONObject().put("data", data).toString();
        }
    }
}
