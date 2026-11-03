package com.grapefruitmusic.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;

import java.util.ArrayList;
import java.util.List;

/**
 * A tab's menu, rising from its button in the tab bar (beside it when the tabs are a rail up the
 * side): the tab's chips as rows (Library's Search, source and sections; Podcasts'; Radio's; Search's
 * filters), the one showing ticked. Opened by tapping the tab that's showing, or touching and
 * holding any tab but Settings (MainActivity).
 */
final class TabMenu {
    /** One row: what it says, an optional icon, whether it's the one showing, and what it does. */
    static final class Item {
        final String label;
        final int icon;
        final boolean chosen;
        final Runnable action;
        /** A line above it, between the tools (Search, the source) and the sections. */
        boolean ruleBefore;

        Item(String label, int icon, boolean chosen, Runnable action) {
            this.label = label;
            this.icon = icon;
            this.chosen = chosen;
            this.action = action;
        }
    }

    static final int NO_ICON = -1;
    private static PopupWindow showing;

    private TabMenu() {
    }

    /** Closes the menu showing, if one is (the screen turned, the app went away). */
    static void dismiss() {
        if (showing != null) {
            PopupWindow p = showing;
            showing = null;
            try {
                p.dismiss();
            } catch (RuntimeException ignored) {
                // Its window was already gone.
            }
        }
    }

    /**
     * Shows items from the tab button anchor: above it with the tabs along the bottom, to its
     * right with the tabs in a rail.
     */
    static void show(final Activity a, View anchor, boolean rail, List<Item> items) {
        dismiss();
        if (items.isEmpty() || anchor.getWindowToken() == null) return;
        final Context c = a;
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        final int margin = Ui.dp(c, 12);

        LinearLayout rows = new LinearLayout(c);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        final PopupWindow[] popup = new PopupWindow[1];
        for (final Item item : items) {
            if (item.ruleBefore && rows.getChildCount() > 0) {
                View rule = Ui.separator(c, 0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, Ui.dp(c, 1)));
                lp.setMargins(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 4));
                rows.addView(rule, lp);
            }
            rows.addView(row(c, item, new Runnable() {
                @Override
                public void run() {
                    if (popup[0] != null) popup[0].dismiss();
                    // After it has gone, so the page it opens isn't drawn under a closing menu.
                    a.getWindow().getDecorView().post(item.action);
                }
            }));
        }
        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(rows);
        FrameLayout card = new FrameLayout(c);
        card.setBackground(Ui.shape(c, Ui.color(c, R.attr.gfSurface), Ui.dp(c, 22), true, false));
        card.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 2));
        card.addView(scroll);

        // As wide as its longest row, within 180-300dp and the screen.
        int widest = Math.min(dm.widthPixels - 2 * margin, Ui.dp(c, 300));
        rows.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int width = Math.max(Math.min(Ui.dp(c, 180), widest), Math.min(widest, rows.getMeasuredWidth()));
        int[] anchorAt = new int[2];
        anchor.getLocationInWindow(anchorAt);
        View bar = (View) anchor.getParent();
        int[] barAt = new int[2];
        bar.getLocationInWindow(barAt);
        int gap = Ui.dp(c, 8);
        // Above the button (below it if there's more room there), or beside the rail.
        int above = anchorAt[1] - gap - margin;
        int below = dm.heightPixels - (anchorAt[1] + anchor.getHeight()) - gap - margin;
        boolean down = !rail && below > above;
        int room = rail ? dm.heightPixels - 2 * margin : Math.max(above, below);
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(Math.max(Ui.dp(c, 96), room), View.MeasureSpec.AT_MOST));
        int height = card.getMeasuredHeight();

        int x;
        int y;
        float pivotX;
        float pivotY;
        if (rail) {
            x = barAt[0] + bar.getWidth() + gap;
            y = anchorAt[1] + anchor.getHeight() / 2 - height / 2;
            y = Math.max(margin, Math.min(y, dm.heightPixels - height - margin));
            pivotX = 0;
            pivotY = anchorAt[1] + anchor.getHeight() / 2f - y;
        } else {
            x = anchorAt[0] + anchor.getWidth() / 2 - width / 2;
            x = Math.max(margin, Math.min(x, dm.widthPixels - width - margin));
            y = down ? anchorAt[1] + anchor.getHeight() + gap : anchorAt[1] - gap - height;
            pivotX = anchorAt[0] + anchor.getWidth() / 2f - x;
            pivotY = down ? 0 : height;
        }

        PopupWindow p = new PopupWindow(card, width, height, true);
        // A background (even a clear one) is what lets a touch outside, or Back, close it on Android 4.
        p.setBackgroundDrawable(new ColorDrawable(0));
        p.setOutsideTouchable(true);
        p.setAnimationStyle(0);
        p.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                if (showing == popup[0]) showing = null;
            }
        });
        popup[0] = p;
        showing = p;
        p.showAtLocation(a.getWindow().getDecorView(), Gravity.TOP | Gravity.LEFT, x, y);
        // It grows out of the button.
        card.setPivotX(pivotX);
        card.setPivotY(pivotY);
        card.setScaleX(0.9f);
        card.setScaleY(0.9f);
        card.setAlpha(0f);
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    /** The label at the left; at the right, a tick on the one showing, or the row's icon (as iOS menus). */
    private static View row(Context c, Item item, final Runnable chosen) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(c, 48));
        row.setPadding(Ui.dp(c, 20), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 4));
        row.setBackground(Ui.rowBackground(c));
        TextView label = Ui.text(c, 16, R.attr.gfTextPrimary, false);
        label.setText(item.label);
        label.setTextColor(item.chosen ? Ui.accent(c) : Ui.color(c, R.attr.gfTextPrimary));
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int mark = item.chosen ? Icons.CHECK : item.icon != NO_ICON ? item.icon : Icons.CHECK;
        IconView end = Ui.icon(c, mark, item.chosen ? Ui.accent(c) : Ui.color(c, R.attr.gfTextSecondary), 18);
        end.setVisibility(item.chosen || item.icon != NO_ICON ? View.VISIBLE : View.INVISIBLE);
        ((LinearLayout.LayoutParams) end.getLayoutParams()).leftMargin = Ui.dp(c, 20);
        row.addView(end);
        row.setContentDescription(item.chosen ? item.label + ", showing" : item.label);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chosen.run();
            }
        });
        return row;
    }

    /** A list to fill: add(...) items in order, rule() before the next one to draw a line. */
    static final class Builder {
        final List<Item> items = new ArrayList<Item>();
        private boolean rule;

        Builder add(String label, int icon, boolean chosen, Runnable action) {
            Item item = new Item(label, icon, chosen, action);
            item.ruleBefore = rule;
            rule = false;
            items.add(item);
            return this;
        }

        Builder rule() {
            rule = !items.isEmpty();
            return this;
        }
    }
}
