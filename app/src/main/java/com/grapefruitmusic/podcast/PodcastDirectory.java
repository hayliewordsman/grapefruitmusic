package com.grapefruitmusic.podcast;

import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;

import org.json.*;

import java.io.IOException;
import java.util.*;

/**
 * Public directory discovery; no API secrets are embedded in the app. RSS is still playable
 * directly.
 */
public final class PodcastDirectory {
    private PodcastDirectory() {}

    public static List<MediaItem> search(String query) throws IOException {
        JSONObject body =
                Http.getJson(
                        "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=40&term="
                                + Http.enc(query),
                        null);
        return results(body.optJSONArray("results"));
    }

    public static List<MediaItem> discover() throws IOException {
        String country = Locale.getDefault().getCountry().toLowerCase(Locale.US);
        if (country.length() != 2) country = "us";
        JSONObject body =
                Http.getJson(
                        "https://rss.applemarketingtools.com/api/v2/"
                                + country
                                + "/podcasts/top/25/podcasts.json",
                        null);
        JSONArray charts =
                body.optJSONObject("feed") != null
                        ? body.optJSONObject("feed").optJSONArray("results")
                        : null;
        if (charts == null || charts.length() == 0) return DiscoveryPolicy.rank(search("podcast"));
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < charts.length(); i++) {
            String id = charts.optJSONObject(i).optString("id");
            if (id.matches("[0-9]+")) {
                if (ids.length() > 0) ids.append(',');
                ids.append(id);
            }
        }
        if (ids.length() == 0) return DiscoveryPolicy.rank(search("podcast"));
        return DiscoveryPolicy.rank(
                results(
                        Http.getJson(
                                        "https://itunes.apple.com/lookup?entity=podcast&id=" + ids,
                                        null)
                                .optJSONArray("results")));
    }

    private static List<MediaItem> results(JSONArray rows) {
        List<MediaItem> out = new ArrayList<MediaItem>();
        Set<String> seen = new HashSet<String>();
        if (rows == null) return out;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i);
            if (o == null) continue;
            String feed = o.optString("feedUrl");
            if (!PodcastFeed.webUrl(feed) || !seen.add(feed)) continue;
            MediaItem m =
                    new MediaItem(
                            MediaItem.PODCAST,
                            PodcastSource.ID,
                            feed,
                            o.optString("collectionName", o.optString("trackName")));
            m.artist = o.optString("artistName");
            m.podcastDirectoryId = o.optString("collectionId", o.optString("trackId"));
            m.artKey = o.optString("artworkUrl600", o.optString("artworkUrl100"));
            out.add(m);
        }
        return out;
    }
}
