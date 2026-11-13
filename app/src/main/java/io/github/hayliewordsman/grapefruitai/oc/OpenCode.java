package io.github.hayliewordsman.grapefruitai.oc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

import io.github.hayliewordsman.grapefruitai.net.Http;
import io.github.hayliewordsman.grapefruitai.net.HttpException;
import io.github.hayliewordsman.grapefruitai.net.Sse;

/**
 * Client for OpenCode's V2 server API (https://opencode.ai/docs/api, OpenAPI at
 * https://opencode.ai/docs/openapi.json): every route lives under /api, the project is chosen
 * with the `location[directory]` query parameter, and errors are tagged objects
 * ({"_tag": "UnauthorizedError", "message": …}).
 *
 * HTTPS and HTTP Basic authentication are required by this app (OPENCODE_SERVER_PASSWORD, user
 * "opencode" by default). The OpenRouter key entered in this app is never sent here; agent runs
 * use the provider credentials configured on the server.
 */
public final class OpenCode {
    /** One HTTP exchange; replaced in tests to check requests against the API spec. */
    public interface Transport {
        /** Returns the response body (JSON or bytes as text); throws HttpException for non-2xx. */
        byte[] send(String method, String url, String authorization, String jsonBody) throws IOException;

        /** Opens an SSE stream; holder[0] receives the request so it can be cancelled. */
        InputStream stream(String url, String authorization, Http[] holder) throws IOException;
    }

    static final Transport HTTPS = new Transport() {
        @Override
        public byte[] send(String method, String url, String authorization, String jsonBody) throws IOException {
            Http h = new Http(method, url).header("Authorization", authorization).header("Accept", "application/json");
            if (jsonBody != null) h.json(jsonBody);
            Http.Response r = h.open();
            try {
                byte[] body = r.bytes();
                if (r.code >= 300) throw new HttpException(r.code, new String(body, "UTF-8"));
                return body;
            } finally {
                r.close();
            }
        }

        @Override
        public InputStream stream(String url, String authorization, Http[] holder) throws IOException {
            Http h = new Http("GET", url).header("Authorization", authorization)
                    .header("Accept", "text/event-stream").readTimeout(Http.STREAM_READ_TIMEOUT_MS);
            if (holder != null) holder[0] = h;
            final Http.Response r = h.open();
            if (r.code >= 300) {
                byte[] body = r.bytes();
                r.close();
                throw new HttpException(r.code, new String(body, "UTF-8"));
            }
            return new java.io.FilterInputStream(r.stream()) {
                @Override
                public void close() throws IOException {
                    r.close();
                }
            };
        }
    };

    private final String base;
    private final String authorization;
    private final String directory;
    private final Transport transport;

    public OpenCode(String url, String user, String password, String directory) throws IOException {
        this(url, user, password, directory, HTTPS);
    }

    public OpenCode(String url, String user, String password, String directory, Transport transport)
            throws IOException {
        String u = Http.requireHttps(url);
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        this.base = u;
        if (password == null || password.length() == 0) {
            throw new IOException("Set the server password (OPENCODE_SERVER_PASSWORD) in Backend settings");
        }
        this.authorization = Http.basic(user == null || user.length() == 0 ? "opencode" : user, password);
        this.directory = directory == null || directory.trim().length() == 0 ? null : directory.trim();
        this.transport = transport;
    }

    public String directory() {
        return directory;
    }

    // --- URLs ---

    /** base + path + query (name/value pairs; nulls skipped), plus location[directory] if asked. */
    String url(String path, boolean location, String... query) {
        StringBuilder b = new StringBuilder(base).append(path);
        char sep = '?';
        for (int i = 0; i + 1 < query.length; i += 2) {
            if (query[i + 1] == null) continue;
            b.append(sep).append(query[i]).append('=').append(Http.encode(query[i + 1]));
            sep = '&';
        }
        if (location && directory != null) {
            // OpenAPI deepObject style, as the official SDK sends it.
            b.append(sep).append("location%5Bdirectory%5D=").append(Http.encode(directory));
        }
        return b.toString();
    }

    private static String seg(String id) {
        return Http.encode(id);
    }

    private JSONObject getObject(String url) throws IOException {
        return parse(transport.send("GET", url, authorization, null));
    }

    private JSONObject post(String url, JSONObject body) throws IOException {
        byte[] r = transport.send("POST", url, authorization, body != null ? body.toString() : null);
        return r.length == 0 ? new JSONObject() : parse(r);
    }

    static JSONObject parse(byte[] body) throws IOException {
        try {
            return new JSONObject(new String(body, "UTF-8"));
        } catch (JSONException e) {
            throw new NotOpenCodeException("The server's answer isn't OpenCode JSON (is the URL the OpenCode server's address?)");
        }
    }

    private static JSONArray data(JSONObject o) throws IOException {
        JSONArray a = o.optJSONArray("data");
        if (a == null) throw new NotOpenCodeException("Unexpected response from the OpenCode server");
        return a;
    }

    /** An answer that isn't OpenCode's API (an HTML page from a tunnel or proxy, say). */
    public static final class NotOpenCodeException extends IOException {
        public NotOpenCodeException(String message) {
            super(message);
        }
    }

    // --- Server ---

    /** GET /api/info: {version, pid, hostname, urls, paths, capabilities}. */
    public JSONObject info() throws IOException {
        JSONObject o = getObject(url("/api/info", false));
        if (!o.has("version")) throw new NotOpenCodeException("The server answered /api/info without a version: is it OpenCode 2?");
        return o;
    }

    /** GET /api/location: the resolved project ({directory, project: {id, directory}}). */
    public JSONObject location() throws IOException {
        return getObject(url("/api/location", true));
    }

    /** GET /api/project: projects the server knows (Project: id, canonical path, name). */
    public JSONArray projects() throws IOException {
        byte[] r = transport.send("GET", url("/api/project", false), authorization, null);
        try {
            return new JSONArray(new String(r, "UTF-8"));
        } catch (JSONException e) {
            throw new NotOpenCodeException("Unexpected response from the OpenCode server");
        }
    }

    /** GET /api/model: enabled and available chat models (Model.Info). */
    public JSONArray models() throws IOException {
        return data(getObject(url("/api/model", true)));
    }

    /** GET /api/model/default: the model used when a session has none, or null. */
    public JSONObject defaultModel() throws IOException {
        return getObject(url("/api/model/default", true)).optJSONObject("data");
    }

    /** GET /api/provider: providers configured on the server. */
    public JSONArray providers() throws IOException {
        return data(getObject(url("/api/provider", true)));
    }

    /** GET /api/agent: agents (Agent.Info: id, name, mode, hidden…). */
    public JSONArray agents() throws IOException {
        return data(getObject(url("/api/agent", true)));
    }

    // --- Sessions ---

    /** GET /api/session: the newest sessions first, top-level only, in the chosen project. */
    public JSONArray sessions() throws IOException {
        return data(getObject(url("/api/session", false, "order", "desc", "limit", "50", "parentID", "null",
                "directory", directory)));
    }

    /** POST /api/session: {data: Session.Info}. */
    public JSONObject createSession(String agent, String providerId, String modelId) throws IOException {
        JSONObject body = new JSONObject();
        try {
            if (agent != null) body.put("agent", agent);
            if (providerId != null && modelId != null) body.put("model", modelRef(providerId, modelId));
            if (directory != null) body.put("location", new JSONObject().put("directory", directory));
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
        JSONObject s = post(url("/api/session", false), body).optJSONObject("data");
        if (s == null) throw new NotOpenCodeException("Unexpected response from the OpenCode server");
        return s;
    }

    public JSONObject session(String sessionId) throws IOException {
        return getObject(url("/api/session/" + seg(sessionId), false)).optJSONObject("data");
    }

    /** DELETE /api/session/{id}: the session and its child sessions. */
    public void deleteSession(String sessionId) throws IOException {
        transport.send("DELETE", url("/api/session/" + seg(sessionId), false), authorization, null);
    }

    /** POST /api/session/{id}/model: subsequent turns use this model. */
    public void switchModel(String sessionId, String providerId, String modelId) throws IOException {
        try {
            post(url("/api/session/" + seg(sessionId) + "/model", false),
                    new JSONObject().put("model", modelRef(providerId, modelId)));
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /** POST /api/session/{id}/agent: subsequent turns use this agent. */
    public void switchAgent(String sessionId, String agent) throws IOException {
        try {
            post(url("/api/session/" + seg(sessionId) + "/agent", false), new JSONObject().put("agent", agent));
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    private static JSONObject modelRef(String providerId, String modelId) throws JSONException {
        return new JSONObject().put("providerID", providerId).put("id", modelId);
    }

    /**
     * POST /api/session/{id}/prompt: durably admits the text and starts the agent; the reply
     * arrives through the event stream. Returns the admitted inbox item (Session.Inbox.User).
     */
    public JSONObject prompt(String sessionId, String text) throws IOException {
        try {
            return post(url("/api/session/" + seg(sessionId) + "/prompt", false), new JSONObject().put("text", text))
                    .optJSONObject("data");
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /** POST /api/session/{id}/interrupt: stops the running turn ({interrupted: boolean}). */
    public boolean interrupt(String sessionId) throws IOException {
        return post(url("/api/session/" + seg(sessionId) + "/interrupt", false), null).optBoolean("interrupted", false);
    }

    /** GET /api/session/{id}/message: the latest messages, oldest first. */
    public JSONArray messages(String sessionId) throws IOException {
        JSONArray desc = data(getObject(url("/api/session/" + seg(sessionId) + "/message", false,
                "order", "desc", "limit", "200")));
        JSONArray asc = new JSONArray();
        for (int i = desc.length() - 1; i >= 0; i--) asc.put(desc.opt(i));
        return asc;
    }

    /** GET /api/session/active: {data: {sessionID: {type: "running"}}}. */
    public JSONObject activeSessions() throws IOException {
        JSONObject d = getObject(url("/api/session/active", false)).optJSONObject("data");
        return d != null ? d : new JSONObject();
    }

    /** GET /api/session/{id}/diff: files the session changed (FileDiff.Info). */
    public JSONArray sessionDiff(String sessionId) throws IOException {
        return data(getObject(url("/api/session/" + seg(sessionId) + "/diff", false, "context", "3")));
    }

    // --- Permissions ---

    /** GET /api/session/{id}/permission: pending requests (Permission.Request). */
    public JSONArray permissions(String sessionId) throws IOException {
        return data(getObject(url("/api/session/" + seg(sessionId) + "/permission", false)));
    }

    /** POST /api/session/{id}/permission/{requestID}/reply: {decision: once|always|reject}. */
    public void replyPermission(String sessionId, String requestId, String decision) throws IOException {
        try {
            post(url("/api/session/" + seg(sessionId) + "/permission/" + seg(requestId) + "/reply", false),
                    new JSONObject().put("decision", decision));
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    // --- Files ---

    /** GET /api/fs/list: direct children of a directory ({path, type}), relative to the project. */
    public JSONArray listFiles(String path) throws IOException {
        return data(getObject(url("/api/fs/list", true, "path", path == null || path.length() == 0 ? null : path)));
    }

    /** GET /api/fs/read/{path}: the file's bytes. */
    public byte[] readFile(String path) throws IOException {
        StringBuilder p = new StringBuilder("/api/fs/read");
        for (String part : path.split("/")) {
            if (part.length() == 0 || part.equals(".")) continue;
            if (part.equals("..")) throw new IOException("Paths outside the project aren't allowed");
            p.append('/').append(seg(part));
        }
        return transport.send("GET", url(p.toString(), true), authorization, null);
    }

    /** GET /api/fs/find: files whose names match. */
    public JSONArray findFiles(String query) throws IOException {
        return data(getObject(url("/api/fs/find", true, "query", query, "type", "file", "limit", "100")));
    }

    /** GET /api/vcs/status: uncommitted changes in the project's working tree. */
    public JSONArray vcsStatus() throws IOException {
        return data(getObject(url("/api/vcs/status", true)));
    }

    // --- Events ---

    public interface EventListener {
        /** Return false to stop listening. */
        boolean onEvent(String type, JSONObject data);
    }

    /**
     * GET /api/event: SSE whose data is a V2 event ({id, created, type, location?, data}).
     * Volatile: events during a disconnection are missed, so callers reload after reconnecting.
     */
    public void events(final EventListener listener, Http[] holder) throws IOException {
        InputStream in = transport.stream(url("/api/event", false), authorization, holder);
        try {
            Sse.read(in, new Sse.Listener() {
                @Override
                public boolean onEvent(String event, String data) {
                    try {
                        JSONObject o = new JSONObject(data);
                        String type = o.optString("type", event != null ? event : "");
                        JSONObject d = o.optJSONObject("data");
                        return listener.onEvent(type, d != null ? d : new JSONObject());
                    } catch (JSONException e) {
                        return true;
                    }
                }
            });
        } finally {
            in.close();
        }
    }
}
