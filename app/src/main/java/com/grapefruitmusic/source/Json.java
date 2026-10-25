package com.grapefruitmusic.source;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** JSON helpers tolerant of servers that send a single object where a list is expected. */
final class Json {
    private Json() {
    }

    static List<JSONObject> list(JSONObject parent, String key) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        if (parent == null) return out;
        Object v = parent.opt(key);
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) out.add(o);
            }
        } else if (v instanceof JSONObject) {
            out.add((JSONObject) v);
        }
        return out;
    }

    static String str(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return null;
        String s = o.optString(key, null);
        return s == null || s.length() == 0 ? null : s;
    }
}
