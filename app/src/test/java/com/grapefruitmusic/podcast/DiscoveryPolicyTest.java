package com.grapefruitmusic.podcast;

import static org.junit.Assert.*;

import com.grapefruitmusic.model.MediaItem;

import org.junit.Test;

import java.util.*;

public class DiscoveryPolicyTest {
    private MediaItem show(String feed, String publisher) {
        MediaItem m = new MediaItem(MediaItem.PODCAST, PodcastSource.ID, feed, "Unrelated title");
        m.artist = publisher;
        return m;
    }

    @Test
    public void excludesVerifiedPublisherCatalogsIncludingNewShows() {
        for (String publisher :
                new String[] {"The Daily Wire", "Turning Point USA", "PragerU", "PragerU Kids"}) {
            assertTrue(DiscoveryPolicy.excluded(show("https://example.org/new-feed", publisher)));
        }
    }

    @Test
    public void exactLeadershipFeedsDoNotNeedPublisherName() {
        assertTrue(DiscoveryPolicy.excluded(show("https://anchor.fm/s/d2a6450/podcast/rss", "")));
        assertTrue(
                DiscoveryPolicy.excluded(
                        show("https://feeds.megaphone.fm/YTYIE3550500328", "Boreing Media")));
        assertTrue(DiscoveryPolicy.excluded(show("https://redice.tv/rss/red-ice-radio", "")));
    }

    @Test
    public void directoryIdentitySurvivesFeedMigration() {
        MediaItem m = show("https://new-host.example.org/renamed", "New label");
        m.podcastDirectoryId = "1478078208";
        assertTrue(DiscoveryPolicy.excluded(m));
    }

    @Test
    public void doesNotMatchGuestsNamesakesOrFormerPresentersByTitle() {
        MediaItem m = show("https://example.org/commentary", "Independent publisher");
        m.title = "Interview with Erika Kirk about The Daily Wire and Turning Point USA";
        assertFalse(DiscoveryPolicy.excluded(m));
        assertFalse(
                DiscoveryPolicy.excluded(
                        show("https://example.org/feed", "The Daily Wire fan club")));
        assertFalse(
                DiscoveryPolicy.excluded(
                        show("https://feeds.megaphone.fm/candace", "Candace Owens")));
        assertFalse(DiscoveryPolicy.excluded(show("https://example.org/feed", "Caleb Robinson")));
        assertFalse(
                DiscoveryPolicy.excluded(
                        show(
                                "https://johndaviddukejr.wordpress.com/category/podcast/feed/",
                                "John David Duke Jr")));
    }

    @Test
    public void filteringDoesNotMutateInputOrReorderRemainingShows() {
        MediaItem a = show("https://example.org/a", "A"), b = show("https://example.org/b", "B");
        List<MediaItem> input =
                new ArrayList<MediaItem>(
                        Arrays.asList(
                                a, show("https://example.org/excluded", "The Daily Wire"), b));
        assertEquals(Arrays.asList(a, b), DiscoveryPolicy.rank(input));
        assertEquals(3, input.size()); // Search/subscription callers retain their original objects.
    }

    @Test
    public void canonicalizesOnlySafeUrlParts() {
        assertTrue(
                DiscoveryPolicy.excluded(
                        show("https://REDICE.TV:443/rss/red-ice-radio#anchor", "")));
        assertFalse(DiscoveryPolicy.excluded(show("https://redice.tv/rss/RED-ICE-RADIO", "")));
        assertFalse(
                DiscoveryPolicy.excluded(
                        show("https://redice.tv.evil.example/rss/red-ice-radio", "")));
        assertFalse(
                DiscoveryPolicy.excluded(
                        show("https://redice.tv/rss/red-ice-radio?different=1", "")));
    }

    @Test
    public void titleFallbackIsExactAndProtectsAmbiguousNames() {
        for (String title :
                new String[] {
                    "The Political Cesspool",
                    "The Daily Shoah",
                    "The Alex Jones Show",
                    "The David Duke Show",
                    "America First with Nick Fuentes"
                }) {
            MediaItem m = show("https://unknown.example/feed", "Unknown");
            m.title = title;
            assertTrue(title, DiscoveryPolicy.excluded(m));
            m.title = "Reporting on " + title;
            assertFalse(DiscoveryPolicy.excluded(m));
        }
        MediaItem m = show("https://unknown.example/feed", "An unrelated publisher");
        m.title = "America First";
        assertFalse(DiscoveryPolicy.excluded(m));
        m.artist = "Nick Fuentes";
        assertTrue(DiscoveryPolicy.excluded(m));
        m.title = "America First with an unrelated host";
        assertFalse(DiscoveryPolicy.excluded(m));
    }

    @Test
    public void petersonOfficialShowIsExcludedButIndependentCoverageIsRetained() {
        assertTrue(
                DiscoveryPolicy.excluded(show("https://feeds.megaphone.fm/BVDWV6444647327", "")));
        MediaItem m = show("https://example.org/feed", "Independent publisher");
        m.title = "Discussing The Jordan B. Peterson Podcast";
        assertFalse(DiscoveryPolicy.excluded(m));
        m.title = "Port of Dorks Cast";
        m.artist = "Jordan Peterson";
        assertFalse(DiscoveryPolicy.excluded(m));
    }

    @Test
    public void roganIdentityIsExcludedWithoutFilteringIndependentCoverage() {
        assertTrue(DiscoveryPolicy.excluded(show("https://feeds.megaphone.fm/GLT1412515089", "")));
        MediaItem m = show("https://new.example/feed", "New label");
        m.podcastDirectoryId = "360084272";
        assertTrue(DiscoveryPolicy.excluded(m));
        m.podcastDirectoryId = null;
        m.title = "The Joe Rogan Experience";
        assertTrue(DiscoveryPolicy.excluded(m));
        for (String title :
                new String[] {
                    "Joe Rogan Experience Review podcast",
                    "The Joe Rogan AI Experience",
                    "Reporting on The Joe Rogan Experience"
                }) {
            m.title = title;
            assertFalse(title, DiscoveryPolicy.excluded(m));
        }
        assertFalse(
                DiscoveryPolicy.excluded(show("https://independent.example/feed", "Joe Rogan")));
    }
}
