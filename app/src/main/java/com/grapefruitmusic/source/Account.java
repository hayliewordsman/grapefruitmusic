package com.grapefruitmusic.source;

import org.json.JSONException;
import org.json.JSONObject;

/** A server the user signed in to. */
public final class Account {
    public static final String SUBSONIC = "subsonic";
    public static final String JELLYFIN = "jellyfin";

    public String id;
    public String type;
    public String name;
    public String url;
    public String username;
    /** Subsonic: the password (needed to compute auth tokens). */
    public String password;
    /** Jellyfin: access token and user id. */
    public String token;
    public String userId;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("type", type);
        o.put("name", name);
        o.put("url", url);
        o.put("username", username);
        o.putOpt("password", password);
        o.putOpt("token", token);
        o.putOpt("userId", userId);
        return o;
    }

    public static Account fromJson(JSONObject o) {
        Account a = new Account();
        a.id = o.optString("id");
        a.type = o.optString("type");
        a.name = o.optString("name");
        a.url = o.optString("url");
        a.username = o.optString("username");
        a.password = o.has("password") ? o.optString("password") : null;
        a.token = o.has("token") ? o.optString("token") : null;
        a.userId = o.has("userId") ? o.optString("userId") : null;
        return a;
    }
}
