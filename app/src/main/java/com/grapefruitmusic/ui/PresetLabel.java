package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;

import com.grapefruitmusic.radio.Radio;
import com.grapefruitmusic.util.Notice;

/**
 * A radio preset's label, typed by the listener: asked for as the preset is saved (Now Playing's
 * + and Stations Found), and from the preset's menu on the Radio tab.
 */
final class PresetLabel {
    private PresetLabel() {
    }

    /** Saves freq as a preset, with the label typed (or none). */
    static void add(final Context c, final int freq) {
        final Radio radio = Radio.get(c);
        String station = freq == radio.frequency() ? radio.name() : null;
        final EditText field = field(c, station != null ? station : Radio.format(freq) + " FM");
        Ui.dialog(c)
                .setTitle("Add to Presets")
                .setMessage("Give " + Radio.format(freq) + " FM a label, like the station's name or what's on. "
                        + "Leave it blank to show " + (station != null ? "the station's own name." : "the frequency."))
                .setView(field)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        radio.addPreset(freq, field.getText().toString());
                        Radio.Preset p = radio.preset(freq);
                        Notice.show(c, "Added " + (p != null ? p.label() : Radio.format(freq) + " FM") + " to Presets");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Changes a preset's label; a blank one goes back to the station's own name or frequency. */
    static void edit(Context c, final Radio.Preset p) {
        final Radio radio = Radio.get(c);
        String fallback = p.stationName != null ? p.stationName : Radio.format(p.frequency) + " FM";
        final EditText field = field(c, fallback);
        if (p.userLabel != null) {
            field.setText(p.userLabel);
            field.setSelection(field.length());
        }
        Ui.dialog(c)
                .setTitle("Edit Label")
                .setMessage("For " + Radio.format(p.frequency) + " FM. Leave it blank to show "
                        + (p.stationName != null ? "the station's own name." : "the frequency."))
                .setView(field)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        radio.setPresetLabel(p.frequency, field.getText().toString());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static EditText field(Context c, String hint) {
        EditText field = Ui.field(c, 16, false, hint);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Radio.Preset.MAX_LABEL)});
        return field;
    }
}
