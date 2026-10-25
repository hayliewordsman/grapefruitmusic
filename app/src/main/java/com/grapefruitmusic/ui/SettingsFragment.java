package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.source.Account;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.util.Format;
import com.grapefruitmusic.util.Prefs;

/** The Settings tab, as an iOS-style grouped list. */
public final class SettingsFragment extends BaseFragment implements Sources.Listener, Downloads.Listener {
    private LinearLayout content;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        Context c = getActivity();
        ScrollView scroll = new ScrollView(c);
        scroll.setBackgroundColor(Ui.color(c, R.attr.gfGroupedBackground));
        scroll.setFillViewport(true);
        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(c, 32));
        scroll.addView(content);
        return scroll;
    }

    @Override
    public void onResume() {
        super.onResume();
        Sources.get(getActivity()).addListener(this);
        Downloads.get(getActivity()).addListener(this);
        build();
    }

    @Override
    public void onPause() {
        Sources.get(getActivity()).removeListener(this);
        Downloads.get(getActivity()).removeListener(this);
        super.onPause();
    }

    @Override
    public void onSourcesChanged() {
        build();
    }

    @Override
    public void onDownloadsChanged() {
        build();
    }

    private void build() {
        final Context c = getActivity();
        content.removeAllViews();
        content.addView(Ui.header(c, null, "Settings", null));

        section(c, "Appearance");
        String[] themes = {"Match System", "Light", "Dark"};
        value(c, "Theme", themes[Prefs.theme()], new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choose("Theme", new String[]{"Match System", "Light", "Dark"}, Prefs.theme(), new Choice() {
                    @Override
                    public void chosen(int which) {
                        if (which != Prefs.theme()) {
                            Prefs.setTheme(which);
                            getActivity().recreate();
                        }
                    }
                });
            }
        });
        footer(c, "Match System follows the phone's dark mode on Android 10 and later.");

        section(c, "Servers");
        for (final Account a : Sources.get(c).accounts()) {
            String type = Account.JELLYFIN.equals(a.type) ? "Jellyfin" : "Navidrome / Subsonic";
            value(c, a.name, type, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.dialog(c)
                            .setTitle(a.name)
                            .setMessage(a.url + "\nSigned in as " + a.username
                                    + "\n\nRemoving the server keeps songs you've downloaded from it.")
                            .setPositiveButton("Remove Server", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Sources.get(c).removeAccount(a.id);
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });
        }
        action(c, "Add a Server…", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AddServerDialog.show(getActivity());
            }
        });
        footer(c, "Grapefruit Music plays music from your own Navidrome, Subsonic or Jellyfin server, "
                + "and from music files on this phone.");

        section(c, "Playback");
        value(c, "Streaming Quality", Prefs.qualityLabel(Prefs.streamQuality()), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickQuality("Streaming Quality", Prefs.streamQuality(), new Choice() {
                    @Override
                    public void chosen(int which) {
                        Prefs.setStreamQuality(Prefs.QUALITIES[which]);
                        build();
                    }
                });
            }
        });
        footer(c, "Original streams the file as it is when this phone can play it, and converts it to MP3 "
                + "when it can't (for example Opus or ALAC).");

        section(c, "Downloads");
        value(c, "Download Quality", Prefs.qualityLabel(Prefs.downloadQuality()), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickQuality("Download Quality", Prefs.downloadQuality(), new Choice() {
                    @Override
                    public void chosen(int which) {
                        Prefs.setDownloadQuality(Prefs.QUALITIES[which]);
                        build();
                    }
                });
            }
        });
        toggle(c, "Download over Wi‑Fi Only", Prefs.wifiOnlyDownloads(), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                Prefs.setWifiOnlyDownloads(on);
                Downloads.get(getActivity()).startWorker();
            }
        });
        final Downloads downloads = Downloads.get(c);
        int done = downloads.count("state = " + Downloads.DONE);
        int queued = downloads.count("state IN (" + Downloads.QUEUED + "," + Downloads.ACTIVE + ")");
        int failed = downloads.count("state = " + Downloads.FAILED);
        value(c, "Downloaded Songs", done + "  ·  " + Format.bytes(downloads.totalBytes()), null);
        if (queued > 0) value(c, "Waiting to Download", String.valueOf(queued), null);
        if (failed > 0) {
            action(c, "Retry " + failed + (failed == 1 ? " Failed Download" : " Failed Downloads"), false,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            downloads.retryFailed();
                        }
                    });
        }
        if (done + queued + failed > 0) {
            action(c, "Remove All Downloads", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.dialog(c)
                            .setMessage("Remove every downloaded song from this phone?")
                            .setPositiveButton("Remove All", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    downloads.removeAll();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });
        }
        footer(c, "Downloaded music plays without a connection. Find it under Library › Downloaded Music.");

        section(c, "Lyrics");
        toggle(c, "Find Lyrics Online", Prefs.onlineLyrics(), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                Prefs.setOnlineLyrics(on);
            }
        });
        footer(c, "When a song has no lyrics on your server or in a .lrc file next to it, Grapefruit looks "
                + "them up on LRCLIB (lrclib.net), a free community database of time-synced lyrics, by artist "
                + "and song title.");

        section(c, "About");
        value(c, "Grapefruit Music", "Version 1.0", null);
    }

    // ---- Grouped-list building blocks ----

    private void section(Context c, String title) {
        TextView t = Ui.text(c, 13, R.attr.gfTextSecondary, false);
        t.setText(title.toUpperCase(java.util.Locale.US));
        t.setPadding(Ui.dp(c, 16), Ui.dp(c, 26), Ui.dp(c, 16), Ui.dp(c, 6));
        content.addView(t);
        content.addView(Ui.separator(c, 0));
    }

    private void footer(Context c, String text) {
        // Close the group with a hairline, then explanatory text.
        TextView t = Ui.text(c, 13, R.attr.gfTextSecondary, false);
        t.setSingleLine(false);
        t.setText(text);
        t.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), 0);
        content.addView(t);
    }

    private LinearLayout row(Context c, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(c, 46));
        row.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        row.setBackgroundColor(Ui.color(c, R.attr.gfSurface));
        if (click != null) row.setOnClickListener(click);
        return row;
    }

    private void addRow(Context c, LinearLayout row, View.OnClickListener click) {
        if (click != null) {
            // Pressed highlight over the surface color.
            LinearLayout wrap = new LinearLayout(c);
            wrap.setBackgroundColor(Ui.color(c, R.attr.gfSurface));
            row.setBackgroundResource(Ui.resource(c, R.attr.gfRowBackground));
            wrap.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            content.addView(wrap);
        } else {
            content.addView(row);
        }
        View sep = Ui.separator(c, 0);
        content.addView(sep);
    }

    private void value(Context c, String label, String value, View.OnClickListener click) {
        LinearLayout row = row(c, click);
        TextView l = Ui.text(c, 17, R.attr.gfTextPrimary, false);
        l.setText(label);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView v = Ui.text(c, 16, R.attr.gfTextSecondary, false);
        v.setText(value);
        v.setPadding(Ui.dp(c, 8), 0, 0, 0);
        row.addView(v);
        if (click != null) {
            row.addView(Ui.icon(c, com.grapefruitmusic.ui.widget.Icons.CHEVRON_RIGHT,
                    Ui.color(c, R.attr.gfSeparator), 18));
        }
        addRow(c, row, click);
    }

    private void action(Context c, String label, boolean destructive, View.OnClickListener click) {
        LinearLayout row = row(c, click);
        TextView l = Ui.text(c, 17, R.attr.gfAccent, false);
        if (destructive) l.setTextColor(0xffff3b30);
        l.setText(label);
        row.addView(l);
        addRow(c, row, click);
    }

    private void toggle(Context c, String label, boolean on, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = row(c, null);
        TextView l = Ui.text(c, 17, R.attr.gfTextPrimary, false);
        l.setText(label);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch s = new Switch(c);
        s.setChecked(on);
        s.setOnCheckedChangeListener(listener);
        row.addView(s);
        addRow(c, row, null);
    }

    interface Choice {
        void chosen(int which);
    }

    private void choose(String title, String[] options, int selected, final Choice choice) {
        Ui.dialog(getActivity())
                .setTitle(title)
                .setSingleChoiceItems(options, selected, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        choice.chosen(which);
                    }
                })
                .show();
    }

    private void pickQuality(String title, int current, Choice choice) {
        String[] labels = new String[Prefs.QUALITIES.length];
        int selected = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Prefs.qualityLabel(Prefs.QUALITIES[i]);
            if (Prefs.QUALITIES[i] == current) selected = i;
        }
        choose(title, labels, selected, choice);
    }
}
