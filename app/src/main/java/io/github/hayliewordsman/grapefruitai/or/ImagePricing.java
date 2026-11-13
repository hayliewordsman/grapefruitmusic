package io.github.hayliewordsman.grapefruitai.or;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An image model's prices in their real units (per image, per megapixel, per token, per request),
 * from OpenRouter's Images API: GET /api/v1/images/models/{id}/endpoints, whose endpoints carry
 * {"billable": "output_image", "unit": "image", "cost_usd": 0.02, "variant": "2k"} entries.
 *
 * When that list is empty for a model, the price OpenRouter publishes on the model's own page
 * (its schema.org UnitPriceSpecification JSON-LD) is used instead, and labelled as such.
 */
public final class ImagePricing {
    public static final String IMAGES_API = "images_api";
    public static final String MODEL_PAGE = "model_page";

    public static final class Entry {
        /** output_image, input_image, input_reference, input_text, input_font… */
        public final String billable;
        /** image, megapixel, token or request. */
        public final String unit;
        /** USD per unit. */
        public final BigDecimal cost;
        /** e.g. "2k", "high_resolution"; null for the base price. */
        public final String variant;
        public final String provider;

        Entry(String billable, String unit, BigDecimal cost, String variant, String provider) {
            this.billable = billable;
            this.unit = unit;
            this.cost = cost;
            this.variant = variant;
            this.provider = provider;
        }
    }

    public final String modelId;
    public final String source;
    public final List<Entry> entries;

    private ImagePricing(String modelId, String source, List<Entry> entries) {
        this.modelId = modelId;
        this.source = source;
        this.entries = Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    // --- Parsing ---

    /** GET /api/v1/images/models/{id}/endpoints: {"id", "endpoints": [{provider_name, pricing: [...]}]}. */
    public static ImagePricing fromEndpoints(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optJSONObject("data") != null) root = root.optJSONObject("data");
        List<Entry> out = new ArrayList<Entry>();
        JSONArray endpoints = root.optJSONArray("endpoints");
        for (int i = 0; endpoints != null && i < endpoints.length(); i++) {
            JSONObject e = endpoints.optJSONObject(i);
            if (e == null) continue;
            String provider = e.optString("provider_name", "");
            JSONArray pricing = e.optJSONArray("pricing");
            for (int j = 0; pricing != null && j < pricing.length(); j++) {
                JSONObject p = pricing.optJSONObject(j);
                if (p == null || !p.has("cost_usd") || p.isNull("cost_usd")) continue;
                BigDecimal cost = decimal(p.opt("cost_usd"));
                String unit = p.optString("unit", "");
                String billable = p.optString("billable", "");
                if (cost == null || unit.length() == 0 || billable.length() == 0) continue;
                String variant = p.has("variant") && !p.isNull("variant") ? p.optString("variant") : null;
                out.add(new Entry(billable, unit, cost, variant, provider));
            }
        }
        return new ImagePricing(root.optString("id", ""), IMAGES_API, out);
    }

    private static final Pattern LD_JSON = Pattern.compile(
            "<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>", Pattern.DOTALL);

    /**
     * The model page's JSON-LD: a SoftwareApplication whose offers hold UnitPriceSpecifications
     * ("Image Output", 0.01 USD, "per image"). Only US-dollar image prices are taken.
     */
    public static ImagePricing fromModelPage(String modelId, String html) {
        List<Entry> out = new ArrayList<Entry>();
        Matcher m = LD_JSON.matcher(html);
        while (m.find()) {
            JSONObject app;
            try {
                app = new JSONObject(m.group(1).trim());
            } catch (JSONException notOne) {
                continue;
            }
            if (!"SoftwareApplication".equals(app.optString("@type"))) continue;
            String url = app.optString("url", "");
            if (url.length() > 0 && !url.endsWith("/" + modelId)) continue;
            List<JSONObject> offers = objects(app.opt("offers"));
            for (JSONObject offer : offers) {
                for (JSONObject spec : objects(offer.opt("priceSpecification"))) {
                    Entry e = fromSpec(spec, offer.optString("priceCurrency", ""));
                    if (e != null) out.add(e);
                }
            }
        }
        return new ImagePricing(modelId, MODEL_PAGE, out);
    }

    private static Entry fromSpec(JSONObject spec, String offerCurrency) {
        String currency = spec.optString("priceCurrency", offerCurrency);
        if (!"USD".equalsIgnoreCase(currency)) return null;
        BigDecimal price = decimal(spec.opt("price"));
        if (price == null) return null;
        String name = spec.optString("name", "").toLowerCase(Locale.US);
        String unitText = spec.optString("unitText", "").toLowerCase(Locale.US);
        if (!name.contains("image")) return null;
        String billable = name.contains("output") ? "output_image" : name.contains("input") ? "input_image" : null;
        if (billable == null) return null;
        String unit;
        if (unitText.contains("megapixel")) {
            unit = "megapixel";
        } else if (unitText.contains("image")) {
            unit = "image";
        } else if (unitText.contains("1m token") || unitText.contains("million token")) {
            unit = "token";
            price = price.divide(BigDecimal.valueOf(1000000L));
        } else {
            return null;
        }
        // "High-Resolution Image Output": the words before "image" name a variant.
        String variant = null;
        int at = name.indexOf("image");
        if (at > 0) {
            String before = name.substring(0, at).trim();
            if (before.length() > 0 && !before.equals("input") && !before.equals("output")) variant = before;
        }
        return new Entry(billable, unit, price, variant, "");
    }

    private static List<JSONObject> objects(Object o) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        if (o instanceof JSONObject) out.add((JSONObject) o);
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i) != null) out.add(a.optJSONObject(i));
        }
        return out;
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) return null;
        try {
            return new BigDecimal(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // --- Cache ---

    public JSONObject toJson() throws JSONException {
        JSONArray a = new JSONArray();
        for (Entry e : entries) {
            JSONObject o = new JSONObject().put("billable", e.billable).put("unit", e.unit)
                    .put("cost", e.cost.toPlainString()).put("provider", e.provider);
            if (e.variant != null) o.put("variant", e.variant);
            a.put(o);
        }
        return new JSONObject().put("id", modelId).put("source", source).put("entries", a);
    }

    public static ImagePricing fromJson(JSONObject o) {
        List<Entry> out = new ArrayList<Entry>();
        JSONArray a = o.optJSONArray("entries");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject e = a.optJSONObject(i);
            BigDecimal cost = e != null ? decimal(e.optString("cost", null)) : null;
            if (cost == null) continue;
            out.add(new Entry(e.optString("billable"), e.optString("unit"), cost,
                    e.has("variant") ? e.optString("variant") : null, e.optString("provider", "")));
        }
        return new ImagePricing(o.optString("id"), o.optString("source", IMAGES_API), out);
    }

    // --- Display ---

    /** "$0.01/image", "$0.03/megapixel", "$40.00 / M image tokens", "$0.02/request". */
    public static String price(BigDecimal cost, String unit, String billable) {
        if ("token".equals(unit)) {
            String what = billable != null && billable.endsWith("_image") ? " image tokens" : " tokens";
            return io.github.hayliewordsman.grapefruitai.util.Format.usd(cost.multiply(BigDecimal.valueOf(1000000L)))
                    + " / M" + what;
        }
        return io.github.hayliewordsman.grapefruitai.util.Format.usd(cost) + "/" + unit;
    }

    /**
     * The headline price of generating an image, e.g. "$0.01/image", "$0.008–$0.036/image by
     * resolution", "$0.03/megapixel"; null when nothing is listed. Per-request surcharges are not
     * part of it (see {@link #lines()}).
     */
    public String generation() {
        Map<String, List<BigDecimal>> byUnit = new LinkedHashMap<String, List<BigDecimal>>();
        boolean variants = false;
        for (Entry e : entries) {
            if (!"output_image".equals(e.billable) || "request".equals(e.unit)) continue;
            List<BigDecimal> l = byUnit.get(e.unit);
            if (l == null) byUnit.put(e.unit, l = new ArrayList<BigDecimal>());
            l.add(e.cost);
            if (e.variant != null) variants = true;
        }
        if (byUnit.isEmpty()) return null;
        List<String> parts = new ArrayList<String>();
        for (Map.Entry<String, List<BigDecimal>> u : byUnit.entrySet()) {
            BigDecimal min = Collections.min(u.getValue());
            BigDecimal max = Collections.max(u.getValue());
            if (min.compareTo(max) == 0) {
                parts.add(price(min, u.getKey(), "output_image"));
            } else {
                String hi = price(max, u.getKey(), "output_image");
                String lo = io.github.hayliewordsman.grapefruitai.util.Format.usd(
                        "token".equals(u.getKey()) ? min.multiply(BigDecimal.valueOf(1000000L)) : min);
                parts.add(lo + "–" + hi + (variants ? " by resolution or quality" : " by provider"));
            }
        }
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (b.length() > 0) b.append(" or ");
            b.append(p);
        }
        return b.toString();
    }

    /**
     * Every listed price as "label: price" lines. Providers are named only where they charge
     * different amounts for the same thing.
     */
    public List<String> lines() {
        Map<String, Set<String>> pricesByLabel = new LinkedHashMap<String, Set<String>>();
        for (Entry e : entries) {
            String key = label(e);
            Set<String> prices = pricesByLabel.get(key);
            if (prices == null) pricesByLabel.put(key, prices = new LinkedHashSet<String>());
            prices.add(price(e.cost, e.unit, e.billable));
        }
        Set<String> out = new LinkedHashSet<String>();
        for (Entry e : entries) {
            String key = label(e);
            String line = key + ": " + price(e.cost, e.unit, e.billable);
            if (pricesByLabel.get(key).size() > 1 && e.provider.length() > 0) line += " (" + e.provider + ")";
            out.add(line);
        }
        return new ArrayList<String>(out);
    }

    static String label(Entry e) {
        String base;
        if ("output_image".equals(e.billable)) {
            base = "request".equals(e.unit) ? "Surcharge" : "Image generation";
        } else if ("input_image".equals(e.billable)) {
            base = "Input images";
        } else if ("input_reference".equals(e.billable)) {
            base = "Reference images";
        } else if ("input_text".equals(e.billable)) {
            base = "Prompt text";
        } else if ("input_font".equals(e.billable)) {
            base = "Font inputs";
        } else {
            base = e.billable.replace('_', ' ');
            base = base.length() > 0 ? Character.toUpperCase(base.charAt(0)) + base.substring(1) : "Other";
        }
        return e.variant != null ? base + ", " + variant(e.variant) : base;
    }

    /** "2k" → "2K", "high_resolution" → "high resolution". */
    static String variant(String v) {
        String s = v.replace('_', ' ').trim();
        if (s.matches("(?i)[0-9.]+k")) return s.toUpperCase(Locale.US);
        return s;
    }
}
