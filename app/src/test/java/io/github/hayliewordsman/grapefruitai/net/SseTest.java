package io.github.hayliewordsman.grapefruitai.net;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SseTest {
    private static List<String> parse(String s) throws IOException {
        final List<String> out = new ArrayList<String>();
        Sse.read(new ByteArrayInputStream(s.getBytes("UTF-8")), new Sse.Listener() {
            @Override
            public boolean onEvent(String event, String data) {
                out.add((event == null ? "" : event + "|") + data);
                return !data.equals("[DONE]");
            }
        });
        return out;
    }

    @Test
    public void skipsCommentsAndStopsAtDone() throws IOException {
        List<String> e = parse(": OPENROUTER PROCESSING\n\ndata: {\"a\":1}\n\ndata: [DONE]\n\ndata: after\n\n");
        assertEquals(2, e.size());
        assertEquals("{\"a\":1}", e.get(0));
        assertEquals("[DONE]", e.get(1));
    }

    @Test
    public void joinsMultiLineDataAndNamedEvents() throws IOException {
        List<String> e = parse("event: message\ndata: one\ndata:two\n\n");
        assertEquals("message|one\ntwo", e.get(0));
    }

    @Test
    public void dispatchesLastEventWithoutTrailingBlankLine() throws IOException {
        List<String> e = parse("data: x");
        assertEquals(1, e.size());
    }

    @Test
    public void handlesCrLf() throws IOException {
        List<String> e = parse("data: x\r\n\r\ndata: y\r\n\r\n");
        assertEquals(2, e.size());
        assertEquals("y", e.get(1));
    }
}
