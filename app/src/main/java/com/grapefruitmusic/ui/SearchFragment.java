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
    private final Runnable debounced = new Runnable() {
        @Override
        public void run() {
            search();
        }
    };

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
        field.setHint("Search " + source().name());
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
                TrackActions.show(SearchFragment.this, item, source());
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
        statusText = "Searching\u2026";
        status.setText(statusText);
        status.setVisibility(View.VISIBLE);
        bg(new Bg.Work<List<MediaItem>>() {
            @Override
            public List<MediaItem> run() throws Exception {
                return s.search(q);
            }
        }, new Bg.Done<List<MediaItem>>() {
            @Override
            public void done(List<MediaItem> found, Exception error) {
                if (gen != generation) return;
                if (error != null) {
                    statusText = describe(error, s);
                } else {
                    results = found;
                    statusText = found.isEmpty() ? "No results for \u201c" + q + "\u201d" : null;
                }
                show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        Player.get(getActivity()).addListener(this);
        onPlayerChanged();
        field.setHint("Search " + source().name());
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
