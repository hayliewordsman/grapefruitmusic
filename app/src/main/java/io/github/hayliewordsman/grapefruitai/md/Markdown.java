package io.github.hayliewordsman.grapefruitai.md;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small CommonMark-ish block parser: fenced code, headings, lists, quotes, rules, tables and
 * paragraphs. Unclosed fences (while a reply streams in) are code blocks up to the end. Inline
 * markup is left in the text for {@link MdRender}.
 */
public final class Markdown {
    public static final int PARAGRAPH = 0, HEADING = 1, CODE = 2, BULLET = 3, ORDERED = 4, QUOTE = 5,
            RULE = 6, TABLE = 7;

    public static final class Block {
        public final int type;
        public final String text;
        /** Heading level, list depth, or the ordered list's number. */
        public final int level;
        /** A code block's language. */
        public final String info;

        Block(int type, String text, int level, String info) {
            this.type = type;
            this.text = text;
            this.level = level;
            this.info = info;
        }

        @Override
        public String toString() {
            return type + ":" + level + ":" + (info == null ? "" : info) + ":" + text;
        }
    }

    private static final Pattern FENCE = Pattern.compile("^\\s{0,3}(`{3,}|~{3,})\\s*([^`\\s]*).*$");
    private static final Pattern HEADING_RE = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern BULLET_RE = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
    private static final Pattern ORDERED_RE = Pattern.compile("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$");
    private static final Pattern RULE_RE = Pattern.compile("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$");
    private static final Pattern TABLE_SEP = Pattern.compile("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$");

    private Markdown() {
    }

    public static List<Block> parse(String source) {
        List<Block> out = new ArrayList<Block>();
        if (source == null) return out;
        String[] lines = source.replace("\r\n", "\n").split("\n", -1);
        StringBuilder para = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            Matcher fence = FENCE.matcher(line);
            if (fence.matches()) {
                flush(out, para);
                String marker = fence.group(1);
                String lang = fence.group(2);
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.length) {
                    String l = lines[i];
                    if (l.trim().startsWith(marker.substring(0, 3)) && l.trim().replace(String.valueOf(marker.charAt(0)), "").length() == 0
                            && l.trim().length() >= marker.length()) {
                        i++;
                        break;
                    }
                    if (code.length() > 0) code.append('\n');
                    code.append(l);
                    i++;
                }
                out.add(new Block(CODE, code.toString(), 0, lang.length() > 0 ? lang : null));
                continue;
            }
            if (line.trim().length() == 0) {
                flush(out, para);
                i++;
                continue;
            }
            Matcher h = HEADING_RE.matcher(line);
            if (h.matches()) {
                flush(out, para);
                out.add(new Block(HEADING, h.group(2), h.group(1).length(), null));
                i++;
                continue;
            }
            if (RULE_RE.matcher(line).matches() && para.length() == 0) {
                out.add(new Block(RULE, "", 0, null));
                i++;
                continue;
            }
            Matcher b = BULLET_RE.matcher(line);
            if (b.matches()) {
                flush(out, para);
                out.add(new Block(BULLET, b.group(2), depth(b.group(1)), null));
                i++;
                continue;
            }
            Matcher o = ORDERED_RE.matcher(line);
            if (o.matches()) {
                flush(out, para);
                int n;
                try {
                    n = Integer.parseInt(o.group(2));
                } catch (NumberFormatException e) {
                    n = 1;
                }
                out.add(new Block(ORDERED, o.group(3), n, String.valueOf(depth(o.group(1)))));
                i++;
                continue;
            }
            if (line.trim().startsWith(">")) {
                flush(out, para);
                StringBuilder q = new StringBuilder();
                while (i < lines.length && lines[i].trim().startsWith(">")) {
                    String t = lines[i].trim().substring(1);
                    if (t.startsWith(" ")) t = t.substring(1);
                    if (q.length() > 0) q.append('\n');
                    q.append(t);
                    i++;
                }
                out.add(new Block(QUOTE, q.toString(), 0, null));
                continue;
            }
            if (line.contains("|") && i + 1 < lines.length && TABLE_SEP.matcher(lines[i + 1]).matches()) {
                flush(out, para);
                StringBuilder t = new StringBuilder(line.trim());
                i += 2;
                while (i < lines.length && lines[i].contains("|") && lines[i].trim().length() > 0) {
                    t.append('\n').append(lines[i].trim());
                    i++;
                }
                out.add(new Block(TABLE, t.toString(), 0, null));
                continue;
            }
            // A line inside a list item that continues it (indented, after a list item).
            if (para.length() == 0 && !out.isEmpty() && line.startsWith("  ")
                    && (last(out).type == BULLET || last(out).type == ORDERED)) {
                Block prev = out.remove(out.size() - 1);
                out.add(new Block(prev.type, prev.text + " " + line.trim(), prev.level, prev.info));
                i++;
                continue;
            }
            if (para.length() > 0) para.append('\n');
            para.append(line.trim());
            i++;
        }
        flush(out, para);
        return out;
    }

    private static Block last(List<Block> l) {
        return l.get(l.size() - 1);
    }

    private static int depth(String indent) {
        int spaces = 0;
        for (char c : indent.toCharArray()) spaces += c == '\t' ? 4 : 1;
        return Math.min(4, spaces / 2);
    }

    private static void flush(List<Block> out, StringBuilder para) {
        if (para.length() > 0) {
            out.add(new Block(PARAGRAPH, para.toString(), 0, null));
            para.setLength(0);
        }
    }

    /** Splits a table row into trimmed cells. */
    public static List<String> cells(String row) {
        String r = row.trim();
        if (r.startsWith("|")) r = r.substring(1);
        if (r.endsWith("|")) r = r.substring(0, r.length() - 1);
        List<String> cells = new ArrayList<String>();
        for (String c : r.split("\\|", -1)) cells.add(c.trim());
        return cells;
    }
}
