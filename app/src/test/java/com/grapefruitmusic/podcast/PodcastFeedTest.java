package com.grapefruitmusic.podcast;

import static org.junit.Assert.*;

import org.junit.Test;

public class PodcastFeedTest {
    private static final String BASE = "https://example.org/feed.xml";

    @Test
    public void rssReadsEnclosuresAndStableGuid() throws Exception {
        String start =
                "<rss xmlns:itunes='http://www.itunes.com/dtds/podcast-1.0.dtd'><channel><title>A"
                    + " Show</title><itunes:author>Host</itunes:author><itunes:image"
                    + " href='/cover.jpg'/><item><title>Episode</title><guid>ep1</guid><itunes:duration>1:02:03</itunes:duration>";
        PodcastFeed first =
                PodcastFeed.parse(
                        start
                                + "<enclosure"
                                + " url='https://cdn.example.org/first.mp3'/></item></channel></rss>",
                        BASE);
        PodcastFeed second =
                PodcastFeed.parse(
                        start
                                + "<enclosure"
                                + " url='https://cdn.example.org/changed.mp3'/></item></channel></rss>",
                        BASE);
        assertEquals(1, first.episodes.size());
        assertEquals("Host", first.show.artist);
        assertEquals("https://example.org/cover.jpg", first.show.artKey);
        assertEquals(3723000, first.episodes.get(0).durationMs);
        assertEquals(first.episodes.get(0).id, second.episodes.get(0).id);
        assertEquals("A Show", first.episodes.get(0).album);
    }

    @Test
    public void atomAndRelativeEnclosure() throws Exception {
        PodcastFeed f =
                PodcastFeed.parse(
                        "<feed"
                            + " xmlns='http://www.w3.org/2005/Atom'><title>Atom</title><entry><title>Episode</title><id>1</id><link"
                            + " rel='enclosure' href='/ep.mp3'/></entry></feed>",
                        BASE);
        assertEquals("https://example.org/ep.mp3", f.episodes.get(0).streamUrl);
    }

    @Test(expected = java.io.IOException.class)
    public void rejectsExternalEntities() throws Exception {
        PodcastFeed.parse(
                "<!DOCTYPE rss [<!ENTITY x SYSTEM"
                    + " 'file:///etc/passwd'>]><rss><channel><title>&x;</title></channel></rss>",
                BASE);
    }

    @Test
    public void skipsInvalidEnclosures() throws Exception {
        PodcastFeed f =
                PodcastFeed.parse(
                        "<rss><channel><title>A</title><item><enclosure"
                                + " url='file:///etc/passwd'/></item><item><title>No"
                                + " audio</title></item></channel></rss>",
                        BASE);
        assertTrue(f.episodes.isEmpty());
    }

    @Test
    public void durationIsBoundedAndRejectsMalformed() {
        assertEquals(120000, PodcastFeed.duration("120"));
        assertEquals(0, PodcastFeed.duration("bad"));
        assertEquals(0, PodcastFeed.duration("-10"));
        assertEquals(0, PodcastFeed.duration("999999999999999999999999"));
    }

    @Test
    public void redirectKeepsIdentityAndResolvesAgainstFinalFeed() throws Exception {
        String xml =
                "<rss><channel><title>Show</title><item><guid>same</guid><enclosure"
                        + " url='ep.mp3'/></item></channel></rss>";
        PodcastFeed original = PodcastFeed.parse(xml, BASE, BASE);
        PodcastFeed moved = PodcastFeed.parse(xml, BASE, "https://cdn.example.net/audio/feed.xml");
        assertEquals(original.episodes.get(0).id, moved.episodes.get(0).id);
        assertEquals("https://cdn.example.net/audio/ep.mp3", moved.episodes.get(0).streamUrl);
    }

    @Test
    public void webUrlsExcludeCredentialsAndLocalSchemes() {
        assertTrue(PodcastFeed.webUrl(BASE));
        assertFalse(PodcastFeed.webUrl("file:///tmp/foo"));
        assertFalse(PodcastFeed.webUrl("https://name:password@example.org/feed"));
    }

    @Test
    public void streamsPastEightMiBWithBoundedFields() throws Exception {
        StringBuilder xml =
                new StringBuilder(
                        "<rss><channel><title>Large</title><item><title>Episode</title><description>");
        for (int i = 0; i < 9 * 1024 * 1024; i++) xml.append('x');
        xml.append(
                "</description><enclosure"
                        + " url='https://example.org/ep.mp3'/></item></channel></rss>");
        PodcastFeed f =
                PodcastFeed.parse(
                        new java.io.ByteArrayInputStream(xml.toString().getBytes("UTF-8")),
                        BASE,
                        BASE);
        assertEquals(1, f.episodes.size());
        assertTrue(f.episodes.get(0).description.length() <= 4096);
    }

    @Test
    public void stopsReadingAfterBoundedEpisodeWindow() throws Exception {
        StringBuilder xml = new StringBuilder("<rss><channel><title>Archive</title>");
        for (int i = 0; i < 1000; i++)
            xml.append(
                    "<item><guid>"
                            + i
                            + "</guid><enclosure url='https://example.org/ep.mp3'/></item>");
        // Deliberately incomplete tail must never be parsed after the first 1,000 entries.
        xml.append("<broken");
        PodcastFeed f =
                PodcastFeed.parse(
                        new java.io.ByteArrayInputStream(xml.toString().getBytes("UTF-8")),
                        BASE,
                        BASE);
        assertEquals(1000, f.episodes.size());
        assertNotEquals(f.episodes.get(0).id, f.episodes.get(999).id);
    }

    @Test(expected = java.io.IOException.class)
    public void streamRejectsUtf16DocumentType() throws Exception {
        String xml =
                "<?xml version='1.0' encoding='UTF-16'?><!DOCTYPE rss [<!ENTITY x 'expanded'>]>"
                        + "<rss><channel><title>&x;</title></channel></rss>";
        PodcastFeed.parse(new java.io.ByteArrayInputStream(xml.getBytes("UTF-16")), BASE, BASE);
    }

    @Test
    public void streamHonorsDeclaredEncodingAndLateShowMetadata() throws Exception {
        String xml =
                "<?xml version='1.0' encoding='ISO-8859-1'?><rss><channel><item>"
                        + "<enclosure url='https://example.org/ep.mp3'/></item><title>Café</title>"
                        + "<author>Host</author><image><url>/art.jpg</url></image></channel></rss>";
        PodcastFeed f =
                PodcastFeed.parse(
                        new java.io.ByteArrayInputStream(xml.getBytes("ISO-8859-1")), BASE, BASE);
        assertEquals("Café", f.episodes.get(0).album);
        assertEquals("Host", f.episodes.get(0).artist);
        assertEquals("https://example.org/art.jpg", f.episodes.get(0).artKey);
    }

    @Test(expected = java.io.IOException.class)
    public void rejectsExcessiveNesting() throws Exception {
        StringBuilder xml = new StringBuilder("<rss><channel>");
        for (int i = 0; i < 70; i++) xml.append("<nested>");
        PodcastFeed.parse(xml.toString(), BASE);
    }

    @Test
    public void invalidShowArtworkCannotPropagateToEpisodes() throws Exception {
        PodcastFeed f =
                PodcastFeed.parse(
                        "<rss><channel><title>A</title><image>"
                                + "<url>file:///private/art.jpg</url></image><item><enclosure "
                                + "url='https://example.org/ep.mp3'/></item></channel></rss>",
                        BASE);
        assertNull(f.show.artKey);
        assertNull(f.episodes.get(0).artKey);
    }
}
