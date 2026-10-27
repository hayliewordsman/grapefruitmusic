package com.grapefruitmusic.ui;

import android.app.Dialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.ui.widget.ArtworkView;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.ArtworkLoader;
import com.grapefruitmusic.util.Bg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Add Songs", while a playlist is being edited: the albums and songs of the playlist's source in
 * a popup, with a filter. + adds a song, or a whole album (its songs not in the playlist yet);
 * ✓ takes back what was added here. Tap an album to pick from its songs. Nothing is saved until
 * the playlist's Done.
 */
final class AddSongsSheet {
    /** The playlist being edited. */
    interface Target {
        /** Is this song in the playlist, as edited so far? */
        boolean has(MediaItem track);

        /** Was it added while editing (so it can be taken back)? */
        boolean added(MediaItem track);

        void add(List<MediaItem> tracks);

        /** Takes back a song added while editing. */
        void takeBack(MediaItem track);

        /** Songs added while editing. */
        int addedCount();
    }

    private static final int ALBUMS = 0;
    private static final int SONGS = 1;

    private final Context context;
    private final MusicSource source;
    private final Target target;
    private final Dialog dialog;
    private final ListView list;
    private final RowsAdapter adapter = new RowsAdapter();
    private final Segmented tabs;
    private final EditText filter;
    private final TextView status;
    private final TextView subtitle;
    private final TextView back;

    private int tab = ALBUMS;
    private List<MediaItem> albums;
    private List<MediaItem> songs;
    private String albumsError;
    private String songsError;
    /** The album whose songs are showing, or null. */
    private MediaItem openAlbum;
    private final Map<String, List<MediaItem>> albumTracks = new HashMap<String, List<MediaItem>>();
    /** Albums added with their +: the songs that added, so ✓ can take them back. */
    private final Map<String, List<MediaItem>> addedAlbums = new HashMap<String, List<MediaItem>>();
    /** The rows showing (after the filter). */
    private final List<MediaItem> rows = new ArrayList<MediaItem>();

    static AddSongsSheet show(Context c, MusicSource source, Target target) {
        AddSongsSheet sheet = new AddSongsSheet(c, source, target);
        sheet.dialog.show();
        sheet.showTab(ALBUMS);
        return sheet;
    }

    private AddSongsSheet(Context c, MusicSource source, Target target) {
        this.context = c;
        this.source = source;
        this.target = target;
        dialog = new Dialog(c, R.style.GrapefruitDialogWindow);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(GrapefruitDialog.rounded(Ui.color(c, R.attr.gfSurface), Ui.dp(c, 16)));
        card.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));

        // ‹ Albums (inside an album) | Add Songs, and how many so far | Done
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
        back = Ui.textButton(c, "‹ Albums", false);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeAlbum();
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(c, 96), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = Ui.text(c, 17, R.attr.gfTextPrimary, true);
        title.setText("Add Songs");
        titles.addView(title);
        subtitle = Ui.text(c, 13, R.attr.gfTextSecondary, false);
        titles.addView(subtitle);
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView done = Ui.textButton(c, "Done", true);
        done.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        bar.addView(done, new LinearLayout.LayoutParams(Ui.dp(c, 96), ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(bar);

        tabs = new Segmented(c, new String[]{"Albums", "Songs"}, ALBUMS);
        tabs.setListener(new Segmented.Listener() {
            @Override
            public void onSelected(int index) {
                showTab(index);
            }
        });
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.setMargins(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), 0);
        card.addView(tabs, tl);

        filter = Ui.field(c, 15, false, "Filter");
        filter.setSingleLine(true);
        filter.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_FILTER);
        filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh();
            }
        });
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        fl.setMargins(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 4));
        card.addView(filter, fl);

        status = Ui.status(c);
        status.setGravity(Gravity.CENTER);
        card.addView(status);

        list = new ListView(c);
        RowAdapter.style(list);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= rows.size()) return;
                MediaItem m = rows.get(position);
                if (m.kind == MediaItem.TRACK) toggleSong(m);
                else openAlbum(m);
            }
        });
        card.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        dialog.setContentView(card);
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        dialog.getWindow().setLayout(Math.min(dm.widthPixels - Ui.dp(c, 32), Ui.dp(c, 560)),
                dm.heightPixels - Ui.dp(c, 64));
        dialog.getWindow().setGravity(Gravity.CENTER);
    }

    /** The dialog (tests). */
    Dialog dialog() {
        return dialog;
    }

    private void showTab(int which) {
        tab = which;
        openAlbum = null;
        if (which == ALBUMS && albums == null && albumsError == null) load(ALBUMS);
        if (which == SONGS && songs == null && songsError == null) load(SONGS);
        refresh();
    }

    private void load(final int which) {
        Bg.run(new Bg.Work<List<MediaItem>>() {
            @Override
            public List<MediaItem> run() throws Exception {
                return which == ALBUMS ? source.albums() : source.songs();
            }
        }, new Bg.Done<List<MediaItem>>() {
            @Override
            public void done(List<MediaItem> result, Exception error) {
                String problem = error != null ? BaseFragment.describe(error, source) : null;
                if (which == ALBUMS) {
                    albums = result;
                    albumsError = problem;
                } else {
                    songs = result;
                    songsError = problem;
                }
                if (dialog.isShowing()) refresh();
            }
        });
    }

    private void openAlbum(final MediaItem album) {
        openAlbum = album;
        filter.setText("");
        refresh();
        if (albumTracks.containsKey(album.key())) return;
        tracksOf(album, new Bg.Done<List<MediaItem>>() {
            @Override
            public void done(List<MediaItem> result, Exception error) {
                if (dialog.isShowing() && openAlbum == album) refresh();
            }
        });
    }

    private void closeAlbum() {
        openAlbum = null;
        filter.setText("");
        refresh();
    }

    /** The album's songs (loaded once), then done. */
    private void tracksOf(final MediaItem album, final Bg.Done<List<MediaItem>> done) {
        List<MediaItem> known = albumTracks.get(album.key());
        if (known != null) {
            done.done(known, null);
            return;
        }
        Bg.run(new Bg.Work<List<MediaItem>>() {
            @Override
            public List<MediaItem> run() throws Exception {
                return source.tracks(album);
            }
        }, new Bg.Done<List<MediaItem>>() {
            @Override
            public void done(List<MediaItem> result, Exception error) {
                if (result != null) albumTracks.put(album.key(), result);
                else status.setText(BaseFragment.describe(error, source));
                done.done(result, error);
            }
        });
    }

    private void toggleSong(MediaItem song) {
        if (target.added(song)) target.takeBack(song);
        else if (!target.has(song)) target.add(java.util.Collections.singletonList(song));
        else return;
        refresh();
    }

    /** +: the album's songs not in the playlist yet. ✓: takes them back. */
    private void toggleAlbum(final MediaItem album) {
        List<MediaItem> added = addedAlbums.remove(album.key());
        if (added != null) {
            for (MediaItem t : added) target.takeBack(t);
            refresh();
            return;
        }
        tracksOf(album, new Bg.Done<List<MediaItem>>() {
            @Override
            public void done(List<MediaItem> tracks, Exception error) {
                if (tracks == null || addedAlbums.containsKey(album.key())) return;
                List<MediaItem> missing = new ArrayList<MediaItem>();
                for (MediaItem t : tracks) {
                    if (!target.has(t)) missing.add(t);
                }
                target.add(missing);
                addedAlbums.put(album.key(), missing);
                if (dialog.isShowing()) refresh();
            }
        });
    }

    /** Rebuilds the rows for the tab (or open album) and the filter. */
    private void refresh() {
        int n = target.addedCount();
        subtitle.setText(n == 0 ? "Nothing added yet" : n == 1 ? "1 song added" : n + " songs added");
        back.setVisibility(openAlbum != null ? View.VISIBLE : View.INVISIBLE);
        tabs.setVisibility(openAlbum != null ? View.GONE : View.VISIBLE);
        filter.setHint(openAlbum != null ? "Filter " + openAlbum.title : tab == ALBUMS ? "Filter Albums" : "Filter Songs");
        List<MediaItem> all;
        String problem;
        if (openAlbum != null) {
            all = albumTracks.get(openAlbum.key());
            problem = null;
        } else if (tab == ALBUMS) {
            all = albums;
            problem = albumsError;
        } else {
            all = songs;
            problem = songsError;
        }
        rows.clear();
        String q = filter.getText().toString().trim().toLowerCase(Locale.getDefault());
        if (all != null) {
            for (MediaItem m : all) {
                if (q.length() == 0 || matches(m.title, q) || matches(m.artist, q) || matches(m.album, q)) rows.add(m);
            }
        }
        if (problem != null) status.setText(problem);
        else if (all == null) status.setText("Loading…");
        else if (rows.isEmpty()) status.setText(q.length() > 0 ? "Nothing matches." : "Nothing here yet.");
        status.setVisibility(problem != null || all == null || rows.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private static boolean matches(String s, String q) {
        return s != null && s.toLowerCase(Locale.getDefault()).contains(q);
    }

    private static final class Holder {
        ArtworkView art;
        TextView title;
        TextView subtitle;
        IconView action;
    }

    private final class RowsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Context c = context;
            Holder h;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Ui.dp(c, 56));
                row.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
                row.setBackgroundResource(Ui.resource(c, R.attr.gfRowBackground));
                h = new Holder();
                h.art = Ui.artwork(c, 44);
                row.addView(h.art);
                LinearLayout texts = new LinearLayout(c);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 6), 0);
                h.title = Ui.text(c, 16, R.attr.gfTextPrimary, false);
                h.subtitle = Ui.text(c, 13, R.attr.gfTextSecondary, false);
                texts.addView(h.title);
                texts.addView(h.subtitle);
                row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                h.action = new IconView(c);
                int pad = Ui.dp(c, 11);
                h.action.setPadding(pad, pad, pad, pad);
                row.addView(h.action, new LinearLayout.LayoutParams(Ui.dp(c, 46), Ui.dp(c, 46)));
                row.setTag(h);
                convertView = row;
            } else {
                h = (Holder) convertView.getTag();
            }
            final MediaItem m = rows.get(position);
            boolean song = m.kind == MediaItem.TRACK;
            ArtworkLoader.get(c).into(h.art, m, Ui.dp(c, 44));
            h.title.setText(m.title);
            String sub = song ? (openAlbum != null ? m.artist : RowAdapter.subtitle(m)) : m.artist;
            h.subtitle.setText(sub != null ? sub : "");
            h.subtitle.setVisibility(sub != null && sub.length() > 0 ? View.VISIBLE : View.GONE);
            boolean in = song ? target.has(m) : addedAlbums.containsKey(m.key());
            // ✓ in grapefruit can be taken back; a grey ✓ was in the playlist already.
            boolean undoable = song ? target.added(m) : in;
            h.action.setIcon(in ? Icons.CHECK : Icons.PLUS);
            h.action.setColor(in && !undoable ? Ui.color(c, R.attr.gfTextSecondary) : Ui.accent(c));
            h.action.setContentDescription(in ? (undoable ? "Take back " : "In the playlist: ") + m.title
                    : "Add " + m.title);
            h.action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (m.kind == MediaItem.TRACK) toggleSong(m);
                    else toggleAlbum(m);
                }
            });
            return convertView;
        }
    }
}
