package io.github.hayliewordsman.grapefruitai.or;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One model from OpenRouter's GET /api/v1/models. Prices are kept exactly as OpenRouter sends them
 * (USD strings, per token / per image / per request / per search); a price OpenRouter doesn't list
 * stays null and is shown as "not listed", never guessed.
 */
public final class OrModel {
    public final String id;
    public final String name;
    public final String provider;
    public final String description;
    public final long created;
    public final long contextLength;
    public final List<String> input;
    public final List<String> output;
    public final List<String> parameters;
    /** API paths the model serves (e.g. /api/v1/chat/completions, /api/v1/images); may be empty. */
    public final List<String> endpoints;
    /** USD per token, per image, per request... as sent; null when not listed. */
    public final String pricePrompt, priceCompletion, priceRequest, priceImage, priceImageOutput, priceImageToken,
            priceWebSearch, priceReasoning, priceCacheRead, priceCacheWrite, priceAudio;
    public final double discount;
    public final boolean hasPriceOverrides;
    public final long maxCompletion;

    private OrModel(JSONObject o) {
        id = o.optString("id", "");
        String n = o.optString("name", id);
        name = n.length() > 0 ? n : id;
        int slash = id.indexOf('/');
        String author = slash > 0 ? id.substring(0, slash) : "";
        int colon = name.indexOf(": ");
        provider = colon > 0 ? name.substring(0, colon) : author;
        description = o.optString("description", "");
        created = o.optLong("created", 0);
        JSONObject top = o.optJSONObject("top_provider");
        long ctx = o.optLong("context_length", 0);
        if (ctx <= 0 && top != null) ctx = top.optLong("context_length", 0);
        contextLength = ctx;
        maxCompletion = top != null ? top.optLong("max_completion_tokens", 0) : 0;
        JSONObject arch = o.optJSONObject("architecture");
        input = strings(arch != null ? arch.optJSONArray("input_modalities") : null);
        output = strings(arch != null ? arch.optJSONArray("output_modalities") : null);
        if (input.isEmpty() && output.isEmpty() && arch != null) {
            // Older "modality": "text+image->text".
            String m = arch.optString("modality", "");
            int arrow = m.indexOf("->");
            if (arrow > 0) {
                for (String s : m.substring(0, arrow).split("\\+")) input.add(s.trim());
                for (String s : m.substring(arrow + 2).split("\\+")) output.add(s.trim());
            }
        }
        parameters = strings(o.optJSONArray("supported_parameters"));
        endpoints = strings(o.optJSONArray("endpoints"));
        JSONObject p = o.optJSONObject("pricing");
        pricePrompt = price(p, "prompt");
        priceCompletion = price(p, "completion");
        priceRequest = price(p, "request");
        priceImage = price(p, "image");
        priceImageOutput = price(p, "image_output");
        priceImageToken = price(p, "image_token");
        priceWebSearch = price(p, "web_search");
        priceReasoning = price(p, "internal_reasoning");
        priceCacheRead = price(p, "input_cache_read");
        priceCacheWrite = price(p, "input_cache_write");
        priceAudio = price(p, "audio");
        discount = p != null ? p.optDouble("discount", 0) : 0;
        JSONArray overrides = p != null ? p.optJSONArray("overrides") : null;
        hasPriceOverrides = overrides != null && overrides.length() > 0;
    }

    public static OrModel parse(JSONObject o) {
        return new OrModel(o);
    }

    public static List<OrModel> parseList(String json) throws org.json.JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray data = root.getJSONArray("data");
        List<OrModel> out = new ArrayList<OrModel>(data.length());
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o != null && o.optString("id", "").length() > 0) out.add(new OrModel(o));
        }
        return out;
    }

    private static String price(JSONObject p, String key) {
        if (p == null || !p.has(key) || p.isNull(key)) return null;
        String s = p.optString(key, null);
        if (s == null || s.trim().length() == 0) return null;
        return s.trim();
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<String>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, null);
            if (s != null) out.add(s.toLowerCase(Locale.US));
        }
        return out;
    }

    // --- Capabilities ---

    public boolean outputsText() {
        return output.isEmpty() || output.contains("text");
    }

    public boolean outputsImage() {
        return output.contains("image");
    }

    public boolean acceptsImages() {
        return input.contains("image");
    }

    public boolean acceptsFiles() {
        return input.contains("file");
    }

    public boolean supportsTools() {
        return parameters.contains("tools");
    }

    public boolean supportsReasoning() {
        return parameters.contains("reasoning") || parameters.contains("include_reasoning");
    }

    public boolean servesChat() {
        return endpoints.isEmpty() || endpoints.contains("/api/v1/chat/completions");
    }

    /** Served by OpenRouter's Image API (POST /api/v1/images). */
    public boolean servesImageApi() {
        return endpoints.contains("/api/v1/images");
    }

    /** Usable in Chat: produces text through chat completions (not embeddings, speech, batches...). */
    public boolean isChat() {
        return outputsText() && servesChat();
    }

    /** Coding filter: text models that can call tools (what agents need). */
    public boolean isCoding() {
        return outputsText() && supportsTools();
    }

    /** Image generation: through the Image API, or image output from chat completions. */
    public boolean isImage() {
        return outputsImage() && (servesImageApi() || servesChat());
    }

    // --- Prices ---

    /** USD per million tokens, or null if not listed. Negative means variable (e.g. routers). */
    public static BigDecimal perMillion(String perToken) {
        if (perToken == null) return null;
        try {
            return new BigDecimal(perToken).multiply(BigDecimal.valueOf(1000000L));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BigDecimal value(String s) {
        if (s == null) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** For sorting: per-million input price, unlisted/variable last. */
    public double sortInputPrice() {
        BigDecimal b = perMillion(pricePrompt);
        return b == null || b.signum() < 0 ? Double.MAX_VALUE : b.doubleValue();
    }

    public double sortOutputPrice() {
        BigDecimal b = perMillion(priceCompletion);
        return b == null || b.signum() < 0 ? Double.MAX_VALUE : b.doubleValue();
    }

    public boolean isFree() {
        BigDecimal in = value(pricePrompt);
        BigDecimal out = value(priceCompletion);
        return in != null && out != null && in.signum() == 0 && out.signum() == 0
                && (value(priceRequest) == null || value(priceRequest).signum() == 0)
                && (value(priceImageOutput) == null || value(priceImageOutput).signum() == 0);
    }

    public List<String> modalitiesIn() {
        return Collections.unmodifiableList(input);
    }

    public List<String> modalitiesOut() {
        return Collections.unmodifiableList(output);
    }
}
