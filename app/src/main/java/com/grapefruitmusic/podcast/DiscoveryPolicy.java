package com.grapefruitmusic.podcast;

import com.grapefruitmusic.model.MediaItem;

import java.net.URI;
import java.util.*;

/** Public editorial policy, applied only to discovery; never search or user libraries. */
public final class DiscoveryPolicy {
    private DiscoveryPolicy() {}

    public static final String EXPLANATION =
            "MagicFlute LLC is proud to stand with marginalized communities and stand against hate"
                + " and disinformation. As such, we have made editorial decisions to exclude known"
                + " disinformation sources and podcasts with known hate/violent rhetoric from the"
                + " main Podcasts tab. You can still find it in Search. Please note that the views"
                + " expressed on all podcasts are solely those of the creators and participants,"
                + " and do not necessarily reflect the views of MagicFlute LLC or Grapefruit Music"
                + " developers.";

    // Exact publisher names as identified in directory/RSS records. Never substring-match titles,
    // guests, political keywords, unrelated namesakes or the listener's subscriptions.
    private static final Set<String> PUBLISHERS =
            new HashSet<String>(
                    Arrays.asList(
                            "the daily wire",
                            "turning point usa",
                            "prageru",
                            "prageru kids",
                            "dr. jordan b. peterson"));
    private static final Set<String> DIRECTORY_IDS =
            new HashSet<String>(
                    Arrays.asList(
                            "1184022695",
                            "1045171376",
                            "1047335260",
                            "1266577963",
                            "1367210511",
                            "1438075528",
                            "1451698435",
                            "1460600818",
                            "1471188269",
                            "1478078208",
                            "1495731488",
                            "1507839530",
                            "1517302239",
                            "1526709896",
                            "1536700147",
                            "1553324105",
                            "1556687660",
                            "1558940738",
                            "1571387103",
                            "1576594336",
                            "1587953712",
                            "1608813115",
                            "1636081621",
                            "1652968644",
                            "1677837037",
                            "1679035893",
                            "1756201855",
                            "1792066154",
                            "1838815214",
                            "1839013338",
                            "1847753282",
                            "1847763622",
                            "1877526360",
                            "1881863400",
                            "1882166978",
                            "1882459249",
                            "1884452677",
                            "1884458051",
                            "1885917003",
                            "1889964153",
                            "1896897095",
                            "1896897437",
                            "1896941754",
                            "568456106",
                            "6781069089",
                            "6781860684",
                            "6783434313",
                            "6788838175",
                            "6797609475",
                            "6797618681",
                            "6797666915",
                            "6798009240",
                            "6798016281",
                            "6798022712",
                            "6798029629",
                            "6798031617",
                            "6803226127",
                            "6804063501",
                            "6808753179",
                            "6816252622"));
    private static final Set<String> FEEDS =
            new HashSet<String>(
                    Arrays.asList(
                            "https://feeds.megaphone.fm/BVDWV6444647327",
                            "https://anchor.fm/s/d2a6450/podcast/rss",
                            "https://anchor.fm/s/fabf41b8/podcast/rss",
                            "https://anchor.fm/s/fac97318/podcast/rss",
                            "https://feeds.megaphone.fm/BVDWV1005032762",
                            "https://feeds.megaphone.fm/BVDWV1098100644",
                            "https://feeds.megaphone.fm/BVDWV1761445886",
                            "https://feeds.megaphone.fm/BVDWV1828240207",
                            "https://feeds.megaphone.fm/BVDWV2694088298",
                            "https://feeds.megaphone.fm/BVDWV3145419038",
                            "https://feeds.megaphone.fm/BVDWV4977994734",
                            "https://feeds.megaphone.fm/BVDWV5370667266",
                            "https://feeds.megaphone.fm/BVDWV5728600983",
                            "https://feeds.megaphone.fm/BVDWV5810064903",
                            "https://feeds.megaphone.fm/BVDWV5810187551",
                            "https://feeds.megaphone.fm/BVDWV6036206771",
                            "https://feeds.megaphone.fm/BVDWV6201904241",
                            "https://feeds.megaphone.fm/BVDWV6938041307",
                            "https://feeds.megaphone.fm/BVDWV7570906776",
                            "https://feeds.megaphone.fm/BVDWV7674508874",
                            "https://feeds.megaphone.fm/BVDWV7762869899",
                            "https://feeds.megaphone.fm/BVDWV8747925072",
                            "https://feeds.megaphone.fm/BVDWV9244082040",
                            "https://feeds.megaphone.fm/BVDWV9269809114",
                            "https://feeds.megaphone.fm/BVDWV9288665287",
                            "https://feeds.megaphone.fm/PRAGERU1601016029",
                            "https://feeds.megaphone.fm/PRAGERU1672223514",
                            "https://feeds.megaphone.fm/PRAGERU2559607583",
                            "https://feeds.megaphone.fm/PRAGERU3904590261",
                            "https://feeds.megaphone.fm/PRAGERU3989592970",
                            "https://feeds.megaphone.fm/PRAGERU4454513241",
                            "https://feeds.megaphone.fm/PRAGERU4699775268",
                            "https://feeds.megaphone.fm/PRAGERU5136417772",
                            "https://feeds.megaphone.fm/PRAGERU6153870509",
                            "https://feeds.megaphone.fm/PRAGERU6249945135",
                            "https://feeds.megaphone.fm/PRAGERU6479015328",
                            "https://feeds.megaphone.fm/PRAGERU6513481560",
                            "https://feeds.megaphone.fm/PRAGERU7350664306",
                            "https://feeds.megaphone.fm/PRAGERU7555363792",
                            "https://feeds.megaphone.fm/PRAGERU7966478625",
                            "https://feeds.megaphone.fm/PRAGERU8124517295",
                            "https://feeds.megaphone.fm/PRAGERU8709330303",
                            "https://feeds.megaphone.fm/PRAGERU8905233727",
                            "https://feeds.megaphone.fm/PRAGERU8945256996",
                            "https://feeds.megaphone.fm/PRAGERU9254798325",
                            "https://feeds.megaphone.fm/PRAGERU9869628003",
                            "https://feeds.megaphone.fm/YTYIE3550500328",
                            "https://feeds.megaphone.fm/the-nuclear-reaction",
                            "https://feeds.soundcloud.com/users/soundcloud:users:849176923/sounds.rss",
                            "https://feeds.transistor.fm/america-s-favorite-music-show-prageru-kids",
                            "https://feeds.transistor.fm/around-the-world-prageru-kids",
                            "https://feeds.transistor.fm/cash-course-financial-literacy-prageru-kids",
                            "https://feeds.transistor.fm/craftory-prageru-kids",
                            "https://feeds.transistor.fm/economic-game-changers-prageru-kids",
                            "https://feeds.transistor.fm/leo-laylas-history-adventures",
                            "https://feeds.transistor.fm/ottos-tales-prageru-kids",
                            "https://feeds.transistor.fm/the-hustle-prageru-kids",
                            "https://feeds.transistor.fm/trailblazers-of-america-prageru-kids",
                            "https://redice.tv/rss/radio-3fourteen",
                            "https://redice.tv/rss/red-ice-radio",
                            "https://rss.pdrl.fm/1fc256/feeds.megaphone.fm/BVDWV7762869899",
                            "https://rss.pdrl.fm/324212/feeds.megaphone.fm/BVDWV3145419038",
                            "https://rss.pdrl.fm/34758b/feeds.megaphone.fm/BVDWV5810064903",
                            "https://rss.pdrl.fm/3f8a3d/feeds.megaphone.fm/BVDWV8747925072",
                            "https://rss.pdrl.fm/b32227/feeds.megaphone.fm/BVDWV5370667266",
                            "https://www.omnycontent.com/d/playlist/5e27a451-e6e6-4c51-aa03-a7370003783c/6962edbd-2574-4123-a3c3-a8400005cd73/340f2516-80e7-4444-b858-a8400005cd7d/podcast.rss",
                            "https://www.omnycontent.com/d/playlist/5e27a451-e6e6-4c51-aa03-a7370003783c/c865b590-c84f-4f7e-a43e-ac64014b61d9/8978e846-cacd-4d65-b085-ac64014cd49f/podcast.rss"));

    static String identity(String feed) {
        try {
            URI u = new URI(feed);
            // Fold scheme/host/default ports only; feed paths and query strings are case-sensitive.
            String scheme = u.getScheme().toLowerCase(Locale.US);
            String host = u.getHost().toLowerCase(Locale.US);
            int port = u.getPort();
            if ((port == 443 && scheme.equals("https")) || (port == 80 && scheme.equals("http")))
                port = -1;
            return new URI(scheme, null, host, port, u.getPath(), u.getQuery(), null).toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String label(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.US).replaceAll("\\s+", " ");
    }

    /** Exact metadata fallback, openly documented; no substring or fuzzy name matching. */
    private static boolean excludedTitle(MediaItem show) {
        String title = label(show.title);
        String creator = label(show.artist);
        if (Arrays.asList(
                        "the jordan b. peterson podcast",
                        "the political cesspool",
                        "the political cesspool radio show",
                        "political cesspool",
                        "the daily shoah",
                        "daily shoah",
                        "the david duke show",
                        "david duke show",
                        "the alex jones show",
                        "alex jones show",
                        "the alex jones show - infowars.com",
                        "alex jones - infowars.com",
                        "america first with nick fuentes",
                        "america first - nick fuentes")
                .contains(title)) return true;
        if (title.equals("america first"))
            return Arrays.asList("nick fuentes", "nicholas j. fuentes", "nicholas fuentes")
                    .contains(creator);
        if (title.equals("david duke")) return creator.equals("david duke");
        return false;
    }

    public static boolean excluded(MediaItem show) {
        if (show == null || show.kind != MediaItem.PODCAST) return false;
        String publisher = show.artist == null ? "" : show.artist.trim().toLowerCase(Locale.US);
        return excludedTitle(show)
                || PUBLISHERS.contains(publisher)
                || DIRECTORY_IDS.contains(show.podcastDirectoryId)
                || FEEDS.contains(identity(show.id));
    }

    public static List<MediaItem> rank(List<MediaItem> items) {
        List<MediaItem> out = new ArrayList<MediaItem>();
        for (MediaItem item : items) if (!excluded(item)) out.add(item);
        return out;
    }
}
