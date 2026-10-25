package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ScrollView;

import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.source.Account;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.util.Format;
import com.grapefruitmusic.util.Prefs;

/** The Settings tab, as an iOS-style grouped list. */
public final class SettingsFragment extends BaseFragment implements Sources.Listener, Downloads.Listener {
    private ScrollView page;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        page = Grouped.page(getActivity());
        return page;
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
        final Grouped g = Grouped.on(page);
        g.add(Ui.header(c, null, "Settings", null));

        g.section("Appearance");
        String[] themes = {"Match System", "Light", "Dark"};
        g.value("Theme", themes[Prefs.theme()], new View.OnClickListener() {
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
        g.footer("Match System follows the phone's dark mode on Android 10 and later.");

        g.section("Servers");
        for (final Account a : Sources.get(c).accounts()) {
            String type = Account.JELLYFIN.equals(a.type) ? "Jellyfin" : "Navidrome / Subsonic";
            g.value(a.name, type, new View.OnClickListener() {
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
        g.action("Add a Server…", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AddServerDialog.show(getActivity());
            }
        });
        g.footer("Grapefruit Music plays music from your own Navidrome, Subsonic or Jellyfin server, "
                + "and from music files on this phone.");

        g.section("On This Phone");
        int excluded = Prefs.excludedFolders().size();
        g.value("Excluded Folders", excluded == 0 ? "None" : excluded == 1 ? "1 folder" : excluded + " folders",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        main().push(new ExcludedFoldersFragment());
                    }
                });
        g.footer("Hide folders such as ringtones or voice messages from On This Phone.");

        g.section("Playback");
        g.value("Streaming Quality", Prefs.qualityLabel(Prefs.streamQuality()), new View.OnClickListener() {
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
        g.footer("Original streams the file as it is when this phone can play it, and converts it to MP3 "
                + "when it can't (for example Opus or ALAC).");

        g.section("Downloads");
        g.value("Download Quality", Prefs.qualityLabel(Prefs.downloadQuality()), new View.OnClickListener() {
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
        g.toggle("Download over Wi‑Fi Only", Prefs.wifiOnlyDownloads(), new CompoundButton.OnCheckedChangeListener() {
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
        g.value("Downloaded Songs", done + "  ·  " + Format.bytes(downloads.totalBytes()), null);
        if (queued > 0) g.value("Waiting to Download", String.valueOf(queued), null);
        if (failed > 0) {
            g.action("Retry " + failed + (failed == 1 ? " Failed Download" : " Failed Downloads"), false,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            downloads.retryFailed();
                        }
                    });
        }
        if (done + queued + failed > 0) {
            g.action("Remove All Downloads", true, new View.OnClickListener() {
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
        g.footer("Downloaded music plays without a connection. Find it under Library › Downloaded Music.");

        g.section("Lyrics");
        g.toggle("Find Lyrics Online", Prefs.onlineLyrics(), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                Prefs.setOnlineLyrics(on);
            }
        });
        g.footer("When a song has no lyrics on your server or in a .lrc file next to it, Grapefruit looks "
                + "them up on LRCLIB (lrclib.net), a free community database of time-synced lyrics, by artist "
                + "and song title.");

        g.section("About");
        g.value("Grapefruit Music", "Version " + versionName(c), null);
    }

    private static String versionName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "?";
        }
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
