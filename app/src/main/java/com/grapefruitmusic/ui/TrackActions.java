package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.source.DownloadedSource;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.util.Bg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Long-press menus for songs, albums and playlists. */
final class TrackActions {
    private TrackActions() {
    }

    static void show(final BaseFragment page, final MediaItem item, final MusicSource listSource) {
        final Context c = page.getActivity();
        final Player player = Player.get(c);
        final Downloads downloads = Downloads.get(c);
        final List<String> labels = new ArrayList<String>();
        final List<Runnable> actions = new ArrayList<Runnable>();

        if (item.kind == MediaItem.TRACK) {
            labels.add("Play Next");
            actions.add(new Runnable() {
                @Override
                public void run() {
                    player.playNext(item);
                }
            });
            labels.add("Add to Up Next");
            actions.add(new Runnable() {
                @Override
                public void run() {
                    player.addToQueue(item);
                }
            });
            MusicSource origin = Sources.get(c).byId(item.sourceId);
            Map<String, Integer> states = downloads.states(item.sourceId);
            Integer state = states.get(item.id);
            if (state != null && state != Downloads.FAILED) {
                labels.add(state == Downloads.DONE ? "Remove Download" : "Cancel Download");
                actions.add(new Runnable() {
                    @Override
                    public void run() {
                        downloads.remove(item);
                    }
                });
            } else if (origin != null && origin.canDownload()) {
                labels.add("Download");
                actions.add(new Runnable() {
                    @Override
                    public void run() {
                        downloads.enqueue(Collections.singletonList(item), null);
                        Toast.makeText(c, "Downloading “" + item.title + "”", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } else if (item.kind == MediaItem.ALBUM || item.kind == MediaItem.PLAYLIST) {
            labels.add("Play");
            actions.add(withTracks(page, listSource, item, new TracksAction() {
                @Override
                public void run(List<MediaItem> tracks) {
                    player.play(tracks, 0, false);
                }
            }));
            labels.add("Shuffle");
            actions.add(withTracks(page, listSource, item, new TracksAction() {
                @Override
                public void run(List<MediaItem> tracks) {
                    player.play(tracks, -1, true);
                }
            }));
            if (listSource.canDownload()) {
                labels.add("Download");
                actions.add(withTracks(page, listSource, item, new TracksAction() {
                    @Override
                    public void run(List<MediaItem> tracks) {
                        downloads.enqueue(tracks, item.kind == MediaItem.PLAYLIST ? item : null);
                        Toast.makeText(c, "Downloading “" + item.title + "”", Toast.LENGTH_SHORT).show();
                    }
                }));
            }
            if (listSource instanceof DownloadedSource) {
                labels.add("Remove Download");
                actions.add(withTracks(page, listSource, item, new TracksAction() {
                    @Override
                    public void run(List<MediaItem> tracks) {
                        removeDownloadedContainer(c, item, tracks);
                    }
                }));
            }
        } else {
            return;
        }

        Ui.dialog(c)
                .setTitle(item.title)
                .setItems(labels.toArray(new String[labels.size()]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        actions.get(which).run();
                    }
                })
                .show();
    }

    /** Deletes a downloaded album or playlist (container from DownloadedSource). */
    static void removeDownloadedContainer(Context c, MediaItem container, List<MediaItem> tracks) {
        Downloads downloads = Downloads.get(c);
        String[] parts = container.id.split(Downloads.SEP, 2);
        if (container.kind == MediaItem.PLAYLIST && parts.length == 2) {
            downloads.removePlaylist(parts[0], parts[1]);
        }
        for (MediaItem t : tracks) downloads.remove(t);
    }

    interface TracksAction {
        void run(List<MediaItem> tracks);
    }

    private static Runnable withTracks(final BaseFragment page, final MusicSource source, final MediaItem container,
                                       final TracksAction action) {
        return new Runnable() {
            @Override
            public void run() {
                final Context c = page.getActivity().getApplicationContext();
                Bg.run(new Bg.Work<List<MediaItem>>() {
                    @Override
                    public List<MediaItem> run() throws Exception {
                        return source.tracks(container);
                    }
                }, new Bg.Done<List<MediaItem>>() {
                    @Override
                    public void done(List<MediaItem> tracks, Exception error) {
                        if (error != null) {
                            Toast.makeText(c, BaseFragment.describe(error, source), Toast.LENGTH_LONG).show();
                        } else if (!tracks.isEmpty()) {
                            action.run(tracks);
                        }
                    }
                });
            }
        };
    }
}
