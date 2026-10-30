package com.grapefruitmusic.podcast;

import android.content.Context;

import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.source.*;

import java.util.List;

/** Podcast playback uses the existing player and relay, without live-radio semantics. */
public final class PodcastSource extends InternetRadioSource {
    public static final String ID = "podcasts";
    private final Context context;

    public PodcastSource(Context c) {
        context = c.getApplicationContext();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Podcasts";
    }

    @Override
    public String typeLabel() {
        return "Podcast feeds";
    }

    @Override
    public List<MediaItem> tracks(MediaItem m) {
        return PodcastStore.get(context).episodes(m.id, false);
    }

    @Override
    public StreamRequest stream(MediaItem m, int quality) {
        return new StreamRequest(m.streamUrl, null, null);
    }
}
