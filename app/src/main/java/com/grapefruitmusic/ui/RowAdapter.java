package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.res.Configuration;
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

/**
 * Lists of artists, albums, playlists and songs: as rows (search results, and the Songs and
 * Playlists pages), or as a grid of cards (the other library pages), two across in portrait or
 * stacked in columns for a sideways HorizontalGrid.
 */
final class RowAdapter extends BaseAdapter {
    interface Listener {
        /** A tap. For songs, index is the position in tracks(). */
        void onItem(MediaItem item, int index);

        void onLongItem(MediaItem item);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;
    private static final int TYPE_GRID = 2;

    private final Context context;
    private final Listener listener;
    private final List<Object> rows = new ArrayList<Object>();
    private final List<MediaItem> tracks = new ArrayList<MediaItem>();
    private List<MediaItem> items = new ArrayList<MediaItem>();
    private boolean grid;
    private boolean sections;
    /** Cards per group: across a row, or down a column when stacked. */
    private int perGroup = 2;
    /** Groups are columns for a HorizontalGrid (landscape) instead of rows. */
    private boolean stacked;
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

    /** Is the screen sideways (library pages scroll sideways then)? */
    static boolean landscape(Context c) {
        return c.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * Cards per column on a sideways page: two if they fit in the height left after reservedDp
     * (tab bar, mini player, page header), else one.
     */
    int landscapeRows(int reservedDp) {
        float density = context.getResources().getDisplayMetrics().density;
        int text = Math.round(cardTextHeight() / density);
        int available = context.getResources().getConfiguration().screenHeightDp - reservedDp;
        return available >= 2 * (text + 110) + 14 ? 2 : 1;
    }

    /** For a HorizontalGrid: the grid is columns of rows cards each. */
    void setStacked(int rows) {
        stacked = true;
        perGroup = Math.max(1, rows);
        build();
    }

    /** Height of the text under a card's picture (for sizing a HorizontalGrid). */
    int cardTextHeight() {
        Card c = card();
        return c.title.getLineHeight() + c.artist.getLineHeight() + c.title.getPaddingTop() + Ui.dp(context, 2);
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
     * @param grid     show cards in a grid instead of rows
     * @param sections add "Artists" / "Albums" / "Songs" headings (search results)
     */
    void setItems(List<MediaItem> items, boolean grid, boolean sections) {
        this.items = items;
        this.grid = grid;
        this.sections = sections;
        build();
    }

    private void build() {
        rows.clear();
        tracks.clear();
        int lastKind = -1;
        MediaItem[] grid = null;
        int filled = 0;
        for (MediaItem m : items) {
            if (sections && m.kind != lastKind) {
                grid = null;
                rows.add(m.kind == MediaItem.ARTIST ? "Artists" : m.kind == MediaItem.ALBUM ? "Albums"
                        : m.kind == MediaItem.PLAYLIST ? "Playlists" : "Songs");
                lastKind = m.kind;
            }
            if (m.kind == MediaItem.TRACK) tracks.add(m);
            if (this.grid) {
                if (grid == null || filled == perGroup) {
                    grid = new MediaItem[perGroup];
                    filled = 0;
                    rows.add(grid);
                }
                grid[filled++] = m;
                continue;
            }
            grid = null;
            rows.add(m);
        }
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
        if (o instanceof MediaItem[]) return TYPE_GRID;
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
            case TYPE_GRID:
                return gridRow(position, convertView);
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
        IconView more;
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
            h.more = Ui.moreButton(context);
            h.more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onLongItem((MediaItem) v.getTag());
                }
            });
            row.addView(h.more);
            row.setTag(h);
            convertView = row;
        } else {
            h = (RowHolder) convertView.getTag();
        }
        MediaItem m = (MediaItem) rows.get(position);
        // Small for songs; medium for playlists on the Playlists page.
        int artSize = m.kind == MediaItem.TRACK ? 44 : m.kind == MediaItem.ARTIST ? 48
                : m.kind == MediaItem.PLAYLIST && !sections ? 72 : 56;
        ViewGroup.LayoutParams lp = h.art.getLayoutParams();
        lp.width = lp.height = Ui.dp(context, artSize);
        h.art.setLayoutParams(lp);
        h.art.setCircle(m.kind == MediaItem.ARTIST);
        ArtworkLoader.get(context).into(h.art, m, Ui.dp(context, artSize));
        h.title.setText(m.title);
        h.title.setTextColor(m.kind == MediaItem.TRACK && m.key().equals(playingKey) ? accent : primary);
        h.subtitle.setText(subtitle(m));
        h.subtitle.setVisibility(h.subtitle.getText().length() > 0 ? View.VISIBLE : View.GONE);
        h.more.setTag(m);
        h.more.setVisibility(m.kind == MediaItem.TRACK ? View.VISIBLE : View.GONE);
        if (m.kind == MediaItem.TRACK) {
            Integer state = downloadStates != null ? downloadStates.get(m.id) : null;
            if (state != null && state == Downloads.DONE) {
                h.accessory.setIcon(Icons.DOWNLOADED);
                h.accessory.setVisibility(View.VISIBLE);
            } else if (state != null && (state == Downloads.QUEUED || state == Downloads.ACTIVE)) {
                h.accessory.setIcon(Icons.DOWNLOAD);
                h.accessory.setVisibility(View.VISIBLE);
            } else {
                h.accessory.setVisibility(View.GONE);
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
        IconView more;
    }

    private View gridRow(int position, View convertView) {
        MediaItem[] group = (MediaItem[]) rows.get(position);
        Card[] cards = convertView != null ? (Card[]) convertView.getTag() : null;
        if (cards == null || cards.length != group.length) {
            LinearLayout box = new LinearLayout(context);
            int gap = Ui.dp(context, 14);
            cards = new Card[group.length];
            if (stacked) {
                // A column for a HorizontalGrid, which sets its width; cards top to bottom.
                box.setOrientation(LinearLayout.VERTICAL);
                for (int i = 0; i < cards.length; i++) {
                    cards[i] = card();
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (i > 0) lp.topMargin = gap;
                    box.addView(cards[i].root, lp);
                }
            } else {
                box.setOrientation(LinearLayout.HORIZONTAL);
                box.setPadding(Ui.dp(context, 16), Ui.dp(context, 4), Ui.dp(context, 16), gap);
                for (int i = 0; i < cards.length; i++) {
                    cards[i] = card();
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                    if (i > 0) lp.leftMargin = gap;
                    box.addView(cards[i].root, lp);
                }
            }
            box.setTag(cards);
            convertView = box;
        }
        for (int i = 0; i < cards.length; i++) bindCard(cards[i], group[i]);
        return convertView;
    }

    private Card card() {
        final Card c = new Card();
        LinearLayout v = new LinearLayout(context);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackgroundResource(Ui.resource(context, R.attr.gfRowBackground));
        c.art = Ui.artwork(context, 0);
        v.addView(c.art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        // Title, with a "⋯" for songs (the same menu as a long press).
        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        c.title = Ui.text(context, 14, R.attr.gfTextPrimary, false);
        c.title.setPadding(0, Ui.dp(context, 5), 0, 0);
        top.addView(c.title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        c.more = Ui.moreButton(context);
        int pad = Ui.dp(context, 3);
        c.more.setPadding(pad, Ui.dp(context, 5), 0, 0);
        c.more.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(context, 22), Ui.dp(context, 22)));
        c.more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onLongItem((MediaItem) view.getTag());
            }
        });
        top.addView(c.more);
        v.addView(top);
        c.artist = Ui.text(context, 14, R.attr.gfTextSecondary, false);
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
        boolean track = m.kind == MediaItem.TRACK;
        c.title.setTextColor(track && m.key().equals(playingKey) ? accent : primary);
        c.artist.setText(subtitle(m));
        c.art.setCircle(m.kind == MediaItem.ARTIST);
        c.more.setTag(m);
        c.more.setVisibility(track ? View.VISIBLE : View.GONE);
        ArtworkLoader.get(context).into(c.art, m, Ui.dp(context, 180));
        c.root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onItem(m, m.kind == MediaItem.TRACK ? tracks.indexOf(m) : -1);
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
