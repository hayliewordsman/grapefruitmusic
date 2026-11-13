package io.github.hayliewordsman.grapefruitai.oc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One OpenCode V2 session: its projected messages (GET /api/session/{id}/message) kept up to date
 * from the V2 event stream — session.text.*, session.reasoning.*, session.tool.*, session.step.*,
 * session.execution.*, session.status/idle, session.usage.updated, permission.asked/replied.
 */
public final class Transcript {
    /** A message (Session.Message.Info) with its assistant content items. */
    public static final class Msg {
        /** The message without "content". */
        public final JSONObject info;
        public final List<JSONObject> content = new ArrayList<JSONObject>();
        /** "text:0", "reasoning:1", "tool:call_x" → its content item. */
        final Map<String, JSONObject> index = new HashMap<String, JSONObject>();
        /** Bumped on every change, for cheap redraw checks. */
        public int version;

        Msg(JSONObject info) {
            this.info = info;
        }

        public String id() {
            return info.optString("id");
        }

        public String type() {
            return info.optString("type");
        }

        public boolean isUser() {
            return "user".equals(type());
        }

        JSONObject item(String key, String type) {
            JSONObject o = index.get(key);
            if (o == null) {
                o = new JSONObject();
                try {
                    o.put("type", type);
                } catch (JSONException ignored) {
                    // Constant key.
                }
                index.put(key, o);
                content.add(o);
            }
            return o;
        }
    }

    public final String sessionId;
    private final LinkedHashMap<String, Msg> messages = new LinkedHashMap<String, Msg>();
    private final LinkedHashMap<String, JSONObject> permissions = new LinkedHashMap<String, JSONObject>();
    private final Set<String> changedFiles = new LinkedHashSet<String>();
    /** idle, busy or retry. */
    public String status = "idle";
    public String statusDetail = "";
    public String lastError;
    public String title = "";
    /** Session totals from Session.Info or session.usage.updated; null until known. */
    private JSONObject usageTokens;
    private double usageCost = -1;

    public Transcript(String sessionId) {
        this.sessionId = sessionId;
    }

    // --- Loading ---

    /** Replaces the messages with the server's projection (oldest first). */
    public synchronized void load(JSONArray list) {
        messages.clear();
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject m = list.optJSONObject(i);
            if (m == null || m.optString("id").length() == 0) continue;
            Msg msg = new Msg(copyWithout(m, "content"));
            JSONArray content = m.optJSONArray("content");
            int ordinal = 0;
            for (int j = 0; content != null && j < content.length(); j++) {
                JSONObject c = content.optJSONObject(j);
                if (c == null) continue;
                msg.content.add(c);
                String t = c.optString("type");
                if ("tool".equals(t)) msg.index.put("tool:" + c.optString("id"), c);
                else msg.index.put(t + ":" + ordinal, c);
                ordinal++;
            }
            JSONObject snapshot = m.optJSONObject("snapshot");
            JSONArray files = snapshot != null ? snapshot.optJSONArray("files") : null;
            for (int j = 0; files != null && j < files.length(); j++) changedFiles.add(files.optString(j));
            messages.put(msg.id(), msg);
        }
    }

    /** Session.Info: title and usage totals. */
    public synchronized void setSession(JSONObject info) {
        if (info == null) return;
        title = info.optString("title", title);
        if (info.has("cost")) usageCost = info.optDouble("cost", -1);
        if (info.optJSONObject("tokens") != null) usageTokens = info.optJSONObject("tokens");
    }

    public synchronized void setPermissions(JSONArray pending) {
        permissions.clear();
        for (int i = 0; pending != null && i < pending.length(); i++) {
            JSONObject p = pending.optJSONObject(i);
            if (p != null && sessionId.equals(p.optString("sessionID", sessionId))) permissions.put(p.optString("id"), p);
        }
    }

    public synchronized void setBusy(boolean busy) {
        status = busy ? "busy" : "idle";
        if (!busy) statusDetail = "";
    }

    // --- Events ---

    /** Applies one V2 event; true if it concerned this session and the view should refresh. */
    public synchronized boolean apply(String type, JSONObject d) {
        if (!sessionId.equals(d.optString("sessionID", ""))) return false;
        try {
            return applyOwn(type, d);
        } catch (JSONException e) {
            return false;
        }
    }

    private boolean applyOwn(String type, JSONObject d) throws JSONException {
        String mid = d.optString("assistantMessageID", "");
        switch (type) {
            case "session.inbox.enqueued": {
                JSONObject item = d.optJSONObject("item");
                if (item == null || !"user".equals(item.optString("type"))) return false;
                JSONObject payload = item.optJSONObject("payload");
                String id = d.optString("inboxID");
                if (messages.containsKey(id)) return false;
                JSONObject info = new JSONObject().put("id", id).put("type", "user")
                        .put("text", payload != null ? payload.optString("text") : "")
                        .put("time", new JSONObject().put("created", System.currentTimeMillis()));
                messages.put(id, new Msg(info));
                return true;
            }
            case "session.step.started": {
                Msg m = assistant(mid);
                if (d.has("agent")) m.info.put("agent", d.optString("agent"));
                if (d.optJSONObject("model") != null) m.info.put("model", d.optJSONObject("model"));
                status = "busy";
                return touch(m);
            }
            case "session.text.started":
            case "session.text.delta":
            case "session.text.ended":
            case "session.reasoning.started":
            case "session.reasoning.delta":
            case "session.reasoning.ended": {
                Msg m = assistant(mid);
                String kind = type.startsWith("session.text") ? "text" : "reasoning";
                JSONObject item = m.item(kind + ":" + d.optInt("ordinal"), kind);
                if (type.endsWith(".delta")) item.put("text", item.optString("text", "") + d.optString("delta"));
                else if (type.endsWith(".ended")) item.put("text", d.optString("text", item.optString("text", "")));
                else if (!item.has("text")) item.put("text", "");
                return touch(m);
            }
            case "session.tool.input.started": {
                Msg m = assistant(mid);
                JSONObject t = tool(m, d.optString("id"));
                t.put("name", d.optString("name"));
                t.put("state", new JSONObject().put("status", "streaming").put("input", ""));
                return touch(m);
            }
            case "session.tool.input.delta":
            case "session.tool.input.ended": {
                Msg m = assistant(mid);
                JSONObject t = tool(m, d.optString("id"));
                JSONObject s = t.optJSONObject("state");
                if (s == null || !"streaming".equals(s.optString("status"))) return false;
                s.put("input", type.endsWith(".delta") ? s.optString("input") + d.optString("delta") : d.optString("text"));
                return touch(m);
            }
            case "session.tool.called": {
                Msg m = assistant(mid);
                JSONObject t = tool(m, d.optString("id"));
                JSONObject input = d.optJSONObject("input");
                t.put("state", new JSONObject().put("status", "running")
                        .put("input", input != null ? input : new JSONObject()).put("metadata", new JSONObject()));
                return touch(m);
            }
            case "session.tool.progress": {
                Msg m = assistant(mid);
                JSONObject s = tool(m, d.optString("id")).optJSONObject("state");
                if (s != null && d.optJSONObject("metadata") != null) s.put("metadata", d.optJSONObject("metadata"));
                return touch(m);
            }
            case "session.tool.success":
            case "session.tool.failed": {
                Msg m = assistant(mid);
                JSONObject t = tool(m, d.optString("id"));
                JSONObject old = t.optJSONObject("state");
                JSONObject input = old != null && old.optJSONObject("input") != null ? old.optJSONObject("input") : new JSONObject();
                JSONObject s = new JSONObject().put("status", type.endsWith("success") ? "completed" : "error")
                        .put("input", input);
                if (d.optJSONArray("content") != null) s.put("content", d.optJSONArray("content"));
                if (d.optJSONObject("metadata") != null) s.put("metadata", d.optJSONObject("metadata"));
                if (d.optJSONObject("error") != null) s.put("error", d.optJSONObject("error"));
                t.put("state", s);
                return touch(m);
            }
            case "session.step.ended":
            case "session.step.failed": {
                Msg m = assistant(mid);
                if (d.has("finish")) m.info.put("finish", d.optString("finish"));
                if (d.has("cost")) m.info.put("cost", m.info.optDouble("cost", 0) + d.optDouble("cost", 0));
                if (d.optJSONObject("tokens") != null) m.info.put("tokens", add(m.info.optJSONObject("tokens"), d.optJSONObject("tokens")));
                if (d.optJSONObject("error") != null) m.info.put("error", d.optJSONObject("error"));
                JSONArray files = d.optJSONArray("files");
                for (int i = 0; files != null && i < files.length(); i++) changedFiles.add(files.optString(i));
                m.info.put("time", m.info.optJSONObject("time") != null ? m.info.optJSONObject("time")
                        .put("completed", System.currentTimeMillis()) : new JSONObject().put("completed", System.currentTimeMillis()));
                return touch(m);
            }
            case "session.shell.started":
            case "session.shell.ended": {
                JSONObject shell = d.optJSONObject("shell");
                if (shell == null) return false;
                String id = "shell:" + shell.optString("id");
                Msg m = messages.get(id);
                if (m == null) {
                    m = new Msg(new JSONObject().put("id", id).put("type", "shell"));
                    messages.put(id, m);
                }
                m.info.put("command", shell.optString("command")).put("status", shell.optString("status"));
                if (d.optJSONObject("output") != null) m.info.put("output", d.optJSONObject("output"));
                return touch(m);
            }
            case "session.compaction.ended": {
                String id = "compaction:" + System.currentTimeMillis();
                messages.put(id, new Msg(new JSONObject().put("id", id).put("type", "compaction")
                        .put("status", "completed").put("summary", d.optString("text"))));
                return true;
            }
            case "session.status": {
                JSONObject s = d.optJSONObject("status");
                if (s == null) return false;
                status = s.optString("type", "idle");
                statusDetail = "retry".equals(status) ? "Retrying (attempt " + s.optInt("attempt") + "): "
                        + s.optString("message") : "";
                return true;
            }
            case "session.retry.scheduled": {
                JSONObject e = d.optJSONObject("error");
                status = "retry";
                statusDetail = "Retrying (attempt " + d.optInt("attempt") + ")" + (e != null ? ": " + e.optString("message") : "");
                return true;
            }
            case "session.execution.started":
                status = "busy";
                lastError = null;
                return true;
            case "session.idle":
            case "session.execution.succeeded":
                status = "idle";
                statusDetail = "";
                return true;
            case "session.execution.interrupted":
                status = "idle";
                statusDetail = "";
                if ("user".equals(d.optString("reason"))) lastError = null;
                else lastError = "Stopped (" + d.optString("reason") + ")";
                return true;
            case "session.execution.failed":
                status = "idle";
                statusDetail = "";
                lastError = errorText(d.optJSONObject("error"));
                return true;
            case "session.usage.updated":
                usageCost = d.optDouble("cost", usageCost);
                if (d.optJSONObject("tokens") != null) usageTokens = d.optJSONObject("tokens");
                return true;
            case "session.renamed":
                title = d.optString("title", title);
                return true;
            case "permission.asked":
                permissions.put(d.optString("id"), d);
                return true;
            case "permission.replied":
                return permissions.remove(d.optString("requestID")) != null;
            default:
                return false;
        }
    }

    private boolean touch(Msg m) {
        m.version++;
        return true;
    }

    private Msg assistant(String id) throws JSONException {
        Msg m = messages.get(id);
        if (m == null) {
            m = new Msg(new JSONObject().put("id", id).put("type", "assistant")
                    .put("time", new JSONObject().put("created", System.currentTimeMillis())));
            messages.put(id, m);
        }
        return m;
    }

    private static JSONObject tool(Msg m, String callId) throws JSONException {
        JSONObject t = m.item("tool:" + callId, "tool");
        if (!t.has("id")) t.put("id", callId);
        if (!t.has("name")) t.put("name", "tool");
        return t;
    }

    private static JSONObject add(JSONObject a, JSONObject b) throws JSONException {
        if (a == null) return new JSONObject(b.toString());
        JSONObject out = new JSONObject();
        for (String k : new String[]{"input", "output", "reasoning"}) out.put(k, a.optDouble(k, 0) + b.optDouble(k, 0));
        JSONObject ca = a.optJSONObject("cache");
        JSONObject cb = b.optJSONObject("cache");
        out.put("cache", new JSONObject()
                .put("read", (ca != null ? ca.optDouble("read", 0) : 0) + (cb != null ? cb.optDouble("read", 0) : 0))
                .put("write", (ca != null ? ca.optDouble("write", 0) : 0) + (cb != null ? cb.optDouble("write", 0) : 0)));
        return out;
    }

    private static JSONObject copyWithout(JSONObject o, String key) {
        JSONObject c = new JSONObject();
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (!k.equals(key)) {
                try {
                    c.put(k, o.opt(k));
                } catch (JSONException ignored) {
                    // Keys from a parsed object are valid.
                }
            }
        }
        return c;
    }

    /** Session.StructuredError ({type, message, status?}) or a V1-style {name, data.message}. */
    public static String errorText(JSONObject e) {
        if (e == null) return "Unknown error";
        String message = e.optString("message", "");
        if (message.length() == 0 && e.optJSONObject("data") != null) message = e.optJSONObject("data").optString("message", "");
        String type = e.optString("type", e.optString("name", "Error"));
        int status = e.optInt("status", 0);
        if ("MessageAbortedError".equals(type)) return "Stopped";
        String head = type + (status > 0 ? " (HTTP " + status + ")" : "");
        return message.length() > 0 ? head + ": " + message : head;
    }

    // --- Reading ---

    public synchronized List<Msg> messages() {
        return new ArrayList<Msg>(messages.values());
    }

    public synchronized List<JSONObject> permissions() {
        return new ArrayList<JSONObject>(permissions.values());
    }

    public synchronized List<String> changedFiles() {
        return new ArrayList<String>(changedFiles);
    }

    public synchronized boolean busy() {
        return !"idle".equals(status);
    }

    /** [input, output, reasoning, cacheRead, cacheWrite]: the session's totals when known, else summed. */
    public synchronized long[] tokens() {
        long[] t = new long[5];
        if (usageTokens != null) {
            fill(t, usageTokens);
            return t;
        }
        for (Msg m : messages.values()) {
            JSONObject tk = m.info.optJSONObject("tokens");
            if (tk != null) fill(t, tk);
        }
        return t;
    }

    private static void fill(long[] t, JSONObject tk) {
        t[0] += tk.optLong("input");
        t[1] += tk.optLong("output");
        t[2] += tk.optLong("reasoning");
        JSONObject cache = tk.optJSONObject("cache");
        if (cache != null) {
            t[3] += cache.optLong("read");
            t[4] += cache.optLong("write");
        }
    }

    public synchronized double cost() {
        if (usageCost >= 0) return usageCost;
        double c = 0;
        for (Msg m : messages.values()) c += m.info.optDouble("cost", 0);
        return c;
    }
}
