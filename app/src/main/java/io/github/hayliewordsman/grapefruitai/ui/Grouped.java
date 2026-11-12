package io.github.hayliewordsman.grapefruitai.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Grouped settings rows: accent section titles over pill rows, kept to a readable width. */
public final class Grouped {
    public final Context c;
    public final LinearLayout content;

    private Grouped(Context c, LinearLayout content) {
        this.c = c;
        this.content = content;
    }

    public static ScrollView page(Context c) {
        ScrollView scroll = new ScrollView(c);
        scroll.setBackgroundColor(Ui.p().grouped);
        scroll.setFillViewport(true);
        final int widest = Ui.dp(c, 640);
        LinearLayout content = new LinearLayout(c) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int side = Math.max(0, (MeasureSpec.getSize(widthSpec) - widest) / 2);
                if (getPaddingLeft() != side) setPadding(side, getPaddingTop(), side, getPaddingBottom());
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(c, 32));
        scroll.addView(content);
        return scroll;
    }

    public static Grouped on(ScrollView page) {
        LinearLayout content = (LinearLayout) page.getChildAt(0);
        content.removeAllViews();
        return new Grouped(page.getContext(), content);
    }

    public void add(View v) {
        content.addView(v, Ui.matchWrap());
    }

    public void section(String title) {
        content.addView(Ui.sectionTitle(c, title));
    }

    public void footer(CharSequence text) {
        TextView t = Ui.body(c, 13, Ui.p().secondary);
        t.setText(text);
        t.setPadding(Ui.dp(c, 18), Ui.dp(c, 6), Ui.dp(c, 18), 0);
        content.addView(t);
    }

    public LinearLayout row(View.OnClickListener click) {
        LinearLayout row = Ui.horizontal(c);
        row.setMinimumHeight(Ui.dp(c, 56));
        row.setPadding(Ui.dp(c, 22), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        if (click != null) row.setOnClickListener(click);
        return row;
    }

    public void addRow(LinearLayout row) {
        row.setBackground(Ui.pressable(c, Ui.p().control, Ui.dp(c, 28), false));
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.setMargins(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4));
        content.addView(row, lp);
    }

    /** A label and a gray value; returns the value view. */
    public TextView value(String label, String value, View.OnClickListener click) {
        LinearLayout row = row(click);
        TextView l = Ui.text(c, 16, Ui.p().text, false);
        l.setText(label);
        row.addView(l, Ui.weight(1));
        TextView v = Ui.text(c, 15, Ui.p().secondary, false);
        v.setText(value);
        v.setPadding(Ui.dp(c, 8), 0, 0, 0);
        v.setMaxWidth(Ui.dp(c, 260));
        row.addView(v);
        if (click != null) {
            IconView chevron = Ui.icon(c, IconView.CHEVRON, Ui.p().disclosure, 16);
            ((LinearLayout.LayoutParams) chevron.getLayoutParams()).leftMargin = Ui.dp(c, 6);
            row.addView(chevron);
        }
        addRow(row);
        return v;
    }

    public TextView action(String label, boolean destructive, View.OnClickListener click) {
        LinearLayout row = row(click);
        TextView l = Ui.text(c, 16, destructive ? Ui.p().danger : Ui.p().accent, true);
        l.setText(label);
        row.addView(l);
        addRow(row);
        return l;
    }

    public Toggle toggle(String label, boolean on, Toggle.Listener listener) {
        final Toggle t = new Toggle(c);
        LinearLayout row = row(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                t.performClick();
            }
        });
        TextView l = Ui.text(c, 16, Ui.p().text, false);
        l.setSingleLine(false);
        l.setText(label);
        row.addView(l, Ui.weight(1));
        t.setChecked(on);
        t.setListener(listener);
        t.setContentDescription(label);
        row.addView(t);
        addRow(row);
        return t;
    }
}
