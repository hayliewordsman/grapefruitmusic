package com.grapefruitmusic.source;

import com.grapefruitmusic.lyrics.Lyrics;
import com.grapefruitmusic.model.MediaItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Internet radio stations, so they play through the music player like songs. Not a library: it's
 * not in the Library tab's list; the Radio tab finds the stations.
 */
public class InternetRadioSource implements MusicSource {
    public static final String ID = "internet-radio";
    /** The artwork key of a station without a logo: generated from its name. */
    public static final String NAMED_ART = "station:";

    /** A station, as a live track. */
    public static MediaItem station(String id, String name, String url, String logo, String about) {
        MediaItem m = new MediaItem(MediaItem.TRACK, ID, id, name);
        m.live = true;
        m.streamUrl = url;
        m.artist = about;
        m.artKey = logo != null && (logo.startsWith("http://") || logo.startsWith("https://")) ? logo : NAMED_ART + name;
        return m;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Internet Radio";
    }

    @Override
    public String typeLabel() {
        return "Radio Browser";
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    @Override
    public boolean canDownload() {
        return false;
    }

    @Override
    public List<MediaItem> playlists() {
        return new ArrayList<MediaItem>();
    }

    @Override
    public List<MediaItem> artists() {
        return new ArrayList<MediaItem>();
    }

    @Override
    public List<MediaItem> albums() {
        return new ArrayList<MediaItem>();
    }

    @Override
    public List<MediaItem> songs() {
        return new ArrayList<MediaItem>();
    }

    @Override
    public List<MediaItem> recentAlbums(int limit) {
        return new ArrayList<MediaItem>();
    }

    @Override
    public List<MediaItem> artistAlbums(MediaItem artist) {
        return new ArrayList<MediaItem>();
    }

    @Override
    public List<MediaItem> tracks(MediaItem container) {
        return new ArrayList<MediaItem>();
    }

    @Override
    public List<MediaItem> search(String query) {
        return new ArrayList<MediaItem>();
    }

    /** The station's logo (a station without one gets a picture made from its name instead). */
    @Override
    public String artworkUrl(String artKey, int sizePx) {
        return artKey != null && artKey.startsWith("http") ? artKey : null;
    }

    @Override
    public StreamRequest stream(MediaItem track, int quality) {
        return track.streamUrl == null ? null : new StreamRequest(track.streamUrl, null, null);
    }

    @Override
    public StreamRequest download(MediaItem track, int quality) {
        return null;
    }

    @Override
    public Lyrics lyrics(MediaItem track) {
        return null;
    }

    @Override
    public boolean canEditPlaylists() {
        return false;
    }

    @Override
    public void addToPlaylist(MediaItem playlist, List<MediaItem> tracks) throws IOException {
        throw new IOException("Radio stations can't go in playlists");
    }

    @Override
    public void createPlaylist(String name, List<MediaItem> tracks) throws IOException {
        throw new IOException("Radio stations can't go in playlists");
    }

    @Override
    public void deletePlaylist(MediaItem playlist) throws IOException {
        throw new IOException("Internet radio has no playlists");
    }

    @Override
    public boolean editPlaylist(MediaItem playlist, String name, String description, List<MediaItem> removed)
            throws IOException {
        throw new IOException("Internet radio has no playlists");
    }
}
