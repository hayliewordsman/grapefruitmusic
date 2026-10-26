package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.radio.Radio;
import com.grapefruitmusic.radio.RadioArt;
import com.grapefruitmusic.ui.widget.ArtworkView;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.Bg;

import java.util.List;

/** The Radio tab: saved stations as tiles, a scan for more, and the state of the radio. */
public final class RadioFragment extends BaseFragment implements Radio.Listener {
    private Radio radio;
    private ScrollView page;
    private LinearLayout content;

    @Override
    boolean rebuildsOnRotate() {
        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        radio = Radio.get(getActivity());
        Context c = getActivity();
        page = new ScrollView(c);
        page.setFillViewport(true);
        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(c, 24));
        page.addView(content);
        build();
        return page;
    }

    @Override
    public void onStart() {
        super.onStart();
        radio.addListener(this);
        build();
    }

    @Override
    public void onStop() {
        radio.removeListener(this);
        Bg.cancelMain(rebuild);
        super.onStop();
    }

    /** Rebuilds once the radio settles (it changes on every step while the dial turns). */
    private final Runnable rebuild = new Runnable() {
        @Override
        public void run() {
            if (getActivity() != null && content != null) build();
        }
    };

    @Override
    public void onRadioChanged() {
        Bg.cancelMain(rebuild);
        Bg.mainDelayed(rebuild, 120);
    }

    private void build() {
        final Context c = getActivity();
        content.removeAllViews();
        content.addView(Ui.header(c, null, "Radio", null));

        if (radio.unsupported()) {
            content.addView(card(c, Icons.RADIO, "This tablet's radio isn't working with Grapefruit yet",
                    "Grapefruit couldn't switch on this tablet's FM radio. Radio Details shows what it found, "
                            + "which helps add support for it.", "Radio Details", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            main().push(new RadioDetailsFragment());
                        }
                    }));
        } else if (!radio.headphones()) {
            content.addView(card(c, Icons.HEADPHONES, "Plug in headphones",
                    "The radio uses their wire as its antenna. Once they're in, you can still listen through "
                            + "the speaker: choose it in Now Playing.", null, null));
        }

        LinearLayout actions = new LinearLayout(c);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 4));
        boolean on = radio.wantsToPlay();
        LinearLayout listen = Ui.pill(c, on ? Icons.STOP : Icons.PLAY,
                on ? "Stop" : "Listen to " + Radio.format(radio.frequency()));
        listen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (radio.wantsToPlay()) radio.stop();
                else main().playRadio(radio.frequency());
            }
        });
        actions.addView(listen, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        boolean scanning = radio.state() == Radio.SCANNING;
        LinearLayout scan = Ui.pill(c, Icons.SEARCH, scanning ? "Finding Stations…" : "Find Stations");
        scan.setEnabled(!scanning);
        scan.setAlpha(scanning ? 0.6f : 1f);
        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                radio.scan();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = Ui.dp(c, 12);
        actions.addView(scan, lp);
        content.addView(actions);

        content.addView(Ui.sectionTitle(c, "Presets"));
        List<Radio.Preset> presets = radio.presets();
        if (presets.isEmpty()) {
            TextView empty = Ui.text(c, 15, R.attr.gfTextSecondary, false);
            empty.setSingleLine(false);
            empty.setText("No presets yet. Tune in a station and tap + in Now Playing to save it here.");
            empty.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
            content.addView(empty);
        } else {
            addTiles(c, presets);
            TextView hint = Ui.text(c, 13, R.attr.gfTextSecondary, false);
            hint.setSingleLine(false);
            hint.setText("Touch and hold a preset to rename or remove it.");
            hint.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), 0);
            content.addView(hint);
        }

        int[] found = radio.found();
        if (found != null) {
            content.addView(Ui.sectionTitle(c, "Stations Found"));
            if (found.length == 0) {
                TextView none = Ui.text(c, 15, R.attr.gfTextSecondary, false);
                none.setSingleLine(false);
                none.setText("No stations came in. Reception is better near a window, with the headphone wire "
                        + "stretched out.");
                none.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
                content.addView(none);
            }
            content.addView(Ui.separator(c, 16));
            for (int f : found) addFoundRow(c, f);
        }

        TextView details = Ui.text(c, 15, R.attr.gfAccent, false);
        details.setText("Radio Details");
        details.setPadding(Ui.dp(c, 16), Ui.dp(c, 22), Ui.dp(c, 16), Ui.dp(c, 8));
        details.setBackgroundResource(Ui.resource(c, R.attr.gfRowBackground));
        details.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                main().push(new RadioDetailsFragment());
            }
        });
        content.addView(details, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** A rounded note with an icon, a bold line, some text and maybe a button. */
    private static View card(Context c, int icon, String title, String text, String action, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setBackground(GrapefruitDialog.rounded(Ui.color(c, R.attr.gfFill), Ui.dp(c, 12)));
        box.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
        IconView i = Ui.icon(c, icon, Ui.accent(c), 30);
        box.addView(i);
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(c, 14), 0, 0, 0);
        TextView t = Ui.text(c, 17, R.attr.gfTextPrimary, true);
        t.setSingleLine(false);
        t.setText(title);
        texts.addView(t);
        TextView body = Ui.text(c, 15, R.attr.gfTextSecondary, false);
        body.setSingleLine(false);
        body.setText(text);
        body.setPadding(0, Ui.dp(c, 4), 0, 0);
        texts.addView(body);
        if (action != null) {
            TextView a = Ui.text(c, 16, R.attr.gfAccent, true);
            a.setText(action);
            a.setPadding(0, Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 2));
            a.setOnClickListener(click);
            texts.addView(a, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        box.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 6));
        box.setLayoutParams(lp);
        return box;
    }

    /** Presets as artwork tiles: three or more across, more when the screen is wider. */
    private void addTiles(Context c, List<Radio.Preset> presets) {
        int width = getResources().getConfiguration().screenWidthDp;
        int columns = Math.max(3, width / 170);
        int gap = Ui.dp(c, 14);
        LinearLayout row = null;
        for (int i = 0; i < presets.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(Ui.dp(c, 16) - gap / 2, 0, Ui.dp(c, 16) - gap / 2, Ui.dp(c, 12));
                content.addView(row);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.leftMargin = gap / 2;
            lp.rightMargin = gap / 2;
            row.addView(tile(c, presets.get(i)), lp);
        }
        // Keep the last row's tiles the same size as the others.
        int rest = presets.size() % columns;
        if (rest != 0) {
            for (int i = rest; i < columns; i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1);
                lp.leftMargin = gap / 2;
                lp.rightMargin = gap / 2;
                row.addView(new View(c), lp);
            }
        }
    }

    private View tile(final Context c, final Radio.Preset p) {
        LinearLayout tile = new LinearLayout(c);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setBackgroundResource(Ui.resource(c, R.attr.gfRowBackground));
        ArtworkView art = Ui.artwork(c, 0);
        art.setCornerRadiusDp(8);
        art.setBitmap(RadioArt.of(p.frequency, p.name, Ui.dp(c, 200)));
        tile.addView(art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        boolean current = radio.inSession() && radio.frequency() == p.frequency;
        TextView name = Ui.text(c, 15, current ? R.attr.gfAccent : R.attr.gfTextPrimary, current);
        name.setText(p.label());
        name.setPadding(0, Ui.dp(c, 6), 0, 0);
        tile.addView(name);
        if (p.name != null && p.name.length() > 0) {
            TextView freq = Ui.text(c, 13, R.attr.gfTextSecondary, false);
            freq.setText(Radio.format(p.frequency) + " FM");
            tile.addView(freq);
        }
        tile.setContentDescription(p.label() + ", " + Radio.format(p.frequency) + " FM");
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                main().playRadio(p.frequency);
            }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                presetMenu(c, p);
                return true;
            }
        });
        return tile;
    }

    private void presetMenu(final Context c, final Radio.Preset p) {
        Ui.dialog(c)
                .setTitle(p.label())
                .setItems(new CharSequence[]{"Rename", "Remove"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) rename(c, p);
                        else radio.removePreset(p.frequency);
                    }
                })
                .show();
    }

    private void rename(Context c, final Radio.Preset p) {
        final EditText field = Ui.field(c, 16, false, Radio.format(p.frequency) + " FM");
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if (p.name != null) field.setText(p.name);
        Ui.dialog(c)
                .setTitle("Rename " + Radio.format(p.frequency) + " FM")
                .setView(field)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        radio.renamePreset(p.frequency, field.getText().toString());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** A station from a scan: tap to listen, + to save it. */
    private void addFoundRow(final Context c, final int f) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(c, 56));
        row.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        row.setBackgroundResource(Ui.resource(c, R.attr.gfRowBackground));
        ArtworkView art = Ui.artwork(c, 44);
        art.setBitmap(RadioArt.of(f, null, Ui.dp(c, 44)));
        row.addView(art);
        boolean current = radio.inSession() && radio.frequency() == f;
        TextView label = Ui.text(c, 17, current ? R.attr.gfAccent : R.attr.gfTextPrimary, false);
        label.setText(Radio.format(f) + " FM");
        label.setPadding(Ui.dp(c, 12), 0, 0, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final boolean saved = radio.preset(f) != null;
        IconView add = new IconView(c);
        add.setIcon(saved ? Icons.CHECK : Icons.PLUS);
        add.setColor(Ui.accent(c));
        int pad = Ui.dp(c, 11);
        add.setPadding(pad, pad, pad, pad);
        add.setContentDescription(saved ? "In Presets" : "Add to Presets");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (saved) radio.removePreset(f);
                else radio.addPreset(f);
            }
        });
        row.addView(add, new LinearLayout.LayoutParams(Ui.dp(c, 46), Ui.dp(c, 46)));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                main().playRadio(f);
            }
        });
        content.addView(row);
        content.addView(Ui.separator(c, 72));
    }
}
