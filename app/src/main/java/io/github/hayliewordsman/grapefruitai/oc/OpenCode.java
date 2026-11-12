package io.github.hayliewordsman.grapefruitai.oc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import io.github.hayliewordsman.grapefruitai.net.Http;
import io.github.hayliewordsman.grapefruitai.net.HttpException;
import io.github.hayliewordsman.grapefruitai.net.Sse;

/**
 * Client for an `opencode serve` server (https://opencode.ai/docs/server/, types from the
 * @opencode-ai/sdk OpenAPI build). HTTPS and HTTP Basic auth (OPENCODE_SERVER_PASSWORD, user
 * "opencode" by default) are required by this app: it refuses to talk to an unprotected server
 * over plain HTTP. The OpenRouter key entered in this app is never sent here; agent runs use the
 * provider credentials configured on the server.
 */
public final class OpenCode {
    private final String base;
    private final String authorization;
    private final String directory;

    public OpenCode(String url, String user, String password, String directory) throws IOException {
        String u = Http.requireHttps(url);
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        this.base = u;
        if (password == null || password.length() == 0) {
            throw new IOException("Set the server password (OPENCODE_SERVER_PASSWORD) in Backend settings");
        }
        this.authorization = Http.basic(user == null || user.length() == 0 ? "opencode" : user, password);
        this.directory = directory == null || directory.trim().length() == 0 ? null : directory.trim();
    }

    public String directory() {
        return directory;
    }

    private String url(String path, String... query) {
        StringBuilder b = new StringBuilder(base).append(path);
        char sep = path.indexOf('?') >= 0 ? '&' : '?';
        for (int i = 0; i + 1 < query.length; i += 2) {
            if (query[i + 1] == null) continue;
            b.append(sep).append(query[i]).append('=').append(Http.encode(query[i + 1]));
            sep = '&';
        }
        if (directory != null) b.append(sep).append("directory=").append(Http.encode(directory));
        return b.toString();
    }

    private Http http(String method, String path, String... query) throws IOException {
        return new Http(method, url(path, query))
                .header("Authorization", authorization)
                .header("Accept", "application/json");
    }

    private String get(String path, String... query) throws IOException {
        return http("GET", path, query).text();
    }

    private String send(String method, String path, JSONObject body) throws IOException {
        Http h = http(method, path);
        if (body != null) h.json(body.toString());
        else if (!method.equals("GET") && !method.equals("DELETE")) h.json("{}");
        return h.text();
    }

    private static JSONObject obj(String s) throws IOException {
        try {
            return new JSONObject(s);
        } catch (JSONException e) {
            throw new IOException("Unexpected response from the OpenCode server");
        }
    }

    private static JSONArray arr(String s) throws IOException {
        try {
            return new JSONArray(s);
        } catch (JSONException e) {
            throw new IOException("Unexpected response from the OpenCode server");
        }
    }

    /** GET /global/health: {healthy, version}. */
    public JSONObject health() throws IOException {
        return obj(new Http("GET", base + "/global/health").header("Authorization", authorization).text());
    }

    public JSONArray projects() throws IOException {
        return arr(get("/project"));
    }

    public JSONObject currentProject() throws IOException {
        return obj(get("/project/current"));
    }

    /** GET /config/providers: {providers: Provider[], default: {providerID: modelID}}. */
    public JSONObject providers() throws IOException {
        return obj(get("/config/providers"));
    }

    public JSONArray agents() throws IOException {
        return arr(get("/agent"));
    }

    public JSONArray sessions() throws IOException {
        return arr(get("/session"));
    }

    public JSONObject createSession(String title) throws IOException {
        JSONObject body = new JSONObject();
        try {
            if (title != null) body.put("title", title);
        } catch (JSONException ignored) {
            // Title optional.
        }
        return obj(send("POST", "/session", body));
    }

    public void deleteSession(String id) throws IOException {
        http("DELETE", "/session/" + Http.encode(id)).text();
    }

    /** GET /session/:id/message: [{info: Message, parts: Part[]}]. */
    public JSONArray messages(String sessionId) throws IOException {
        return arr(get("/session/" + Http.encode(sessionId) + "/message"));
    }

    public JSONObject sessionStatus() throws IOException {
        return obj(get("/session/status"));
    }

    /** POST /session/:id/prompt_async (204): the reply arrives through the event stream. */
    public void prompt(String sessionId, JSONArray parts, String providerId, String modelId, String agent)
            throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("parts", parts);
            if (providerId != null && modelId != null) {
                body.put("model", new JSONObject().put("providerID", providerId).put("modelID", modelId));
            }
            if (agent != null) body.put("agent", agent);
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        http("POST", "/session/" + Http.encode(sessionId) + "/prompt_async").json(body.toString()).text();
    }

    public void abort(String sessionId) throws IOException {
        http("POST", "/session/" + Http.encode(sessionId) + "/abort").json("{}").text();
    }

    /** GET /permission: pending permission requests. */
    public JSONArray pendingPermissions() throws IOException {
        return arr(get("/permission"));
    }

    /**
     * Answers a permission request: "once", "always" or "reject". Uses POST
     * /permission/{requestID}/reply, falling back to the older
     * /session/{id}/permissions/{permissionID} on servers that lack it.
     */
    public void replyPermission(String sessionId, String requestId, String reply) throws IOException {
        try {
            JSONObject body = new JSONObject().put("reply", reply);
            http("POST", "/permission/" + Http.encode(requestId) + "/reply").json(body.toString()).text();
        } catch (HttpException e) {
            if (e.code != 404 && e.code != 405) throw e;
            try {
                JSONObject body = new JSONObject().put("response", reply);
                http("POST", "/session/" + Http.encode(sessionId) + "/permissions/" + Http.encode(requestId))
                        .json(body.toString()).text();
            } catch (JSONException je) {
                throw new IOException(je.getMessage());
            }
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /** GET /file?path=: the entries of a directory (FileNode[]). */
    public JSONArray listFiles(String path) throws IOException {
        return arr(get("/file", "path", path == null ? "" : path));
    }

    /** GET /file/content?path=: {type: text|binary, content, encoding?: base64, mimeType?}. */
    public JSONObject readFile(String path) throws IOException {
        return obj(get("/file/content", "path", path));
    }

    /** GET /file/status: changed files in the working tree (File[]). */
    public JSONArray fileStatus() throws IOException {
        return arr(get("/file/status"));
    }

    public JSONArray findFiles(String query) throws IOException {
        return arr(get("/find/file", "query", query, "limit", "100"));
    }

    public JSONArray diff(String sessionId) throws IOException {
        return arr(get("/session/" + Http.encode(sessionId) + "/diff"));
    }

    public interface EventListener {
        /** Return false to stop listening. */
        boolean onEvent(String type, JSONObject properties);
    }

    /** GET /event: the server's event stream, until cancelled or the connection drops. */
    public void events(final EventListener listener, Http[] holder) throws IOException {
        Http h = http("GET", "/event").header("Accept", "text/event-stream").readTimeout(Http.STREAM_READ_TIMEOUT_MS);
        if (holder != null) holder[0] = h;
        Http.Response r = h.open();
        try {
            if (r.code >= 300) throw new HttpException(r.code, new String(r.bytes(), "UTF-8"));
            Sse.read(r.stream(), new Sse.Listener() {
                @Override
                public boolean onEvent(String event, String data) {
                    try {
                        JSONObject o = new JSONObject(data);
                        // Global streams wrap events as {directory, payload}.
                        if (o.has("payload") && o.optJSONObject("payload") != null) o = o.optJSONObject("payload");
                        JSONObject props = o.optJSONObject("properties");
                        return listener.onEvent(o.optString("type", ""), props != null ? props : new JSONObject());
                    } catch (JSONException e) {
                        return true;
                    }
                }
            });
        } finally {
            r.close();
        }
    }
}
