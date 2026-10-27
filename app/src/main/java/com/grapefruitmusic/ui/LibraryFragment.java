package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.source.DownloadedSource;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.ui.widget.HorizontalGrid;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;

import java.util.List;

/** The Library tab: source picker, Playlists / Artists / Albums / Songs, and Recently Added. */
public final class LibraryFragment extends BaseFragment implements Sources.Listener, Downloads.Listener {
    private ListView list;
    /** Landscape: the sideways page instead of list. */
    private HorizontalGrid grid;
    private RowAdapter adapter;
    private TextView sourceName;
    private LinearLayout rows;
    private TextView recentTitle;
    private TextView status;
    private int generation;
    /** Recently Added for recentFor (a source id). Kept while an album page is on top. */
    private List<MediaItem> recent;
    private String recentFor;
    private String error;

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
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.VERTICAL);
        top.addView(Ui.header(c, null, "Library", null));

        LinearLayout chip = new LinearLayout(c);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(Ui.dp(c, 16), Ui.dp(c, 2), Ui.dp(c, 16), Ui.dp(c, 10));
        chip.setBackgroundResource(Ui.resource(c, R.attr.gfRowBackground));
        sourceName = Ui.text(c, 15, R.attr.gfAccent, true);
        chip.addView(sourceName);
        IconView down = Ui.icon(c, Icons.CHEVRON_DOWN, Ui.accent(c), 16);
        ((LinearLayout.LayoutParams) down.getLayoutParams()).leftMargin = Ui.dp(c, 4);
        chip.addView(down);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickSource();
            }
        });
        top.addView(chip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        top.addView(Ui.separator(c, 16));

        rows = new LinearLayout(c);
        rows.setOrientation(LinearLayout.VERTICAL);
        top.addView(rows);

        recentTitle = Ui.sectionTitle(c, "Recently Added");
        status = Ui.status(c);

        adapter = new RowAdapter(c, new RowAdapter.Listener() {
            @Override
            public void onItem(MediaItem item, int index) {
                push(AlbumFragment.newInstance(item.sourceId, item, "Library"));
            }

            @Override
            public void onLongItem(MediaItem item) {
                MusicSource s = Sources.get(getActivity()).byId(item.sourceId);
                if (s != null) TrackActions.show(LibraryFragment.this, item, s);
            }
        });
        boolean turned = turnedSinceLastView();
        View page;
        if (RowAdapter.landscape(c)) {
            // Sideways: the menu is the first column, then Recently Added, two high, scrolling on.
            grid = new HorizontalGrid(c);
            // Clear of the mini player and tab bar below, like the other sideways pages.
            grid.setPadding(0, 0, 0, Ui.dp(c, 8));
            ScrollView menu = new ScrollView(c);
            menu.setVerticalScrollBarEnabled(false);
            menu.addView(top);
            menu.setLayoutParams(new ViewGroup.LayoutParams(Ui.dp(c, 300), ViewGroup.LayoutParams.MATCH_PARENT));
            grid.setLeading(menu);
            LinearLayout heading = new LinearLayout(c);
            heading.setOrientation(LinearLayout.VERTICAL);
            heading.addView(recentTitle);
            heading.addView(status);
            grid.setTitle(heading);
            int perColumn = adapter.landscapeRows(50 + 60 + 56);
            adapter.setStacked(perColumn);
            grid.setShape(perColumn, adapter.cardTextHeight());
            grid.setAdapter(adapter);
            list = null;
            page = grid;
        } else {
            list = new ListView(c);
            RowAdapter.style(list);
            top.addView(recentTitle);
            list.addHeaderView(top, null, false);
            list.addFooterView(status, null, false);
            list.setAdapter(adapter);
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    adapter.click(position - list.getHeaderViewsCount());
                }
            });
            grid = null;
            page = list;
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
            firstShown = grid.isPastLeading() ? grid.getFirstVisibleColumn() * grid.getRows() : -1;
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
    public void onDownloadsChanged() {
        if (Sources.get(getActivity()).current() instanceof DownloadedSource) refresh(true);
    }

    private void refresh(boolean force) {
        final MusicSource s = Sources.get(getActivity()).current();
        sourceName.setText(s.name());
        buildRows(s);
        boolean sameSource = s.id().equals(recentFor);
        // Keep a server's albums; phone and download lists are cheap to re-read (and may have
        // changed, e.g. after removing a download), so refresh those quietly.
        if (!force && sameSource && recent != null && s.isRemote()) return;
        if (!sameSource) {
            recent = null;
            error = null;
            resetScroll();
            showRecent();
        }
        final int gen = ++generation;
        bg(new com.grapefruitmusic.util.Bg.Work<List<MediaItem>>() {
            @Override
            public List<MediaItem> run() throws Exception {
                return s.recentAlbums(20);
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
            recentTitle.setVisibility(View.VISIBLE);
            status.setText(error != null ? error : "Loading\u2026");
            status.setVisibility(View.VISIBLE);
            return;
        }
        adapter.setItems(recent, true, false);
        recentTitle.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
        if (recent.isEmpty()) {
            status.setText(s instanceof DownloadedSource
                    ? "Nothing downloaded yet. Open an album on your server and tap Download."
                    : "No music here yet.");
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
        }
    }

    private void buildRows(final MusicSource s) {
        Context c = getActivity();
        rows.removeAllViews();
        addRow(c, "Playlists", BrowseFragment.PLAYLISTS, s);
        addRow(c, "Artists", BrowseFragment.ARTISTS, s);
        addRow(c, "Albums", BrowseFragment.ALBUMS, s);
        addRow(c, "Songs", BrowseFragment.SONGS, s);
        if (!(s instanceof DownloadedSource)) {
            TextView t = rowText(c, "Downloaded Music");
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Sources sources = Sources.get(getActivity());
                    sources.setCurrent(sources.downloaded());
                }
            });
            rows.addView(t);
            rows.addView(Ui.separator(c, 16));
        }
    }

    private void addRow(Context c, String label, final int mode, final MusicSource s) {
        TextView t = rowText(c, label);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                push(BrowseFragment.newInstance(s.id(), mode, null, "Library"));
            }
        });
        rows.addView(t);
        rows.addView(Ui.separator(c, 16));
    }

    private TextView rowText(Context c, String label) {
        TextView t = Ui.text(c, 21, R.attr.gfAccent, false);
        t.setText(label);
        t.setPadding(Ui.dp(c, 16), Ui.dp(c, 11), Ui.dp(c, 16), Ui.dp(c, 11));
        t.setBackgroundResource(Ui.resource(c, R.attr.gfRowBackground));
        return t;
    }

    private void pickSource() {
        final Context c = getActivity();
        final List<MusicSource> all = Sources.get(c).all();
        String[] names = new String[all.size() + 1];
        for (int i = 0; i < all.size(); i++) names[i] = all.get(i).name() + "  ·  " + all.get(i).typeLabel();
        names[all.size()] = "Add a Server…";
        Ui.dialog(c)
                .setTitle("Show music from")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < all.size()) Sources.get(c).setCurrent(all.get(which));
                        else AddServerDialog.show(getActivity());
                    }
                })
                .show();
    }
}
