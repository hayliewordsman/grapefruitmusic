package com.grapefruitmusic.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.ui.widget.Toggle;


/** Builds Settings and its sub-pages: section titles over rounded rows, into a vertical container. */
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
        // On a wide screen the rows keep to a readable width, centred.
        final int widest = Ui.dp(c, 640);
        LinearLayout content = new LinearLayout(c) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int side = Math.max(0, (MeasureSpec.getSize(widthMeasureSpec) - widest) / 2);
                if (getPaddingLeft() != side) setPadding(side, getPaddingTop(), side, getPaddingBottom());
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
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
        TextView t = Ui.text(c, 15, R.attr.gfAccent, true);
        t.setText(title);
        t.setPadding(Ui.dp(c, 18), Ui.dp(c, 24), Ui.dp(c, 16), Ui.dp(c, 6));
        content.addView(t);
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
        row.setMinimumHeight(Ui.dp(c, 56));
        // Inset further, for the rounder ends.
        row.setPadding(Ui.dp(c, 22), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        row.setBackgroundColor(Ui.color(c, R.attr.gfSurface));
        if (click != null) row.setOnClickListener(click);
        return row;
    }

    void addRow(LinearLayout row, boolean clickable) {
        row.setBackground(Ui.actionBackground(c));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(c,12),Ui.dp(c,4),Ui.dp(c,12),Ui.dp(c,4));
        content.addView(row,lp);
    }

    /** A label and a gray value; returns the value's view (to update it later). */
    TextView value(String label, String value, View.OnClickListener click) {
        LinearLayout row = row(click);
        TextView l = Ui.text(c, 16, R.attr.gfTextPrimary, false);
        l.setText(label);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView v = Ui.text(c, 15, R.attr.gfTextSecondary, false);
        v.setText(value);
        v.setPadding(Ui.dp(c, 8), 0, 0, 0);
        row.addView(v);
        addRow(row, click != null);
        return v;
    }

    void action(String label, boolean destructive, View.OnClickListener click) {
        LinearLayout row = row(click);
        TextView l = Ui.text(c, 16, R.attr.gfAccent, true);
        if (destructive) l.setTextColor(0xffe5484d);
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
        TextView l = Ui.text(c, 16, R.attr.gfTextPrimary, false);
        l.setText(label);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        t.setOffColors(Ui.color(c, R.attr.gfToggleOff), Ui.color(c, R.attr.gfToggleOffSegments));
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
