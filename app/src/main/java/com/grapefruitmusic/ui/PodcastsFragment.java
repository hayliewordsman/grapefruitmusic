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
    private List<MediaItem> items = new ArrayList<MediaItem>();

    private int mode() {
        return getArguments() == null ? HOME : getArguments().getInt("podcast_mode");
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
        String title =
                mode == SHOW
                        ? item().title
                        : mode == SEARCH
                                ? "Search Podcasts"
                                : mode == DOWNLOADED
                                        ? "Downloaded Episodes"
                                        : mode == SUBSCRIPTIONS ? "Subscriptions" : "Podcasts";
        LinearLayout header =
                Ui.header(
                        c, mode == HOME ? null : "Podcasts", title, mode == HOME ? null : goBack());
        root.addView(header);
        if (mode == HOME) {
            LinearLayout buttons = new LinearLayout(c);
            addButton(buttons, "Search Podcasts", v -> main().push(page(SEARCH, null)));
            addButton(buttons, "Subscriptions", v -> main().push(page(SUBSCRIPTIONS, null)));
            addButton(buttons, "Downloads", v -> main().push(page(DOWNLOADED, null)));
            header.addView(buttons);
            TextView disclosure = Ui.text(c, 12, R.attr.gfTextSecondary, false);
            disclosure.setSingleLine(false);
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
                                    .show();
                        }
                    },
                    "Discovery recommendations are curated. ".length(),
                    text.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            disclosure.setText(text);
            disclosure.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            header.addView(disclosure);
        }
        if (mode == SEARCH) {
            field = Ui.field(c, 16, false, "Podcast name");
            field.setSingleLine(true);
            field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
            header.addView(field);
            header.addView(Ui.button(c, "Search Podcasts", v -> load()));
            field.setOnEditorActionListener(
                    (v, action, event) -> {
                        load();
                        return true;
                    });
            header.addView(Ui.button(c, "Add by RSS address", v -> addFeed()));
        }
        if (mode == SHOW) {
            final MediaItem show = item();
            header.addView(
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
                            }));
            header.addView(Ui.button(c, "Refresh Episodes", v -> load()));
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
            offline.addView(
                    Ui.button(
                            c,
                            "Browse Downloaded Episodes",
                            v -> main().push(page(DOWNLOADED, null))));
            body.addView(offline, new FrameLayout.LayoutParams(-1, -1));
            return root;
        }
        ListView list = new ListView(c);
        RowAdapter.style(list);
        body.addView(list);
        status = Ui.status(c);
        list.addFooterView(status, null, false);
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
        return root;
    }

    private void addButton(LinearLayout row, String title, View.OnClickListener action) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(3, 3, 3, 6);
        row.addView(Ui.button(getActivity(), title, action), lp);
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
        episodeActions(
                this,
                m,
                () -> {
                    if (mode() == DOWNLOADED) load();
                });
    }

    static void episodeActions(final BaseFragment page, final MediaItem m, final Runnable refresh) {
        final Context c = page.getActivity();
        final PodcastStore store = PodcastStore.get(c);
        boolean local = store.localFile(m) != null;
        Ui.dialog(c)
                .setTitle(m.title)
                .setItems(
                        new String[] {
                            "Play", local ? "Remove Download" : "Download Episode", "Mark Unplayed"
                        },
                        (d, w) -> {
                            if (w == 0) {
                                Player.get(c).playNow(m);
                                page.main().openNowPlaying();
                            } else if (w == 2) {
                                Prefs.raw().edit().remove("podcast_position_" + m.id).apply();
                                if (m.sameAs(Player.get(c).current())) Player.get(c).seekTo(0);
                            } else {
                                try {
                                    if (local) store.removeDownload(m);
                                    else store.download(m);
                                    Notice.show(
                                            c,
                                            local
                                                    ? "Download removed"
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
                    if (error == null) {
                        items = found;
                        render();
                        status.setText(found.isEmpty() ? "Nothing here yet." : "");
                    } else
                        status.setText(
                                "Couldn't load podcasts. Check your connection and try again.\n"
                                        + error.getMessage());
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mode() == DOWNLOADED && adapter != null) load();
    }

    @Override
    public void onDestroyView() {
        generation++;
        adapter = null;
        super.onDestroyView();
    }
}
