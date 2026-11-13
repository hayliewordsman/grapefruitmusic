package io.github.hayliewordsman.grapefruitai.or;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What one Image API model accepts, from GET /api/v1/images/models: its supported_parameters map
 * ({"aspect_ratio": {"type":"enum","values":[...]}, "input_references": {"type":"range","max":10}}).
 * Controls are shown only for parameters the model lists, with only the values it lists.
 */
public final class ImageParams {
    public final String id;
    public final List<String> aspectRatios = new ArrayList<String>();
    public final List<String> resolutions = new ArrayList<String>();
    /** Reference images for editing: 0 when the model takes none. */
    public int maxReferences;
    /** Whether OpenRouter lists input_references for this model at all (with any limit). */
    public boolean listsReferences;
    /** Whether OpenRouter lists any parameters for this model (some, like Muse Image, list none). */
    public boolean listsParameters;
    public boolean supportsStreaming;

    private ImageParams(String id) {
        this.id = id;
    }

    public static ImageParams parse(JSONObject o) {
        ImageParams p = new ImageParams(o.optString("id", ""));
        p.supportsStreaming = o.optBoolean("supports_streaming", false);
        JSONObject params = o.optJSONObject("supported_parameters");
        if (params != null) {
            p.listsParameters = params.length() > 0;
            enumValues(params.optJSONObject("aspect_ratio"), p.aspectRatios);
            enumValues(params.optJSONObject("resolution"), p.resolutions);
            JSONObject refs = params.optJSONObject("input_references");
            if (refs != null) {
                p.listsReferences = true;
                p.maxReferences = Math.max(0, refs.optInt("max", 1));
            }
        }
        return p;
    }

    private static void enumValues(JSONObject param, List<String> into) {
        if (param == null) return;
        JSONArray values = param.optJSONArray("values");
        for (int i = 0; values != null && i < values.length(); i++) {
            String v = values.optString(i, "");
            if (v.length() > 0) into.add(v);
        }
    }

    /** For an image model reached through chat completions: Gemini-style aspect ratios only. */
    public static ImageParams forChatModel(String id) {
        ImageParams p = new ImageParams(id);
        return p;
    }
}
