package com.grapefruitmusic.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;

/**
 * Grapefruit's own dialogs, drawn the same on every Android version (instead of Holo on 4.x and
 * Material later): a centered card for messages and forms, with pill buttons like Play and
 * Shuffle, and a sheet that slides up from the bottom for menus and choices.
 *
 * <p>Built like an AlertDialog: {@code Ui.dialog(c).setTitle(..).setItems(..).show()}.
 */
public final class GrapefruitDialog extends Dialog {
    private static final int RED = 0xffff3b30;

    private TextView positiveButton;
    private TextView negativeButton;
    private CharSequence[] items;

    private GrapefruitDialog(Context context) {
        super(context, R.style.GrapefruitDialogWindow);
    }

    /** DialogInterface.BUTTON_POSITIVE or BUTTON_NEGATIVE, or null if there is no such button. */
    public TextView getButton(int which) {
        if (which == BUTTON_POSITIVE) return positiveButton;
        if (which == BUTTON_NEGATIVE) return negativeButton;
        return null;
    }

    /** The menu's items, or null. */
    public CharSequence[] getItems() {
        return items;
    }

    /** Keep long actions readable on narrow dialogs or with larger system fonts. */
    private static final class ButtonBar extends LinearLayout {
        ButtonBar(Context c) { super(c); setBaselineAligned(false); }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int gap = Ui.dp(getContext(), 8);
            int width = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
            int count = getChildCount();
            int cell = count == 0 ? width : (width - gap * (count - 1)) / count;
            boolean stack = false;
            for (int i = 0; i < count; i++) {
                TextView button = (TextView)getChildAt(i);
                if (button.getPaint().measureText(button.getText().toString())
                        + button.getPaddingLeft() + button.getPaddingRight() > cell) stack = true;
            }
            setOrientation(stack ? VERTICAL : HORIZONTAL);
            for (int i = 0; i < count; i++) {
                LayoutParams lp = (LayoutParams)getChildAt(i).getLayoutParams();
                lp.width = stack ? LayoutParams.MATCH_PARENT : 0;
                lp.height = stack ? LayoutParams.WRAP_CONTENT : LayoutParams.MATCH_PARENT;
                lp.weight = stack ? 0 : 1;
                lp.leftMargin = !stack && i > 0 ? gap : 0;
                lp.topMargin = stack && i > 0 ? gap : 0;
            }
            super.onMeasure(widthSpec, heightSpec);
        }
    }

    public static final class Builder {
        private final Context context;
        private CharSequence title;
        private CharSequence message;
        private View view;
        private CharSequence[] items;
        private int checkedItem = -1;
        private boolean singleChoice;
        private DialogInterface.OnClickListener itemListener;
        private CharSequence positiveText;
        private DialogInterface.OnClickListener positiveListener;
        private CharSequence negativeText;
        private DialogInterface.OnClickListener negativeListener;
        private boolean destructive;

        Builder(Context context) {
            this.context = context;
        }

        public Builder setTitle(CharSequence title) {
            this.title = title;
            return this;
        }

        public Builder setMessage(CharSequence message) {
            this.message = message;
            return this;
        }

        public Builder setView(View view) {
            this.view = view;
            return this;
        }

        /** A menu: tapping an item closes the dialog, then calls listener with its index. */
        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
            this.items = items;
            this.itemListener = listener;
            singleChoice = false;
            return this;
        }

        /** A list with a checkmark on the chosen item; the listener decides when to close. */
        public Builder setSingleChoiceItems(CharSequence[] items, int checked, DialogInterface.OnClickListener listener) {
            this.items = items;
            this.checkedItem = checked;
            this.itemListener = listener;
            singleChoice = true;
            return this;
        }

        public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
            positiveText = text;
            positiveListener = listener;
            return this;
        }

        public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
            negativeText = text;
            negativeListener = listener;
            return this;
        }

        /** The positive button removes or deletes something: it's red. */
        public Builder setDestructive(boolean destructive) {
            this.destructive = destructive;
            return this;
        }

        public GrapefruitDialog create() {
            GrapefruitDialog d = new GrapefruitDialog(context);
            d.setCanceledOnTouchOutside(true);
            d.items = items;
            Window w = d.getWindow();
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            if (items != null && view == null) {
                d.setContentView(sheet(d));
                w.setGravity(Gravity.BOTTOM);
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                w.setWindowAnimations(R.style.GrapefruitSheetAnimation);
            } else {
                d.setContentView(card(d));
                w.setGravity(Gravity.CENTER);
                w.setLayout(Math.min(dm.widthPixels - Ui.dp(context, 40), Ui.dp(context, 360)),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                if (view != null && hasField(view)) {
                    w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                }
            }
            return d;
        }

        public GrapefruitDialog show() {
            GrapefruitDialog d = create();
            d.show();
            return d;
        }

        /** Title, message or form, and pill buttons on a rounded card. */
        private View card(final GrapefruitDialog d) {
            Context c = context;
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(rounded(Ui.color(c, R.attr.gfSurface), Ui.dp(c, 16)));
            int pad = Ui.dp(c, 20);
            card.setPadding(pad, Ui.dp(c, 22), pad, Ui.dp(c, 16));
            if (title != null) {
                TextView t = Ui.text(c, 18, R.attr.gfTextPrimary, true);
                t.setSingleLine(false);
                t.setMaxLines(3);
                t.setGravity(Gravity.CENTER_HORIZONTAL);
                t.setText(title);
                card.addView(t, matchWidth());
            }
            if (message != null) {
                TextView m = Ui.text(c, title != null ? 15 : 16,
                        title != null ? R.attr.gfTextSecondary : R.attr.gfTextPrimary, false);
                m.setSingleLine(false);
                m.setGravity(Gravity.CENTER_HORIZONTAL);
                m.setLineSpacing(0, 1.1f);
                m.setText(message);
                m.setPadding(0, title != null ? Ui.dp(c, 6) : 0, 0, 0);
                ScrollView scroll = new ScrollView(c);
                scroll.addView(m);
                card.addView(scroll, shrinkable(0));
            }
            if (view != null) {
                card.addView(view, shrinkable(title != null || message != null ? Ui.dp(c, 14) : 0));
            } else if (items != null) {
                card.addView(itemList(d, false), shrinkable(Ui.dp(c, 10)));
            }
            if (positiveText != null || negativeText != null) {
                LinearLayout buttons = new ButtonBar(c);
                buttons.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams blp = matchWidth();
                blp.topMargin = Ui.dp(c, 20);
                card.addView(buttons, blp);
                if (negativeText != null) {
                    d.negativeButton = pill(negativeText, false);
                    buttons.addView(d.negativeButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    d.negativeButton.setOnClickListener(buttonClick(d, DialogInterface.BUTTON_NEGATIVE, negativeListener));
                }
                if (positiveText != null) {
                    d.positiveButton = pill(positiveText, true);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                    if (negativeText != null) lp.leftMargin = Ui.dp(c, 10);
                    buttons.addView(d.positiveButton, lp);
                    d.positiveButton.setOnClickListener(buttonClick(d, DialogInterface.BUTTON_POSITIVE, positiveListener));
                }
            }
            return card;
        }

        /** Menu rows on a rounded sheet at the bottom, with Cancel below. */
        private View sheet(final GrapefruitDialog d) {
            Context c = context;
            FrameLayout frame = new FrameLayout(c);
            int margin = Ui.dp(c, 8);
            frame.setPadding(margin, 0, margin, margin);
            final int maxWidth = Ui.dp(c, 480);
            LinearLayout column = new LinearLayout(c) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    // Full width, up to 480dp (also after the screen turns).
                    int width = Math.min(MeasureSpec.getSize(widthMeasureSpec), maxWidth);
                    super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
                }
            };
            column.setOrientation(LinearLayout.VERTICAL);
            frame.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            LinearLayout menu = new LinearLayout(c);
            menu.setOrientation(LinearLayout.VERTICAL);
            menu.setBackground(rounded(Ui.color(c, R.attr.gfSurface), Ui.dp(c, 16)));
            if (title != null) {
                TextView t = Ui.text(c, 14, R.attr.gfTextSecondary, true);
                t.setSingleLine(false);
                t.setMaxLines(2);
                t.setGravity(Gravity.CENTER_HORIZONTAL);
                t.setText(title);
                t.setPadding(Ui.dp(c, 20), Ui.dp(c, 14), Ui.dp(c, 20), Ui.dp(c, 12));
                menu.addView(t, matchWidth());
                menu.addView(Ui.separator(c, 0));
            }
            menu.addView(itemList(d, true), shrinkable(0));
            column.addView(menu, shrinkable(0));

            TextView cancel = Ui.text(c, 17, R.attr.gfAccent, true);
            cancel.setText("Cancel");
            cancel.setSingleLine(false);
            cancel.setEllipsize(null);
            cancel.setMinHeight(Ui.dp(c, 54));
            cancel.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
            cancel.setGravity(Gravity.CENTER);
            cancel.setBackground(pressable(Ui.color(c, R.attr.gfSurface), Ui.color(c, R.attr.gfControlPressed),
                    Ui.dp(c, 16)));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.topMargin = Ui.dp(c, 8);
            column.addView(cancel, clp);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.cancel();
                }
            });
            return frame;
        }

        /** The items, one row each, scrolling when there are more than fit. */
        private View itemList(final GrapefruitDialog d, boolean inset) {
            final Context c = context;
            ScrollView scroll = new ScrollView(c);
            final LinearLayout rows = new LinearLayout(c);
            rows.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(rows);
            for (int i = 0; i < items.length; i++) {
                if (i > 0) rows.addView(Ui.separator(c, inset ? 20 : 0));
                final int index = i;
                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Ui.dp(c, 52));
                int side = inset ? Ui.dp(c, 20) : 0;
                row.setPadding(side, Ui.dp(c, 6), inset ? Ui.dp(c, 16) : 0, Ui.dp(c, 6));
                row.setBackgroundResource(Ui.resource(c, R.attr.gfRowBackground));
                TextView label = Ui.text(c, 17, R.attr.gfTextPrimary, false);
                label.setSingleLine(false);
                label.setMaxLines(2);
                label.setText(items[i]);
                row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                if (singleChoice) {
                    IconView check = Ui.icon(c, Icons.CHECK, Ui.accent(c), 22);
                    check.setVisibility(i == checkedItem ? View.VISIBLE : View.INVISIBLE);
                    row.addView(check);
                    if (i == checkedItem) label.setTextColor(Ui.accent(c));
                }
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (singleChoice) {
                            checkedItem = index;
                            showChecked(rows);
                        } else {
                            d.dismiss();
                        }
                        if (itemListener != null) itemListener.onClick(d, index);
                    }
                });
                rows.addView(row, matchWidth());
            }
            return scroll;
        }

        /** Moves the checkmark (and accent color) to checkedItem. */
        private void showChecked(LinearLayout rows) {
            int item = 0;
            for (int i = 0; i < rows.getChildCount(); i++) {
                View child = rows.getChildAt(i);
                if (!(child instanceof LinearLayout)) continue;
                LinearLayout row = (LinearLayout) child;
                boolean on = item == checkedItem;
                ((TextView) row.getChildAt(0)).setTextColor(on ? Ui.accent(context)
                        : Ui.color(context, R.attr.gfTextPrimary));
                row.getChildAt(1).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
                item++;
            }
        }

        private View.OnClickListener buttonClick(final GrapefruitDialog d, final int which,
                                                 final DialogInterface.OnClickListener listener) {
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onClick(d, which);
                    d.dismiss();
                }
            };
        }

        /** Filled grapefruit (or red) for the main action, a gray pill for the other. */
        private TextView pill(CharSequence text, boolean primary) {
            Context c = context;
            TextView b = Ui.text(c, 16, R.attr.gfAccent, true);
            b.setText(text);
            b.setSingleLine(false);
            b.setEllipsize(null);
            b.setMinHeight(Ui.dp(c, 48));
            b.setGravity(Gravity.CENTER);
            b.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
            int radius = Ui.dp(c, 23);
            if (primary) {
                int fill = destructive ? RED : Ui.accent(c);
                b.setTextColor(0xffffffff);
                b.setBackground(pressable(fill, darker(fill), radius));
            } else {
                b.setBackground(pressable(Ui.color(c, R.attr.gfControl), Ui.color(c, R.attr.gfControlPressed), radius));
            }
            return b;
        }

        private LinearLayout.LayoutParams matchWidth() {
            return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        /** Takes what height is left, shrinking (and scrolling) when the screen is too short. */
        private LinearLayout.LayoutParams shrinkable(int topMargin) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.topMargin = topMargin;
            return lp;
        }
    }

    static GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    static Drawable pressable(int color, int pressed, float radius) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(pressed, radius));
        s.addState(new int[]{-android.R.attr.state_enabled}, rounded((color & 0x00ffffff) | 0x80000000, radius));
        s.addState(new int[0], rounded(color, radius));
        return s;
    }

    private static int darker(int color) {
        int r = (color >> 16) & 0xff;
        int g = (color >> 8) & 0xff;
        int b = color & 0xff;
        return 0xff000000 | ((r * 85 / 100) << 16) | ((g * 85 / 100) << 8) | (b * 85 / 100);
    }

    private static boolean hasField(View v) {
        if (v instanceof EditText) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (hasField(g.getChildAt(i))) return true;
            }
        }
        return false;
    }
}
