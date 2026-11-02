package com.grapefruitmusic.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A tab's menu, the same on each of its sections: the tab's title and a search button, then a chip
 * for each section (the Library's Recently Added, Playlists, Artists, Albums and Songs, after where
 * the music comes from; Podcasts' Discover, Subscriptions and Downloads). A chip swaps the section
 * under the menu (MainActivity.showLibrary, showPodcasts) instead of opening a page on top, so the
 * chips are always there to go to another. The first chip is the tab's first page, where Back goes
 * too. Up and down, the menu is pinned above the section; on a wide screen it is a column down the
 * left, with the chips stacked.
 */
final class SectionMenu {
    /** Each tab's first section: the Library's Recently Added, Podcasts' Discover. */
    static final int FIRST = 0;
    /** The Library's sections: Recently Added, then BrowseFragment's PLAYLISTS to SONGS (1 to 4). */
    private static final String[] LIBRARY = {"Recently Added", "Playlists", "Artists", "Albums", "Songs"};
    /** Podcasts' sections after Discover. */
    static final int SUBSCRIPTIONS = 1, DOWNLOADS = 2;
    private static final String[] PODCASTS = {"Discover", "Subscriptions", "Downloads"};

    /** Width of the menu column on a wide screen. */
    private static final int COLUMN_DP = 260;
    /**
     * Beside the menu column, the height cards leave for the mini player and their grid's padding
     * (RowAdapter.landscapeRows: two cards high if they fit).
     */
    static final int WIDE_RESERVED_DP = 92;

    /** Shows a section: MainActivity.showLibrary or showPodcasts. */
    interface Picker {
        void show(int section);
    }

    /*
     * Each section builds its own menu, so where a tab's chips were scrolled to is kept here (by the
     * tab's title), for its next section's menu to start in the same place.
     */
    private static final Map<String, Integer> scrolled = new HashMap<String, Integer>();

    /** Stacked down a column on the left (a wide screen), or in a row across the top. */
    final boolean wide;
    private final String title;
    private final View view;
    private final LinearLayout body;
    /** The chips: the row inside a sideways scroller, or the stack. */
    private final LinearLayout chips;
    private View chosenChip;
    /** Where the section chips start among chips (after where the music comes from). */
    private int firstSection;
    private TextView sourceName;

    /** The Library's menu, with where the music comes from before its sections. */
    static SectionMenu library(final BaseFragment page, int chosen) {
        SectionMenu menu = new SectionMenu(page, "Library", "Search", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                page.main().showTab(MainActivity.TAB_SEARCH);
            }
        });
        menu.addSource(page);
        menu.addSections(LIBRARY, chosen, new Picker() {
            @Override
            public void show(int section) {
                page.main().showLibrary(section);
            }
        });
        return menu;
    }

    /** Podcasts' menu; search opens Search Podcasts. */
    static SectionMenu podcasts(final BaseFragment page, int chosen, View.OnClickListener search) {
        SectionMenu menu = new SectionMenu(page, "Podcasts", "Search Podcasts", search);
        menu.addSections(PODCASTS, chosen, new Picker() {
            @Override
            public void show(int section) {
                page.main().showPodcasts(section);
            }
        });
        return menu;
    }

    private SectionMenu(BaseFragment page, String title, String searchLabel, View.OnClickListener search) {
        Context c = page.getActivity();
        this.title = title;
        wide = RowAdapter.landscape(c);
        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout header = Ui.header(c, null, title, null);
        if (wide) header.setPadding(header.getPaddingLeft(), Ui.dp(c, 14), header.getPaddingRight(), Ui.dp(c, 6));
        IconView button = Ui.circleButton(c, Icons.SEARCH, 42, searchLabel);
        button.setOnClickListener(search);
        Ui.headerAction(header, button);
        body.addView(header);
        if (wide) {
            chips = new LinearLayout(c);
            chips.setOrientation(LinearLayout.VERTICAL);
            chips.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 6));
            body.addView(chips);
            ScrollView column = new ScrollView(c);
            column.setVerticalScrollBarEnabled(false);
            column.addView(body);
            view = column;
        } else {
            HorizontalScrollView row = Ui.chipRow(c);
            chips = (LinearLayout) row.getChildAt(0);
            body.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            view = body;
        }
    }

    /**
     * Where the music comes from: a plain pill with an arrow (it opens a list), leaving the accent
     * to the section showing; set apart from the sections by a hairline, or a gap when stacked.
     */
    private void addSource(final BaseFragment page) {
        Context c = chips.getContext();
        LinearLayout source = new LinearLayout(c);
        source.setOrientation(LinearLayout.HORIZONTAL);
        source.setGravity(Gravity.CENTER_VERTICAL);
        source.setBackground(Ui.pressable(c, Ui.color(c, R.attr.gfControl), Ui.dp(c, 19), true));
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
        if (wide) {
            source.setMinimumHeight(Ui.dp(c, 40));
            source.setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 16), 0);
            LinearLayout.LayoutParams lp = stacked(c);
            lp.bottomMargin = Ui.dp(c, 14);
            chips.addView(source, lp);
        } else {
            source.setMinimumHeight(Ui.dp(c, 38));
            source.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 12), 0);
            chips.addView(source, inRow(c));
            View rule = new View(c);
            rule.setBackgroundColor(Ui.color(c, R.attr.gfOutline));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, Ui.dp(c, 1)), Ui.dp(c, 22));
            lp.rightMargin = Ui.dp(c, 8);
            chips.addView(rule, lp);
        }
    }

    private void addSections(String[] names, int chosen, final Picker picker) {
        Context c = chips.getContext();
        firstSection = chips.getChildCount();
        for (int i = 0; i < names.length; i++) {
            final int section = i;
            TextView chip = Ui.chip(c, names[i], i == chosen);
            if (i == chosen) chosenChip = chip;
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v == chosenChip) return;
                    scrolled.put(title, wide ? view.getScrollY() : ((View) chips.getParent()).getScrollX());
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

    /** A line for this section under the chips (Podcasts: how Discover's shows are chosen). */
    void addNote(View note) {
        Context c = note.getContext();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(c, 16);
        lp.rightMargin = Ui.dp(c, 16);
        lp.topMargin = Ui.dp(c, wide ? 6 : 2);
        lp.bottomMargin = Ui.dp(c, 6);
        body.addView(note, lp);
    }

    /** The menu and a section together: the menu above it, or (wide) beside it. */
    View around(View section) {
        Context c = view.getContext();
        LinearLayout page = new LinearLayout(c);
        if (wide) {
            page.setOrientation(LinearLayout.HORIZONTAL);
            page.addView(view, new LinearLayout.LayoutParams(Ui.dp(c, COLUMN_DP), ViewGroup.LayoutParams.MATCH_PARENT));
            page.addView(section, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        } else {
            page.setOrientation(LinearLayout.VERTICAL);
            page.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            page.addView(section, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }
        return page;
    }

    /**
     * Once laid out: scrolled to where the last section's chips were, then (if need be) on so the
     * chosen chip shows in full; at the first section, back to the start.
     */
    private void keepScroll() {
        final View scroller = wide ? view : (View) chips.getParent();
        scroller.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r - l == 0 || b - t == 0) return;
                v.removeOnLayoutChangeListener(this);
                Integer was = scrolled.get(title);
                int at = was != null && chosenChip != chips.getChildAt(firstSection) ? was : 0;
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
