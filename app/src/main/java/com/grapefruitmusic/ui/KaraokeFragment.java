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
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.ui.widget.Scrubber;
import com.grapefruitmusic.ui.widget.Toggle;
import com.grapefruitmusic.util.Prefs;

/** Settings › Karaoke Mode: on or off, and how much of the lead vocal to take away. */
public final class KaraokeFragment extends BaseFragment {
    private ScrollView page;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        page = Grouped.page(getActivity());
        build();
        return page;
    }

    private void build() {
        final Context c = getActivity();
        Grouped g = Grouped.on(page);
        g.add(Ui.header(c, "Settings", "Karaoke Mode", goBack()));

        g.section("Karaoke Mode");
        g.toggle(
                "Karaoke Mode",
                Prefs.karaoke(),
                new Toggle.Listener() {
                    @Override
                    public void onToggled(Toggle t, boolean on) {
                        Prefs.setKaraoke(on);
                        if (on) Player.prepareKaraoke(getActivity());
                        Player.get(getActivity()).audioEffectsChanged();
                    }
                });
        g.footer(
                "Reduces vocals using a lightweight stereo separator while music plays. Vocal"
                    + " processing runs on this device, including during offline playback. The"
                    + " microphone button in Now Playing switches it on and off, and the slider"
                    + " beside the artwork sets the strength.");

        g.section("Vocal Removal");
        LinearLayout row = g.row(null);
        final TextView percent = Ui.text(c, 16, R.attr.gfTextSecondary, false);
        Scrubber amount = new Scrubber(c);
        amount.setColors(Ui.color(c, R.attr.gfSeparator), Ui.accent(c), Ui.color(c, R.attr.gfKnob));
        amount.setThumb(8, 11, true);
        amount.setMax(100);
        amount.setValue(Math.round(Prefs.karaokeAmount() * 100));
        amount.setContentDescription("Vocal removal");
        percent.setText(Math.round(Prefs.karaokeAmount() * 100) + "%");
        amount.setListener(
                new Scrubber.Listener() {
                    @Override
                    public void onScrubStart() {}

                    @Override
                    public void onScrub(long value) {
                        percent.setText(value + "%");
                        Prefs.setKaraokeAmount(value / 100f);
                        Player.get(getActivity()).audioEffectsChanged();
                    }

                    @Override
                    public void onScrubEnd(long value) {
                        onScrub(value);
                    }
                });
        row.addView(amount, new LinearLayout.LayoutParams(0, Ui.dp(c, 36), 1));
        percent.setPadding(Ui.dp(c, 12), 0, 0, 0);
        row.addView(
                percent,
                new LinearLayout.LayoutParams(Ui.dp(c, 56), ViewGroup.LayoutParams.WRAP_CONTENT));
        g.addRow(row, false);
        g.footer(
                "100% is the strongest setting, not a promise of a voice-free track. Singing,"
                    + " reverberation and backing voices may remain, and some instruments may"
                    + " become quieter. Reduce the strength to preserve more of the backing."
                    + " Playback has a short processing delay.");
    }
}
