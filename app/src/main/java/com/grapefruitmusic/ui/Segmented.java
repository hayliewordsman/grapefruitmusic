package com.grapefruitmusic.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.grapefruitmusic.R;

/** Options side by side as pill chips; the chosen one is filled with the accent gradient. */
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
        // Multiline labels need equal-height backgrounds, without baseline offsets.
        setBaselineAligned(false);
        segments = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView t = Ui.chip(c, labels[i], false);
            t.setSingleLine(false);
            t.setEllipsize(null);
            t.setMinHeight(Ui.dp(c, 44));
            t.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
            t.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(index);
                }
            });
            LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
            if (i > 0) lp.leftMargin = Ui.dp(c, 8);
            addView(t, lp);
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
        for (int i = 0; i < segments.length; i++) Ui.setChipChosen(segments[i], i == index);
        if (listener != null) listener.onSelected(index);
    }
}
