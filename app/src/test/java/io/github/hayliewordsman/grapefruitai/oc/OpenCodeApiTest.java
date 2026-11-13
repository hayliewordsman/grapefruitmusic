package io.github.hayliewordsman.grapefruitai.oc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.hayliewordsman.grapefruitai.TestFiles;
import io.github.hayliewordsman.grapefruitai.net.Http;
import io.github.hayliewordsman.grapefruitai.net.HttpException;

/**
 * Every request the client makes must be a route of OpenCode's V2 API with only documented query
 * parameters and body fields (routes extracted from https://opencode.ai/docs/openapi.json into
 * opencode-v2-routes.json), sent with HTTP Basic auth over HTTPS. Runs on the API 17 framework.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 17)
public class OpenCodeApiTest {
    static final class Request {
        final String method, url, auth, body;

        Request(String method, String url, String auth, String body) {
            this.method = method;
            this.url = url;
            this.auth = auth;
            this.body = body;
        }
    }

    /** Answers by method + path; records every request. */
    static final class Fake implements OpenCode.Transport {
        final List<Request> requests = new ArrayList<Request>();
        final Map<String, Object> answers = new LinkedHashMap<String, Object>();
        String events = "";

        Fake answer(String methodAndPath, Object body) {
            answers.put(methodAndPath, body);
            return this;
        }

        @Override
        public byte[] send(String method, String url, String authorization, String jsonBody) throws IOException {
            requests.add(new Request(method, url, authorization, jsonBody));
            String path = URI.create(url).getRawPath();
            Object a = answers.get(method + " " + path);
            if (a instanceof IOException) throw (IOException) a;
            if (a == null) {
                if (path.equals("/api/project")) return "[]".getBytes("UTF-8");
                if (method.equals("POST") && path.equals("/api/session")) return "{\"data\":{\"id\":\"ses_1\"}}".getBytes("UTF-8");
                return "{\"data\":[],\"cursor\":{}}".getBytes("UTF-8");
            }
            return a.toString().getBytes("UTF-8");
        }

        @Override
        public InputStream stream(String url, String authorization, Http[] holder) throws IOException {
            requests.add(new Request("GET", url, authorization, null));
            return new ByteArrayInputStream(events.getBytes("UTF-8"));
        }
    }

    private JSONObject routes;
    private Fake fake;
    private OpenCode client;

    @Before
    public void setUp() throws Exception {
        routes = new JSONObject(TestFiles.read("opencode-v2-routes.json")).getJSONObject("routes");
        fake = new Fake();
        client = new OpenCode("https://agent.example.com/", "opencode", "s3cret", "/home/me/app", fake);
    }

    /** The spec route a request matches ("POST /api/session/{sessionID}/prompt"), or fails. */
    private JSONObject route(Request r) throws Exception {
        String path = URI.create(r.url).getRawPath();
        Iterator<String> keys = routes.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            int space = k.indexOf(' ');
            if (!k.substring(0, space).equals(r.method)) continue;
            String template = k.substring(space + 1);
            String regex = "\\Q" + template.replaceAll("\\{[^}]+}", "\\\\E[^/]+\\\\Q").replace("*", "\\E.+\\Q") + "\\E";
            if (Pattern.matches(regex, path)) return routes.getJSONObject(k);
        }
        fail("Not an OpenCode V2 route: " + r.method + " " + path);
        return null;
    }

    private void checkAll() throws Exception {
        assertFalse(fake.requests.isEmpty());
        for (Request r : fake.requests) {
            assertTrue(r.url, r.url.startsWith("https://agent.example.com/api/"));
            assertEquals("Basic b3BlbmNvZGU6czNjcmV0", r.auth);
            JSONObject spec = route(r);
            Set<String> allowed = new HashSet<String>();
            JSONArray q = spec.optJSONArray("query");
            for (int i = 0; q != null && i < q.length(); i++) allowed.add(q.getString(i));
            String query = URI.create(r.url).getRawQuery();
            if (query != null) {
                for (String pair : query.split("&")) {
                    String name = URLDecoder.decode(pair.substring(0, pair.indexOf('=')), "UTF-8");
                    String base = name.contains("[") ? name.substring(0, name.indexOf('[')) : name;
                    assertTrue(r.method + " " + r.url + ": query " + name, allowed.contains(base));
                }
            }
            if (r.body != null) {
                JSONArray fields = spec.optJSONArray("body");
                assertTrue(r.method + " " + r.url + " takes no body", fields != null);
                Set<String> ok = new HashSet<String>();
                for (int i = 0; i < fields.length(); i++) ok.add(fields.getString(i));
                JSONObject body = new JSONObject(r.body);
                Iterator<String> it = body.keys();
                while (it.hasNext()) {
                    String f = it.next();
                    assertTrue(r.url + ": body field " + f, ok.contains(f));
                }
                JSONArray required = spec.optJSONArray("required");
                for (int i = 0; required != null && i < required.length(); i++) {
                    assertTrue(r.url + ": missing " + required.getString(i), body.has(required.getString(i)));
                }
            } else {
                JSONArray required = spec.optJSONArray("required");
                assertTrue(r.url + " needs a body", required == null || required.length() == 0);
            }
        }
    }

    @Test
    public void everyCallIsAV2Route() throws Exception {
        fake.answer("GET /api/info", "{\"version\":\"2.2.7\",\"pid\":1,\"urls\":[],\"paths\":{\"tmp\":\"/tmp\"}}")
                .answer("GET /api/session", "{\"data\":[],\"cursor\":{}}")
                .answer("POST /api/session", "{\"data\":{\"id\":\"ses_1\",\"title\":\"t\"}}")
                .answer("GET /api/session/ses_1/message", "{\"data\":[{\"id\":\"b\",\"type\":\"assistant\"},"
                        + "{\"id\":\"a\",\"type\":\"user\",\"text\":\"hi\"}],\"cursor\":{}}")
                .answer("POST /api/session/ses_1/interrupt", "{\"interrupted\":true}")
                .answer("GET /api/fs/read/app/src/My%20File.kt", "class A");
        client.info();
        client.location();
        client.models();
        client.defaultModel();
        client.providers();
        client.agents();
        client.projects();
        client.sessions();
        assertEquals("ses_1", client.createSession("build", "openrouter", "anthropic/claude-sonnet-5-5").optString("id"));
        client.session("ses_1");
        client.switchModel("ses_1", "openrouter", "openai/gpt-5");
        client.switchAgent("ses_1", "plan");
        client.prompt("ses_1", "Create an Android app");
        assertTrue(client.interrupt("ses_1"));
        JSONArray ms = client.messages("ses_1");
        assertEquals("a", ms.getJSONObject(0).optString("id"));
        client.activeSessions();
        client.sessionDiff("ses_1");
        client.permissions("ses_1");
        client.replyPermission("ses_1", "per_1", "once");
        client.listFiles("");
        client.listFiles("app/src");
        assertEquals("class A", new String(client.readFile("app/src/My File.kt"), "UTF-8"));
        client.findFiles(".apk");
        client.vcsStatus();
        client.deleteSession("ses_1");
        client.events(new OpenCode.EventListener() {
            public boolean onEvent(String type, JSONObject data) {
                return true;
            }
        }, null);
        checkAll();
    }

    @Test
    public void bodiesMatchTheSpec() throws Exception {
        client.createSession("build", "openrouter", "x/y");
        client.prompt("ses_1", "hello");
        client.replyPermission("ses_1", "per_1", "reject");
        client.switchModel("ses_1", "openrouter", "x/y");
        JSONObject create = new JSONObject(fake.requests.get(0).body);
        assertEquals("/home/me/app", create.getJSONObject("location").getString("directory"));
        assertEquals("x/y", create.getJSONObject("model").getString("id"));
        assertEquals("openrouter", create.getJSONObject("model").getString("providerID"));
        assertEquals("{\"text\":\"hello\"}", fake.requests.get(1).body);
        assertEquals("{\"decision\":\"reject\"}", fake.requests.get(2).body);
        assertTrue(fake.requests.get(3).body.contains("\"model\""));
        checkAll();
    }

    @Test
    public void projectIsSentAsLocationDirectory() throws Exception {
        client.models();
        assertTrue(fake.requests.get(0).url, fake.requests.get(0).url.endsWith("?location%5Bdirectory%5D=%2Fhome%2Fme%2Fapp"));
        client.sessions();
        assertTrue(fake.requests.get(1).url.contains("directory=%2Fhome%2Fme%2Fapp"));
    }

    @Test
    public void eventsAreParsedFromSse() throws Exception {
        fake.events = ": keepalive\n\nevent: session.text.delta\nid: e1\ndata: {\"id\":\"e1\",\"created\":1,"
                + "\"type\":\"session.text.delta\",\"data\":{\"sessionID\":\"ses_1\",\"assistantMessageID\":\"m\","
                + "\"ordinal\":0,\"delta\":\"Hi\"}}\n\ndata: {\"id\":\"e2\",\"created\":2,\"type\":\"session.idle\","
                + "\"data\":{\"sessionID\":\"ses_1\"}}\n\n";
        final Transcript t = new Transcript("ses_1");
        final List<String> types = new ArrayList<String>();
        client.events(new OpenCode.EventListener() {
            public boolean onEvent(String type, JSONObject data) {
                types.add(type);
                t.apply(type, data);
                return true;
            }
        }, null);
        assertEquals("[session.text.delta, session.idle]", types.toString());
        assertEquals("Hi", t.messages().get(0).content.get(0).optString("text"));
    }

    @Test
    public void refusesUnsafeInputs() throws Exception {
        try {
            client.readFile("../etc/passwd");
            fail();
        } catch (IOException expected) {
            // Good.
        }
        try {
            new OpenCode("http://agent.example.com", "opencode", "x", null, fake);
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("HTTPS"));
        }
        try {
            new OpenCode("https://agent.example.com", "opencode", "", null, fake);
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("password"));
        }
    }

    @Test
    public void connectionCheckStopsAtTheFailingStep() throws Exception {
        fake.answer("GET /api/info", "<html>Tunnel login</html>");
        assertEquals(ConnectionCheck.Kind.NOT_OPENCODE, ConnectionCheck.run(client).kind);

        fake = new Fake();
        client = new OpenCode("https://agent.example.com", "opencode", "wrong", "/home/me/app", fake);
        fake.answer("GET /api/info", "{\"version\":\"2.2.7\"}")
                .answer("GET /api/location", new HttpException(401, "{\"_tag\":\"UnauthorizedError\",\"message\":\"no\"}"));
        assertEquals(ConnectionCheck.Kind.UNAUTHORIZED, ConnectionCheck.run(client).kind);

        fake.answer("GET /api/location", new HttpException(404,
                "{\"_tag\":\"LocationNotFoundError\",\"location\":{\"directory\":\"/home/me/app\"},\"message\":\"no\"}"));
        assertEquals(ConnectionCheck.Kind.PROJECT_NOT_FOUND, ConnectionCheck.run(client).kind);

        fake.answer("GET /api/info", new HttpException(404, "Not Found"));
        ConnectionCheck.Result notFound = ConnectionCheck.run(client);
        assertEquals(ConnectionCheck.Kind.NOT_FOUND, notFound.kind);
        assertTrue(notFound.detail.contains("/api/info"));

        fake = new Fake();
        client = new OpenCode("https://agent.example.com", "opencode", "s3cret", null, fake);
        fake.answer("GET /api/info", "{\"version\":\"2.2.7\"}")
                .answer("GET /api/location", "{\"directory\":\"/srv/app\",\"project\":{\"id\":\"p\"}}")
                .answer("GET /api/model", "{\"location\":{\"directory\":\"/srv/app\"},\"data\":[{\"id\":\"anthropic/claude\","
                        + "\"providerID\":\"openrouter\",\"name\":\"Claude\",\"enabled\":true}]}")
                .answer("GET /api/agent", "{\"location\":{\"directory\":\"/srv/app\"},\"data\":[{\"id\":\"build\"}]}");
        ConnectionCheck.Result ok = ConnectionCheck.run(client);
        assertTrue(ok.title, ok.ok());
        assertEquals("2.2.7", ok.version);
        assertEquals("/srv/app", ok.project);
        assertEquals(1, ok.models.length());
    }
}
