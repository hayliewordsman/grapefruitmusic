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

import io.github.hayliewordsman.grapefruitai.md.MdRender;
import io.github.hayliewordsman.grapefruitai.oc.Transcript;
import io.github.hayliewordsman.grapefruitai.or.OpenRouter;
import io.github.hayliewordsman.grapefruitai.util.Format;

/** Views for OpenCode message parts: text, reasoning, tool calls, files, patches, retries. */
final class PartView {
    private PartView() {
    }

    static View build(final Activity a, Transcript.Msg msg, final JSONObject part) {
        Palette p = Ui.p();
        String type = part.optString("type");
        if (msg.isUser()) {
            if (!"text".equals(type) || part.optBoolean("synthetic", false)) {
                if ("file".equals(type)) return fileRow(a, part);
                return null;
            }
            LinearLayout row = Ui.vertical(a);
            row.setGravity(Gravity.END);
            row.setPadding(Ui.dp(a, 48), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 4));
            TextView t = Ui.body(a, 15, p.text);
            t.setText(part.optString("text"));
            t.setTextIsSelectable(true);
            t.setBackground(Ui.shape(a, p.control, Ui.dp(a, 20), true, false));
            t.setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8));
            row.addView(t, Ui.wrap());
            return row;
        }
        switch (type) {
            case "text": {
                if (part.optBoolean("synthetic", false) || part.optBoolean("ignored", false)) return null;
                LinearLayout box = Ui.vertical(a);
                box.setPadding(Ui.dp(a, 14), Ui.dp(a, 4), Ui.dp(a, 14), Ui.dp(a, 4));
                MdRender.render(a, box, part.optString("text"), 15);
                return box;
            }
            case "reasoning":
                return collapsible(a, "Reasoning", part.optString("text"), false);
            case "tool":
                return tool(a, part);
            case "file":
                return fileRow(a, part);
            case "patch": {
                JSONArray files = part.optJSONArray("files");
                StringBuilder b = new StringBuilder();
                for (int i = 0; files != null && i < files.length(); i++) {
                    if (b.length() > 0) b.append('\n');
                    b.append(files.optString(i));
                }
                return note(a, "Changed files", b.toString());
            }
            case "subtask":
                return note(a, "Subtask for " + part.optString("agent"), part.optString("description"));
            case "agent":
                return note(a, "Agent", part.optString("name"));
            case "retry": {
                JSONObject err = part.optJSONObject("error");
                return note(a, "Retry " + part.optInt("attempt"), Transcript.errorText(err));
            }
            case "compaction":
                return note(a, "Context compacted", "Older messages were summarised to fit the model's context.");
            default:
                return null;
        }
    }

    /** Model, tokens, cost and errors of an assistant message. */
    static View footer(Activity a, Transcript.Msg msg) {
        JSONObject info = msg.info;
        StringBuilder b = new StringBuilder();
        String model = info.optString("modelID", "");
        if (model.length() > 0) b.append(info.optString("providerID", "")).append('/').append(model);
        JSONObject tk = info.optJSONObject("tokens");
        if (tk != null && (tk.optLong("input") > 0 || tk.optLong("output") > 0)) {
            b.append(" · ").append(Format.count(tk.optLong("input"))).append(" in / ")
                    .append(Format.count(tk.optLong("output"))).append(" out");
            if (tk.optLong("reasoning") > 0) b.append(" / ").append(Format.count(tk.optLong("reasoning"))).append(" reasoning");
        }
        if (info.has("cost")) b.append(" · ").append(Format.cost(info.optDouble("cost", 0))).append(" USD");
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

    private static View tool(Activity a, JSONObject part) {
        Palette p = Ui.p();
        JSONObject state = part.optJSONObject("state");
        String status = state != null ? state.optString("status", "pending") : "pending";
        String tool = part.optString("tool", "tool");
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
        String title = state != null ? state.optString("title", "") : "";
        name.setText(tool + (title.length() > 0 ? " · " + title : "") + " · " + status);
        head.addView(name, Ui.weight(1));
        card.addView(head);
        JSONObject input = state != null ? state.optJSONObject("input") : null;
        String summary = inputSummary(input);
        if (summary.length() > 0) {
            TextView in = Ui.body(a, 12, p.secondary);
            in.setTypeface(Typeface.MONOSPACE);
            in.setText(summary.length() > 600 ? summary.substring(0, 600) + "…" : summary);
            in.setTextIsSelectable(true);
            card.addView(in);
        }
        if (state != null && "error".equals(status)) {
            TextView e = Ui.body(a, 13, p.danger);
            e.setText(state.optString("error"));
            e.setTextIsSelectable(true);
            card.addView(e);
        }
        if (state != null && "completed".equals(status)) {
            String output = state.optString("output", "");
            if (output.length() > 0) card.addView(collapsible(a, "Output", output, true));
            JSONArray attachments = state.optJSONArray("attachments");
            for (int i = 0; attachments != null && i < attachments.length(); i++) {
                View f = fileRow(a, attachments.optJSONObject(i));
                if (f != null) card.addView(f);
            }
        }
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
        head.setText("Show " + label.toLowerCase(java.util.Locale.US) + " (" + text.length() + " characters)");
        head.setMinHeight(Ui.dp(a, 36));
        head.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(head);
        head.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (box.getChildCount() > 1) {
                    box.removeViewAt(1);
                    head.setText("Show " + label.toLowerCase(java.util.Locale.US) + " (" + text.length() + " characters)");
                    return;
                }
                head.setText("Hide " + label.toLowerCase(java.util.Locale.US));
                if (mono) {
                    String shown = text.length() > 20000 ? text.substring(0, 20000) + "\n… (truncated)" : text;
                    box.addView(MdRender.codeBlock(a, shown, label.toLowerCase(java.util.Locale.US)));
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

    /** A file the agent attached or produced: data: URLs are saved directly. */
    private static View fileRow(final Activity a, final JSONObject part) {
        if (part == null) return null;
        Palette p = Ui.p();
        final String name = part.optString("filename", "file");
        final String url = part.optString("url", "");
        LinearLayout row = Ui.horizontal(a);
        row.setPadding(Ui.dp(a, 14), Ui.dp(a, 4), Ui.dp(a, 14), Ui.dp(a, 4));
        TextView t = Ui.text(a, 14, p.text, false);
        t.setText(name + " (" + part.optString("mime", "") + ")");
        row.addView(t, Ui.weight(1));
        if (url.startsWith("data:")) {
            row.addView(Ui.button(a, "Download", false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    OpenRouter.DataUrl d = OpenRouter.DataUrl.parse(url);
                    if (d == null) Ui.toast(a, "Unreadable file");
                    else Saver.saveFile(a, d.bytes, name, d.mime);
                }
            }));
        } else if (url.length() > 0) {
            TextView u = Ui.text(a, 12, p.secondary, false);
            u.setText(url);
            row.addView(u);
        }
        return row;
    }
}
