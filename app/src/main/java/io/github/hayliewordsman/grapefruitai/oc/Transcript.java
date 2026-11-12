package io.github.hayliewordsman.grapefruitai.oc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One OpenCode session's messages and parts, kept up to date from the event stream
 * (message.updated, message.part.updated, message.part.delta, message.removed,
 * message.part.removed, session.status, permission.asked/replied, session.error).
 */
public final class Transcript {
    public static final class Msg {
        public JSONObject info;
        public final LinkedHashMap<String, JSONObject> parts = new LinkedHashMap<String, JSONObject>();

        public String id() {
            return info.optString("id");
        }

        public boolean isUser() {
            return "user".equals(info.optString("role"));
        }
    }

    public final String sessionId;
    private final LinkedHashMap<String, Msg> messages = new LinkedHashMap<String, Msg>();
    private final LinkedHashMap<String, JSONObject> permissions = new LinkedHashMap<String, JSONObject>();
    /** idle, busy or retry. */
    public String status = "idle";
    public String statusDetail = "";
    public String lastError;

    public Transcript(String sessionId) {
        this.sessionId = sessionId;
    }

    /** Replaces everything with GET /session/:id/message. */
    public synchronized void load(JSONArray list) {
        messages.clear();
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject m = list.optJSONObject(i);
            if (m == null) continue;
            JSONObject info = m.optJSONObject("info");
            if (info == null) continue;
            Msg msg = new Msg();
            msg.info = info;
            JSONArray parts = m.optJSONArray("parts");
            for (int j = 0; parts != null && j < parts.length(); j++) {
                JSONObject p = parts.optJSONObject(j);
                if (p != null) msg.parts.put(p.optString("id"), p);
            }
            messages.put(msg.id(), msg);
        }
    }

    public synchronized void setPermissions(JSONArray pending) {
        permissions.clear();
        for (int i = 0; pending != null && i < pending.length(); i++) {
            JSONObject p = pending.optJSONObject(i);
            if (p != null && sessionId.equals(p.optString("sessionID"))) permissions.put(p.optString("id"), p);
        }
    }

    /** Applies one event; true if it concerned this session (and the view should refresh). */
    public synchronized boolean apply(String type, JSONObject props) {
        String sid = props.optString("sessionID", null);
        if (sid == null) {
            JSONObject info = props.optJSONObject("info");
            JSONObject part = props.optJSONObject("part");
            if (info != null) sid = info.optString("sessionID", info.optString("id", null));
            else if (part != null) sid = part.optString("sessionID", null);
        }
        if (sid != null && !sid.equals(sessionId)) return false;
        switch (type) {
            case "message.updated": {
                JSONObject info = props.optJSONObject("info");
                if (info == null) return false;
                Msg m = msg(info.optString("id"));
                m.info = info;
                return true;
            }
            case "message.removed":
                return messages.remove(props.optString("messageID")) != null;
            case "message.part.updated": {
                JSONObject part = props.optJSONObject("part");
                if (part == null) return false;
                Msg m = msg(part.optString("messageID"));
                m.parts.put(part.optString("id"), part);
                return true;
            }
            case "message.part.delta": {
                Msg m = msg(props.optString("messageID"));
                String partId = props.optString("partID");
                JSONObject part = m.parts.get(partId);
                try {
                    if (part == null) {
                        part = new JSONObject().put("id", partId).put("messageID", m.id())
                                .put("sessionID", sessionId).put("type", "text");
                        m.parts.put(partId, part);
                    }
                    String field = props.optString("field", "text");
                    part.put(field, part.optString(field, "") + props.optString("delta", ""));
                } catch (JSONException e) {
                    return false;
                }
                return true;
            }
            case "message.part.removed": {
                Msg m = messages.get(props.optString("messageID"));
                return m != null && m.parts.remove(props.optString("partID")) != null;
            }
            case "session.status": {
                JSONObject s = props.optJSONObject("status");
                if (s == null) return false;
                status = s.optString("type", "idle");
                statusDetail = "retry".equals(status) ? "Retrying (attempt " + s.optInt("attempt") + "): "
                        + s.optString("message") : "";
                return true;
            }
            case "session.idle":
                status = "idle";
                statusDetail = "";
                return true;
            case "session.error": {
                JSONObject e = props.optJSONObject("error");
                lastError = errorText(e);
                return true;
            }
            case "permission.asked":
            case "permission.updated":
                permissions.put(props.optString("id"), props);
                return true;
            case "permission.replied": {
                String id = props.optString("requestID", props.optString("permissionID", ""));
                return permissions.remove(id) != null;
            }
            default:
                return false;
        }
    }

    public static String errorText(JSONObject e) {
        if (e == null) return "Unknown error";
        String name = e.optString("name", "Error");
        JSONObject data = e.optJSONObject("data");
        String message = data != null ? data.optString("message", "") : "";
        if ("MessageAbortedError".equals(name)) return "Stopped";
        return message.length() > 0 ? name + ": " + message : name;
    }

    private Msg msg(String id) {
        Msg m = messages.get(id);
        if (m == null) {
            m = new Msg();
            try {
                m.info = new JSONObject().put("id", id).put("role", "assistant").put("sessionID", sessionId);
            } catch (JSONException ignored) {
                m.info = new JSONObject();
            }
            messages.put(id, m);
        }
        return m;
    }

    public synchronized List<Msg> messages() {
        return new ArrayList<Msg>(messages.values());
    }

    public synchronized List<JSONObject> permissions() {
        return new ArrayList<JSONObject>(permissions.values());
    }

    public synchronized boolean busy() {
        return !"idle".equals(status);
    }

    /** Totals over the session's assistant messages: [input, output, reasoning, cacheRead, cacheWrite]. */
    public synchronized long[] tokens() {
        long[] t = new long[5];
        for (Map.Entry<String, Msg> e : messages.entrySet()) {
            JSONObject tk = e.getValue().info.optJSONObject("tokens");
            if (tk == null) continue;
            t[0] += tk.optLong("input");
            t[1] += tk.optLong("output");
            t[2] += tk.optLong("reasoning");
            JSONObject cache = tk.optJSONObject("cache");
            if (cache != null) {
                t[3] += cache.optLong("read");
                t[4] += cache.optLong("write");
            }
        }
        return t;
    }

    public synchronized double cost() {
        double c = 0;
        for (Msg m : messages.values()) c += m.info.optDouble("cost", 0);
        return c;
    }
}
