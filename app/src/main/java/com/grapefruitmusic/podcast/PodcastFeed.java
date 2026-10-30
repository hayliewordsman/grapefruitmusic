package com.grapefruitmusic.podcast;

import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.util.Hashes;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

import javax.xml.parsers.*;

/** Bounded RSS/Atom ingestion. Feeds cannot resolve document types or external entities. */
public final class PodcastFeed {
    public final MediaItem show;
    public final List<MediaItem> episodes = new ArrayList<MediaItem>();

    private PodcastFeed(MediaItem show) {
        this.show = show;
    }

    public static boolean webUrl(String s) {
        if (s == null) return false;
        try {
            URI u = new URI(s);
            return ("https".equalsIgnoreCase(u.getScheme())
                            || "http".equalsIgnoreCase(u.getScheme()))
                    && u.getHost() != null
                    && u.getUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    public static PodcastFeed parse(String xml, String url) throws IOException {
        return parse(xml, url, url);
    }

    public static PodcastFeed parse(String xml, String url, String baseUrl) throws IOException {
        if (!webUrl(url)
                || xml.length() > 8 * 1024 * 1024
                || xml.toUpperCase(Locale.US).contains("<!DOCTYPE"))
            throw new IOException("Invalid or oversized podcast feed");
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            Element root = b.parse(new InputSource(new StringReader(xml))).getDocumentElement();
            Element channel = child(root, "channel");
            if (channel == null && "feed".equals(local(root))) channel = root;
            if (channel == null)
                throw new IOException("This address is not an RSS or Atom podcast feed");
            MediaItem show =
                    new MediaItem(MediaItem.PODCAST, PodcastSource.ID, url, text(channel, "title"));
            show.artist = text(channel, "author");
            show.artKey = art(channel, baseUrl);
            show.description = text(channel, "description");
            PodcastFeed result = new PodcastFeed(show);
            for (Node n = channel.getFirstChild();
                    n != null && result.episodes.size() < 1000;
                    n = n.getNextSibling()) {
                if (!(n instanceof Element)
                        || !("item".equals(local(n)) || "entry".equals(local(n)))) continue;
                Element e = (Element) n;
                String enclosure = null;
                for (Node k = e.getFirstChild(); k != null; k = k.getNextSibling()) {
                    if (!(k instanceof Element)) continue;
                    Element a = (Element) k;
                    if ("enclosure".equals(local(a))) enclosure = a.getAttribute("url");
                    if ("link".equals(local(a)) && "enclosure".equals(a.getAttribute("rel")))
                        enclosure = a.getAttribute("href");
                }
                if (enclosure == null) continue;
                enclosure = resolve(baseUrl, enclosure);
                if (!webUrl(enclosure)) continue;
                String guid = text(e, "guid");
                if (guid.length() == 0) guid = text(e, "id");
                if (guid.length() == 0) guid = enclosure;
                MediaItem m =
                        new MediaItem(
                                MediaItem.TRACK,
                                PodcastSource.ID,
                                Hashes.sha1(url + "\n" + guid),
                                text(e, "title"));
                m.streamUrl = enclosure;
                m.albumId = url;
                m.album = show.title;
                m.artist = show.artist;
                m.artKey = art(e, baseUrl);
                if (m.artKey == null) m.artKey = show.artKey;
                m.description = text(e, "description");
                m.durationMs = duration(text(e, "duration"));
                m.added = date(text(e, "pubDate"));
                if (m.added == 0) m.added = date(text(e, "published"));
                result.episodes.add(m);
            }
            return result;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Couldn't read podcast feed", e);
        }
    }

    private static String local(Node n) {
        return n.getLocalName() != null ? n.getLocalName() : n.getNodeName();
    }

    private static Element child(Element e, String name) {
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element && name.equals(local(n))) return (Element) n;
        return null;
    }

    private static String text(Element e, String name) {
        Element n = child(e, name);
        return n == null ? "" : n.getTextContent().trim();
    }

    private static String art(Element e, String base) {
        Element n = child(e, "image");
        if (n == null) return null;
        String s = n.getAttribute("href");
        if (s.length() == 0) s = text(n, "url");
        s = resolve(base, s);
        return webUrl(s) ? s : null;
    }

    private static String resolve(String base, String value) {
        if (value == null || value.trim().length() == 0) return "";
        try {
            return new URI(base).resolve(value.trim()).toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static long duration(String value) {
        try {
            long seconds = 0;
            for (String s : value.split(":")) {
                long part = Long.parseLong(s);
                if (part < 0
                        || part > Integer.MAX_VALUE / 1000
                        || seconds > Integer.MAX_VALUE / 60000) return 0;
                seconds = seconds * 60 + part;
            }
            return seconds >= 0 && seconds < Integer.MAX_VALUE / 1000 ? seconds * 1000 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static long date(String s) {
        for (String format :
                new String[] {
                    "EEE, dd MMM yyyy HH:mm:ss Z",
                    "dd MMM yyyy HH:mm:ss Z",
                    "yyyy-MM-dd'T'HH:mm:ssXXX",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'"
                }) {
            try {
                SimpleDateFormat d = new SimpleDateFormat(format, Locale.US);
                d.setTimeZone(TimeZone.getTimeZone("UTC"));
                return d.parse(s).getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }
}
