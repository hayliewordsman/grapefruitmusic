package io.github.hayliewordsman.grapefruitai.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Grapefruit's own dialog: a rounded card in the theme's surface, with pill buttons. */
public final class Sheet {
    private final Context c;
    private final Dialog dialog;
    private final LinearLayout body;
    private final LinearLayout buttons;

    public Sheet(Context c, String title) {
        this.c = c;
        dialog = new Dialog(c);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Palette p = Ui.p();
        LinearLayout card = Ui.vertical(c);
        card.setBackground(Ui.shape(c, p.surface, Ui.dp(c, 26), true, false));
        card.setPadding(Ui.dp(c, 20), Ui.dp(c, 18), Ui.dp(c, 20), Ui.dp(c, 14));
        if (title != null) {
            TextView t = Ui.text(c, 20, p.text, true);
            t.setSingleLine(false);
            t.setText(title);
            t.setPadding(0, 0, 0, Ui.dp(c, 10));
            card.addView(t);
        }
        body = Ui.vertical(c);
        ScrollView scroll = new ScrollView(c);
        scroll.addView(body);
        card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        buttons = Ui.horizontal(c);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        buttons.setPadding(0, Ui.dp(c, 12), 0, 0);
        card.addView(buttons, Ui.matchWrap());
        dialog.setContentView(card);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    public Sheet message(CharSequence text) {
        TextView t = Ui.body(c, 15, Ui.p().secondary);
        t.setText(text);
        t.setPadding(0, 0, 0, Ui.dp(c, 8));
        t.setTextIsSelectable(true);
        body.addView(t);
        return this;
    }

    public Sheet view(View v) {
        body.addView(v, Ui.matchWrap());
        return this;
    }

    public LinearLayout body() {
        return body;
    }

    /** A button that closes the sheet after running action (which may be null). */
    public Sheet button(String label, boolean main, final Runnable action) {
        return button(label, main, true, action);
    }

    public Sheet button(String label, boolean main, final boolean closes, final Runnable action) {
        TextView b = Ui.button(c, label, main, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (closes) dismiss();
                if (action != null) action.run();
            }
        });
        LinearLayout.LayoutParams lp = Ui.wrap();
        lp.leftMargin = Ui.dp(c, 8);
        buttons.addView(b, lp);
        return this;
    }

    /** A tappable row in the body (a choice in a list). */
    public TextView row(String label, String detail, boolean chosen, final Runnable action) {
        LinearLayout row = Ui.horizontal(c);
        row.setMinimumHeight(Ui.dp(c, 48));
        row.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        row.setBackground(Ui.rowBackground(c));
        LinearLayout texts = Ui.vertical(c);
        TextView t = Ui.text(c, 16, chosen ? Ui.p().accent : Ui.p().text, chosen);
        t.setSingleLine(false);
        t.setText(label);
        texts.addView(t);
        if (detail != null && detail.length() > 0) {
            TextView d = Ui.body(c, 13, Ui.p().secondary);
            d.setText(detail);
            texts.addView(d);
        }
        row.addView(texts, Ui.weight(1));
        if (chosen) row.addView(Ui.icon(c, IconView.CHECK, Ui.p().accent, 20));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (action != null) action.run();
            }
        });
        body.addView(row, Ui.matchWrap());
        return t;
    }

    public Sheet cancelable(boolean b) {
        dialog.setCancelable(b);
        dialog.setCanceledOnTouchOutside(b);
        return this;
    }

    public Sheet onDismiss(final Runnable r) {
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface d) {
                r.run();
            }
        });
        return this;
    }

    public Sheet show() {
        try {
            dialog.show();
            Window w = dialog.getWindow();
            if (w != null) {
                int width = Math.min(c.getResources().getDisplayMetrics().widthPixels - Ui.dp(c, 24),
                        Ui.dp(c, 560));
                w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        } catch (RuntimeException windowGone) {
            // The activity went away first.
        }
        return this;
    }

    public void dismiss() {
        try {
            dialog.dismiss();
        } catch (RuntimeException ignored) {
            // Already gone.
        }
    }

    public static void alert(Context c, String title, CharSequence message) {
        new Sheet(c, title).message(message).button("OK", true, null).show();
    }

    public static void confirm(Context c, String title, CharSequence message, String yes, boolean destructive,
                               Runnable action) {
        Sheet s = new Sheet(c, title).message(message).button("Cancel", false, null);
        s.button(yes, !destructive, action);
        if (destructive) {
            TextView last = (TextView) s.buttons.getChildAt(s.buttons.getChildCount() - 1);
            last.setTextColor(Ui.p().danger);
        }
        s.show();
    }
}
