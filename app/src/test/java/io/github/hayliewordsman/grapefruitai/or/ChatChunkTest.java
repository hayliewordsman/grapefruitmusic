package io.github.hayliewordsman.grapefruitai.or;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.hayliewordsman.grapefruitai.chat.Message;

public class ChatChunkTest {
    static final class Collect implements OpenRouter.ChatListener {
        final StringBuilder text = new StringBuilder();
        final StringBuilder reasoning = new StringBuilder();
        final List<String> urls = new ArrayList<String>();
        double cost = -2;
        long in, out;

        public void onText(String d) {
            text.append(d);
        }

        public void onReasoning(String d) {
            reasoning.append(d);
        }

        public void onImage(byte[] data, String mime) {
        }

        public void onCitation(String title, String url) {
            urls.add(url);
        }

        public void onUsage(long p, long c, double cost) {
            in = p;
            out = c;
            this.cost = cost;
        }
    }

    @Test
    public void streamsTextReasoningCitationsAndUsage() throws Exception {
        Collect c = new Collect();
        OpenRouter.handleChunk(new JSONObject("{\"choices\":[{\"delta\":{\"content\":\"Hel\",\"reasoning\":\"think\"}}]}"), c);
        OpenRouter.handleChunk(new JSONObject("{\"choices\":[{\"delta\":{\"content\":\"lo\",\"annotations\":[{\"type\":"
                + "\"url_citation\",\"url_citation\":{\"url\":\"https://example.org\",\"title\":\"Ex\"}}]}}]}"), c);
        OpenRouter.handleChunk(new JSONObject("{\"choices\":[{\"delta\":{\"content\":null},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":3,\"cost\":0.00042}}"), c);
        assertEquals("Hello", c.text.toString());
        assertEquals("think", c.reasoning.toString());
        assertEquals(Collections.singletonList("https://example.org"), c.urls);
        assertEquals(12, c.in);
        assertEquals(0.00042, c.cost, 1e-9);
    }

    @Test
    public void midStreamErrorsThrow() throws Exception {
        try {
            OpenRouter.handleChunk(new JSONObject("{\"error\":{\"code\":502,\"message\":\"Provider disconnected\"},"
                    + "\"choices\":[{\"delta\":{},\"finish_reason\":\"error\"}]}"), new Collect());
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Provider disconnected"));
        }
    }

    @Test
    public void historySkipsFailedRepliesAndAddsSystem() throws Exception {
        Message u = new Message(Message.USER);
        u.text = "hi";
        Message bad = new Message(Message.ASSISTANT);
        bad.text = "partial";
        bad.error = "boom";
        Message ok = new Message(Message.ASSISTANT);
        ok.text = "hello";
        List<Message> h = new ArrayList<Message>();
        h.add(u);
        h.add(bad);
        h.add(ok);
        JSONArray a = OpenRouter.messages(h, "sys");
        assertEquals(3, a.length());
        assertEquals("system", a.getJSONObject(0).getString("role"));
        assertEquals("hello", a.getJSONObject(2).getString("content"));
    }
}
