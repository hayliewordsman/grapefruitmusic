package io.github.hayliewordsman.grapefruitai.oc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/** V2 events and messages, shaped as in https://opencode.ai/docs/openapi.json (2026-11-13). */
public class TranscriptTest {
    private static JSONObject o(String s) throws Exception {
        return new JSONObject(s);
    }

    private static final String S = "\"sessionID\":\"ses_1\"";
    private static final String A = S + ",\"assistantMessageID\":\"msg_a\"";

    @Test
    public void streamsATurn() throws Exception {
        Transcript t = new Transcript("ses_1");
        assertTrue(t.apply("session.inbox.enqueued", o("{" + S + ",\"inboxID\":\"msg_u\",\"item\":{\"type\":\"user\","
                + "\"payload\":{\"text\":\"build it\"},\"delivery\":\"queue\"}}")));
        assertTrue(t.apply("session.execution.started", o("{" + S + "}")));
        assertTrue(t.busy());
        t.apply("session.step.started", o("{" + A + ",\"agent\":\"build\",\"model\":{\"id\":\"anthropic/claude-sonnet-5-5\","
                + "\"providerID\":\"openrouter\"},\"started\":1}"));
        t.apply("session.reasoning.started", o("{" + A + ",\"ordinal\":0}"));
        t.apply("session.reasoning.delta", o("{" + A + ",\"ordinal\":0,\"delta\":\"think\"}"));
        t.apply("session.text.started", o("{" + A + ",\"ordinal\":1}"));
        t.apply("session.text.delta", o("{" + A + ",\"ordinal\":1,\"delta\":\"Hel\"}"));
        t.apply("session.text.delta", o("{" + A + ",\"ordinal\":1,\"delta\":\"lo\"}"));
        t.apply("session.tool.input.started", o("{" + A + ",\"id\":\"call_1\",\"name\":\"bash\"}"));
        t.apply("session.tool.input.delta", o("{" + A + ",\"id\":\"call_1\",\"delta\":\"{\\\"command\\\"\"}"));
        t.apply("session.tool.called", o("{" + A + ",\"id\":\"call_1\",\"input\":{\"command\":\"./gradlew assembleDebug\"},"
                + "\"executed\":false}"));
        t.apply("session.tool.success", o("{" + A + ",\"id\":\"call_1\",\"content\":[{\"type\":\"text\",\"text\":\"BUILD "
                + "SUCCESSFUL\"}],\"executed\":false}"));
        t.apply("session.step.ended", o("{" + A + ",\"finish\":\"stop\",\"cost\":0.01,\"tokens\":{\"input\":100,"
                + "\"output\":20,\"reasoning\":5,\"cache\":{\"read\":50,\"write\":0}},\"files\":[\"app/build.gradle\"]}"));
        assertTrue(t.apply("session.execution.succeeded", o("{" + S + "}")));
        assertFalse(t.busy());

        List<Transcript.Msg> ms = t.messages();
        assertEquals(2, ms.size());
        assertEquals("build it", ms.get(0).info.optString("text"));
        Transcript.Msg a = ms.get(1);
        assertEquals("assistant", a.type());
        assertEquals(3, a.content.size());
        assertEquals("think", a.content.get(0).optString("text"));
        assertEquals("Hello", a.content.get(1).optString("text"));
        JSONObject tool = a.content.get(2);
        assertEquals("bash", tool.optString("name"));
        assertEquals("completed", tool.getJSONObject("state").optString("status"));
        assertEquals("./gradlew assembleDebug", tool.getJSONObject("state").getJSONObject("input").optString("command"));
        assertEquals(100, t.tokens()[0]);
        assertEquals(0.01, t.cost(), 1e-9);
        assertEquals("[app/build.gradle]", t.changedFiles().toString());
    }

    @Test
    public void usageTotalsWinOverSums() throws Exception {
        Transcript t = new Transcript("ses_1");
        t.apply("session.step.ended", o("{" + A + ",\"finish\":\"stop\",\"cost\":0.01,\"tokens\":{\"input\":1,"
                + "\"output\":1,\"reasoning\":0,\"cache\":{\"read\":0,\"write\":0}}}"));
        t.apply("session.usage.updated", o("{" + S + ",\"cost\":0.5,\"tokens\":{\"input\":900,\"output\":80,"
                + "\"reasoning\":0,\"cache\":{\"read\":10,\"write\":2}}}"));
        assertEquals(0.5, t.cost(), 1e-9);
        assertEquals(900, t.tokens()[0]);
        assertEquals(2, t.tokens()[4]);
    }

    @Test
    public void loadsProjectedMessages() throws Exception {
        Transcript t = new Transcript("ses_1");
        t.load(new JSONArray("[{\"id\":\"m1\",\"type\":\"user\",\"text\":\"hi\",\"time\":{\"created\":1}},"
                + "{\"id\":\"m2\",\"type\":\"assistant\",\"agent\":\"build\",\"model\":{\"id\":\"x\",\"providerID\":\"openrouter\"},"
                + "\"time\":{\"created\":2},\"content\":[{\"type\":\"text\",\"text\":\"Hi!\"},{\"type\":\"tool\",\"id\":\"c1\","
                + "\"name\":\"read\",\"state\":{\"status\":\"completed\",\"input\":{\"filePath\":\"a.txt\"},\"content\":[{\"type\":"
                + "\"text\",\"text\":\"…\"}]},\"time\":{\"created\":3}}],\"cost\":0.02,\"tokens\":{\"input\":10,\"output\":5,"
                + "\"reasoning\":0,\"cache\":{\"read\":0,\"write\":0}},\"snapshot\":{\"files\":[\"a.txt\"]}},"
                + "{\"id\":\"m3\",\"type\":\"idle\",\"outcome\":\"succeeded\",\"time\":{\"created\":4}}]"));
        assertEquals(3, t.messages().size());
        assertEquals(2, t.messages().get(1).content.size());
        assertFalse(t.messages().get(1).info.has("content"));
        assertEquals(0.02, t.cost(), 1e-9);
        assertEquals("[a.txt]", t.changedFiles().toString());
        // A later event continues the loaded tool by its ID.
        t.apply("session.tool.failed", o("{\"sessionID\":\"ses_1\",\"assistantMessageID\":\"m2\",\"id\":\"c1\","
                + "\"error\":{\"type\":\"tool\",\"message\":\"boom\"},\"executed\":false}"));
        assertEquals("error", t.messages().get(1).content.get(1).getJSONObject("state").optString("status"));
    }

    @Test
    public void ignoresOtherSessions() throws Exception {
        Transcript t = new Transcript("ses_1");
        assertFalse(t.apply("session.text.delta", o("{\"sessionID\":\"ses_2\",\"assistantMessageID\":\"m\","
                + "\"ordinal\":0,\"delta\":\"no\"}")));
        assertFalse(t.apply("permission.asked", o("{\"id\":\"per_1\",\"sessionID\":\"ses_2\",\"action\":\"bash\","
                + "\"resources\":[]}")));
        assertTrue(t.messages().isEmpty());
        assertTrue(t.permissions().isEmpty());
    }

    @Test
    public void permissionsComeAndGo() throws Exception {
        Transcript t = new Transcript("ses_1");
        t.apply("permission.asked", o("{\"id\":\"per_1\"," + S + ",\"action\":\"bash\","
                + "\"resources\":[\"git push origin main\"],\"save\":[\"git push *\"]}"));
        assertEquals(1, t.permissions().size());
        t.apply("permission.replied", o("{" + S + ",\"requestID\":\"per_1\",\"reply\":\"reject\"}"));
        assertTrue(t.permissions().isEmpty());
        t.setPermissions(new JSONArray("[{\"id\":\"per_2\"," + S + ",\"action\":\"edit\",\"resources\":[]}]"));
        assertEquals(1, t.permissions().size());
    }

    @Test
    public void failuresAndRetries() throws Exception {
        Transcript t = new Transcript("ses_1");
        t.apply("session.retry.scheduled", o("{" + A + ",\"attempt\":2,\"at\":1,\"error\":{\"type\":\"api\","
                + "\"message\":\"overloaded\",\"status\":529}}"));
        assertTrue(t.busy());
        assertTrue(t.statusDetail, t.statusDetail.contains("overloaded"));
        t.apply("session.execution.failed", o("{" + S + ",\"error\":{\"type\":\"provider_auth\",\"message\":"
                + "\"bad key\",\"status\":401}}"));
        assertFalse(t.busy());
        assertEquals("provider_auth (HTTP 401): bad key", t.lastError);
        t.apply("session.execution.interrupted", o("{" + S + ",\"reason\":\"user\"}"));
        assertNull(t.lastError);
    }
}
