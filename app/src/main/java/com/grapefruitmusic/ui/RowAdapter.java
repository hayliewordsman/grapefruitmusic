package com.grapefruitmusic.ui;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.ui.widget.ArtworkView;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.ArtworkLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lists of artists, albums (as rows or a two-column grid), playlists and songs. */
final class RowAdapter extends BaseAdapter {
    interface Listener {
        /** A tap. For songs, index is the position in tracks(). */
        void onItem(MediaItem item, int index);

        void onLongItem(MediaItem item);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;
    private static final int TYPE_PAIR = 2;

    private final Context context;
    private final Listener listener;
    private final List<Object> rows = new ArrayList<Object>();
    private final List<MediaItem> tracks = new ArrayList<MediaItem>();
    private Map<String, Integer> downloadStates;
    private String playingKey;
    private final int accent;
    private final int primary;
    private final int secondary;

    RowAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        accent = Ui.accent(context);
        primary = Ui.color(context, R.attr.gfTextPrimary);
        secondary = Ui.color(context, R.attr.gfTextSecondary);
    }

    /** Styles a page's ListView: inset hairlines, no Holo highlight. */
    static void style(ListView list) {
        Context c = list.getContext();
        list.setDivider(new InsetDrawable(new ColorDrawable(Ui.color(c, R.attr.gfSeparator)), Ui.dp(c, 16), 0, 0, 0));
        list.setDividerHeight(Math.max(1, Ui.dp(c, 0.5f)));
        list.setSelector(new ColorDrawable(0));
        list.setCacheColorHint(0);
        list.setHeaderDividersEnabled(false);
        list.setFooterDividersEnabled(false);
        list.setVerticalScrollBarEnabled(false);
    }

    /**
     * @param albumGrid show albums as a two-column grid
     * @param sections  add "Artists" / "Albums" / "Songs" headings (search results)
     */
    void setItems(List<MediaItem> items, boolean albumGrid, boolean sections) {
        rows.clear();
        tracks.clear();
        int lastKind = -1;
        MediaItem pending = null;
        for (MediaItem m : items) {
            if (sections && m.kind != lastKind) {
                if (pending != null) {
                    rows.add(new MediaItem[]{pending, null});
                    pending = null;
                }
                rows.add(m.kind == MediaItem.ARTIST ? "Artists" : m.kind == MediaItem.ALBUM ? "Albums"
                        : m.kind == MediaItem.PLAYLIST ? "Playlists" : "Songs");
                lastKind = m.kind;
            }
            if (albumGrid && m.kind == MediaItem.ALBUM) {
                if (pending == null) {
                    pending = m;
                } else {
                    rows.add(new MediaItem[]{pending, m});
                    pending = null;
                }
                continue;
            }
            if (pending != null) {
                rows.add(new MediaItem[]{pending, null});
                pending = null;
            }
            rows.add(m);
            if (m.kind == MediaItem.TRACK) tracks.add(m);
        }
        if (pending != null) rows.add(new MediaItem[]{pending, null});
        notifyDataSetChanged();
    }

    List<MediaItem> tracks() {
        return tracks;
    }

    void setDownloadStates(Map<String, Integer> states) {
        downloadStates = states;
        notifyDataSetChanged();
    }

    void setPlaying(MediaItem current) {
        String key = current != null ? current.key() : null;
        if (key == null ? playingKey != null : !key.equals(playingKey)) {
            playingKey = key;
            notifyDataSetChanged();
        }
    }

    /** Called by the ListView's item click listener. */
    void click(int position) {
        Object o = rows.get(position);
        if (o instanceof MediaItem) {
            MediaItem m = (MediaItem) o;
            listener.onItem(m, m.kind == MediaItem.TRACK ? tracks.indexOf(m) : -1);
        }
    }

    void longClick(int position) {
        Object o = rows.get(position);
        if (o instanceof MediaItem) listener.onLongItem((MediaItem) o);
    }

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
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public int getItemViewType(int position) {
        Object o = rows.get(position);
        if (o instanceof String) return TYPE_HEADER;
        if (o instanceof MediaItem[]) return TYPE_PAIR;
        return TYPE_ROW;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return getItemViewType(position) == TYPE_ROW;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        switch (getItemViewType(position)) {
            case TYPE_HEADER:
                return header(position, convertView);
            case TYPE_PAIR:
                return pair(position, convertView);
            default:
                return row(position, convertView);
        }
    }

    private View header(int position, View convertView) {
        TextView t = (TextView) convertView;
        if (t == null) {
            t = Ui.text(context, 20, R.attr.gfTextPrimary, true);
            t.setPadding(Ui.dp(context, 16), Ui.dp(context, 18), Ui.dp(context, 16), Ui.dp(context, 6));
        }
        t.setText((String) rows.get(position));
        return t;
    }

    private static final class RowHolder {
        ArtworkView art;
        TextView title;
        TextView subtitle;
        IconView accessory;
    }

    private View row(int position, View convertView) {
        RowHolder h;
        if (convertView == null) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(context, 56));
            row.setPadding(Ui.dp(context, 16), Ui.dp(context, 6), Ui.dp(context, 10), Ui.dp(context, 6));
            row.setBackgroundResource(Ui.resource(context, R.attr.gfRowBackground));
            h = new RowHolder();
            h.art = Ui.artwork(context, 48);
            row.addView(h.art);
            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 6), 0);
            h.title = Ui.text(context, 16, R.attr.gfTextPrimary, false);
            h.subtitle = Ui.text(context, 13, R.attr.gfTextSecondary, false);
            texts.addView(h.title);
            texts.addView(h.subtitle);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            h.accessory = Ui.icon(context, Icons.CHEVRON_RIGHT, secondary, 18);
            row.addView(h.accessory);
            row.setTag(h);
            convertView = row;
        } else {
            h = (RowHolder) convertView.getTag();
        }
        MediaItem m = (MediaItem) rows.get(position);
        int artSize = m.kind == MediaItem.TRACK ? 44 : m.kind == MediaItem.ARTIST ? 48 : 56;
        ViewGroup.LayoutParams lp = h.art.getLayoutParams();
        lp.width = lp.height = Ui.dp(context, artSize);
        h.art.setLayoutParams(lp);
        h.art.setCircle(m.kind == MediaItem.ARTIST);
        ArtworkLoader.get(context).into(h.art, m, Ui.dp(context, artSize));
        h.title.setText(m.title);
        h.title.setTextColor(m.kind == MediaItem.TRACK && m.key().equals(playingKey) ? accent : primary);
        h.subtitle.setText(subtitle(m));
        h.subtitle.setVisibility(h.subtitle.getText().length() > 0 ? View.VISIBLE : View.GONE);
        if (m.kind == MediaItem.TRACK) {
            Integer state = downloadStates != null ? downloadStates.get(m.id) : null;
            if (state != null && state == Downloads.DONE) {
                h.accessory.setIcon(Icons.DOWNLOADED);
                h.accessory.setVisibility(View.VISIBLE);
            } else if (state != null && (state == Downloads.QUEUED || state == Downloads.ACTIVE)) {
                h.accessory.setIcon(Icons.DOWNLOAD);
                h.accessory.setVisibility(View.VISIBLE);
            } else {
                h.accessory.setVisibility(View.INVISIBLE);
            }
        } else {
            h.accessory.setIcon(Icons.CHEVRON_RIGHT);
            h.accessory.setVisibility(View.VISIBLE);
        }
        return convertView;
    }

    static String subtitle(MediaItem m) {
        switch (m.kind) {
            case MediaItem.ARTIST:
                return m.count > 0 ? m.count + (m.count == 1 ? " album" : " albums") : "";
            case MediaItem.ALBUM:
                return m.artist != null ? m.artist : "";
            case MediaItem.PLAYLIST:
                return m.count > 0 ? m.count + (m.count == 1 ? " song" : " songs") : "";
            default:
                String s = m.artist != null ? m.artist : "";
                if (m.album != null) s = s.length() == 0 ? m.album : s + " — " + m.album;
                return s;
        }
    }

    private static final class Card {
        View root;
        ArtworkView art;
        TextView title;
        TextView artist;
    }

    private View pair(int position, View convertView) {
        Card[] cards;
        if (convertView == null) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(Ui.dp(context, 16), Ui.dp(context, 4), Ui.dp(context, 16), Ui.dp(context, 14));
            cards = new Card[]{card(), card()};
            LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            right.leftMargin = Ui.dp(context, 14);
            row.addView(cards[0].root, left);
            row.addView(cards[1].root, right);
            row.setTag(cards);
            convertView = row;
        } else {
            cards = (Card[]) convertView.getTag();
        }
        MediaItem[] pair = (MediaItem[]) rows.get(position);
        for (int i = 0; i < 2; i++) bindCard(cards[i], pair[i]);
        return convertView;
    }

    private Card card() {
        Card c = new Card();
        LinearLayout v = new LinearLayout(context);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackgroundResource(Ui.resource(context, R.attr.gfRowBackground));
        c.art = Ui.artwork(context, 0);
        v.addView(c.art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        c.title = Ui.text(context, 14, R.attr.gfTextPrimary, false);
        c.title.setPadding(0, Ui.dp(context, 5), 0, 0);
        c.artist = Ui.text(context, 14, R.attr.gfTextSecondary, false);
        v.addView(c.title);
        v.addView(c.artist);
        c.root = v;
        return c;
    }

    private void bindCard(Card c, final MediaItem m) {
        if (m == null) {
            c.root.setVisibility(View.INVISIBLE);
            c.root.setOnClickListener(null);
            c.root.setOnLongClickListener(null);
            return;
        }
        c.root.setVisibility(View.VISIBLE);
        c.title.setText(m.title);
        c.artist.setText(m.artist != null ? m.artist : "");
        ArtworkLoader.get(context).into(c.art, m, Ui.dp(context, 180));
        c.root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onItem(m, -1);
            }
        });
        c.root.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                listener.onLongItem(m);
                return true;
            }
        });
    }
}
