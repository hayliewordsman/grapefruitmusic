package com.grapefruitmusic.podcast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OPML, the file podcast apps trade subscriptions in: a list of outlines, each a podcast's name and
 * the address of its RSS feed. Written as OPML 2.0; read forgivingly, because files from other apps
 * are often not quite XML (a bare "&" in an address, a missing type, outlines nested in folders,
 * odd capitals), and a strict parser would refuse the whole file over one of them.
 */
public final class Opml {
    /** One podcast in a file: its name (may be empty) and its feed address. */
    public static final class Feed {
        public final String title;
        public final String url;

        Feed(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern OUTLINE = Pattern.compile("<outline\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE =
            Pattern.compile("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);");

    private Opml() {
    }

    /**
     * The podcasts in a file, each feed once, in the file's order. Outlines without a feed address
     * (folders, links to web pages) are left out; so are addresses that aren't the web's.
     */
    public static List<Feed> parse(String text) {
        Map<String, Feed> feeds = new LinkedHashMap<String, Feed>();
        if (text == null) return new ArrayList<Feed>();
        String body = COMMENT.matcher(text).replaceAll(" ");
        Matcher outline = OUTLINE.matcher(body);
        while (outline.find()) {
            Map<String, String> attributes = attributes(outline.group(1));
            String url = attributes.get("xmlurl");
            // Some apps put the feed in url (with type="rss"), not xmlUrl.
            if (url == null && "rss".equalsIgnoreCase(attributes.get("type"))) url = attributes.get("url");
            url = feedUrl(url);
            if (url == null || feeds.containsKey(url)) continue;
            String title = attributes.get("text");
            if (title == null || title.trim().length() == 0) title = attributes.get("title");
            feeds.put(url, new Feed(title == null ? "" : title.trim().replaceAll("\\s+", " "), url));
        }
        return new ArrayList<Feed>(feeds.values());
    }

    /** An outline's attributes, names in lower case, values decoded. */
    private static Map<String, String> attributes(String tag) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        Matcher m = ATTRIBUTE.matcher(tag);
        while (m.find()) {
            String value = m.group(2) != null ? m.group(2) : m.group(3);
            String name = m.group(1).toLowerCase(Locale.US);
            if (!out.containsKey(name)) out.put(name, decode(value));
        }
        return out;
    }

    /**
     * A feed address as subscribed to: trimmed, with the old feed://, itpc:// and pcast:// prefixes
     * (from iTunes days) turned into https, or null if it isn't a web address.
     */
    static String feedUrl(String raw) {
        if (raw == null) return null;
        String url = raw.trim();
        String lower = url.toLowerCase(Locale.US);
        if (lower.startsWith("feed:http://") || lower.startsWith("feed:https://")) {
            url = url.substring(5);
        } else {
            for (String scheme : new String[]{"feed://", "itpc://", "pcast://"}) {
                if (lower.startsWith(scheme)) {
                    url = "https://" + url.substring(scheme.length());
                    break;
                }
            }
        }
        lower = url.toLowerCase(Locale.US);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) return null;
        int host = url.indexOf("://") + 3;
        if (host >= url.length() || url.charAt(host) == '/' || url.indexOf(' ') >= 0) return null;
        return url;
    }

    /** XML's entities, numbers, and a few HTML ones titles carry; anything else, and a bare &, as it is. */
    static String decode(String s) {
        if (s == null || s.indexOf('&') < 0) return s;
        Matcher m = ENTITY.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            String value = null;
            if (name.startsWith("#x") || name.startsWith("#X")) {
                value = codePoint(name.substring(2), 16);
            } else if (name.startsWith("#")) {
                value = codePoint(name.substring(1), 10);
            } else {
                value = named(name);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String codePoint(String digits, int radix) {
        try {
            int cp = Integer.parseInt(digits, radix);
            if (cp <= 0 || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) return null;
            return new String(Character.toChars(cp));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String named(String name) {
        switch (name) {
            case "amp": return "&";
            case "lt": return "<";
            case "gt": return ">";
            case "quot": return "\"";
            case "apos": return "'";
            case "nbsp": return "\u00a0";
            case "ndash": return "\u2013";
            case "mdash": return "\u2014";
            case "lsquo": return "\u2018";
            case "rsquo": return "\u2019";
            case "ldquo": return "\u201c";
            case "rdquo": return "\u201d";
            case "hellip": return "\u2026";
            default: return null;
        }
    }

    /** An OPML 2.0 file of these podcasts (title and feed address each), dated now. */
    public static String write(List<Feed> feeds, long now) {
        SimpleDateFormat rfc822 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        rfc822.setTimeZone(TimeZone.getTimeZone("GMT"));
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        b.append("<opml version=\"2.0\">\n");
        b.append("  <head>\n");
        b.append("    <title>Grapefruit Audio podcast subscriptions</title>\n");
        b.append("    <dateCreated>").append(rfc822.format(new Date(now))).append("</dateCreated>\n");
        b.append("  </head>\n");
        b.append("  <body>\n");
        for (Feed f : feeds) {
            String title = escape(f.title == null || f.title.length() == 0 ? f.url : f.title);
            b.append("    <outline type=\"rss\" text=\"").append(title).append("\" title=\"").append(title)
                    .append("\" xmlUrl=\"").append(escape(f.url)).append("\" />\n");
        }
        b.append("  </body>\n");
        b.append("</opml>\n");
        return b.toString();
    }

    public static Feed feed(String title, String url) {
        return new Feed(title, url);
    }

    /** Safe in a double-quoted XML attribute; control characters XML can't hold are dropped. */
    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&apos;"); break;
                case '\n': case '\r': case '\t': b.append(' '); break;
                default:
                    if (ch >= 0x20 || ch == '\u0009') b.append(ch);
            }
        }
        return b.toString();
    }
}
