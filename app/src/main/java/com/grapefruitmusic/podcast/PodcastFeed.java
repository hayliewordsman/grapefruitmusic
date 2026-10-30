package com.grapefruitmusic.podcast;

import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.util.Hashes;

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
        return read(new InputSource(new StringReader(xml)), url, baseUrl);
    }

    /** Stream large archives without retaining the XML document. Honors the XML encoding. */
    public static PodcastFeed parse(InputStream stream, String url, String baseUrl)
            throws IOException {
        InputStream bounded =
                new FilterInputStream(stream) {
                    private long remaining = 64L * 1024 * 1024;

                    @Override
                    public int read() throws IOException {
                        int b = super.read();
                        if (b != -1 && --remaining < 0)
                            throw new IOException("Feed exceeds safety budget");
                        return b;
                    }

                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        int n = in.read(b, off, (int) Math.min(len, remaining + 1));
                        if (n > 0 && (remaining -= n) < 0)
                            throw new IOException("Feed exceeds safety budget");
                        return n;
                    }
                };
        return read(new InputSource(bounded), url, baseUrl);
    }

    private static final class Enough extends org.xml.sax.SAXException {}

    private static PodcastFeed read(InputSource input, String url, String baseUrl)
            throws IOException {
        if (!webUrl(url) || !webUrl(baseUrl)) throw new IOException("Invalid podcast address");
        final PodcastFeed result =
                new PodcastFeed(new MediaItem(MediaItem.PODCAST, PodcastSource.ID, url, ""));
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            org.xml.sax.XMLReader reader = factory.newSAXParser().getXMLReader();
            // The lexical handler rejects DTDs on both Android and the JVM. The resolver also
            // rejects every external entity; no network/file access is permitted by the parser.
            org.xml.sax.ext.DefaultHandler2 handler =
                    new org.xml.sax.ext.DefaultHandler2() {
                        int depth, channelDepth, episodeDepth, entries;
                        boolean channelFound;
                        MediaItem episode;
                        String guid, enclosure;
                        final List<String> names = new ArrayList<String>();
                        final List<StringBuilder> values = new ArrayList<StringBuilder>();

                        @Override
                        public void startDTD(String name, String publicId, String systemId)
                                throws org.xml.sax.SAXException {
                            throw new org.xml.sax.SAXException("Document types are not allowed");
                        }

                        @Override
                        public InputSource resolveEntity(String publicId, String systemId)
                                throws org.xml.sax.SAXException {
                            throw new org.xml.sax.SAXException("External entities are not allowed");
                        }

                        @Override
                        public void startElement(
                                String uri,
                                String local,
                                String qualified,
                                org.xml.sax.Attributes a)
                                throws org.xml.sax.SAXException {
                            String name = local.length() == 0 ? qualified : local;
                            if (++depth > 64)
                                throw new org.xml.sax.SAXException("Feed nesting is too deep");
                            names.add(name);
                            values.add(new StringBuilder());
                            if ((depth == 2 && "channel".equals(name) && "rss".equals(names.get(0)))
                                    || (depth == 1 && "feed".equals(name))) {
                                channelDepth = depth;
                                channelFound = true;
                            }
                            if (channelDepth == 0) return;
                            if (depth == channelDepth + 1
                                    && ("item".equals(name) || "entry".equals(name))) {
                                episodeDepth = depth;
                                episode = new MediaItem(MediaItem.TRACK, PodcastSource.ID, "", "");
                                guid = enclosure = "";
                            }
                            MediaItem target = episode == null ? result.show : episode;
                            int parent = episode == null ? channelDepth : episodeDepth;
                            if (depth == parent + 1) {
                                if ("image".equals(name) && a.getValue("href") != null)
                                    target.artKey = resolve(baseUrl, a.getValue("href"));
                                if (episode != null && "enclosure".equals(name))
                                    enclosure = a.getValue("url");
                                if (episode != null
                                        && "link".equals(name)
                                        && "enclosure".equals(a.getValue("rel")))
                                    enclosure = a.getValue("href");
                            }
                        }

                        @Override
                        public void characters(char[] ch, int start, int length) {
                            // Each field is bounded, including ignored extension elements and
                            // descriptions.
                            if (!values.isEmpty()) {
                                StringBuilder b = values.get(values.size() - 1);
                                b.append(
                                        ch,
                                        start,
                                        Math.min(length, Math.max(0, 4096 - b.length())));
                            }
                        }

                        @Override
                        public void endElement(String uri, String local, String qualified)
                                throws org.xml.sax.SAXException {
                            String name = names.remove(names.size() - 1);
                            String text = values.remove(values.size() - 1).toString().trim();
                            if (channelDepth > 0) {
                                MediaItem target = episode == null ? result.show : episode;
                                int parent = episode == null ? channelDepth : episodeDepth;
                                if (depth == parent + 1) {
                                    if ("title".equals(name)) target.title = text;
                                    if ("author".equals(name)) target.artist = text;
                                    if ("description".equals(name) || "summary".equals(name))
                                        target.description = text;
                                    if (episode != null) {
                                        if ("guid".equals(name)
                                                || ("id".equals(name) && guid.length() == 0))
                                            guid = text;
                                        if ("duration".equals(name))
                                            target.durationMs = duration(text);
                                        if ("pubDate".equals(name) || "published".equals(name))
                                            target.added = date(text);
                                    }
                                }
                                if (depth == parent + 2
                                        && "image".equals(names.get(names.size() - 1))
                                        && "url".equals(name))
                                    target.artKey = resolve(baseUrl, text);
                                if (episode != null && depth == episodeDepth) {
                                    String audio = resolve(baseUrl, enclosure);
                                    if (webUrl(audio)) {
                                        episode.id =
                                                Hashes.sha1(
                                                        url
                                                                + "\n"
                                                                + (guid.length() == 0
                                                                        ? audio
                                                                        : guid));
                                        episode.streamUrl = audio;
                                        episode.albumId = url;
                                        result.episodes.add(episode);
                                    }
                                    episode = null;
                                    // Count all entries so a feed of invalid entries cannot force
                                    // unlimited work.
                                    if (++entries >= 1000) throw new Enough();
                                }
                                if (depth == channelDepth) channelDepth = 0;
                            }
                            // Preserve mixed text / Atom author-name text, still bounded per field.
                            if (!values.isEmpty()) {
                                StringBuilder b = values.get(values.size() - 1);
                                b.append(
                                        text,
                                        0,
                                        Math.min(text.length(), Math.max(0, 4096 - b.length())));
                            }
                            depth--;
                        }

                        @Override
                        public void endDocument() throws org.xml.sax.SAXException {
                            if (!channelFound)
                                throw new org.xml.sax.SAXException("Not an RSS or Atom feed");
                        }
                    };
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);
            reader.setEntityResolver(handler);
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
            try {
                reader.parse(input);
            } catch (Enough complete) {
                // Intentional early stop; only the bounded initial episode window is ingested.
            }
            for (MediaItem episode : result.episodes) {
                episode.album = result.show.title;
                if (episode.artist == null || episode.artist.length() == 0)
                    episode.artist = result.show.artist;
                if (!webUrl(episode.artKey)) episode.artKey = result.show.artKey;
            }
            if (!webUrl(result.show.artKey)) result.show.artKey = null;
            return result;
        } catch (Exception e) {
            throw new IOException("Couldn't read podcast feed", e);
        }
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
