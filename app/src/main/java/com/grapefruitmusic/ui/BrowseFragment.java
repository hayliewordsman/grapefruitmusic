package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.data.PlaylistMeta;
import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.ui.widget.FastScrollListView;
import com.grapefruitmusic.ui.widget.HorizontalGrid;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Notice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Playlists, Artists, Albums, Songs, or one artist's albums. */
public final class BrowseFragment extends BaseFragment implements Downloads.Listener, Player.Listener {
    static final int PLAYLISTS = 1;
    static final int ARTISTS = 2;
    static final int ALBUMS = 3;
    static final int SONGS = 4;
    static final int ARTIST_ALBUMS = 5;

    private ListView list;
    /** Landscape: the sideways grid instead of list (pages of cards only). */
    private HorizontalGrid grid;
    private RowAdapter adapter;
    private TextView status;
    /** Loaded items, kept while a page on top of this one is open. */
    private List<MediaItem> items;
    /** PlaylistMeta.version() when items were loaded. */
    private int loadedMeta;

    static BrowseFragment newInstance(String sourceId, int mode, MediaItem artist, String back) {
        BrowseFragment f = new BrowseFragment();
        Bundle b = args(sourceId, artist, back);
        b.putInt(ARG_MODE, mode);
        f.setArguments(b);
        return f;
    }

    private int mode() {
        return getArguments().getInt(ARG_MODE);
    }

    /**
     * Songs (small artwork) and playlists (medium artwork) are lists the same way up or sideways;
     * the other pages are cards.
     */
    private boolean listed() {
        return mode() == SONGS || mode() == PLAYLISTS;
    }

    /** Items in a ListView row: one in a list, two cards in a grid. */
    private int perRow() {
        return listed() ? 1 : 2;
    }

    private String title() {
        switch (mode()) {
            case PLAYLISTS: return "Playlists";
            case ARTISTS: return "Artists";
            case ALBUMS: return "Albums";
            case SONGS: return "Songs";
            default: return item() != null ? item().title : "Albums";
        }
    }

    @Override
    String backTitle() {
        return title();
    }

    @Override
    boolean rebuildsOnRotate() {
        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        Context c = getActivity();
        boolean sideways = RowAdapter.landscape(c) && !listed();
        LinearLayout header = Ui.header(c, backLabel(), title(), goBack());
        if (backLabel() == null) {
            // Used as the Playlists tab: say which source the playlists come from.
            TextView from = Ui.text(c, 15, R.attr.gfTextSecondary, false);
            from.setText(source().name());
            header.addView(from);
        }
        // Under the title: Shuffle All for songs, New Playlist for playlists that can be made.
        LinearLayout shuffle = null;
        if (mode() == SONGS) {
            shuffle = Ui.pill(c, Icons.SHUFFLE, "Shuffle All");
            shuffle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!adapter.tracks().isEmpty()) Player.get(getActivity()).play(adapter.tracks(), -1, true);
                }
            });
        } else if (mode() == PLAYLISTS && source().canEditPlaylists()) {
            shuffle = Ui.pill(c, Icons.PLUS, "New Playlist");
            shuffle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    askNewPlaylist();
                }
            });
        }
        status = Ui.status(c);
        adapter = new RowAdapter(c, new RowAdapter.Listener() {
            @Override
            public void onItem(MediaItem item, int index) {
                open(item, index);
            }

            @Override
            public void onLongItem(MediaItem item) {
                TrackActions.show(BrowseFragment.this, item, source());
            }
        });
        boolean turned = turnedSinceLastView();
        View page;
        if (sideways) {
            // Sideways: the header stays put and the cards scroll sideways under it.
            LinearLayout column = new LinearLayout(c);
            column.setOrientation(LinearLayout.VERTICAL);
            LinearLayout bar = new LinearLayout(c);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(android.view.Gravity.BOTTOM);
            bar.addView(header, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (shuffle != null) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = Ui.dp(c, 16);
                lp.bottomMargin = Ui.dp(c, 10);
                bar.addView(shuffle, lp);
            }
            column.addView(bar);
            android.widget.FrameLayout area = new android.widget.FrameLayout(c);
            grid = new HorizontalGrid(c);
            grid.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 8));
            int perColumn = adapter.landscapeRows(50 + 60 + 72);
            adapter.setStacked(perColumn);
            grid.setShape(perColumn, adapter.cardTextHeight());
            grid.setAdapter(adapter);
            area.addView(grid);
            area.addView(status, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            column.addView(area, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            list = null;
            page = column;
        } else {
            boolean longList = mode() == SONGS || mode() == ARTISTS;
            list = longList ? new FastScrollListView(c) : new ListView(c);
            RowAdapter.style(list);
            if (shuffle != null) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(c, 10);
                lp.bottomMargin = Ui.dp(c, 6);
                header.addView(shuffle, lp);
            }
            list.addHeaderView(header, null, false);
            list.addFooterView(status, null, false);
            list.setAdapter(adapter);
            if (longList) {
                FastScrollListView fast = (FastScrollListView) list;
                fast.setThumbColor(Ui.accent(c));
                fast.setLabeler(new FastScrollListView.Labeler() {
                    @Override
                    public String label(int position) {
                        int i = position - list.getHeaderViewsCount();
                        if (i < 0 || i >= adapter.getCount()) return null;
                        Object row = adapter.getItem(i);
                        if (row instanceof MediaItem[]) row = ((MediaItem[]) row)[0];
                        String title = row instanceof MediaItem ? ((MediaItem) row).title : null;
                        if (title == null || title.trim().length() == 0) return null;
                        char first = Character.toUpperCase(title.trim().charAt(0));
                        return Character.isLetter(first) ? String.valueOf(first) : "#";
                    }
                });
            }
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    adapter.click(position - list.getHeaderViewsCount());
                }
            });
            list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    adapter.longClick(position - list.getHeaderViewsCount());
                    return true;
                }
            });
            grid = null;
            page = list;
        }
        if (items != null) {
            show();
            if (grid != null) {
                if (firstShown > 0) grid.scrollToColumn(firstShown / grid.getRows());
            } else if (turned) {
                if (firstShown > 0) list.setSelection(list.getHeaderViewsCount() + firstShown / perRow());
            } else {
                restoreScroll(list);
            }
            // Phone and download lists are cheap to re-read and may have changed meanwhile, and
            // playlists change when one is edited.
            if (!source().isRemote() || (mode() == PLAYLISTS && loadedMeta != PlaylistMeta.version())) load();
        } else {
            load();
        }
        return page;
    }

    @Override
    public void onDestroyView() {
        if (list != null) {
            rememberScroll(list);
            int position = list.getFirstVisiblePosition() - list.getHeaderViewsCount();
            firstShown = position >= 0 ? position * perRow() : 0;
        } else if (grid != null) {
            firstShown = grid.getFirstVisibleColumn() * grid.getRows();
        }
        super.onDestroyView();
    }

    private void open(MediaItem item, int index) {
        MusicSource s = source();
        if (item.kind == MediaItem.TRACK) {
            Player p = Player.get(getActivity());
            p.play(adapter.tracks(), index, p.isShuffle());
        } else if (item.kind == MediaItem.ARTIST) {
            main().push(BrowseFragment.newInstance(s.id(), ARTIST_ALBUMS, item, title()));
        } else {
            main().push(AlbumFragment.newInstance(s.id(), item, title()));
        }
    }

    /** New Playlist: asks its name, makes it (empty), and opens it ready for Add Songs. */
    private void askNewPlaylist() {
        final Context c = getActivity();
        final EditText field = Ui.field(c, 16, false, "Playlist name");
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        Ui.dialog(c)
                .setTitle("New Playlist")
                .setView(field)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = field.getText().toString().trim();
                        if (name.length() > 0) createPlaylist(name);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createPlaylist(final String name) {
        final MusicSource s = source();
        final PlaylistMeta meta = PlaylistMeta.get(getActivity());
        final Set<String> before = new HashSet<String>();
        if (items != null) {
            for (MediaItem m : items) before.add(m.id);
        }
        final Context app = getActivity().getApplicationContext();
        bg(new Bg.Work<List<MediaItem>>() {
            @Override
            public List<MediaItem> run() throws Exception {
                s.createPlaylist(name, new ArrayList<MediaItem>());
                return meta.apply(s.playlists());
            }
        }, new Bg.Done<List<MediaItem>>() {
            @Override
            public void done(List<MediaItem> result, Exception error) {
                if (error != null) {
                    Notice.showLong(app, describe(error, s));
                    return;
                }
                // Recently Added lists new playlists too.
                PlaylistMeta.changed();
                items = result;
                loadedMeta = PlaylistMeta.version();
                show();
                MediaItem created = null;
                for (MediaItem m : result) {
                    if (name.equals(m.title) && !before.contains(m.id)) created = m;
                }
                if (created != null) main().push(AlbumFragment.editing(s.id(), created, title()));
                else Notice.show(app, "Created “" + name + "”");
            }
        });
    }

    private void load() {
        final MusicSource s = source();
        final int mode = mode();
        final MediaItem artist = item();
        final PlaylistMeta meta = PlaylistMeta.get(getActivity());
        loadedMeta = PlaylistMeta.version();
        if (items == null) {
            status.setText("Loading\u2026");
            status.setVisibility(View.VISIBLE);
        }
        bg(new Bg.Work<List<MediaItem>>() {
            @Override
            public List<MediaItem> run() throws Exception {
                switch (mode) {
                    case PLAYLISTS: return meta.apply(s.playlists());
                    case ARTISTS: return s.artists();
                    case SONGS: return s.songs();
                    case ARTIST_ALBUMS: return s.artistAlbums(artist);
                    default: return s.albums();
                }
            }
        }, new Bg.Done<List<MediaItem>>() {
            @Override
            public void done(List<MediaItem> result, Exception error) {
                if (error != null) {
                    if (items == null) status.setText(describe(error, s));
                    return;
                }
                items = result;
                show();
            }
        });
    }

    private void show() {
        int mode = mode();
        adapter.setItems(items, !listed(), false);
        status.setVisibility(View.VISIBLE);
        if (items.isEmpty()) {
            status.setText("Nothing here yet.");
        } else if (mode == SONGS && grid == null) {
            status.setText(items.size() + (items.size() == 1 ? " song" : " songs"));
        } else {
            status.setVisibility(View.GONE);
        }
        refreshStates();
    }

    private void refreshStates() {
        if (items == null || mode() != SONGS || !source().canDownload()) return;
        final String sourceId = source().id();
        final Context app = getActivity().getApplicationContext();
        bg(new Bg.Work<Map<String, Integer>>() {
            @Override
            public Map<String, Integer> run() {
                return Downloads.get(app).states(sourceId);
            }
        }, new Bg.Done<Map<String, Integer>>() {
            @Override
            public void done(Map<String, Integer> states, Exception error) {
                if (states != null) adapter.setDownloadStates(states);
            }
        });
    }

    @Override
    void onPlaylistsChanged() {
        if (mode() == PLAYLISTS) load();
    }

    @Override
    public void onResume() {
        super.onResume();
        Downloads.get(getActivity()).addListener(this);
        Player.get(getActivity()).addListener(this);
        onPlayerChanged();
        refreshStates();
    }

    @Override
    public void onPause() {
        Downloads.get(getActivity()).removeListener(this);
        Player.get(getActivity()).removeListener(this);
        super.onPause();
    }

    @Override
    public void onDownloadsChanged() {
        refreshStates();
    }

    @Override
    public void onPlayerChanged() {
        adapter.setPlaying(Player.get(getActivity()).current());
    }
}
