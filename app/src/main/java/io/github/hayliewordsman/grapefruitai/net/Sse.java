package io.github.hayliewordsman.grapefruitai.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Server-sent events: "data:" lines (joined with newlines) make one event, dispatched at a blank
 * line; ":" lines are comments (OpenRouter's ": OPENROUTER PROCESSING" keep-alives).
 */
public final class Sse {
    public interface Listener {
        /** One event; return false to stop reading. */
        boolean onEvent(String event, String data) throws IOException;
    }

    private Sse() {
    }

    public static void read(InputStream in, Listener listener) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"), 8192);
        StringBuilder data = new StringBuilder();
        String event = null;
        boolean any = false;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.length() == 0) {
                if (any) {
                    if (!listener.onEvent(event, data.toString())) return;
                }
                data.setLength(0);
                event = null;
                any = false;
                continue;
            }
            if (line.charAt(0) == ':') continue;
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) value = value.substring(1);
            if (field.equals("data")) {
                if (any) data.append('\n');
                data.append(value);
                any = true;
            } else if (field.equals("event")) {
                event = value;
            }
        }
        if (any) listener.onEvent(event, data.toString());
    }
}
