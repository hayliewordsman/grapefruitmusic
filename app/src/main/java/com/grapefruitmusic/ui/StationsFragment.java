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
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.radio.RadioBrowser;
import com.grapefruitmusic.source.InternetRadioSource;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.Bg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Internet radio stations from the directory: a search, a genre, or the most listened-to. */
public final class StationsFragment extends BaseFragment implements Player.Listener {
    static final int SEARCH = 0;
    static final int GENRE = 1;
    static final int TOP = 2;
    private static final String ARG_TAG = "tag";

    private ListView list;
    private RowAdapter adapter;
    private EditText field;
    private TextView status;
    private int generation;
    /** Kept while a page opened from this one is on top. */
    private String query = "";
    private List<MediaItem> results;
    private String statusText;
    private final Runnable debounced = new Runnable() {
        @Override
        public void run() {
            load();
        }
    };

    /** mode SEARCH, GENRE (tag is the directory's tag, title what to call it) or TOP. */
    static StationsFragment newInstance(int mode, String tag, String title, String back) {
        StationsFragment f = new StationsFragment();
        Bundle b = args(InternetRadioSource.ID, null, back);
        b.putInt(ARG_MODE, mode);
        b.putString(ARG_TAG, tag);
        b.putString(ARG_TITLE, title);
        f.setArguments(b);
        return f;
    }

    private int mode() {
        return getArguments().getInt(ARG_MODE);
    }

    private String title() {
        return getArguments().getString(ARG_TITLE);
    }

    @Override
    String backTitle() {
        return title();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        Context c = getActivity();
        list = new ListView(c);
        RowAdapter.style(list);
        LinearLayout header = Ui.header(c, backLabel(), title(), goBack());
        if (mode() == SEARCH) header.addView(searchBox(c), searchBoxParams(c));
        list.addHeaderView(header, null, false);
        status = Ui.status(c);
        list.addFooterView(status, null, false);
        adapter = new RowAdapter(c, new RowAdapter.Listener() {
            @Override
            public void onItem(MediaItem item, int index) {
                play(getActivity(), adapter.tracks(), index);
            }

            @Override
            public void onLongItem(MediaItem item) {
                TrackActions.show(StationsFragment.this, item, source());
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
        if (results == null && statusText == null && mode() != SEARCH) load();
        show();
        restoreScroll(list);
        return list;
    }

    /** Plays a list of stations from index, and tells the directory. */
    static void play(Context c, List<MediaItem> stations, int index) {
        if (index < 0 || index >= stations.size()) return;
        if (!Http.isOnline(c)) {
            com.grapefruitmusic.util.Notice.show(c, "You're offline. Internet radio needs Wi-Fi.");
            return;
        }
        Player.get(c).play(stations, index, false);
        RadioBrowser.countPlay(stations.get(index));
    }

    private View searchBox(Context c) {
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
        field.setHint("Station name");
        box.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
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
                Bg.mainDelayed(debounced, 600);
            }
        });
        field.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                Bg.cancelMain(debounced);
                load();
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
                return true;
            }
        });
        return box;
    }

    private static LinearLayout.LayoutParams searchBoxParams(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 10);
        lp.bottomMargin = Ui.dp(c, 4);
        return lp;
    }

    @Override
    public void onResume() {
        super.onResume();
        Player.get(getActivity()).addListener(this);
        onPlayerChanged();
    }

    @Override
    public void onPause() {
        Player.get(getActivity()).removeListener(this);
        Bg.cancelMain(debounced);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        rememberScroll(list);
        super.onDestroyView();
    }

    @Override
    public void onPlayerChanged() {
        if (adapter != null) adapter.setPlaying(Player.get(getActivity()).current());
    }

    private void show() {
        adapter.setItems(results != null ? results : new ArrayList<MediaItem>(), false, false);
        status.setText(statusText != null ? statusText : "");
        status.setVisibility(statusText != null ? View.VISIBLE : View.GONE);
    }

    private void load() {
        final int mode = mode();
        final String q = field != null ? field.getText().toString().trim() : "";
        final String tag = getArguments().getString(ARG_TAG);
        final int gen = ++generation;
        if (mode == SEARCH) {
            query = q;
            resetScroll();
            if (q.length() == 0) {
                results = null;
                statusText = null;
                show();
                return;
            }
        }
        if (!Http.isOnline(getActivity())) {
            results = null;
            statusText = "You're offline. Connect to Wi-Fi to find stations.";
            show();
            return;
        }
        statusText = mode == SEARCH ? "Searching…" : "Loading…";
        show();
        bg(new Bg.Work<List<MediaItem>>() {
            @Override
            public List<MediaItem> run() throws Exception {
                if (mode == SEARCH) return RadioBrowser.search(q);
                if (mode == GENRE) return RadioBrowser.byTag(tag);
                return RadioBrowser.top(Locale.getDefault().getCountry());
            }
        }, new Bg.Done<List<MediaItem>>() {
            @Override
            public void done(List<MediaItem> found, Exception error) {
                if (gen != generation) return;
                if (error != null) {
                    results = null;
                    statusText = "Can't reach the station directory right now. Try again in a moment.";
                } else {
                    results = found;
                    statusText = !found.isEmpty() ? null : mode == SEARCH
                            ? "No stations called “" + q + "”" : "No stations here right now.";
                }
                show();
                if (adapter != null) adapter.setPlaying(Player.get(getActivity()).current());
            }
        });
    }
}
