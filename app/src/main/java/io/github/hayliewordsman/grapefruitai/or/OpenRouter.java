package io.github.hayliewordsman.grapefruitai.or;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.hayliewordsman.grapefruitai.chat.Attachment;
import io.github.hayliewordsman.grapefruitai.chat.Message;
import io.github.hayliewordsman.grapefruitai.net.Http;
import io.github.hayliewordsman.grapefruitai.net.HttpException;
import io.github.hayliewordsman.grapefruitai.net.Sse;

/**
 * OpenRouter's documented API (https://openrouter.ai/docs): the model list, the current key,
 * streaming chat completions with the web plugin, and the Image API. The user's own key is sent
 * only to openrouter.ai, as a Bearer token, and never logged.
 */
public final class OpenRouter {
    public static final String BASE = "https://openrouter.ai/api/v1";
    public static final String REFERER = "https://github.com/hayliewordsman/grapefruitmusic";
    public static final String TITLE = "Grapefruit AI";
    /** Inline attachments above this are refused rather than sent (memory on old tablets). */
    public static final long MAX_ATTACHMENT = 12L * 1024 * 1024;

    private final String key;
    private final Transport transport;

    /** Sends one JSON POST and returns the response text; replaced in tests. */
    public interface Transport {
        String post(String url, Map<String, String> headers, String json, Http[] holder) throws IOException;
    }

    static final Transport HTTPS = new Transport() {
        @Override
        public String post(String url, Map<String, String> headers, String json, Http[] holder) throws IOException {
            Http h = new Http("POST", url).json(json).readTimeout(Http.STREAM_READ_TIMEOUT_MS);
            for (Map.Entry<String, String> e : headers.entrySet()) h.header(e.getKey(), e.getValue());
            if (holder != null) holder[0] = h;
            return h.text();
        }
    };

    public OpenRouter(String key) {
        this(key, HTTPS);
    }

    public OpenRouter(String key, Transport transport) {
        this.key = key;
        this.transport = transport;
    }

    private Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<String, String>();
        h.put("HTTP-Referer", REFERER);
        h.put("X-OpenRouter-Title", TITLE);
        h.put("Accept", "application/json");
        if (key != null) h.put("Authorization", "Bearer " + key);
        return h;
    }

    private Http request(String method, String path) throws IOException {
        Http h = new Http(method, BASE + path)
                .header("HTTP-Referer", REFERER)
                .header("X-OpenRouter-Title", TITLE)
                .header("Accept", "application/json");
        if (key != null) h.header("Authorization", "Bearer " + key);
        return h;
    }

    /** Every model, all output modalities (the API lists text-only models unless asked). */
    public static String fetchModelsJson() throws IOException {
        return new OpenRouter(null).request("GET", "/models?output_modalities=all").text();
    }

    /** Image API models and their supported parameters, by model ID. */
    public static Map<String, ImageParams> fetchImageModels() throws IOException {
        Map<String, ImageParams> out = new LinkedHashMap<String, ImageParams>();
        try {
            JSONArray data = new JSONObject(new OpenRouter(null).request("GET", "/images/models").text())
                    .getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject o = data.optJSONObject(i);
                if (o != null) {
                    ImageParams p = ImageParams.parse(o);
                    out.put(p.id, p);
                }
            }
        } catch (JSONException e) {
            throw new IOException("Unexpected image model list: " + e.getMessage());
        }
        return out;
    }

    private static final java.util.regex.Pattern MODEL_ID =
            java.util.regex.Pattern.compile("[a-z0-9][a-z0-9._-]*/[a-z0-9][a-z0-9._-]*", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** An OpenRouter model ID safe to put in a URL path ("author/slug"). */
    public static boolean validModelId(String id) {
        return id != null && MODEL_ID.matcher(id).matches();
    }

    /** GET /images/models/{id}/endpoints: each provider's prices with their units. */
    public static String fetchImageEndpointsJson(String id) throws IOException {
        if (!validModelId(id)) throw new IOException("Unexpected model ID");
        return new OpenRouter(null).request("GET", "/images/models/" + id + "/endpoints").text();
    }

    /**
     * The model's page on openrouter.ai, read only for its schema.org price data when the
     * Images API lists no prices for the model.
     */
    public static String fetchModelPage(String id) throws IOException {
        if (!validModelId(id)) throw new IOException("Unexpected model ID");
        return new Http("GET", "https://openrouter.ai/" + id).header("Accept", "text/html").text();
    }

    /** GET /key: label, usage and limit of the user's key. */
    public JSONObject keyInfo() throws IOException {
        try {
            return new JSONObject(request("GET", "/key").text()).getJSONObject("data");
        } catch (JSONException e) {
            throw new IOException("Unexpected key response");
        }
    }

    // --- Chat ---

    public interface ChatListener {
        void onText(String delta);

        void onReasoning(String delta);

        void onImage(byte[] data, String mime);

        void onCitation(String title, String url);

        void onUsage(long promptTokens, long completionTokens, double cost);
    }

    /**
     * Streams one reply into listener. Returns normally when the stream ends; throws on errors,
     * including errors sent mid-stream. Cancel with {@link Http#cancel()} on the request passed
     * back through holder[0] (aborting the connection stops billing for providers that support it).
     */
    public void chat(String model, List<Message> history, boolean webSearch, String system,
                     final ChatListener listener, Http[] holder) throws IOException {
        // Image-only models (e.g. meta/muse-image) are never sent to chat/completions.
        String block = Routing.chatBlock(Models.find(model));
        if (block != null) throw new Friendly.ModelRouteException(block);
        JSONObject body = new JSONObject();
        try {
            body.put("model", model);
            body.put("stream", true);
            body.put("messages", messages(history, system));
            body.put("usage", new JSONObject().put("include", true));
            if (webSearch) {
                body.put("plugins", new JSONArray().put(new JSONObject().put("id", "web").put("max_results", 5)));
            }
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        Http h = request("POST", "/chat/completions").header("Accept", "text/event-stream")
                .json(body.toString()).readTimeout(Http.STREAM_READ_TIMEOUT_MS);
        if (holder != null) holder[0] = h;
        Http.Response r = h.open();
        try {
            if (r.code >= 300) throw new HttpException(r.code, new String(r.bytes(), "UTF-8"));
            final IOException[] failure = new IOException[1];
            Sse.read(r.stream(), new Sse.Listener() {
                @Override
                public boolean onEvent(String event, String data) {
                    if (data.equals("[DONE]")) return false;
                    try {
                        handleChunk(new JSONObject(data), listener);
                    } catch (JSONException e) {
                        return true;
                    } catch (IOException e) {
                        failure[0] = e;
                        return false;
                    }
                    return true;
                }
            });
            if (failure[0] != null) throw failure[0];
        } finally {
            r.close();
        }
    }

    static void handleChunk(JSONObject chunk, ChatListener listener) throws IOException {
        JSONObject error = chunk.optJSONObject("error");
        if (error != null) {
            throw new IOException("Provider error: " + error.optString("message", "unknown"));
        }
        JSONArray choices = chunk.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject choice = choices.optJSONObject(0);
            JSONObject delta = choice.optJSONObject("delta");
            if (delta == null) delta = choice.optJSONObject("message");
            if (delta != null) {
                String content = str(delta, "content");
                if (content != null && content.length() > 0) listener.onText(content);
                String reasoning = str(delta, "reasoning");
                if (reasoning != null && reasoning.length() > 0) listener.onReasoning(reasoning);
                images(delta.optJSONArray("images"), listener);
                annotations(delta.optJSONArray("annotations"), listener);
            }
            if ("error".equals(choice.optString("finish_reason"))) {
                throw new IOException("The provider stopped with an error");
            }
        }
        JSONObject usage = chunk.optJSONObject("usage");
        if (usage != null) {
            listener.onUsage(usage.optLong("prompt_tokens", 0), usage.optLong("completion_tokens", 0),
                    usage.has("cost") && !usage.isNull("cost") ? usage.optDouble("cost", -1) : -1);
        }
    }

    private static String str(JSONObject o, String key) {
        return o.has(key) && !o.isNull(key) ? o.optString(key, null) : null;
    }

    private static void images(JSONArray images, ChatListener listener) {
        if (images == null) return;
        for (int i = 0; i < images.length(); i++) {
            JSONObject img = images.optJSONObject(i);
            JSONObject url = img != null ? img.optJSONObject("image_url") : null;
            if (url == null) continue;
            DataUrl d = DataUrl.parse(url.optString("url", ""));
            if (d != null) listener.onImage(d.bytes, d.mime);
        }
    }

    private static void annotations(JSONArray annotations, ChatListener listener) {
        if (annotations == null) return;
        for (int i = 0; i < annotations.length(); i++) {
            JSONObject a = annotations.optJSONObject(i);
            if (a == null || !"url_citation".equals(a.optString("type"))) continue;
            JSONObject c = a.optJSONObject("url_citation");
            if (c != null && c.optString("url", "").length() > 0) {
                listener.onCitation(c.optString("title", c.optString("url")), c.optString("url"));
            }
        }
    }

    /** The conversation in OpenRouter's message format, with attachments inlined. */
    static JSONArray messages(List<Message> history, String system) throws IOException {
        JSONArray out = new JSONArray();
        try {
            if (system != null) out.put(new JSONObject().put("role", "system").put("content", system));
            for (Message m : history) {
                if (m.error != null && !m.isUser()) continue;
                if (!m.isUser() && m.text.length() == 0) continue;
                JSONObject msg = new JSONObject().put("role", m.isUser() ? "user" : "assistant");
                if (m.attachments.isEmpty()) {
                    msg.put("content", m.text);
                } else {
                    JSONArray parts = new JSONArray();
                    if (m.text.length() > 0) parts.put(new JSONObject().put("type", "text").put("text", m.text));
                    for (Attachment a : m.attachments) parts.put(part(a));
                    msg.put("content", parts);
                }
                out.put(msg);
            }
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        return out;
    }

    static JSONObject part(Attachment a) throws IOException, JSONException {
        File f = new File(a.path);
        if (f.length() > MAX_ATTACHMENT) throw new IOException(a.name + " is too large to send");
        byte[] bytes = readFile(f);
        if (a.isImage()) {
            return new JSONObject().put("type", "image_url")
                    .put("image_url", new JSONObject().put("url", DataUrl.of(a.mime, bytes)));
        }
        if (a.isText()) {
            return new JSONObject().put("type", "text")
                    .put("text", "Attached file " + a.name + ":\n\n```\n" + new String(bytes, "UTF-8") + "\n```");
        }
        return new JSONObject().put("type", "file").put("file",
                new JSONObject().put("filename", a.name).put("file_data", DataUrl.of(a.mime, bytes)));
    }

    static byte[] readFile(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            return Http.readAll(in, (int) MAX_ATTACHMENT + 1);
        } finally {
            in.close();
        }
    }

    // --- Images ---

    public static final class ImageResult {
        public final List<byte[]> images = new ArrayList<byte[]>();
        public final List<String> mimes = new ArrayList<String>();
        public String text = "";
        public double cost = -1;
        public long promptTokens, completionTokens;
    }

    /**
     * POST /images (OpenRouter's Image API). Only parameters the model lists are sent; null means
     * "model default". References are data URLs for image editing.
     */
    public ImageResult generateImage(String model, String prompt, String aspectRatio, String resolution,
                                     List<String> referenceDataUrls, Http[] holder) throws IOException {
        String text = transport.post(BASE + IMAGES_PATH, headers(),
                imagesRequest(model, prompt, aspectRatio, resolution, referenceDataUrls).toString(), holder);
        return parseImages(text);
    }

    public static final String IMAGES_PATH = "/images";
    public static final String CHAT_PATH = "/chat/completions";

    /** The Images API body: model, prompt, the listed options, and input_references for editing. */
    static JSONObject imagesRequest(String model, String prompt, String aspectRatio, String resolution,
                                    List<String> referenceDataUrls) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("model", model).put("prompt", prompt);
            if (aspectRatio != null) body.put("aspect_ratio", aspectRatio);
            if (resolution != null) body.put("resolution", resolution);
            if (referenceDataUrls != null && !referenceDataUrls.isEmpty()) {
                JSONArray refs = new JSONArray();
                for (String u : referenceDataUrls) {
                    refs.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", u)));
                }
                body.put("input_references", refs);
            }
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        return body;
    }

    static ImageResult parseImages(String text) throws IOException {
        ImageResult result = new ImageResult();
        try {
            JSONObject o = new JSONObject(text);
            JSONArray data = o.optJSONArray("data");
            for (int i = 0; data != null && i < data.length(); i++) {
                JSONObject d = data.getJSONObject(i);
                String b64 = d.optString("b64_json", "");
                if (b64.length() > 0) {
                    result.images.add(Base64.decode(b64, Base64.DEFAULT));
                    result.mimes.add(d.optString("media_type", "image/png"));
                } else if (d.optString("url", "").startsWith("data:")) {
                    DataUrl u = DataUrl.parse(d.optString("url"));
                    if (u != null) {
                        result.images.add(u.bytes);
                        result.mimes.add(u.mime);
                    }
                }
            }
            usage(o.optJSONObject("usage"), result);
        } catch (JSONException e) {
            throw new IOException("Unexpected image response");
        }
        if (result.images.isEmpty()) throw new IOException("The model returned no image");
        return result;
    }

    /**
     * Image output through chat completions (modalities ["image","text"]), for image-capable
     * models the Image API doesn't list. Non-streaming.
     */
    public ImageResult generateImageViaChat(String model, String prompt, String aspectRatio,
                                            List<String> referenceDataUrls, Http[] holder) throws IOException {
        OrModel known = Models.find(model);
        if (known != null && !known.servesChat()) {
            throw new Friendly.ModelRouteException(known.name + " uses OpenRouter's Images API, not chat.");
        }
        JSONObject body = new JSONObject();
        try {
            JSONObject msg = new JSONObject().put("role", "user");
            if (referenceDataUrls == null || referenceDataUrls.isEmpty()) {
                msg.put("content", prompt);
            } else {
                JSONArray parts = new JSONArray().put(new JSONObject().put("type", "text").put("text", prompt));
                for (String u : referenceDataUrls) {
                    parts.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", u)));
                }
                msg.put("content", parts);
            }
            body.put("model", model).put("messages", new JSONArray().put(msg))
                    .put("modalities", new JSONArray().put("image").put("text"))
                    .put("usage", new JSONObject().put("include", true));
            if (aspectRatio != null) body.put("image_config", new JSONObject().put("aspect_ratio", aspectRatio));
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        String responseText = transport.post(BASE + CHAT_PATH, headers(), body.toString(), holder);
        final ImageResult result = new ImageResult();
        try {
            JSONObject o = new JSONObject(responseText);
            final StringBuilder text = new StringBuilder();
            handleChunk(o, new ChatListener() {
                public void onText(String d) {
                    text.append(d);
                }

                public void onReasoning(String d) {
                }

                public void onImage(byte[] data, String mime) {
                    result.images.add(data);
                    result.mimes.add(mime);
                }

                public void onCitation(String t, String u) {
                }

                public void onUsage(long p, long c, double cost) {
                }
            });
            result.text = text.toString();
            usage(o.optJSONObject("usage"), result);
        } catch (JSONException e) {
            throw new IOException("Unexpected response");
        }
        if (result.images.isEmpty()) {
            throw new IOException(result.text.length() > 0 ? "No image; the model said: " + result.text
                    : "The model returned no image");
        }
        return result;
    }

    private static void usage(JSONObject usage, ImageResult r) {
        if (usage == null) return;
        r.promptTokens = usage.optLong("prompt_tokens", 0);
        r.completionTokens = usage.optLong("completion_tokens", 0);
        if (usage.has("cost") && !usage.isNull("cost")) r.cost = usage.optDouble("cost", -1);
    }

    /** data:[mime];base64,[data] */
    public static final class DataUrl {
        public final String mime;
        public final byte[] bytes;

        DataUrl(String mime, byte[] bytes) {
            this.mime = mime;
            this.bytes = bytes;
        }

        public static DataUrl parse(String url) {
            if (url == null || !url.startsWith("data:")) return null;
            int comma = url.indexOf(',');
            if (comma < 0) return null;
            String meta = url.substring(5, comma);
            if (!meta.endsWith(";base64")) return null;
            String mime = meta.substring(0, meta.length() - 7);
            try {
                return new DataUrl(mime.length() > 0 ? mime : "application/octet-stream",
                        Base64.decode(url.substring(comma + 1), Base64.DEFAULT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public static String of(String mime, byte[] bytes) {
            return "data:" + mime + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
        }
    }
}
