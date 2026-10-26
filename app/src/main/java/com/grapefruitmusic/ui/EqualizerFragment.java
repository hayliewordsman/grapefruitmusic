package com.grapefruitmusic.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.playback.Equalizer;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.ui.widget.EqualizerView;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.Prefs;

import java.util.ArrayList;
import java.util.List;

/** Settings › Equalizer: ten bands to drag, and presets to pick from. */
public final class EqualizerFragment extends BaseFragment {
    private ScrollView page;
    private EqualizerView bands;
    private List<Integer> presets;
    /** The check mark on each preset's row, in presets order. */
    private final List<IconView> checks = new ArrayList<IconView>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        page = Grouped.page(getActivity());
        build();
        return page;
    }

    private void build() {
        Context c = getActivity();
        Grouped g = Grouped.on(page);
        g.add(Ui.header(c, "Settings", "Equalizer", goBack()));

        g.section("Bands");
        bands = new EqualizerView(c);
        bands.setColors(Ui.color(c, R.attr.gfTextSecondary), Ui.color(c, R.attr.gfSeparator),
                Ui.color(c, R.attr.gfKnob));
        bands.setGains(Player.eqCurve(), false);
        bands.setListener(new EqualizerView.Listener() {
            @Override
            public void onBandChanged(EqualizerView view, int band, float db) {
                // Moving a band makes the curve your own: Custom, starting from what was showing.
                Prefs.setCustomEq(view.getGains());
                if (Prefs.equalizer() != Equalizer.CUSTOM) {
                    Prefs.setEqualizer(Equalizer.CUSTOM);
                    showChecks();
                }
                Player.get(getActivity()).audioEffectsChanged();
            }
        });
        LinearLayout box = new LinearLayout(c);
        box.setBackgroundColor(Ui.color(c, R.attr.gfSurface));
        box.setPadding(0, Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 4));
        box.addView(bands, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        g.add(box);
        g.add(Ui.separator(c, 0));
        g.footer("Drag a band up or down to make your own curve (saved as Custom), or pick a preset. "
                + "The equalizer only changes Grapefruit's sound.");

        g.section("Presets");
        presets = Equalizer.listOrder();
        checks.clear();
        for (final int preset : presets) {
            LinearLayout row = g.row(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pick(preset);
                }
            });
            TextView label = Ui.text(c, 17, R.attr.gfTextPrimary, false);
            label.setText(Equalizer.name(preset));
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            IconView check = Ui.icon(c, Icons.CHECK, Ui.accent(c), 20);
            row.addView(check);
            checks.add(check);
            g.addRow(row, true);
        }
        showChecks();
    }

    private void pick(int preset) {
        Prefs.setEqualizer(preset);
        bands.setGains(Player.eqCurve(), true);
        showChecks();
        Player.get(getActivity()).audioEffectsChanged();
    }

    private void showChecks() {
        int current = Prefs.equalizer();
        for (int i = 0; i < presets.size(); i++) {
            checks.get(i).setVisibility(presets.get(i) == current ? View.VISIBLE : View.INVISIBLE);
        }
    }
}
