package com.grapefruitmusic.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.ui.widget.HorizontalGrid;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A tab's menu, the same on each of its sections: the tab's title, then chips: Search, where the
 * music comes from (the Library), and one for each section (the Library's Recently Added,
 * Playlists, Artists, Albums and Songs; Podcasts' Discover, Subscriptions and Downloads). A section
 * chip swaps the section under the menu (MainActivity.showLibrary, showPodcasts) instead of opening
 * a page on top, so the chips are always there to go to another; the first is the tab's first
 * page, where Back goes too.
 *
 * <p>The title scrolls with the page. Up and down, the chips sit in a row under it and, once the
 * page scrolls them up to the top, stay there, floating over the page as it scrolls under them (as
 * the mini player and tabs do at the bottom). On a wide screen the chips are stacked in a column
 * floating at the left, and the page (title first) scrolls beside and under it.
 */
final class SectionMenu {
    /** Each tab's first section: the Library's Recently Added, Podcasts' Discover. */
    static final int FIRST = 0;
    /** The Library's sections: Recently Added, then BrowseFragment's PLAYLISTS to SONGS (1 to 4). */
    static final String[] LIBRARY = {"Recently Added", "Playlists", "Artists", "Albums", "Songs"};
    /** Podcasts' sections after Discover. */
    static final int SUBSCRIPTIONS = 1, DOWNLOADS = 2;
    static final String[] PODCASTS = {"Discover", "Subscriptions", "Downloads"};

    /** Width of the chip column on a wide screen, with the fade at its right edge. */
    private static final int COLUMN_DP = 232;
    /** How far pages fade out under the floating chips. */
    private static final int FADE_DP = 20;
    /**
     * On a wide screen, the height cards leave for the page's title, the mini player and their
     * grid's padding (RowAdapter.landscapeRows: two cards high if they fit).
     */
    static final int WIDE_RESERVED_DP = 92 + 64;

    /** Shows a section: MainActivity.showLibrary or showPodcasts. */
    interface Picker {
        void show(int section);
    }

    /*
     * Each section builds its own menu, so where a tab's chips were scrolled to is kept here (by the
     * tab's title), for its next section's menu to start in the same place.
     */
    private static final Map<String, Integer> scrolled = new HashMap<String, Integer>();

    /** Stacked down a floating column on the left (a wide screen), or in a row across the top. */
    final boolean wide;
    private final Context context;
    private final String title;
    /** The tab's title, which scrolls with the page. */
    private final LinearLayout header;
    /** The chips' scroller: a row that scrolls sideways, or (wide) the column. */
    private final ViewGroup chipsView;
    /** The chips themselves, inside chipsView. */
    private final LinearLayout chips;
    /** Lines for this section after the title (Podcasts: how Discover's shows are chosen). */
    private final List<View> notes = new ArrayList<View>();
    private View chosenChip;
    /** Where the section chips start among chips (after Search and where the music comes from). */
    private int firstSection;
    private TextView sourceName;
    /** Up and down: the room in the page where the chips sit until they float. */
    private View slot;
    private int slotPosition;

    /** The Library's menu: Search, where the music comes from, and its sections. */
    static SectionMenu library(final BaseFragment page, int chosen) {
        SectionMenu menu = new SectionMenu(page, "Library");
        menu.addSearch(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                page.main().showTab(MainActivity.TAB_SEARCH);
            }
        });
        menu.addSource(page);
        menu.addRule();
        menu.addSections(LIBRARY, chosen, new Picker() {
            @Override
            public void show(int section) {
                page.main().showLibrary(section);
            }
        });
        return menu;
    }

    /** Podcasts' menu: Search (Search Podcasts) and its sections. */
    static SectionMenu podcasts(final BaseFragment page, int chosen, View.OnClickListener search) {
        SectionMenu menu = new SectionMenu(page, "Podcasts");
        menu.addSearch(search);
        menu.addRule();
        menu.addSections(PODCASTS, chosen, new Picker() {
            @Override
            public void show(int section) {
                page.main().showPodcasts(section);
            }
        });
        return menu;
    }

    private SectionMenu(BaseFragment page, String title) {
        Context c = page.getActivity();
        context = c;
        this.title = title;
        wide = RowAdapter.landscape(c);
        header = Ui.header(c, null, title, null);
        if (wide) header.setPadding(header.getPaddingLeft(), Ui.dp(c, 14), header.getPaddingRight(), Ui.dp(c, 6));
        if (wide) {
            chips = new LinearLayout(c);
            chips.setOrientation(LinearLayout.VERTICAL);
            chips.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16 + FADE_DP), Ui.dp(c, 8));
            ScrollView column = new ScrollView(c);
            column.setVerticalScrollBarEnabled(false);
            column.setOverScrollMode(View.OVER_SCROLL_NEVER);
            column.addView(chips);
            // The page scrolls under the column's right edge, fading out.
            column.setBackground(Ui.fadingFill(Ui.color(c, R.attr.gfBackground), Ui.dp(c, FADE_DP), true));
            chipsView = column;
        } else {
            HorizontalScrollView row = Ui.chipRow(c);
            chips = (LinearLayout) row.getChildAt(0);
            chipsView = row;
        }
    }

    /** A pill with an icon and a label: Search. */
    private void addSearch(View.OnClickListener search) {
        Context c = context;
        LinearLayout chip = new LinearLayout(c);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(Ui.pressable(c, Ui.color(c, R.attr.gfControl), Ui.PILL, true));
        chip.addView(Ui.icon(c, Icons.SEARCH, Ui.color(c, R.attr.gfTextPrimary), 16));
        TextView label = Ui.text(c, 14, R.attr.gfTextPrimary, false);
        label.setTypeface(Fonts.medium());
        label.setText("Search");
        label.setPadding(Ui.dp(c, 7), 0, 0, 0);
        chip.addView(label);
        chip.setContentDescription(title.equals("Podcasts") ? "Search Podcasts" : "Search");
        chip.setOnClickListener(search);
        addTool(chip);
    }

    /**
     * Where the music comes from: a plain pill with an arrow (it opens a list), leaving the accent
     * to the section showing.
     */
    private void addSource(final BaseFragment page) {
        Context c = context;
        LinearLayout source = new LinearLayout(c);
        source.setOrientation(LinearLayout.HORIZONTAL);
        source.setGravity(Gravity.CENTER_VERTICAL);
        source.setBackground(Ui.pressable(c, Ui.color(c, R.attr.gfControl), Ui.PILL, true));
        sourceName = Ui.text(c, 14, R.attr.gfTextPrimary, false);
        sourceName.setTypeface(Fonts.medium());
        // Stacked, the arrow goes at the far end.
        source.addView(sourceName, wide ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        IconView down = Ui.icon(c, Icons.CHEVRON_DOWN, Ui.color(c, R.attr.gfTextSecondary), 14);
        ((LinearLayout.LayoutParams) down.getLayoutParams()).leftMargin = Ui.dp(c, 6);
        source.addView(down);
        source.setContentDescription("Choose where the music comes from");
        source.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickSource(page.getActivity());
            }
        });
        setSource(Sources.get(c).current());
        addTool(source);
    }

    /** Search and the source: pills before the sections, padded like them. */
    private void addTool(LinearLayout tool) {
        Context c = context;
        if (wide) {
            tool.setMinimumHeight(Ui.dp(c, 40));
            tool.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 16), 0);
            chips.addView(tool, stacked(c));
        } else {
            tool.setMinimumHeight(Ui.dp(c, 38));
            tool.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0);
            chips.addView(tool, inRow(c));
        }
    }

    /** Between the tools and the sections: a hairline in the row, a gap down the column. */
    private void addRule() {
        Context c = context;
        if (wide) {
            View gap = new View(c);
            chips.addView(gap, new LinearLayout.LayoutParams(1, Ui.dp(c, 8)));
            return;
        }
        View rule = new View(c);
        rule.setBackgroundColor(Ui.color(c, R.attr.gfOutline));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, Ui.dp(c, 1)), Ui.dp(c, 22));
        lp.rightMargin = Ui.dp(c, 8);
        chips.addView(rule, lp);
    }

    private void addSections(String[] names, int chosen, final Picker picker) {
        Context c = context;
        firstSection = chips.getChildCount();
        for (int i = 0; i < names.length; i++) {
            final int section = i;
            TextView chip = Ui.chip(c, names[i], i == chosen);
            if (i == chosen) chosenChip = chip;
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v == chosenChip) return;
                    scrolled.put(title, wide ? chipsView.getScrollY() : chipsView.getScrollX());
                    picker.show(section);
                }
            });
            if (wide) {
                chip.setMinHeight(Ui.dp(c, 40));
                chip.setGravity(Gravity.CENTER_VERTICAL);
                chip.setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 16), 0);
                chips.addView(chip, stacked(c));
            } else {
                chips.addView(chip, inRow(c));
            }
        }
        keepScroll();
    }

    private static LinearLayout.LayoutParams stacked(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 40));
        lp.bottomMargin = Ui.dp(c, 6);
        return lp;
    }

    private static LinearLayout.LayoutParams inRow(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(c, 8);
        return lp;
    }

    void setSource(MusicSource s) {
        if (sourceName != null) sourceName.setText(s.name());
    }

    /** A line for this section after the title (and the chips): call before addHeaders. */
    void addNote(View note) {
        Context c = context;
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 16), Ui.dp(c, 2), Ui.dp(c, 16), Ui.dp(c, 6));
        box.addView(note, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        notes.add(box);
    }

    /**
     * The top of a page that scrolls up and down: the title, then (up and down) the room where the
     * chips sit until they float, then any notes. Call before the page adds its own headers and
     * its adapter (Android 4.2 takes headers only before the adapter), then build it with page().
     */
    void addHeaders(ListView list) {
        list.addHeaderView(header, null, false);
        if (!wide) {
            chipsView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            slot = new View(context);
            slot.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    chipsView.getMeasuredHeight()));
            slotPosition = list.getHeaderViewsCount();
            list.addHeaderView(slot, null, false);
        }
        for (View note : notes) list.addHeaderView(note, null, false);
    }

    /** A page that scrolls up and down (after addHeaders), with the chips floating over it. */
    View page(ListView list) {
        if (wide) {
            list.setPadding(Ui.dp(context, COLUMN_DP), list.getPaddingTop(), list.getPaddingRight(),
                    list.getPaddingBottom());
            return withColumn(list);
        }
        return new FloatingChips(list);
    }

    /**
     * A wide screen's cards scrolling sideways: the title (and status, for loading and empty) above
     * the first columns, scrolling with them, and the cards sliding under the chip column.
     */
    View page(HorizontalGrid grid, View status) {
        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.addView(header);
        for (View note : notes) heading.addView(note);
        if (status != null) heading.addView(status);
        grid.setTitle(heading);
        grid.setPadding(Ui.dp(context, COLUMN_DP), grid.getPaddingTop(), grid.getPaddingRight(),
                grid.getPaddingBottom());
        return withColumn(grid);
    }

    /** A page that doesn't scroll (Podcasts offline): the title and the chips above it, or beside it. */
    View pinned(View body) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(header);
        if (!wide) {
            column.addView(chipsView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        for (View note : notes) column.addView(note);
        column.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        if (!wide) return column;
        column.setPadding(Ui.dp(context, COLUMN_DP), 0, 0, 0);
        return withColumn(column);
    }

    /** A wide page with the chip column floating at its left. */
    private View withColumn(View page) {
        FrameLayout frame = new FrameLayout(context);
        frame.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(chipsView, new FrameLayout.LayoutParams(Ui.dp(context, COLUMN_DP),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
        return frame;
    }

    /**
     * Up and down: the list, and the chip row over it, kept where its slot is in the list, or at
     * the top once the slot has scrolled past. There the page shows through a fade under the chips
     * as it scrolls by, like the floating mini player and tabs.
     */
    private final class FloatingChips extends FrameLayout implements ViewTreeObserver.OnPreDrawListener {
        private final ListView list;
        private final android.graphics.drawable.Drawable fade;
        private final int fadeIn;
        private int alpha = -1;

        FloatingChips(ListView list) {
            super(list.getContext());
            this.list = list;
            Context c = getContext();
            addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout bar = new LinearLayout(c);
            bar.setOrientation(LinearLayout.VERTICAL);
            bar.setPadding(0, 0, 0, Ui.dp(c, FADE_DP));
            bar.addView(chipsView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            fade = Ui.fadingFill(Ui.color(c, R.attr.gfBackground), Ui.dp(c, FADE_DP), false);
            bar.setBackground(fade);
            fadeIn = Ui.dp(c, 12);
            addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnPreDrawListener(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            getViewTreeObserver().removeOnPreDrawListener(this);
            super.onDetachedFromWindow();
        }

        @Override
        public boolean onPreDraw() {
            // Where the slot is: on screen, scrolled past the top, or (not laid out yet) under the title.
            int y;
            if (list.indexOfChild(slot) >= 0) y = list.getTop() + slot.getTop();
            else if (list.getFirstVisiblePosition() > slotPosition) y = -fadeIn;
            else y = list.getTop() + header.getHeight();
            View bar = getChildAt(1);
            float top = Math.max(0, y);
            if (bar.getTranslationY() != top) bar.setTranslationY(top);
            // The fade behind the chips comes in as the page starts to pass under them.
            int a = y >= 0 ? 0 : Math.min(255, -y * 255 / fadeIn);
            if (a != alpha) {
                alpha = a;
                fade.setAlpha(a);
            }
            return true;
        }
    }

    /**
     * Once laid out: scrolled to where the last section's chips were, then (if need be) on so the
     * chosen chip shows in full; at the first section, the start.
     */
    private void keepScroll() {
        final View scroller = chipsView;
        scroller.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r - l == 0 || b - t == 0) return;
                v.removeOnLayoutChangeListener(this);
                // At the first section, the start: Search and the source show first.
                if (chosenChip == chips.getChildAt(firstSection)) {
                    v.scrollTo(0, 0);
                    return;
                }
                Integer was = scrolled.get(title);
                int at = was != null ? was : 0;
                int margin = Ui.dp(v.getContext(), 16);
                if (wide) {
                    if (chosenChip != null) {
                        int top = chosenChip.getTop() + chips.getTop();
                        int bottom = top + chosenChip.getHeight();
                        int visible = v.getHeight() - v.getPaddingBottom();
                        if (bottom + margin > at + visible) at = bottom + margin - visible;
                        if (top - margin < at) at = Math.max(0, top - margin);
                    }
                    v.scrollTo(0, at);
                } else {
                    if (chosenChip != null) {
                        int left = chosenChip.getLeft();
                        int right = chosenChip.getRight();
                        if (right + margin > at + v.getWidth()) at = right + margin - v.getWidth();
                        if (left - margin < at) at = Math.max(0, left - margin);
                    }
                    v.scrollTo(at, 0);
                }
            }
        });
    }

    /** "Show music from": the phone, downloads and each server, or a new server. */
    static void pickSource(final Activity a) {
        final List<MusicSource> all = Sources.get(a).all();
        String[] names = new String[all.size() + 1];
        for (int i = 0; i < all.size(); i++) names[i] = all.get(i).name() + "  ·  " + all.get(i).typeLabel();
        names[all.size()] = "Add a Server…";
        Ui.dialog(a)
                .setTitle("Show music from")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < all.size()) Sources.get(a).setCurrent(all.get(which));
                        else AddServerDialog.show(a);
                    }
                })
                .show();
    }
}
