package com.grapefruitmusic.podcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class OpmlTest {
    @Test
    public void readsAPlainExport() {
        String file = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<opml version=\"1.0\"><head><title>Pocket Casts Feeds</title></head><body>\n"
                + "<outline text=\"feeds\">\n"
                + "<outline type=\"rss\" text=\"Deadline: White House\" xmlUrl=\"https://feeds.example.com/dwh\" />\n"
                + "<outline type=\"rss\" text=\"The Daily\" xmlUrl=\"https://feeds.example.org/the-daily\"/>\n"
                + "</outline></body></opml>";
        List<Opml.Feed> feeds = Opml.parse(file);
        assertEquals(2, feeds.size());
        assertEquals("Deadline: White House", feeds.get(0).title);
        assertEquals("https://feeds.example.com/dwh", feeds.get(0).url);
        assertEquals("https://feeds.example.org/the-daily", feeds.get(1).url);
    }

    @Test
    public void readsFoldersAndOddFiles() {
        String file = "﻿<opml version='2.0'><body>\n"
                + "<!-- <outline type=\"rss\" text=\"Commented out\" xmlUrl=\"https://gone.example/feed\"/> -->\n"
                + "<outline text='News' title='News'>\n"
                + "  <outline text='Tom &amp; Jerry&#39;s Show &#x2014; Live' XMLURL='https://a.example/feed?x=1&y=2'/>\n"
                + "  <outline title=\"Only a title\" xmlurl=\"http://b.example/rss\" type=\"RSS\"></outline>\n"
                + "</outline>\n"
                + "<outline type=\"rss\" text=\"Uses url\" url=\"https://c.example/podcast.xml\"/>\n"
                + "<outline type=\"link\" text=\"A web page\" url=\"https://d.example/\"/>\n"
                + "<outline text=\"Old schemes\" xmlUrl=\"itpc://e.example/feed\"/>\n"
                + "<outline text=\"\" xmlUrl=\"feed:https://f.example/feed\"/>\n"
                + "<outline text=\"Bad\" xmlUrl=\"javascript:alert(1)\"/>\n"
                + "<outline text=\"Relative\" xmlUrl=\"/feed.xml\"/>\n"
                + "<outline text=\"Again\" xmlUrl=\" https://a.example/feed?x=1&y=2 \"/>\n"
                + "<outline text=\"Unknown &eacute;ntity &amp nope\" xmlUrl=\"https://g.example/f\"/>\n"
                + "</body></opml>";
        List<Opml.Feed> feeds = Opml.parse(file);
        assertEquals(6, feeds.size());
        assertEquals("Tom & Jerry's Show — Live", feeds.get(0).title);
        assertEquals("https://a.example/feed?x=1&y=2", feeds.get(0).url);
        assertEquals("Only a title", feeds.get(1).title);
        assertEquals("http://b.example/rss", feeds.get(1).url);
        assertEquals("https://c.example/podcast.xml", feeds.get(2).url);
        assertEquals("https://e.example/feed", feeds.get(3).url);
        assertEquals("", feeds.get(4).title);
        assertEquals("https://f.example/feed", feeds.get(4).url);
        assertEquals("Unknown &eacute;ntity &amp nope", feeds.get(5).title);
    }

    @Test
    public void nothingUsableIsEmpty() {
        assertTrue(Opml.parse(null).isEmpty());
        assertTrue(Opml.parse("").isEmpty());
        assertTrue(Opml.parse("not xml at all <<< & >>>").isEmpty());
        assertTrue(Opml.parse("<opml><body><outline text=\"folder\"/></body></opml>").isEmpty());
    }

    @Test
    public void addresses() {
        assertEquals("https://x.example/f", Opml.feedUrl("pcast://x.example/f"));
        assertEquals("https://x.example/f", Opml.feedUrl("feed://x.example/f"));
        assertEquals("http://x.example/f", Opml.feedUrl("feed:http://x.example/f"));
        assertNull(Opml.feedUrl("https:///nohost"));
        assertNull(Opml.feedUrl("https://has space.example/f"));
        assertNull(Opml.feedUrl("mailto:someone@example.com"));
        assertNull(Opml.feedUrl(null));
    }

    @Test
    public void writesWhatItReads() {
        List<Opml.Feed> feeds = new ArrayList<Opml.Feed>();
        feeds.add(Opml.feed("Quotes \"and\" <tags> & 'apostrophes'", "https://a.example/feed?x=1&y=2"));
        feeds.add(Opml.feed("Café — 日本", "http://b.example/rss"));
        feeds.add(Opml.feed("", "https://c.example/untitled"));
        String file = Opml.write(feeds, 0L);
        assertTrue(file.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\">"));
        assertTrue(file.contains("<dateCreated>Thu, 01 Jan 1970 00:00:00 GMT</dateCreated>"));
        assertTrue(file.contains("xmlUrl=\"https://a.example/feed?x=1&amp;y=2\""));
        List<Opml.Feed> back = Opml.parse(file);
        assertEquals(3, back.size());
        assertEquals(feeds.get(0).title, back.get(0).title);
        assertEquals(feeds.get(0).url, back.get(0).url);
        assertEquals(feeds.get(1).title, back.get(1).title);
        // An untitled podcast is written with its address as its name.
        assertEquals("https://c.example/untitled", back.get(2).title);
    }
}
