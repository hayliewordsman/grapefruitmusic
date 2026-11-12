package io.github.hayliewordsman.grapefruitai.chat;

import org.json.JSONException;
import org.json.JSONObject;

/** A file kept in the app's private storage: an attachment sent, or an image received. */
public final class Attachment {
    public String name;
    public String mime;
    /** Absolute path inside the app's files directory. */
    public String path;
    public long size;

    public Attachment(String name, String mime, String path, long size) {
        this.name = name;
        this.mime = mime;
        this.path = path;
        this.size = size;
    }

    public boolean isImage() {
        return mime != null && mime.startsWith("image/");
    }

    public boolean isPdf() {
        return "application/pdf".equals(mime);
    }

    public boolean isText() {
        if (mime == null) return false;
        return mime.startsWith("text/") || mime.equals("application/json") || mime.equals("application/xml")
                || mime.equals("application/javascript") || mime.endsWith("+json") || mime.endsWith("+xml");
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("name", name).put("mime", mime).put("path", path).put("size", size);
    }

    static Attachment fromJson(JSONObject o) {
        return new Attachment(o.optString("name", "file"), o.optString("mime", "application/octet-stream"),
                o.optString("path", ""), o.optLong("size", 0));
    }
}
