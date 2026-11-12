package io.github.hayliewordsman.grapefruitai.chat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One turn of a conversation. */
public final class Message {
    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";

    public String role;
    public String text = "";
    public String reasoning = "";
    public final List<Attachment> attachments = new ArrayList<Attachment>();
    /** Images the model produced. */
    public final List<Attachment> images = new ArrayList<Attachment>();
    /** Web citations: [title, url]. */
    public final List<String[]> citations = new ArrayList<String[]>();
    public String model;
    public String error;
    /** Streaming, stopped by the user, or done. */
    public boolean streaming;
    public boolean stopped;
    public boolean webSearch;
    public long promptTokens, completionTokens;
    /** USD, as reported by OpenRouter; negative when not reported. */
    public double cost = -1;
    public long time;

    public Message(String role) {
        this.role = role;
        this.time = System.currentTimeMillis();
    }

    public boolean isUser() {
        return USER.equals(role);
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("role", role).put("text", text).put("time", time);
        if (reasoning.length() > 0) o.put("reasoning", reasoning);
        if (model != null) o.put("model", model);
        if (error != null) o.put("error", error);
        if (stopped) o.put("stopped", true);
        if (webSearch) o.put("web", true);
        if (promptTokens > 0) o.put("in", promptTokens);
        if (completionTokens > 0) o.put("out", completionTokens);
        if (cost >= 0) o.put("cost", cost);
        JSONArray a = new JSONArray();
        for (Attachment x : attachments) a.put(x.toJson());
        if (a.length() > 0) o.put("attachments", a);
        JSONArray im = new JSONArray();
        for (Attachment x : images) im.put(x.toJson());
        if (im.length() > 0) o.put("images", im);
        JSONArray ci = new JSONArray();
        for (String[] c : citations) ci.put(new JSONArray().put(c[0]).put(c[1]));
        if (ci.length() > 0) o.put("citations", ci);
        return o;
    }

    static Message fromJson(JSONObject o) {
        Message m = new Message(o.optString("role", USER));
        m.text = o.optString("text", "");
        m.reasoning = o.optString("reasoning", "");
        m.time = o.optLong("time", 0);
        m.model = o.has("model") ? o.optString("model") : null;
        m.error = o.has("error") ? o.optString("error") : null;
        m.stopped = o.optBoolean("stopped", false);
        m.webSearch = o.optBoolean("web", false);
        m.promptTokens = o.optLong("in", 0);
        m.completionTokens = o.optLong("out", 0);
        m.cost = o.optDouble("cost", -1);
        JSONArray a = o.optJSONArray("attachments");
        for (int i = 0; a != null && i < a.length(); i++) m.attachments.add(Attachment.fromJson(a.optJSONObject(i)));
        JSONArray im = o.optJSONArray("images");
        for (int i = 0; im != null && i < im.length(); i++) m.images.add(Attachment.fromJson(im.optJSONObject(i)));
        JSONArray ci = o.optJSONArray("citations");
        for (int i = 0; ci != null && i < ci.length(); i++) {
            JSONArray c = ci.optJSONArray(i);
            if (c != null) m.citations.add(new String[]{c.optString(0), c.optString(1)});
        }
        return m;
    }
}
