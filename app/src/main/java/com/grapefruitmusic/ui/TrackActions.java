package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.DialogInterface;

import com.grapefruitmusic.data.PlaylistMeta;
import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.radio.MyStations;
import com.grapefruitmusic.source.DownloadedSource;
import com.grapefruitmusic.source.JellyfinSource;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Notice;

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

        if (item.kind == MediaItem.TRACK && item.live) {
            stationActions(c, player, item, labels, actions);
        } else if (item.kind == MediaItem.TRACK) {
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
            final MusicSource origin = Sources.get(c).byId(item.sourceId);
            if (origin != null && origin.canEditPlaylists()) {
                labels.add("Add to Playlist\u2026");
                actions.add(new Runnable() {
                    @Override
                    public void run() {
                        PlaylistPicker.show(page, origin, Collections.singletonList(item));
                    }
                });
            }
            final MediaItem album = albumOf(item, listSource);
            if (album != null && !showing(page, album)) {
                labels.add("Go to Album");
                actions.add(new Runnable() {
                    @Override
                    public void run() {
                        page.main().push(AlbumFragment.newInstance(album.sourceId, album, page.backTitle()));
                    }
                });
            }
            final MediaItem artist = artistOf(item, listSource, origin);
            if (artist != null) {
                labels.add("Go to Artist");
                actions.add(new Runnable() {
                    @Override
                    public void run() {
                        page.main().push(BrowseFragment.newInstance(artist.sourceId, BrowseFragment.ARTIST_ALBUMS,
                                artist, page.backTitle()));
                    }
                });
            }
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
                        Notice.show(c, "Downloading “" + item.title + "”");
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
            if (listSource.canEditPlaylists()) {
                labels.add("Add to Playlist\u2026");
                actions.add(withTracks(page, listSource, item, new TracksAction() {
                    @Override
                    public void run(List<MediaItem> tracks) {
                        PlaylistPicker.show(page, listSource, tracks);
                    }
                }));
            }
            if (listSource.canDownload()) {
                labels.add("Download");
                actions.add(withTracks(page, listSource, item, new TracksAction() {
                    @Override
                    public void run(List<MediaItem> tracks) {
                        downloads.enqueue(tracks, item.kind == MediaItem.PLAYLIST ? item : null);
                        Notice.show(c, "Downloading “" + item.title + "”");
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
            if (item.kind == MediaItem.PLAYLIST && listSource.canEditPlaylists()) {
                labels.add("Delete Playlist");
                actions.add(new Runnable() {
                    @Override
                    public void run() {
                        confirmDelete(page, item, listSource);
                    }
                });
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

    /** An internet radio station: queue it, keep it in My Stations or let it go. */
    private static void stationActions(final Context c, final Player player, final MediaItem station,
                                       List<String> labels, List<Runnable> actions) {
        labels.add("Play Next");
        actions.add(new Runnable() {
            @Override
            public void run() {
                player.playNext(station);
            }
        });
        labels.add("Add to Up Next");
        actions.add(new Runnable() {
            @Override
            public void run() {
                player.addToQueue(station);
            }
        });
        final MyStations mine = MyStations.get(c);
        final boolean kept = mine.contains(station);
        labels.add(kept ? "Remove from My Stations" : "Add to My Stations");
        actions.add(new Runnable() {
            @Override
            public void run() {
                if (kept) {
                    mine.remove(station);
                } else {
                    mine.add(station);
                    Notice.show(c, "Added to My Stations");
                }
            }
        });
    }

    /** Delete Playlist: asks first, then deletes it from its source (the songs stay). */
    private static void confirmDelete(final BaseFragment page, final MediaItem playlist, final MusicSource source) {
        final Context c = page.getActivity();
        final Context app = c.getApplicationContext();
        Ui.dialog(c)
                .setTitle("Delete “" + playlist.title + "”?")
                .setMessage(source.isRemote() ? "It's deleted from " + source.name() + " too. The songs in it stay."
                        : "The songs in it stay on this device.")
                .setDestructive(true)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Bg.run(new Bg.Work<Void>() {
                            @Override
                            public Void run() throws Exception {
                                source.deletePlaylist(playlist);
                                PlaylistMeta.get(app).forget(playlist);
                                return null;
                            }
                        }, new Bg.Done<Void>() {
                            @Override
                            public void done(Void result, Exception error) {
                                if (error != null) {
                                    Notice.showLong(app, BaseFragment.describe(error, source));
                                    return;
                                }
                                Notice.show(app, "Deleted “" + playlist.title + "”");
                                if (page.getActivity() != null) page.onPlaylistsChanged();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** The album a track is on, as an item the album page can open (null if unknown). */
    private static MediaItem albumOf(MediaItem t, MusicSource listSource) {
        if (t.albumId == null) return null;
        MediaItem a;
        if (listSource instanceof DownloadedSource) {
            a = new MediaItem(MediaItem.ALBUM, DownloadedSource.ID, t.sourceId + Downloads.SEP + t.albumId, t.album);
        } else {
            a = new MediaItem(MediaItem.ALBUM, t.sourceId, t.albumId, t.album);
        }
        a.artist = t.albumArtist != null ? t.albumArtist : t.artist;
        a.artKey = t.artKey;
        a.year = t.year;
        return a;
    }

    /** The track's artist, as an item the artist page can open (null if unknown). */
    private static MediaItem artistOf(MediaItem t, MusicSource listSource, MusicSource origin) {
        if (listSource instanceof DownloadedSource) {
            String name = t.albumArtist != null ? t.albumArtist : t.artist;
            return name == null ? null : new MediaItem(MediaItem.ARTIST, DownloadedSource.ID, name, name);
        }
        if (t.artistId == null) return null;
        // Jellyfin's artist id is the album artist's; Subsonic's and the phone's are the track artist's.
        String name = origin instanceof JellyfinSource && t.albumArtist != null ? t.albumArtist : t.artist;
        return new MediaItem(MediaItem.ARTIST, t.sourceId, t.artistId, name != null ? name : "Artist");
    }

    /** Is page already the album page for album? */
    private static boolean showing(BaseFragment page, MediaItem album) {
        MediaItem shown = page instanceof AlbumFragment ? page.item() : null;
        return shown != null && shown.id.equals(album.id) && shown.sourceId.equals(album.sourceId);
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
                            Notice.showLong(c, BaseFragment.describe(error, source));
                        } else if (!tracks.isEmpty()) {
                            action.run(tracks);
                        }
                    }
                });
            }
        };
    }
}
