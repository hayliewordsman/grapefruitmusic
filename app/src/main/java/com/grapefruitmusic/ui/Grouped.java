package com.grapefruitmusic.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.ui.widget.Toggle;

import java.util.Locale;

/** Builds iOS-style grouped lists (Settings and its sub-pages) into a vertical container. */
final class Grouped {
    final Context c;
    final LinearLayout content;

    private Grouped(Context c, LinearLayout content) {
        this.c = c;
        this.content = content;
    }

    /** A scrolling page with the grouped background; fill it with on(). */
    static ScrollView page(Context c) {
        ScrollView scroll = new ScrollView(c);
        scroll.setBackgroundColor(Ui.color(c, R.attr.gfGroupedBackground));
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(c, 32));
        scroll.addView(content);
        return scroll;
    }

    /** Clears the page and returns a builder for it. */
    static Grouped on(ScrollView page) {
        LinearLayout content = (LinearLayout) page.getChildAt(0);
        content.removeAllViews();
        return new Grouped(page.getContext(), content);
    }

    void add(View v) {
        content.addView(v);
    }

    void section(String title) {
        TextView t = Ui.text(c, 13, R.attr.gfTextSecondary, false);
        t.setText(title.toUpperCase(Locale.US));
        t.setPadding(Ui.dp(c, 16), Ui.dp(c, 26), Ui.dp(c, 16), Ui.dp(c, 6));
        content.addView(t);
        content.addView(Ui.separator(c, 0));
    }

    void footer(String text) {
        TextView t = Ui.text(c, 13, R.attr.gfTextSecondary, false);
        t.setSingleLine(false);
        t.setText(text);
        t.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), 0);
        content.addView(t);
    }

    /** An empty row to fill; pass it to addRow(). */
    LinearLayout row(View.OnClickListener click) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(c, 46));
        row.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        row.setBackgroundColor(Ui.color(c, R.attr.gfSurface));
        if (click != null) row.setOnClickListener(click);
        return row;
    }

    void addRow(LinearLayout row, boolean clickable) {
        if (clickable) {
            // Pressed highlight over the surface color.
            LinearLayout wrap = new LinearLayout(c);
            wrap.setBackgroundColor(Ui.color(c, R.attr.gfSurface));
            row.setBackgroundResource(Ui.resource(c, R.attr.gfRowBackground));
            wrap.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            content.addView(wrap);
        } else {
            content.addView(row);
        }
        content.addView(Ui.separator(c, 0));
    }

    /** A label and a gray value; returns the value's view (to update it later). */
    TextView value(String label, String value, View.OnClickListener click) {
        LinearLayout row = row(click);
        TextView l = Ui.text(c, 17, R.attr.gfTextPrimary, false);
        l.setText(label);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView v = Ui.text(c, 16, R.attr.gfTextSecondary, false);
        v.setText(value);
        v.setPadding(Ui.dp(c, 8), 0, 0, 0);
        row.addView(v);
        if (click != null) row.addView(Ui.icon(c, Icons.CHEVRON_RIGHT, Ui.color(c, R.attr.gfSeparator), 18));
        addRow(row, click != null);
        return v;
    }

    void action(String label, boolean destructive, View.OnClickListener click) {
        LinearLayout row = row(click);
        TextView l = Ui.text(c, 17, R.attr.gfAccent, false);
        if (destructive) l.setTextColor(0xffff3b30);
        l.setText(label);
        row.addView(l);
        addRow(row, true);
    }

    /** A label and a Toggle; tapping anywhere on the row switches it. */
    Toggle toggle(String label, boolean on, Toggle.Listener listener) {
        final Toggle t = new Toggle(c);
        LinearLayout row = row(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                t.performClick();
            }
        });
        TextView l = Ui.text(c, 17, R.attr.gfTextPrimary, false);
        l.setText(label);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        boolean dark = Ui.isDark(c);
        t.setOffColors(dark ? 0xff39393d : 0xffe3e3e8, dark ? 0xffd1d1d6 : 0xffdcdce0);
        t.setChecked(on);
        t.setListener(listener);
        t.setContentDescription(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = -Ui.dp(c, 4);
        row.addView(t, lp);
        addRow(row, true);
        return t;
    }
}
