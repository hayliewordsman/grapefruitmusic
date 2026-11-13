package io.github.hayliewordsman.grapefruitai.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import io.github.hayliewordsman.grapefruitai.or.Catalog;
import io.github.hayliewordsman.grapefruitai.or.Models;
import io.github.hayliewordsman.grapefruitai.or.OrModel;
import io.github.hayliewordsman.grapefruitai.util.Format;

/**
 * The model dropdown: OpenRouter's whole live catalog, with search, Chat/Coding/Image filters,
 * capability filters and sorting by name, provider, price, context and age. Each row shows the
 * provider, context window, modalities, tool calling and USD prices; "Details" shows everything.
 */
public final class ModelPicker {
    public interface Listener {
        void onPicked(OrModel model);
    }

    private ModelPicker() {
    }

    public static void show(final Context c, int kind, final String current, final Listener listener) {
        final Palette p = Ui.p();
        final Dialog dialog = new Dialog(c);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final Catalog catalog = new Catalog();
        catalog.kind = kind;
        final List<OrModel> shown = new ArrayList<OrModel>();

        LinearLayout root = Ui.vertical(c);
        root.setBackground(Ui.shape(c, p.surface, Ui.dp(c, 26), true, false));
        root.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 8));

        LinearLayout top = Ui.horizontal(c);
        top.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 12), 0);
        TextView title = Ui.text(c, 20, p.text, true);
        title.setText("Choose a model");
        top.addView(title, Ui.weight(1));
        final IconView refresh = Ui.circle(c, IconView.REFRESH, 40, false, "Refresh the model list");
        top.addView(refresh);
        IconView close = Ui.circle(c, IconView.CLOSE, 40, false, "Close");
        ((LinearLayout.LayoutParams) close.getLayoutParams()).leftMargin = Ui.dp(c, 8);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        top.addView(close);
        root.addView(top, Ui.matchWrap());

        final android.widget.EditText search = Ui.field(c, "Search name, provider or capability", false);
        LinearLayout.LayoutParams slp = Ui.matchWrap();
        slp.setMargins(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 4));
        root.addView(search, slp);

        final TextView[] kinds = new TextView[Catalog.KIND_NAMES.length];
        final TextView[] sorts = new TextView[Catalog.SORT_NAMES.length];
        final TextView count = Ui.text(c, 13, p.secondary, false);
        final BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return shown.size();
            }

            @Override
            public Object getItem(int i) {
                return shown.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convert, ViewGroup parent) {
                return row(c, convert, shown.get(i), shown.get(i).id.equals(current));
            }
        };
        final Runnable refreshList = new Runnable() {
            @Override
            public void run() {
                catalog.query = search.getText().toString();
                shown.clear();
                shown.addAll(catalog.apply(Models.all()));
                adapter.notifyDataSetChanged();
                String err = Models.lastError();
                count.setText(shown.size() + " of " + Models.all().size() + " models · prices in US dollars"
                        + (err != null && Models.all().isEmpty() ? " · " + err : ""));
                for (int i = 0; i < kinds.length; i++) Ui.setChipChosen(kinds[i], catalog.kind == i);
                for (int i = 0; i < sorts.length; i++) Ui.setChipChosen(sorts[i], catalog.sort == i);
            }
        };

        HorizontalScrollView kindRow = Ui.chipRow(c);
        for (int i = 0; i < Catalog.KIND_NAMES.length; i++) {
            final int k = i;
            kinds[i] = Ui.chip(c, Catalog.KIND_NAMES[i], catalog.kind == i);
            kinds[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    catalog.kind = k;
                    refreshList.run();
                }
            });
            Ui.addChip(kindRow, kinds[i]);
        }
        final String[] capNames = {"Tools", "Vision", "Files", "Reasoning", "Free"};
        for (int i = 0; i < capNames.length; i++) {
            final int k = i;
            final TextView chip = Ui.chip(c, capNames[i], false);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean on = !chip.isSelected();
                    if (k == 0) catalog.needTools = on;
                    if (k == 1) catalog.needVision = on;
                    if (k == 2) catalog.needFiles = on;
                    if (k == 3) catalog.needReasoning = on;
                    if (k == 4) catalog.freeOnly = on;
                    Ui.setChipChosen(chip, on);
                    refreshList.run();
                }
            });
            Ui.addChip(kindRow, chip);
        }
        root.addView(kindRow, Ui.matchWrap());

        HorizontalScrollView sortRow = Ui.chipRow(c);
        TextView sortLabel = Ui.text(c, 13, p.secondary, false);
        sortLabel.setText("Sort");
        Ui.addChip(sortRow, sortLabel);
        for (int i = 0; i < Catalog.SORT_NAMES.length; i++) {
            final int k = i;
            sorts[i] = Ui.chip(c, Catalog.SORT_NAMES[i], catalog.sort == i);
            sorts[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    catalog.sort = k;
                    refreshList.run();
                }
            });
            Ui.addChip(sortRow, sorts[i]);
        }
        root.addView(sortRow, Ui.matchWrap());
        count.setPadding(Ui.dp(c, 18), Ui.dp(c, 2), Ui.dp(c, 18), Ui.dp(c, 4));
        root.addView(count, Ui.matchWrap());

        ListView list = new ListView(c);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(0));
        list.setCacheColorHint(0);
        list.setAdapter(adapter);
        list.setFastScrollEnabled(true);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                dialog.dismiss();
                listener.onPicked(shown.get(position));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                details(c, shown.get(position));
                return true;
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView hint = Ui.text(c, 12, p.secondary, false);
        hint.setText("Touch and hold a model for all its details and prices.");
        hint.setPadding(Ui.dp(c, 18), Ui.dp(c, 4), Ui.dp(c, 18), 0);
        root.addView(hint);

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void afterTextChanged(Editable s) {
                refreshList.run();
            }
        });
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                count.setText("Refreshing from OpenRouter…");
                Models.load(c, true, new Models.Listener() {
                    @Override
                    public void onModels(List<OrModel> models, String error) {
                        refreshList.run();
                        if (error != null) Ui.toast(c, "Couldn't refresh: " + error);
                    }
                });
            }
        });

        // Image prices arrive after the model list: redraw the rows when they do.
        final Runnable pricesArrived = new Runnable() {
            @Override
            public void run() {
                adapter.notifyDataSetChanged();
            }
        };
        Models.addPricingListener(pricesArrived);
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface d) {
                Models.removePricingListener(pricesArrived);
            }
        });
        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        if (w != null) {
            android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
            w.setLayout(Math.min(dm.widthPixels - Ui.dp(c, 16), Ui.dp(c, 720)),
                    dm.heightPixels - Ui.dp(c, 48));
        }
        refreshList.run();
        if (Models.all().isEmpty()) {
            count.setText("Loading OpenRouter's model list…");
            Models.load(c, false, new Models.Listener() {
                @Override
                public void onModels(List<OrModel> models, String error) {
                    refreshList.run();
                }
            });
        }
    }

    private static View row(Context c, View convert, OrModel m, boolean chosen) {
        Palette p = Ui.p();
        LinearLayout row;
        TextView name, summary, prices;
        if (convert instanceof LinearLayout) {
            row = (LinearLayout) convert;
            name = (TextView) row.getChildAt(0);
            summary = (TextView) row.getChildAt(1);
            prices = (TextView) row.getChildAt(2);
        } else {
            row = Ui.vertical(c);
            row.setPadding(Ui.dp(c, 18), Ui.dp(c, 8), Ui.dp(c, 18), Ui.dp(c, 8));
            row.setBackground(Ui.rowBackground(c));
            row.setMinimumHeight(Ui.dp(c, 56));
            name = Ui.text(c, 16, p.text, true);
            summary = Ui.text(c, 13, p.secondary, false);
            summary.setSingleLine(false);
            summary.setMaxLines(2);
            prices = Ui.text(c, 13, p.secondary, false);
            row.addView(name);
            row.addView(summary);
            row.addView(prices);
        }
        name.setText(m.name);
        name.setTextColor(chosen ? p.accent : p.text);
        summary.setText(Format.modelSummary(m));
        prices.setText(Format.modelPrices(m, Models.pricing(m.id)));
        row.setContentDescription(m.name + (chosen ? ", chosen" : ""));
        return row;
    }

    public static void details(Context c, OrModel m) {
        new Sheet(c, m.name).message(Format.modelDetails(m, Models.pricing(m.id))).button("Close", true, null).show();
    }
}
