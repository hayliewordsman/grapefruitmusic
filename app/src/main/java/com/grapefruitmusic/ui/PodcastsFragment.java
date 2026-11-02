package com.grapefruitmusic.ui;

import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import com.grapefruitmusic.R;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.podcast.*;
import com.grapefruitmusic.util.*;

import java.util.*;

/** Discovery, subscriptions, show details and offline episodes share the app's view vocabulary. */
public final class PodcastsFragment extends BaseFragment {
    private static final int HOME = 0, SEARCH = 1, SHOW = 2, DOWNLOADED = 3, SUBSCRIPTIONS = 4;
    private RowAdapter adapter;
    private TextView status;
    private EditText field;
    private int generation;
    private boolean resumed, loading, downloadPollInFlight;
    private final Runnable downloadPoll = () -> refreshDownloadProgress();
    private List<MediaItem> items = new ArrayList<MediaItem>();

    private int mode() {
        return getArguments() == null ? HOME : getArguments().getInt("podcast_mode");
    }

    /** A Podcasts section (SectionMenu.FIRST: Discover, SUBSCRIPTIONS or DOWNLOADS) under the tab's chips. */
    static PodcastsFragment section(int section) {
        return page(section == SectionMenu.SUBSCRIPTIONS ? SUBSCRIPTIONS
                : section == SectionMenu.DOWNLOADS ? DOWNLOADED : HOME, null);
    }

    /** Discover, Subscriptions and Downloads are the tab's sections; a show and search open on top. */
    private boolean isSection() {
        int mode = mode();
        return mode == HOME || mode == SUBSCRIPTIONS || mode == DOWNLOADED;
    }

    private int sectionNumber() {
        int mode = mode();
        return mode == SUBSCRIPTIONS ? SectionMenu.SUBSCRIPTIONS
                : mode == DOWNLOADED ? SectionMenu.DOWNLOADS : SectionMenu.FIRST;
    }

    @Override
    boolean rebuildsOnRotate() {
        // The sections' menu is a column on a wide screen.
        return isSection();
    }

    /** Back from Subscriptions or Downloads: to the first section, Discover. */
    @Override
    boolean onBackPressed() {
        if (!isSection() || mode() == HOME) return false;
        main().showPodcasts(SectionMenu.FIRST);
        return true;
    }

    public static PodcastsFragment show(MediaItem show) {
        return page(SHOW, show);
    }

    private static PodcastsFragment page(int mode, MediaItem item) {
        PodcastsFragment f = new PodcastsFragment();
        Bundle b = args(PodcastSource.ID, item, "Podcasts");
        b.putInt("podcast_mode", mode);
        f.setArguments(b);
        return f;
    }

    @Override
    String backTitle() {
        return "Podcasts";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        final Context c = getActivity();
        final int mode = mode();
        PodcastStore store = PodcastStore.get(c);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        // The sections share the tab's menu (title, search, chips); a show and search have a header.
        SectionMenu menu = null;
        LinearLayout header = null;
        if (isSection()) {
            menu = SectionMenu.podcasts(this, sectionNumber(), v -> main().push(page(SEARCH, null)));
        } else {
            header = Ui.header(c, "Podcasts", mode == SHOW ? item().title : "Search Podcasts", goBack());
            root.addView(header);
        }
        if (mode == HOME) {
            TextView disclosure = Ui.text(c, 12, R.attr.gfTextSecondary, false);
            disclosure.setSingleLine(false);
            disclosure.setLinkTextColor(Ui.accent(c));
            android.text.SpannableString text =
                    new android.text.SpannableString(
                            "Discovery recommendations are curated. Learn more");
            text.setSpan(
                    new android.text.style.ClickableSpan() {
                        @Override
                        public void onClick(View v) {
                            Ui.dialog(c)
                                    .setTitle("Discovery recommendations")
                                    .setMessage(DiscoveryPolicy.EXPLANATION)
                                    .setPositiveButton("Done", null)
                                    .setView(
                                            Ui.button(
                                                    c,
                                                    "Exclusions & sources",
                                                    button -> editorialSources()))
                                    .show();
                        }
                    },
                    "Discovery recommendations are curated. ".length(),
                    text.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            disclosure.setText(text);
            disclosure.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            menu.addNote(disclosure);
        }
        if (mode == SEARCH) {
            field = Ui.field(c, 16, false, "Podcast name");
            field.setSingleLine(true);
            field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
            field.setTypeface(Fonts.regular());
            field.setMinHeight(Ui.dp(c, 48));
            addHeaderControl(header, field, 10);
            addHeaderControl(header, Ui.button(c, "Search Podcasts", v -> load()), 8);
            field.setOnEditorActionListener(
                    (v, action, event) -> {
                        load();
                        return true;
                    });
            addHeaderControl(header, Ui.button(c, "Add by RSS address", v -> addFeed()), 8);
        }
        if (mode == DOWNLOADED) {
            TextView hint = Ui.text(c, 14, R.attr.gfTextSecondary, false);
            hint.setSingleLine(false);
            hint.setText(
                    "Active downloads appear first. Open an episode's menu to cancel or retry.");
            menu.addNote(hint);
        }
        if (mode == SHOW) {
            final MediaItem show = item();
            addHeaderControl(
                    header,
                    Ui.button(
                            c,
                            store.subscribed(show.id) ? "Unsubscribe" : "Subscribe",
                            v -> {
                                boolean on = !store.subscribed(show.id);
                                store.subscribe(show, on);
                                ((TextView) v).setText(on ? "Unsubscribe" : "Subscribe");
                                Notice.show(
                                        c,
                                        on
                                                ? "Subscribed. New episodes are checked"
                                                        + " periodically."
                                                : "Unsubscribed. Downloads are kept.");
                            }),
                    8);
            addHeaderControl(header, Ui.button(c, "Refresh Episodes", v -> load()), 8);
        }
        FrameLayout body = new FrameLayout(c);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        if (mode == HOME && !Http.isOnline(c)) {
            LinearLayout offline = new LinearLayout(c);
            offline.setOrientation(LinearLayout.VERTICAL);
            offline.setGravity(Gravity.CENTER);
            offline.setPadding(Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24));
            TextView note = Ui.text(c, 17, R.attr.gfTextSecondary, false);
            note.setText("You're offline. Your downloaded episodes are ready to listen to.");
            note.setSingleLine(false);
            note.setGravity(Gravity.CENTER);
            offline.addView(note);
            LinearLayout.LayoutParams offlineButton = new LinearLayout.LayoutParams(-1, -2);
            offlineButton.topMargin = Ui.dp(c, 16);
            offline.addView(
                    Ui.button(
                            c,
                            "Browse Downloaded Episodes",
                            v -> main().showPodcasts(SectionMenu.DOWNLOADS)),
                    offlineButton);
            if (menu != null) return menu.pinned(offline);
            body.addView(offline, new FrameLayout.LayoutParams(-1, -1));
            return root;
        }
        status = Ui.status(c);
        adapter =
                new RowAdapter(
                        c,
                        new RowAdapter.Listener() {
                            @Override
                            public void onItem(MediaItem m, int index) {
                                if (m.kind == MediaItem.PODCAST) main().push(show(m));
                                else {
                                    if (!Http.isOnline(c)
                                            && PodcastStore.get(c).localFile(m) == null) {
                                        Notice.show(c, "This episode hasn't been downloaded.");
                                        return;
                                    }
                                    Player.get(c).play(adapter.tracks(), index, false);
                                    main().openNowPlaying();
                                }
                            }

                            @Override
                            public void onLongItem(MediaItem m) {
                                if (m.kind == MediaItem.PODCAST) main().push(show(m));
                                else episodeActions(m);
                            }
                        });
        if (menu != null && menu.wide && (mode == HOME || mode == SUBSCRIPTIONS)) {
            // On a wide screen, Discover's and Subscriptions' shows are cards scrolling sideways
            // beside the chip column, as the Library's albums are, the title (and note) with them.
            com.grapefruitmusic.ui.widget.HorizontalGrid grid = new com.grapefruitmusic.ui.widget.HorizontalGrid(c);
            grid.setPadding(0, 0, 0, Ui.dp(c, 8));
            int perColumn = adapter.landscapeRows(SectionMenu.WIDE_RESERVED_DP + (mode == HOME ? 28 : 0));
            adapter.setStacked(perColumn);
            grid.setShape(perColumn, adapter.cardTextHeight());
            grid.setAdapter(adapter);
            load();
            return menu.page(grid, status);
        }
        ListView list = new ListView(c);
        RowAdapter.style(list);
        // A section: the tab's title and chips (and its note) at the top of the list.
        if (menu != null) menu.addHeaders(list);
        else body.addView(list);
        list.addFooterView(status, null, false);
        list.setAdapter(adapter);
        list.setOnItemClickListener(
                (parent, v, pos, id) -> adapter.click(pos - list.getHeaderViewsCount()));
        list.setOnItemLongClickListener(
                (parent, v, pos, id) -> {
                    adapter.longClick(pos - list.getHeaderViewsCount());
                    return true;
                });
        if (mode != SEARCH) load();
        else render();
        return menu != null ? menu.page(list) : root;
    }

    /** The loading, empty or error line; gone when there's nothing to say. */
    private void showStatus(String text) {
        status.setText(text);
        status.setVisibility(text.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void editorialSources() {
        final Context c = getActivity();
        try {
            java.io.InputStream in = c.getAssets().open("podcast-discovery-policy.html");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            try {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            } finally {
                in.close();
            }
            TextView text = Ui.text(c, 15, R.attr.gfTextPrimary, false);
            text.setSingleLine(false);
            // The card has its own margins.
            text.setPadding(Ui.dp(c, 2), 0, Ui.dp(c, 2), Ui.dp(c, 4));
            text.setText(android.text.Html.fromHtml(out.toString("UTF-8")));
            text.setLinkTextColor(Ui.accent(c));
            text.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            ScrollView scroll = new ScrollView(c);
            scroll.addView(text);
            Ui.dialog(c)
                    .setTitle("Editorial exclusions & sources")
                    .setView(scroll)
                    .setPositiveButton("Done", null)
                    .show();
        } catch (java.io.IOException e) {
            Notice.show(c, "Couldn't open the bundled editorial policy.");
        }
    }

    private void addHeaderControl(LinearLayout header, View control, int gapDp) {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(header.getContext(), gapDp);
        header.addView(control, lp);
    }

    private void addFeed() {
        final EditText address =
                Ui.field(getActivity(), 16, false, "https://example.com/podcast.xml");
        address.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        Ui.dialog(getActivity())
                .setTitle("Add Podcast by RSS")
                .setView(address)
                .setPositiveButton(
                        "Open",
                        (d, w) -> {
                            String url = address.getText().toString().trim();
                            if (!PodcastFeed.webUrl(url)) {
                                Notice.show(
                                        getActivity(), "Enter an http or https RSS feed address.");
                                return;
                            }
                            main().push(
                                            show(
                                                    new MediaItem(
                                                            MediaItem.PODCAST,
                                                            PodcastSource.ID,
                                                            url,
                                                            "Podcast")));
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void episodeActions(final MediaItem m) {
        episodeActions(this, m, () -> refreshDownloadProgress());
    }

    static void episodeActions(final BaseFragment page, final MediaItem m, final Runnable refresh) {
        final Context c = page.getActivity();
        final PodcastStore store = PodcastStore.get(c);
        PodcastDownload progress = store.downloadProgress(m);
        boolean local = progress != null && progress.status == PodcastDownload.COMPLETE;
        boolean active = progress != null && progress.active();
        String action =
                local
                        ? "Remove Download"
                        : active
                                ? "Cancel Download"
                                : progress != null ? "Retry Download" : "Download Episode";
        Ui.dialog(c)
                .setTitle(m.title)
                .setItems(
                        new String[] {"Play", action, "Mark Unplayed"},
                        (d, w) -> {
                            if (w == 0) {
                                Player.get(c).playNow(m);
                                page.main().openNowPlaying();
                            } else if (w == 2) {
                                PodcastPositions.get(c).clear(m.id);
                                if (m.sameAs(Player.get(c).current())) Player.get(c).seekTo(0);
                            } else {
                                try {
                                    if (local || active) store.removeDownload(m);
                                    else store.download(m);
                                    Notice.show(
                                            c,
                                            local
                                                    ? "Download removed"
                                                    : active
                                                            ? "Download canceled"
                                                            : "Episode queued for download");
                                    if (refresh != null) refresh.run();
                                } catch (Exception e) {
                                    Notice.show(
                                            c,
                                            e.getMessage() != null
                                                    ? e.getMessage()
                                                    : "Couldn't download episode");
                                }
                            }
                        })
                .show();
    }

    private void render() {
        if (adapter != null)
            adapter.setItems(items, mode() == HOME || mode() == SUBSCRIPTIONS, false);
    }

    private void load() {
        if (adapter == null) return;
        final int gen = ++generation, mode = mode();
        final Context c = getActivity().getApplicationContext();
        final MediaItem show = mode == SHOW ? item() : null;
        final String query = field == null ? "" : field.getText().toString().trim();
        if (mode == SEARCH && query.length() == 0) return;
        loading = true;
        status.setText("Loading…");
        status.setVisibility(View.VISIBLE);
        bg(
                () -> {
                    PodcastStore store = PodcastStore.get(c);
                    if (mode == DOWNLOADED) return store.episodes(null, true);
                    if (mode == SUBSCRIPTIONS) return store.subscriptions();
                    if (mode == SHOW) {
                        if (Http.isOnline(c))
                            try {
                                store.refresh(show);
                            } catch (java.io.IOException e) {
                                if (store.episodes(show.id, false).isEmpty()) throw e;
                            }
                        return store.episodes(show.id, false);
                    }
                    return mode == SEARCH
                            ? PodcastDirectory.search(query)
                            : PodcastDirectory.discover();
                },
                (List<MediaItem> found, Exception error) -> {
                    if (gen != generation || getView() == null) return;
                    loading = false;
                    if (error == null) {
                        items = found;
                        render();
                        showStatus(found.isEmpty() ? "Nothing here yet." : "");
                    } else
                        showStatus(
                                "Couldn't load podcasts. Check your connection and try again.\n"
                                        + error.getMessage());
                });
    }

    private void scheduleDownloadPoll() {
        Bg.cancelMain(downloadPoll);
        if (resumed && adapter != null && (mode() == DOWNLOADED || mode() == SHOW))
            Bg.mainDelayed(downloadPoll, 1500);
    }

    private void refreshDownloadProgress() {
        if (!resumed || adapter == null || downloadPollInFlight) return;
        if (loading) {
            scheduleDownloadPoll();
            return;
        }
        int mode = mode();
        if (mode != DOWNLOADED && mode != SHOW) return;
        final int gen = generation;
        final Context c = getActivity().getApplicationContext();
        final String feed = mode == SHOW ? item().id : null;
        downloadPollInFlight = true;
        Bg.run(
                () -> PodcastStore.get(c).episodes(feed, mode == DOWNLOADED),
                (List<MediaItem> found, Exception error) -> {
                    downloadPollInFlight = false;
                    if (!resumed || getView() == null) return;
                    if (gen == generation && error == null) {
                        items = found;
                        render();
                        showStatus(found.isEmpty() ? "Nothing here yet." : "");
                    }
                    scheduleDownloadPoll();
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        resumed = true;
        scheduleDownloadPoll();
    }

    @Override
    public void onPause() {
        resumed = false;
        Bg.cancelMain(downloadPoll);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        generation++;
        resumed = false;
        Bg.cancelMain(downloadPoll);
        adapter = null;
        super.onDestroyView();
    }
}
