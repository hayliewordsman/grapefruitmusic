package com.grapefruitmusic.podcast;

import com.grapefruitmusic.model.MediaItem;

import java.util.*;

/** Discovery-only policy. Search, subscriptions and playback never pass through this ranking. */
public final class DiscoveryPolicy {
    private DiscoveryPolicy() {}

    public static final String EXPLANATION =
            "Discovery uses the directory's regional popularity list. Editorial demotion is"
                + " reserved for documented promotion of extremist violence, supremacist ideology,"
                + " or targeted dehumanization, applied consistently across viewpoints. Political"
                + " affiliation alone is not a criterion. Search and subscriptions remain"
                + " available. We do not infer your political beliefs from subscriptions. No"
                + " outlet-specific demotions are active in this beta pending a reviewed source"
                + " list.";

    /**
     * Reserved for reviewed rules with evidence; never guess political classifications from titles.
     */
    public static List<MediaItem> rank(List<MediaItem> items) {
        return new ArrayList<MediaItem>(items);
    }
}
