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
import com.grapefruitmusic.radio.Radio;
import com.grapefruitmusic.radio.RadioSoundTest;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Notice;

/**
 * Settings › Developer Options › Radio Sound Test: finds out whether Grapefruit can hear this
 * tablet's radio (see RadioSoundTest), the first step to cleaning up a noisy station.
 */
public final class RadioSoundTestFragment extends BaseFragment {
    private TextView status;
    private TextView results;
    private LinearLayout start;
    private boolean running;
    /** Kept while the page's views are rebuilt. */
    private String shown;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        Context c = getActivity();
        ScrollView page = new ScrollView(c);
        page.setFillViewport(true);
        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(c, 24));
        page.addView(content);
        content.addView(Ui.header(c, "Developer Options", "Radio Sound Test", goBack()));

        TextView intro = Ui.text(c, 15, R.attr.gfTextSecondary, false);
        intro.setSingleLine(false);
        intro.setText("Can Grapefruit hear this tablet's radio? On tablets like the JP5S, the radio's sound goes "
                + "straight from the radio chip to the headphones, so Grapefruit can't filter it there. To clean up "
                + "a noisy station, it would have to record the radio inside the tablet (the way the tablet's own FM "
                + "Radio app records) and play the cleaned sound through the speaker. This test finds out whether "
                + "that works here. If it does, it records 10 seconds of the station to tune a hum filter to.\n\n"
                + "Tune to the station with the noise, keep the headphones plugged in, then tap Start Test. It takes "
                + "about 20 seconds, and the radio goes quiet a few times. Grapefruit only keeps a recording if it's "
                + "the radio's sound, never the room's.");
        intro.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 12));
        content.addView(intro);

        start = Ui.pill(c, Icons.PLAY, "Start Test");
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTest();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
        content.addView(start, lp);

        status = Ui.text(c, 15, R.attr.gfTextPrimary, false);
        status.setSingleLine(false);
        status.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 4));
        content.addView(status);
        results = Ui.text(c, 13, R.attr.gfTextSecondary, false);
        results.setSingleLine(false);
        results.setTextIsSelectable(true);
        results.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), 0);
        content.addView(results);
        if (shown != null) results.setText(shown);
        showRunning();
        return page;
    }

    @Override
    public void onDestroyView() {
        status = null;
        results = null;
        start = null;
        super.onDestroyView();
    }

    private void showRunning() {
        if (start == null) return;
        start.setEnabled(!running);
        start.setAlpha(running ? 0.5f : 1f);
        if (!running && status != null && shown == null) status.setText("");
    }

    private void startTest() {
        final Context c = getActivity();
        final Radio radio = Radio.get(c);
        if (running) return;
        if (!radio.wantsToPlay() || radio.state() != Radio.PLAYING) {
            Notice.showLong(c, "Listen to the station first: tune to it in the Radio tab, then come back here.");
            return;
        }
        running = true;
        shown = null;
        results.setText("");
        status.setText("Starting…");
        showRunning();
        bg(new Bg.Work<RadioSoundTest.Result>() {
            @Override
            public RadioSoundTest.Result run() {
                return RadioSoundTest.run(radio, new RadioSoundTest.Progress() {
                    @Override
                    public void step(final String what) {
                        Bg.main(new Runnable() {
                            @Override
                            public void run() {
                                if (status != null && running) status.setText(what);
                            }
                        });
                    }
                });
            }
        }, new Bg.Done<RadioSoundTest.Result>() {
            @Override
            public void done(RadioSoundTest.Result result, Exception error) {
                running = false;
                String headline;
                if (result == null) {
                    headline = "The test stopped: " + error;
                    shown = headline;
                } else {
                    radio.setSoundTest(result.summary);
                    headline = !result.heard ? "Grapefruit can't hear this tablet's radio."
                            : result.clip != null ? "Grapefruit can hear this tablet's radio. Please send "
                            + "Grapefruit/" + RadioSoundTest.CLIP_NAME + " and the radio files (Developer Options › "
                            + "Radio Details › Save Radio Files) to " + AboutFragment.DEVELOPER + "."
                            : "Grapefruit can hear this tablet's radio, but the station clip couldn't be saved.";
                    shown = result.summary;
                }
                if (status != null) status.setText(headline);
                if (results != null) results.setText(shown);
                showRunning();
            }
        });
    }
}
