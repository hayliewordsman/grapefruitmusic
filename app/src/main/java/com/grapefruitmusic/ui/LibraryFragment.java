package com.grapefruitmusic.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.grapefruitmusic.data.PlaylistMeta;
import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.source.DownloadedSource;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.ui.widget.HorizontalGrid;

import java.util.List;

/**
 * The Library tab's first section, Recently Added, under the Library's menu (where the music comes
 * from, and chips for the other sections: see SectionMenu).
 */
public final class LibraryFragment extends BaseFragment implements Sources.Listener, Downloads.Listener {
    private ListView list;
    /** Wide screens: cards scrolling sideways instead of list. */
    private HorizontalGrid grid;
    private RowAdapter adapter;
    /** The title and chips, shared with the Library's other sections. */
    private SectionMenu menu;
    private TextView status;
    private int generation;
    /** Recently Added for recentFor (a source id). Kept while an album page is on top. */
    private List<MediaItem> recent;
    private String recentFor;
    private String error;
    /** PlaylistMeta.version() when Recently Added was loaded (it lists playlists too). */
    private int loadedMeta = -1;

    @Override
    String backTitle() {
        return "Library";
    }

    @Override
    boolean rebuildsOnRotate() {
        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        final Context c = getActivity();
        menu = SectionMenu.library(this, SectionMenu.FIRST);
        status = Ui.status(c);

        adapter = new RowAdapter(c, new RowAdapter.Listener() {
            @Override
            public void onItem(MediaItem item, int index) {
                if (item.kind == MediaItem.PODCAST) push(PodcastsFragment.show(item));
                else push(AlbumFragment.newInstance(item.sourceId, item, "Library"));
            }

            @Override
            public void onLongItem(MediaItem item) {
                if (item.kind == MediaItem.PODCAST) { push(PodcastsFragment.show(item)); return; }
                MusicSource s = Sources.get(getActivity()).byId(item.sourceId);
                if (s != null) TrackActions.show(LibraryFragment.this, item, s);
            }
        });
        boolean turned = turnedSinceLastView();
        View page;
        if (menu.wide) {
            // Beside the floating chip column: Recently Added, two high if they fit, scrolling
            // sideways (the title with them).
            grid = new HorizontalGrid(c);
            grid.setPadding(0, 0, 0, Ui.dp(c, 8));
            int perColumn = adapter.landscapeRows(SectionMenu.WIDE_RESERVED_DP);
            adapter.setStacked(perColumn);
            grid.setShape(perColumn, adapter.cardTextHeight());
            grid.setAdapter(adapter);
            list = null;
            page = menu.page(grid, status);
        } else {
            list = new ListView(c);
            RowAdapter.style(list);
            menu.addHeaders(list);
            list.addFooterView(status, null, false);
            list.setAdapter(adapter);
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    adapter.click(position - list.getHeaderViewsCount());
                }
            });
            grid = null;
            page = menu.page(list);
        }
        showRecent();
        if (grid != null) {
            if (firstShown >= 0) grid.scrollToColumn(firstShown / grid.getRows());
        } else if (turned) {
            if (firstShown >= 0) list.setSelection(list.getHeaderViewsCount() + firstShown / 2);
        } else {
            restoreScroll(list);
        }
        return page;
    }

    @Override
    public void onDestroyView() {
        if (list != null) {
            rememberScroll(list);
            int position = list.getFirstVisiblePosition() - list.getHeaderViewsCount();
            firstShown = position >= 0 ? position * 2 : -1;
        } else if (grid != null) {
            firstShown = grid.getFirstVisibleColumn() * grid.getRows();
        }
        super.onDestroyView();
    }

    private void push(android.app.Fragment f) {
        main().push(f);
    }

    @Override
    public void onResume() {
        super.onResume();
        Sources.get(getActivity()).addListener(this);
        Downloads.get(getActivity()).addListener(this);
        refresh(false);
    }

    @Override
    public void onPause() {
        Sources.get(getActivity()).removeListener(this);
        Downloads.get(getActivity()).removeListener(this);
        super.onPause();
    }

    @Override
    public void onSourcesChanged() {
        refresh(true);
    }

    @Override
    void onPlaylistsChanged() {
        refresh(true);
    }

    @Override
    public void onDownloadsChanged() {
        if (Sources.get(getActivity()).current() instanceof DownloadedSource) refresh(true);
    }

    private void refresh(boolean force) {
        final MusicSource s = Sources.get(getActivity()).current();
        menu.setSource(s);
        boolean sameSource = s.id().equals(recentFor);
        // Keep a server's albums; phone and download lists are cheap to re-read (and may have
        // changed, e.g. after removing a download), so refresh those quietly.
        boolean playlistsChanged = loadedMeta != PlaylistMeta.version();
        if (!force && sameSource && recent != null && s.isRemote() && !playlistsChanged) return;
        if (!sameSource) {
            recent = null;
            error = null;
            resetScroll();
            showRecent();
        }
        final int gen = ++generation;
        final android.content.Context app = getActivity().getApplicationContext();
        final PlaylistMeta meta = PlaylistMeta.get(app);
        loadedMeta = PlaylistMeta.version();
        bg(new com.grapefruitmusic.util.Bg.Work<List<MediaItem>>() {
            @Override
            public List<MediaItem> run() throws Exception {
                List<MediaItem> albums = new java.util.ArrayList<MediaItem>();
                try { albums.addAll(s.recentAlbums(20)); }
                catch (Exception e) { if (com.grapefruitmusic.podcast.PodcastStore.get(app).subscriptions().isEmpty()) throw e; }
                albums.addAll(com.grapefruitmusic.podcast.PodcastStore.get(app).subscriptions());
                List<MediaItem> playlists;
                try {
                    playlists = meta.apply(s.playlists());
                } catch (Exception e) {
                    // Albums alone, rather than nothing.
                    playlists = null;
                }
                return com.grapefruitmusic.util.Lists.recentlyAdded(albums, playlists, 20);
            }
        }, new com.grapefruitmusic.util.Bg.Done<List<MediaItem>>() {
            @Override
            public void done(List<MediaItem> albums, Exception e) {
                if (gen != generation) return;
                recentFor = s.id();
                recent = e == null ? albums : null;
                error = e == null ? null : describe(e, s);
                showRecent();
            }
        });
    }

    /** Fills the list from the fields (also used to repopulate a rebuilt view). */
    private void showRecent() {
        MusicSource s = Sources.get(getActivity()).current();
        if (recent == null) {
            adapter.setItems(new java.util.ArrayList<MediaItem>(), true, false);
            status.setText(error != null ? error : "Loading\u2026");
            status.setVisibility(View.VISIBLE);
            return;
        }
        adapter.setItems(recent, true, false);
        if (recent.isEmpty()) {
            status.setText(s instanceof DownloadedSource
                    ? "Nothing downloaded yet. Open an album on your server and tap Download."
                    : "No music here yet.");
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
        }
    }
}
