package com.grapefruitmusic.tags;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;

import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.source.DownloadedSource;
import com.grapefruitmusic.source.JellyfinSource;
import com.grapefruitmusic.source.LocalSource;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.util.ArtworkLoader;
import com.grapefruitmusic.util.Bg;

import java.io.File;
import java.io.IOException;

/** Where a song's tags can be changed (its file, its Jellyfin server, or both), and changing them. */
public final class SongTags {
    /** Where an edit goes. */
    public static final class Target {
        /** The song's file on this device, or null. */
        public final File file;
        /** The Jellyfin server that has the song, or null. */
        public final JellyfinSource server;
        /** Where the changes are saved, for the editor to say, or null when it's plain (the song's own file). */
        public final String note;

        Target(File file, JellyfinSource server, String note) {
            this.file = file;
            this.server = server;
            this.note = note;
        }
    }

    private SongTags() {
    }

    /** Where a song's tags can be changed, or null if they can't (Navidrome and Subsonic songs not downloaded). */
    public static Target target(Context c, MediaItem song) {
        if (song == null || song.kind != MediaItem.TRACK || song.live) return null;
        MusicSource origin = Sources.get(c).byId(song.sourceId);
        File file = null;
        if (song.path != null && new File(song.path).exists()) file = new File(song.path);
        else if (!LocalSource.ID.equals(song.sourceId)) file = Downloads.get(c).fileFor(song);
        if (file != null && !TagFile.supports(file.getPath())) file = null;
        if (origin instanceof JellyfinSource) {
            return new Target(file, (JellyfinSource) origin, "Changes are saved on " + origin.name()
                    + (file != null ? ", and in the downloaded copy on this device." : "."));
        }
        if (file == null) return null;
        if (LocalSource.ID.equals(song.sourceId)) return new Target(file, null, null);
        String server = origin != null && !(origin instanceof DownloadedSource) ? origin.name() : "The server";
        return new Target(file, null, "This changes the downloaded copy on this device. " + server
                + " keeps its own copy as it is: Navidrome and Subsonic servers don't let apps change songs.");
    }

    /** The song's tags as they are now. Blocking. */
    public static Tags read(Target t, MediaItem song) throws IOException {
        return t.server != null ? t.server.readTags(song) : TagFile.read(t.file);
    }

    /**
     * Saves the tags (and the picture when picture is true) wherever the song is, then has the
     * device's music library read the file again. Blocking.
     */
    public static void write(final Context c, Target t, MediaItem song, Tags tags, boolean picture) throws IOException {
        if (t.server != null) t.server.writeTags(song, tags, picture);
        if (t.file == null) return;
        TagFile.write(t.file, tags, picture);
        if (!LocalSource.ID.equals(song.sourceId)) {
            Downloads.get(c).tagsChanged(song, tags, picture);
            return;
        }
        final Context app = c.getApplicationContext();
        MediaScannerConnection.scanFile(app, new String[]{t.file.getPath()}, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        Bg.main(new Runnable() {
                            @Override
                            public void run() {
                                Sources.get(app).local().forgetArtwork();
                                Sources.get(app).notifyChanged();
                            }
                        });
                    }
                });
    }

    /** After a save, on the main thread: the queue, lists and artwork show the change. */
    public static void saved(Context c, MediaItem song, Tags tags, boolean picture) {
        SongEdits.record(song, tags);
        if (picture) {
            ArtworkLoader.get(c).forget(song);
            Sources.get(c).local().forgetArtwork();
        }
        Player.get(c).songsEdited();
        if (!LocalSource.ID.equals(song.sourceId)) Sources.get(c).notifyChanged();
    }
}
