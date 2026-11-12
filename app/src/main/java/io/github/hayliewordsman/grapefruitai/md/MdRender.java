package io.github.hayliewordsman.grapefruitai.md;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import io.github.hayliewordsman.grapefruitai.ui.Fonts;
import io.github.hayliewordsman.grapefruitai.ui.IconView;
import io.github.hayliewordsman.grapefruitai.ui.Palette;
import io.github.hayliewordsman.grapefruitai.ui.Ui;

/** Markdown to views: text blocks as spans in Geist, code blocks as monospace cards with Copy. */
public final class MdRender {
    private MdRender() {
    }

    /** Fills into with the rendered markdown (replacing what was there). */
    public static void render(Context c, LinearLayout into, String markdown, float sp) {
        into.removeAllViews();
        Palette p = Ui.p();
        List<Markdown.Block> blocks = Markdown.parse(markdown);
        SpannableStringBuilder run = new SpannableStringBuilder();
        for (Markdown.Block b : blocks) {
            switch (b.type) {
                case Markdown.CODE:
                    flush(c, into, run, sp);
                    into.addView(codeBlock(c, b.text, b.info), blockParams(c));
                    break;
                case Markdown.TABLE:
                    flush(c, into, run, sp);
                    into.addView(table(c, b.text), blockParams(c));
                    break;
                case Markdown.RULE:
                    flush(c, into, run, sp);
                    View rule = Ui.separator(c, 0);
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rule.getLayoutParams();
                    lp.topMargin = Ui.dp(c, 8);
                    lp.bottomMargin = Ui.dp(c, 8);
                    into.addView(rule, lp);
                    break;
                default:
                    if (run.length() > 0) run.append("\n\n".substring(0, b.type == Markdown.BULLET || b.type == Markdown.ORDERED ? 1 : 2));
                    appendBlock(c, run, b, p);
            }
        }
        flush(c, into, run, sp);
    }

    private static LinearLayout.LayoutParams blockParams(Context c) {
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(c, 6);
        lp.bottomMargin = Ui.dp(c, 6);
        return lp;
    }

    private static void appendBlock(Context c, SpannableStringBuilder run, Markdown.Block b, Palette p) {
        int start = run.length();
        switch (b.type) {
            case Markdown.HEADING: {
                inline(run, b.text, p);
                float size = b.level == 1 ? 1.4f : b.level == 2 ? 1.25f : b.level == 3 ? 1.12f : 1.0f;
                run.setSpan(new RelativeSizeSpan(size), start, run.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                run.setSpan(new FontSpan(Fonts.semiBold()), start, run.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            }
            case Markdown.BULLET:
            case Markdown.ORDERED: {
                int depth = b.type == Markdown.BULLET ? b.level : parseInt(b.info);
                String marker = b.type == Markdown.BULLET ? (depth % 2 == 0 ? "•  " : "–  ") : b.level + ".  ";
                run.append(marker);
                run.setSpan(new ForegroundColorSpan(p.accent), start, run.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                inline(run, b.text, p);
                int indent = Ui.dp(c, 6 + depth * 16);
                run.setSpan(new LeadingMarginSpan.Standard(indent, indent + Ui.dp(c, 16)), start, run.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            }
            case Markdown.QUOTE: {
                inline(run, b.text, p);
                run.setSpan(new ForegroundColorSpan(p.secondary), start, run.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                run.setSpan(new android.text.style.QuoteSpan(p.accent), start, run.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            }
            default:
                inline(run, b.text, p);
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static void flush(Context c, LinearLayout into, SpannableStringBuilder run, float sp) {
        if (run.length() == 0) return;
        TextView t = Ui.body(c, sp, Ui.p().text);
        t.setText(new SpannableStringBuilder(run));
        t.setLineSpacing(0, 1.12f);
        t.setLinkTextColor(Ui.p().accent);
        t.setMovementMethod(LinkMovementMethod.getInstance());
        t.setTextIsSelectable(true);
        into.addView(t, Ui.matchWrap());
        run.clear();
        run.clearSpans();
    }

    /** Inline markup: `code`, **bold**, *italic*, ~~strike~~, [links](https://...). */
    public static void inline(SpannableStringBuilder out, String s, Palette p) {
        int i = 0;
        int n = s.length();
        while (i < n) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < n && "\\`*_~[]()#+-.!|>".indexOf(s.charAt(i + 1)) >= 0) {
                out.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            if (ch == '`') {
                int end = s.indexOf('`', i + 1);
                if (end > i) {
                    int start = out.length();
                    out.append(s, i + 1, end);
                    out.setSpan(new MonoSpan(), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new BackgroundColorSpan(p.control), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }
            if ((ch == '*' || ch == '_') && i + 1 < n && s.charAt(i + 1) == ch) {
                String marker = s.substring(i, i + 2);
                int end = s.indexOf(marker, i + 2);
                if (end > i + 2) {
                    int start = out.length();
                    inline(out, s.substring(i + 2, end), p);
                    out.setSpan(new FontSpan(Fonts.semiBold()), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            }
            if (ch == '~' && i + 1 < n && s.charAt(i + 1) == '~') {
                int end = s.indexOf("~~", i + 2);
                if (end > i + 2) {
                    int start = out.length();
                    inline(out, s.substring(i + 2, end), p);
                    out.setSpan(new StrikethroughSpan(), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            }
            if ((ch == '*' || ch == '_') && i + 1 < n && !Character.isWhitespace(s.charAt(i + 1))
                    && (ch == '*' || i == 0 || !Character.isLetterOrDigit(s.charAt(i - 1)))) {
                int end = s.indexOf(ch, i + 1);
                if (end > i + 1 && !Character.isWhitespace(s.charAt(end - 1))
                        && (ch == '*' || end + 1 >= n || !Character.isLetterOrDigit(s.charAt(end + 1)))) {
                    int start = out.length();
                    inline(out, s.substring(i + 1, end), p);
                    out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }
            if (ch == '[') {
                int close = s.indexOf("](", i + 1);
                int paren = close > 0 ? s.indexOf(')', close + 2) : -1;
                if (close > i && paren > close) {
                    String label = s.substring(i + 1, close);
                    String url = s.substring(close + 2, paren).trim();
                    int space = url.indexOf(' ');
                    if (space > 0) url = url.substring(0, space);
                    int start = out.length();
                    inline(out, label, p);
                    if (url.startsWith("https://") || url.startsWith("http://")) {
                        out.setSpan(new URLSpan(url), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    i = paren + 1;
                    continue;
                }
            }
            out.append(ch);
            i++;
        }
    }

    /** A code block: language label, Copy button, monospace text that scrolls sideways. */
    public static View codeBlock(final Context c, final String code, String lang) {
        Palette p = Ui.p();
        LinearLayout card = Ui.vertical(c);
        card.setBackground(Ui.shape(c, p.light ? 0xfffff6f1 : 0x33000000 | (p.control & 0xffffff), Ui.dp(c, 16), true, false));
        LinearLayout top = Ui.horizontal(c);
        top.setPadding(Ui.dp(c, 14), Ui.dp(c, 4), Ui.dp(c, 4), 0);
        TextView label = Ui.text(c, 12, p.secondary, false);
        label.setTypeface(Fonts.medium());
        label.setText(lang != null ? lang : "code");
        top.addView(label, Ui.weight(1));
        LinearLayout copy = Ui.horizontal(c);
        copy.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        copy.setBackground(Ui.rowBackground(c));
        copy.addView(Ui.icon(c, IconView.COPY, p.accent, 16));
        TextView copyLabel = Ui.text(c, 13, p.accent, true);
        copyLabel.setText(" Copy");
        copy.addView(copyLabel);
        copy.setContentDescription("Copy code");
        copy.setMinimumHeight(Ui.dp(c, 40));
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.copy(c, "Code", code);
            }
        });
        top.addView(copy);
        card.addView(top, Ui.matchWrap());
        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(true);
        TextView t = new TextView(c);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(p.text);
        t.setText(code);
        t.setTextIsSelectable(true);
        t.setHorizontallyScrolling(true);
        t.setPadding(Ui.dp(c, 14), Ui.dp(c, 2), Ui.dp(c, 14), Ui.dp(c, 12));
        scroll.addView(t);
        card.addView(scroll, Ui.matchWrap());
        return card;
    }

    /** A table as aligned monospace text (light on old devices, and copyable). */
    private static View table(Context c, String text) {
        String[] rows = text.split("\n");
        List<List<String>> cells = new java.util.ArrayList<List<String>>();
        int cols = 0;
        for (String r : rows) {
            List<String> row = Markdown.cells(r);
            cells.add(row);
            cols = Math.max(cols, row.size());
        }
        int[] widths = new int[cols];
        for (List<String> row : cells) {
            for (int i = 0; i < row.size(); i++) widths[i] = Math.min(40, Math.max(widths[i], row.get(i).length()));
        }
        StringBuilder b = new StringBuilder();
        for (int r = 0; r < cells.size(); r++) {
            List<String> row = cells.get(r);
            for (int i = 0; i < cols; i++) {
                String v = i < row.size() ? row.get(i) : "";
                if (v.length() > 40) v = v.substring(0, 39) + "…";
                b.append(v);
                for (int k = v.length(); k < widths[i] + 2; k++) b.append(' ');
            }
            b.append('\n');
            if (r == 0) {
                for (int i = 0; i < cols; i++) {
                    for (int k = 0; k < widths[i]; k++) b.append('-');
                    b.append("  ");
                }
                b.append('\n');
            }
        }
        return codeBlock(c, b.toString().replaceAll("\\s+$", ""), "table");
    }

    static final class FontSpan extends MetricAffectingSpan {
        private final Typeface face;

        FontSpan(Typeface face) {
            this.face = face;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            apply(tp);
        }

        @Override
        public void updateMeasureState(TextPaint tp) {
            apply(tp);
        }

        private void apply(Paint paint) {
            paint.setTypeface(face);
            paint.setFakeBoldText(false);
        }
    }

    static final class MonoSpan extends MetricAffectingSpan {
        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setTypeface(Typeface.MONOSPACE);
            tp.setTextSize(tp.getTextSize() * 0.92f);
        }

        @Override
        public void updateMeasureState(TextPaint tp) {
            tp.setTypeface(Typeface.MONOSPACE);
            tp.setTextSize(tp.getTextSize() * 0.92f);
        }
    }
}
