package com.grapefruitmusic.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.grapefruitmusic.R;

/** Options side by side in a rounded bar; the chosen one is filled with grapefruit. */
final class Segmented extends LinearLayout {
    interface Listener {
        void onSelected(int index);
    }

    private final TextView[] segments;
    private int selected = -1;
    private Listener listener;

    Segmented(Context c, String[] labels, int initial) {
        super(c);
        setOrientation(HORIZONTAL);
        setBackground(GrapefruitDialog.rounded(Ui.color(c, R.attr.gfControl), Ui.dp(c, 12)));
        int pad = Ui.dp(c, 3);
        setPadding(pad, pad, pad, pad);
        segments = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView t = Ui.text(c, 15, R.attr.gfTextPrimary, true);
            t.setText(labels[i]);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(false);
            t.setMaxLines(2);
            t.setMinHeight(Ui.dp(c, 48));
            t.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
            t.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(index);
                }
            });
            addView(t, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
            segments[i] = t;
        }
        select(initial);
    }

    void setListener(Listener l) {
        listener = l;
    }

    int selected() {
        return selected;
    }

    void select(int index) {
        if (index == selected) return;
        selected = index;
        Context c = getContext();
        for (int i = 0; i < segments.length; i++) {
            boolean on = i == index;
            segments[i].setSelected(on);
            segments[i].setBackground(on ? GrapefruitDialog.rounded(Ui.accent(c), Ui.dp(c, 9)) : null);
            segments[i].setTextColor(on ? 0xffffffff : Ui.color(c, R.attr.gfTextPrimary));
        }
        if (listener != null) listener.onSelected(index);
    }
}
