package com.grapefruitmusic.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.Bg;

import java.util.List;
import java.util.Map;

/** Playlists, Artists, Albums, Songs, or one artist's albums. */
public final class BrowseFragment extends BaseFragment implements Downloads.Listener, Player.Listener {
    static final int PLAYLISTS = 1;
    static final int ARTISTS = 2;
    static final int ALBUMS = 3;
    static final int SONGS = 4;
    static final int ARTIST_ALBUMS = 5;

    private ListView list;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        Context c = getActivity();
        boolean longList = mode() == SONGS || mode() == ARTISTS;
        list = longList ? new FastScrollListView(c) : new ListView(c);
        RowAdapter.style(list);
        LinearLayout header = Ui.header(c, backLabel(), title(), goBack());
        if (backLabel() == null) {
            // Used as the Playlists tab: say which source the playlists come from.
            TextView from = Ui.text(c, 15, R.attr.gfTextSecondary, false);
            from.setText(source().name());
            header.addView(from);
        }
        if (mode() == SONGS) {
            LinearLayout shuffle = Ui.pill(c, Icons.SHUFFLE, "Shuffle All");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(c, 10);
            lp.bottomMargin = Ui.dp(c, 6);
            header.addView(shuffle, lp);
            shuffle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!adapter.tracks().isEmpty()) Player.get(getActivity()).play(adapter.tracks(), -1, true);
                }
            });
        }
        list.addHeaderView(header, null, false);
        status = Ui.status(c);
        list.addFooterView(status, null, false);
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
        if (items != null) {
            show();
            restoreScroll(list);
            // Phone and download lists are cheap to re-read and may have changed meanwhile, and
            // playlists change when one is edited.
            if (!source().isRemote() || (mode() == PLAYLISTS && loadedMeta != PlaylistMeta.version())) load();
        } else {
            load();
        }
        return list;
    }

    @Override
    public void onDestroyView() {
        rememberScroll(list);
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
        adapter.setItems(items, mode == ALBUMS || mode == ARTIST_ALBUMS, false);
        status.setVisibility(View.VISIBLE);
        if (items.isEmpty()) {
            status.setText("Nothing here yet.");
        } else if (mode == SONGS) {
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
