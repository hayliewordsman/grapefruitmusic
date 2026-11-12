package io.github.hayliewordsman.grapefruitai.oc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** Event shapes from the @opencode-ai/sdk v2 types (EventMessagePartUpdated etc.). */
public class TranscriptTest {
    private static JSONObject o(String s) throws Exception {
        return new JSONObject(s);
    }

    @Test
    public void appliesStreamingEvents() throws Exception {
        Transcript t = new Transcript("ses_1");
        t.load(new JSONArray("[{\"info\":{\"id\":\"msg_u\",\"sessionID\":\"ses_1\",\"role\":\"user\"},"
                + "\"parts\":[{\"id\":\"p0\",\"type\":\"text\",\"text\":\"build it\",\"messageID\":\"msg_u\"}]}]"));
        assertTrue(t.apply("session.status", o("{\"sessionID\":\"ses_1\",\"status\":{\"type\":\"busy\"}}")));
        assertTrue(t.busy());
        assertTrue(t.apply("message.updated", o("{\"sessionID\":\"ses_1\",\"info\":{\"id\":\"msg_a\",\"sessionID\":"
                + "\"ses_1\",\"role\":\"assistant\",\"cost\":0.01,\"tokens\":{\"input\":100,\"output\":20,"
                + "\"reasoning\":5,\"cache\":{\"read\":50,\"write\":0}}}}")));
        assertTrue(t.apply("message.part.updated", o("{\"sessionID\":\"ses_1\",\"part\":{\"id\":\"p1\",\"sessionID\":"
                + "\"ses_1\",\"messageID\":\"msg_a\",\"type\":\"text\",\"text\":\"Hel\"},\"time\":1}")));
        assertTrue(t.apply("message.part.delta", o("{\"sessionID\":\"ses_1\",\"messageID\":\"msg_a\",\"partID\":\"p1\","
                + "\"field\":\"text\",\"delta\":\"lo\"}")));
        assertTrue(t.apply("message.part.updated", o("{\"sessionID\":\"ses_1\",\"part\":{\"id\":\"p2\",\"sessionID\":"
                + "\"ses_1\",\"messageID\":\"msg_a\",\"type\":\"tool\",\"tool\":\"bash\",\"callID\":\"c\",\"state\":"
                + "{\"status\":\"running\",\"input\":{\"command\":\"./gradlew assembleDebug\"},\"time\":{\"start\":1}}}}")));
        assertEquals(2, t.messages().size());
        Transcript.Msg a = t.messages().get(1);
        assertEquals("Hello", a.parts.get("p1").optString("text"));
        assertEquals("bash", a.parts.get("p2").optString("tool"));
        assertEquals(100, t.tokens()[0]);
        assertEquals(0.01, t.cost(), 1e-9);
        assertTrue(t.apply("session.idle", o("{\"sessionID\":\"ses_1\"}")));
        assertFalse(t.busy());
    }

    @Test
    public void ignoresOtherSessions() throws Exception {
        Transcript t = new Transcript("ses_1");
        assertFalse(t.apply("message.part.updated", o("{\"sessionID\":\"ses_2\",\"part\":{\"id\":\"x\",\"sessionID\":"
                + "\"ses_2\",\"messageID\":\"m\",\"type\":\"text\",\"text\":\"no\"}}")));
        assertFalse(t.apply("permission.asked", o("{\"id\":\"per_1\",\"sessionID\":\"ses_2\",\"permission\":\"bash\"}")));
        assertTrue(t.messages().isEmpty());
        assertTrue(t.permissions().isEmpty());
    }

    @Test
    public void permissionsComeAndGo() throws Exception {
        Transcript t = new Transcript("ses_1");
        t.apply("permission.asked", o("{\"id\":\"per_1\",\"sessionID\":\"ses_1\",\"permission\":\"bash\","
                + "\"patterns\":[\"git push origin main\"],\"metadata\":{},\"always\":[\"git push *\"]}"));
        assertEquals(1, t.permissions().size());
        t.apply("permission.replied", o("{\"sessionID\":\"ses_1\",\"requestID\":\"per_1\",\"reply\":\"reject\"}"));
        assertTrue(t.permissions().isEmpty());
        t.setPermissions(new JSONArray("[{\"id\":\"per_2\",\"sessionID\":\"ses_1\"},{\"id\":\"per_3\",\"sessionID\":\"ses_9\"}]"));
        assertEquals(1, t.permissions().size());
    }

    @Test
    public void errorsReadable() throws Exception {
        assertEquals("Stopped", Transcript.errorText(o("{\"name\":\"MessageAbortedError\",\"data\":{\"message\":\"x\"}}")));
        assertEquals("ProviderAuthError: bad key", Transcript.errorText(o("{\"name\":\"ProviderAuthError\","
                + "\"data\":{\"providerID\":\"openrouter\",\"message\":\"bad key\"}}")));
    }
}
