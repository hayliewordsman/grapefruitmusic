package io.github.hayliewordsman.grapefruitai.net;

import java.io.IOException;

/** A non-2xx answer, with the server's own message when it sent one ({"error":{"message"}}). */
public final class HttpException extends IOException {
    public final int code;
    public final String body;

    /** OpenCode V2's error tag ("UnauthorizedError", "LocationNotFoundError"…), or null. */
    public String tag() {
        if (body == null) return null;
        try {
            String t = new org.json.JSONObject(body).optString("_tag", "");
            return t.length() > 0 ? t : null;
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    public HttpException(int code, String body) {
        super(describe(code, body));
        this.code = code;
        this.body = body;
    }

    static String describe(int code, String body) {
        String message = null;
        if (body != null) {
            try {
                org.json.JSONObject o = new org.json.JSONObject(body);
                Object e = o.opt("error");
                if (e instanceof org.json.JSONObject) {
                    message = ((org.json.JSONObject) e).optString("message", null);
                    if (message == null) message = ((org.json.JSONObject) e).optString("name", null);
                } else if (e instanceof String) {
                    message = (String) e;
                }
                if (message == null) message = o.optString("message", null);
                if (message == null && o.optJSONObject("data") != null) {
                    message = o.optJSONObject("data").optString("message", null);
                }
            } catch (org.json.JSONException notJson) {
                String trimmed = body.trim();
                if (trimmed.length() > 0 && trimmed.length() < 300 && !trimmed.startsWith("<")) message = trimmed;
            }
        }
        String what;
        switch (code) {
            case 401: what = "Not authorised (check the key or password)"; break;
            case 402: what = "Payment required (insufficient credits)"; break;
            case 403: what = "Forbidden"; break;
            case 404: what = "Not found"; break;
            case 408: what = "Timed out"; break;
            case 409: what = "Busy"; break;
            case 429: what = "Rate limited, try again shortly"; break;
            case 502: what = "Bad gateway (the provider failed)"; break;
            case 503: what = "Service unavailable"; break;
            default: what = "HTTP " + code;
        }
        return message != null && message.length() > 0 ? what + ": " + message : what;
    }
}
