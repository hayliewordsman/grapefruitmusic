package io.github.hayliewordsman.grapefruitai.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

import io.github.hayliewordsman.grapefruitai.md.MdRender;
import io.github.hayliewordsman.grapefruitai.oc.Transcript;
import io.github.hayliewordsman.grapefruitai.util.Format;

/**
 * Views for OpenCode V2 messages (Session.Message.Info): user prompts, assistant content (text,
 * reasoning, tool calls), shell commands, compaction summaries, and notes.
 */
final class PartView {
    private PartView() {
    }

    /** One message as a view, or null for kinds that aren't shown. */
    static View build(Activity a, Transcript.Msg msg) {
        Palette p = Ui.p();
        JSONObject info = msg.info;
        String type = msg.type();
        if ("user".equals(type)) {
            LinearLayout row = Ui.vertical(a);
            row.setGravity(Gravity.END);
            row.setPadding(Ui.dp(a, 48), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 4));
            TextView t = Ui.body(a, 15, p.text);
            t.setText(info.optString("text"));
            t.setTextIsSelectable(true);
            t.setBackground(Ui.shape(a, p.control, Ui.dp(a, 20), true, false));
            t.setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8));
            row.addView(t, Ui.wrap());
            JSONArray files = info.optJSONArray("files");
            for (int i = 0; files != null && i < files.length(); i++) {
                JSONObject f = files.optJSONObject(i);
                if (f == null) continue;
                TextView n = Ui.text(a, 12, p.secondary, false);
                n.setText("Attached: " + f.optString("name", f.optString("mime", "file")));
                row.addView(n, Ui.wrap());
            }
            return row;
        }
        if ("assistant".equals(type)) {
            LinearLayout box = Ui.vertical(a);
            for (JSONObject c : msg.content) {
                View v = content(a, c);
                if (v != null) box.addView(v, Ui.matchWrap());
            }
            View footer = footer(a, info);
            if (footer != null) box.addView(footer, Ui.matchWrap());
            return box;
        }
        if ("shell".equals(type)) {
            JSONObject out = info.optJSONObject("output");
            return card(a, "Shell · " + info.optString("status", "running"), info.optString("command"),
                    out != null ? out.optString("output") : "", null);
        }
        if ("compaction".equals(type)) {
            return note(a, "Context compacted", info.optString("summary", "Older messages were summarised to fit the model's context."));
        }
        if ("idle".equals(type)) {
            if (!"failed".equals(info.optString("outcome"))) return null;
            return note(a, "Run failed", Transcript.errorText(info.optJSONObject("error")));
        }
        if ("synthetic".equals(type) || "system".equals(type)) {
            String label = info.optString("description", "synthetic".equals(type) ? "Added context" : "System note");
            return collapsible(a, label, info.optString("text"), false);
        }
        if ("agent-switched".equals(type) || "agent-selected".equals(type)) {
            return note(a, "Agent: " + info.optString("agent"), null);
        }
        if ("model-switched".equals(type) || "model-selected".equals(type)) {
            JSONObject m = info.optJSONObject("model");
            return note(a, "Model: " + (m != null ? m.optString("providerID") + "/" + m.optString("id") : "?"), null);
        }
        return null;
    }

    private static View content(Activity a, JSONObject c) {
        String t = c.optString("type");
        if ("text".equals(t)) {
            String text = c.optString("text", "");
            if (text.length() == 0) return null;
            LinearLayout box = Ui.vertical(a);
            box.setPadding(Ui.dp(a, 14), Ui.dp(a, 4), Ui.dp(a, 14), Ui.dp(a, 4));
            MdRender.render(a, box, text, 15);
            return box;
        }
        if ("reasoning".equals(t)) {
            String text = c.optString("text", "");
            return text.length() == 0 ? null : collapsible(a, "Reasoning", text, false);
        }
        if ("tool".equals(t)) return tool(a, c);
        return null;
    }

    /** Model, tokens, cost and errors of an assistant message. */
    private static View footer(Activity a, JSONObject info) {
        StringBuilder b = new StringBuilder();
        JSONObject model = info.optJSONObject("model");
        if (model != null) b.append(model.optString("providerID")).append('/').append(model.optString("id"));
        JSONObject tk = info.optJSONObject("tokens");
        if (tk != null && (tk.optLong("input") > 0 || tk.optLong("output") > 0)) {
            b.append(" · ").append(Format.count(tk.optLong("input"))).append(" in / ")
                    .append(Format.count(tk.optLong("output"))).append(" out");
            if (tk.optLong("reasoning") > 0) b.append(" / ").append(Format.count(tk.optLong("reasoning"))).append(" reasoning");
        }
        if (info.has("cost")) b.append(" · ").append(Format.cost(info.optDouble("cost", 0))).append(" USD");
        String finish = info.optString("finish", "");
        if (finish.equals("length")) b.append(" · stopped at the length limit");
        if (finish.equals("content-filter")) b.append(" · stopped by the content filter");
        JSONObject error = info.optJSONObject("error");
        Palette p = Ui.p();
        if (b.length() == 0 && error == null) return null;
        LinearLayout box = Ui.vertical(a);
        box.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 14), Ui.dp(a, 6));
        if (b.length() > 0) {
            TextView t = Ui.body(a, 12, p.secondary);
            t.setText(b.toString());
            box.addView(t);
        }
        if (error != null) {
            TextView e = Ui.body(a, 13, p.danger);
            e.setText(Transcript.errorText(error));
            e.setTextIsSelectable(true);
            box.addView(e);
        }
        return box;
    }

    private static View tool(Activity a, JSONObject c) {
        JSONObject state = c.optJSONObject("state");
        String status = state != null ? state.optString("status", "streaming") : "streaming";
        Object input = state != null ? state.opt("input") : null;
        String summary = input instanceof JSONObject ? inputSummary((JSONObject) input)
                : input != null ? String.valueOf(input) : "";
        StringBuilder output = new StringBuilder();
        JSONArray content = state != null ? state.optJSONArray("content") : null;
        StringBuilder files = new StringBuilder();
        for (int i = 0; content != null && i < content.length(); i++) {
            JSONObject item = content.optJSONObject(i);
            if (item == null) continue;
            if ("text".equals(item.optString("type"))) {
                if (output.length() > 0) output.append('\n');
                output.append(item.optString("text"));
            } else if ("file".equals(item.optString("type"))) {
                files.append(item.optString("name", item.optString("uri"))).append(" (").append(item.optString("mime")).append(")\n");
            }
        }
        String error = state != null && state.optJSONObject("error") != null
                ? Transcript.errorText(state.optJSONObject("error")) : null;
        return card(a, c.optString("name", "tool") + " · " + status, summary, output.toString(),
                error != null ? error : files.length() > 0 ? "Files: " + files.toString().trim() : null, status);
    }

    private static View card(Activity a, String title, String input, String output, String extra) {
        return card(a, title, input, output, extra, null);
    }

    private static View card(Activity a, String title, String input, String output, String extra, String status) {
        Palette p = Ui.p();
        LinearLayout card = Ui.vertical(a);
        card.setBackground(Ui.shape(a, p.surface, Ui.dp(a, 16), true, false));
        card.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8));
        LinearLayout head = Ui.horizontal(a);
        View dot = new View(a);
        int color = "completed".equals(status) ? 0xff3fbf6f : "error".equals(status) ? p.danger
                : "running".equals(status) ? 0xffe8b33c : p.secondary;
        dot.setBackground(Ui.shape(a, color, 0, false, true));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(a, 8), Ui.dp(a, 8));
        dlp.rightMargin = Ui.dp(a, 8);
        head.addView(dot, dlp);
        TextView name = Ui.text(a, 14, p.text, true);
        name.setText(title);
        head.addView(name, Ui.weight(1));
        card.addView(head);
        if (input != null && input.length() > 0) {
            TextView in = Ui.body(a, 12, p.secondary);
            in.setTypeface(Typeface.MONOSPACE);
            in.setText(input.length() > 600 ? input.substring(0, 600) + "…" : input);
            in.setTextIsSelectable(true);
            card.addView(in);
        }
        if (extra != null) {
            TextView e = Ui.body(a, 13, "error".equals(status) ? p.danger : p.secondary);
            e.setText(extra);
            e.setTextIsSelectable(true);
            card.addView(e);
        }
        if (output != null && output.length() > 0) card.addView(collapsible(a, "Output", output, true));
        LinearLayout wrap = Ui.vertical(a);
        wrap.setPadding(Ui.dp(a, 12), Ui.dp(a, 3), Ui.dp(a, 12), Ui.dp(a, 3));
        wrap.addView(card, Ui.matchWrap());
        return wrap;
    }

    static String inputSummary(JSONObject input) {
        if (input == null) return "";
        for (String k : new String[]{"command", "filePath", "path", "pattern", "url", "query", "description"}) {
            String v = input.optString(k, "");
            if (v.length() > 0) return v;
        }
        StringBuilder b = new StringBuilder();
        Iterator<String> it = input.keys();
        while (it.hasNext() && b.length() < 300) {
            String k = it.next();
            Object v = input.opt(k);
            if (v instanceof String || v instanceof Number || v instanceof Boolean) {
                if (b.length() > 0) b.append(", ");
                b.append(k).append(": ").append(v);
            }
        }
        return b.toString();
    }

    private static View collapsible(final Activity a, final String label, final String text, final boolean mono) {
        final Palette p = Ui.p();
        final LinearLayout box = Ui.vertical(a);
        box.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 14), 0);
        final TextView head = Ui.text(a, 13, p.accent, true);
        final String lower = label.toLowerCase(Locale.US);
        head.setText("Show " + lower + " (" + text.length() + " characters)");
        head.setMinHeight(Ui.dp(a, 36));
        head.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(head);
        head.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (box.getChildCount() > 1) {
                    box.removeViewAt(1);
                    head.setText("Show " + lower + " (" + text.length() + " characters)");
                    return;
                }
                head.setText("Hide " + lower);
                if (mono) {
                    String shown = text.length() > 20000 ? text.substring(0, 20000) + "\n… (truncated)" : text;
                    box.addView(MdRender.codeBlock(a, shown, lower));
                } else {
                    TextView t = Ui.body(a, 13, p.secondary);
                    t.setText(text);
                    t.setTextIsSelectable(true);
                    box.addView(t);
                }
            }
        });
        return box;
    }

    private static View note(Activity a, String title, String text) {
        Palette p = Ui.p();
        LinearLayout box = Ui.vertical(a);
        box.setPadding(Ui.dp(a, 14), Ui.dp(a, 4), Ui.dp(a, 14), Ui.dp(a, 4));
        TextView t = Ui.text(a, 13, p.accent, true);
        t.setText(title);
        box.addView(t);
        if (text != null && text.length() > 0) {
            TextView b = Ui.body(a, 13, p.secondary);
            b.setText(text);
            b.setTextIsSelectable(true);
            box.addView(b);
        }
        return box;
    }
}
