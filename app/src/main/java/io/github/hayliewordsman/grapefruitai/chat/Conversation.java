package io.github.hayliewordsman.grapefruitai.chat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A conversation (Chat or Image mode), kept on the device only. */
public final class Conversation {
    public static final String CHAT = "chat";
    public static final String IMAGE = "image";

    public final String id;
    public String mode;
    public String title = "New conversation";
    public String model;
    public long updated;
    public final List<Message> messages = new ArrayList<Message>();

    public Conversation(String mode) {
        this(UUID.randomUUID().toString(), mode);
    }

    private Conversation(String id, String mode) {
        this.id = id;
        this.mode = mode;
        this.updated = System.currentTimeMillis();
    }

    /** The title from the first user message, up to about 48 characters. */
    public void autoTitle() {
        for (Message m : messages) {
            if (m.isUser() && m.text.trim().length() > 0) {
                String t = m.text.trim().replaceAll("\\s+", " ");
                title = t.length() > 48 ? t.substring(0, 47).trim() + "…" : t;
                return;
            }
        }
    }

    public String toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id).put("mode", mode).put("title", title).put("updated", updated);
        if (model != null) o.put("model", model);
        JSONArray a = new JSONArray();
        for (Message m : messages) a.put(m.toJson());
        o.put("messages", a);
        return o.toString();
    }

    public static Conversation fromJson(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        Conversation c = new Conversation(o.getString("id"), o.optString("mode", CHAT));
        c.title = o.optString("title", c.title);
        c.updated = o.optLong("updated", 0);
        c.model = o.has("model") ? o.optString("model") : null;
        JSONArray a = o.optJSONArray("messages");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject m = a.optJSONObject(i);
            if (m != null) c.messages.add(Message.fromJson(m));
        }
        return c;
    }
}
