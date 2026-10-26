package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.playback.Dsp;
import com.grapefruitmusic.playback.Equalizer;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.playback.VolumeMatch;
import com.grapefruitmusic.source.Account;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.ui.widget.Toggle;
import com.grapefruitmusic.util.Format;
import com.grapefruitmusic.util.Prefs;

/** The Settings tab, as an iOS-style grouped list. */
public final class SettingsFragment extends BaseFragment
        implements Sources.Listener, Downloads.Listener, VolumeMatch.Listener {
    /** Theme names, in Prefs.THEME_* order from THEME_STARLIGHT. */
    private static final String[] THEMES = {"Starlight", "Pitch Black", "Grapefruit Light", "Grapefruit Dark"};

    private ScrollView page;
    /** "12 of 340" while Volume Match measures songs, else null. */
    private TextView measuring;

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
        VolumeMatch.get(getActivity()).addListener(this);
        build();
    }

    @Override
    public void onPause() {
        Sources.get(getActivity()).removeListener(this);
        Downloads.get(getActivity()).removeListener(this);
        VolumeMatch.get(getActivity()).removeListener(this);
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

    @Override
    public void onVolumeMatchProgress() {
        int[] p = VolumeMatch.get(getActivity()).progress();
        if (p != null && measuring != null) {
            measuring.setText(progressText(p));
        } else if ((p != null) != (measuring != null)) {
            build(); // the Measuring Songs row comes or goes
        }
    }

    private static String progressText(int[] p) {
        return p[0] + " of " + p[1];
    }

    private void build() {
        final Context c = getActivity();
        final Grouped g = Grouped.on(page);
        g.add(Ui.header(c, null, "Settings", null));

        g.section("Appearance");
        final int showing = Ui.theme(c);
        g.value("Theme", THEMES[showing - Prefs.THEME_STARLIGHT], new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choose("Theme", THEMES, showing - Prefs.THEME_STARLIGHT, new Choice() {
                    @Override
                    public void chosen(int which) {
                        // Picking the theme already showing changes nothing (and keeps following the phone).
                        int theme = which + Prefs.THEME_STARLIGHT;
                        if (theme != showing) {
                            Prefs.setTheme(theme);
                            getActivity().recreate();
                        }
                    }
                });
            }
        });
        if (Prefs.theme() == Prefs.THEME_SYSTEM && Build.VERSION.SDK_INT >= 29) {
            g.footer("Follows your phone's dark mode until you pick a theme.");
        }

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
                            .setDestructive(true)
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

        g.section("Volume");
        final VolumeMatch volumeMatch = VolumeMatch.get(c);
        g.toggle("Volume Match", volumeMatch.enabled(), new Toggle.Listener() {
            @Override
            public void onToggled(Toggle t, boolean on) {
                volumeMatch.setEnabled(on);
                Player.get(getActivity()).refreshVolumeMatch();
            }
        });
        int[] progress = volumeMatch.enabled() ? volumeMatch.progress() : null;
        measuring = progress != null ? g.value("Measuring Songs", progressText(progress), null) : null;
        g.footer("Plays songs at about the same volume, so a loud song doesn't jump out after a quiet one. "
                + "Loud songs are turned down to match; quiet ones play as they are. Grapefruit measures the "
                + "songs on this phone, and uses the loudness your server reports for streamed songs (or "
                + "measures them the first time they play).");

        g.section("Sound");
        g.value("Karaoke Mode", Prefs.karaoke() ? Math.round(Prefs.karaokeAmount() * 100) + "%" : "Off",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        main().push(new KaraokeFragment());
                    }
                });
        g.value("Volume Boost", Dsp.BOOST_NAMES[Prefs.volumeBoost()], new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choose("Volume Boost", Dsp.BOOST_NAMES, Prefs.volumeBoost(), new Choice() {
                    @Override
                    public void chosen(int which) {
                        Prefs.setVolumeBoost(which);
                        Player.get(getActivity()).audioEffectsChanged();
                        build();
                    }
                });
            }
        });
        g.value("Equalizer", Equalizer.name(Prefs.equalizer()), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                main().push(new EqualizerFragment());
            }
        });
        g.footer("Volume Boost makes Grapefruit louder than the phone's own maximum, like eXtra-Loud: a limiter "
                + "keeps the loudest moments from crackling, and the higher levels squeeze the music more to "
                + "get louder. The Equalizer shapes the sound with ten bands and presets. Karaoke Mode turns "
                + "down the lead vocal. They only change Grapefruit's sound. Listening loud for a long time "
                + "can damage your hearing.");

        g.section("Accessibility");
        g.toggle("Mono Audio", Prefs.monoAudio(), new Toggle.Listener() {
            @Override
            public void onToggled(Toggle t, boolean on) {
                Prefs.setMonoAudio(on);
                Player.get(getActivity()).audioEffectsChanged();
            }
        });
        g.footer("Plays the left and right channels together in both ears, so you hear everything with one "
                + "earbud in, or with hearing in one ear. Sound quality stays the same as stereo.");

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
        g.toggle("Download over Wi‑Fi Only", Prefs.wifiOnlyDownloads(), new Toggle.Listener() {
            @Override
            public void onToggled(Toggle t, boolean on) {
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
                            .setDestructive(true)
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
        g.toggle("Find Lyrics Online", Prefs.onlineLyrics(), new Toggle.Listener() {
            @Override
            public void onToggled(Toggle t, boolean on) {
                Prefs.setOnlineLyrics(on);
            }
        });
        g.footer("When a song has no lyrics on your server or in a .lrc file next to it, Grapefruit looks "
                + "them up on LRCLIB (lrclib.net), a free community database of time-synced lyrics, by artist "
                + "and song title.");

        g.section("About");
        g.value("About Grapefruit Music", "Version " + versionName(c), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                main().push(new AboutFragment());
            }
        });
    }

    static String versionName(Context c) {
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
