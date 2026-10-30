package com.grapefruitmusic.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.Bg;

import java.util.ArrayList;
import java.util.List;

/** The Search tab: searches the source picked in Library. */
public final class SearchFragment extends BaseFragment implements Player.Listener {
    private ListView list;
    private RowAdapter adapter;
    private EditText field;
    private TextView status;
    private int generation;
    /** The last search, kept while a page opened from the results is on top. */
    private String query = "";
    private List<MediaItem> results;
    private String statusText;
    private final List<MediaItem> music = new ArrayList<MediaItem>();
    private final List<MediaItem> stations = new ArrayList<MediaItem>();
    private final List<MediaItem> podcasts = new ArrayList<MediaItem>();
    private final List<String> errors = new ArrayList<String>();
    private int pending;
    private final Runnable debounced = new Runnable() {
        @Override
        public void run() {
            search();
        }
    };

    @Override
    String backTitle() {
        return "Search";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        Context c = getActivity();
        list = new ListView(c);
        RowAdapter.style(list);
        LinearLayout header = Ui.header(c, null, "Search", null);

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackgroundResource(Ui.resource(c, R.attr.gfFieldBackground));
        box.addView(Ui.icon(c, Icons.SEARCH, Ui.color(c, R.attr.gfTextSecondary), 18));
        field = new EditText(c);
        field.setBackgroundColor(0);
        field.setSingleLine(true);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        field.setTextColor(Ui.color(c, R.attr.gfTextPrimary));
        field.setHintTextColor(Ui.color(c, R.attr.gfTextSecondary));
        field.setPadding(Ui.dp(c, 8), Ui.dp(c, 2), 0, Ui.dp(c, 2));
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setHint("Music, stations and podcasts");
        box.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 10);
        lp.bottomMargin = Ui.dp(c, 4);
        header.addView(box, lp);
        // Restore before listening, so coming back doesn't search again.
        field.setText(query);
        field.setSelection(field.getText().length());

        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                Bg.cancelMain(debounced);
                Bg.mainDelayed(debounced, 450);
            }
        });
        field.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                Bg.cancelMain(debounced);
                search();
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
                return true;
            }
        });

        list.addHeaderView(header, null, false);
        status = Ui.status(c);
        status.setVisibility(View.GONE);
        list.addFooterView(status, null, false);
        adapter = new RowAdapter(c, new RowAdapter.Listener() {
            @Override
            public void onItem(MediaItem item, int index) {
                if (item.kind == MediaItem.PODCAST) { main().push(PodcastsFragment.show(item)); return; }
                if (item.live) { StationsFragment.play(getActivity(), java.util.Collections.singletonList(item), 0); return; }
                MusicSource s = source();
                if (item.kind == MediaItem.TRACK) {
                    Player p = Player.get(getActivity());
                    p.play(adapter.tracks(), index, p.isShuffle());
                } else if (item.kind == MediaItem.ARTIST) {
                    main().push(BrowseFragment.newInstance(s.id(), BrowseFragment.ARTIST_ALBUMS, item, "Search"));
                } else {
                    main().push(AlbumFragment.newInstance(s.id(), item, "Search"));
                }
            }

            @Override
            public void onLongItem(MediaItem item) {
                if (item.kind == MediaItem.PODCAST) { main().push(PodcastsFragment.show(item)); return; }
                MusicSource s = com.grapefruitmusic.source.Sources.get(getActivity()).byId(item.sourceId);
                if (s != null) TrackActions.show(SearchFragment.this, item, s);
            }
        });
        list.setAdapter(adapter);
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
        show();
        restoreScroll(list);
        return list;
    }

    @Override
    public void onDestroyView() {
        generation++;
        rememberScroll(list);
        super.onDestroyView();
    }

    private void show() {
        adapter.setItems(results != null ? results : new ArrayList<MediaItem>(), false, true);
        status.setText(statusText != null ? statusText : "");
        status.setVisibility(statusText != null ? View.VISIBLE : View.GONE);
    }

    private void search() {
        if (field == null) return;
        final String q = field.getText().toString().trim();
        final int gen = ++generation;
        query = q;
        resetScroll();
        if (q.length() == 0) {
            results = null;
            statusText = null;
            show();
            return;
        }
        final MusicSource s = source();
        music.clear(); stations.clear(); podcasts.clear(); errors.clear(); pending = 3;
        results = new ArrayList<MediaItem>();
        statusText = "Searching…"; show();
        provider(gen, "Music", music, () -> s.search(q));
        provider(gen, "Stations", stations, () -> com.grapefruitmusic.radio.RadioBrowser.search(q));
        provider(gen, "Podcasts", podcasts, () -> com.grapefruitmusic.podcast.PodcastDirectory.search(q));
    }

    private void provider(final int gen, final String name, final List<MediaItem> destination,
                          Bg.Work<List<MediaItem>> work) {
        bg(work, new Bg.Done<List<MediaItem>>() {
            @Override public void done(List<MediaItem> found, Exception error) {
                if (gen != generation || adapter == null || getView() == null) return;
                pending--;
                if (error != null) errors.add(name + " unavailable");
                else if (found != null) destination.addAll(found);
                results = new ArrayList<MediaItem>();
                results.addAll(music); results.addAll(stations); results.addAll(podcasts);
                StringBuilder message = new StringBuilder(pending > 0 ? "Searching…" : "");
                for (String e : errors) { if (message.length() > 0) message.append(" · "); message.append(e); }
                if (pending == 0 && results.isEmpty() && errors.isEmpty()) message.append("No results");
                statusText = message.length() == 0 ? null : message.toString(); show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        Player.get(getActivity()).addListener(this);
        if (com.grapefruitmusic.tags.SongEdits.apply(results)) show();
        onPlayerChanged();
        field.setHint("Music, stations and podcasts");
    }

    @Override
    public void onPause() {
        Player.get(getActivity()).removeListener(this);
        Bg.cancelMain(debounced);
        super.onPause();
    }

    @Override
    public void onPlayerChanged() {
        adapter.setPlaying(Player.get(getActivity()).current());
    }
}
