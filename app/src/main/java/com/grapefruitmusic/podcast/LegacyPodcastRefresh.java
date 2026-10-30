package com.grapefruitmusic.podcast;

import android.app.IntentService;
import android.content.Intent;

/** Alarm delivery on Jelly Bean/KitKat; modern Android uses JobScheduler. */
public final class LegacyPodcastRefresh extends IntentService {
    public LegacyPodcastRefresh() {
        super("gf-podcast-refresh");
    }

    @Override
    protected void onHandleIntent(Intent i) {
        PodcastRefresh.refresh(this);
    }
}
