package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;

import com.grapefruitmusic.R;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.radio.MyStations;
import com.grapefruitmusic.radio.Radio;
import com.grapefruitmusic.radio.RadioArt;
import com.grapefruitmusic.radio.RadioBrowser;
import com.grapefruitmusic.source.InternetRadioSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.ui.widget.ArtworkView;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.ArtworkLoader;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Notice;
import com.grapefruitmusic.util.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Radio tab: internet radio on every device (My Stations, genres, popular stations, search),
 * and the FM radio where the tablet has one Grapefruit can use (presets and a scan for stations).
 */
public final class RadioFragment extends BaseFragment implements Radio.Listener, MyStations.Listener,
        Player.Listener {
    /** Genres to browse: what Grapefruit calls them, and the directory's tag. */
    private static final String[][] GENRES = {
            {"Pop", "pop"}, {"Rock", "rock"}, {"Country", "country"}, {"Hip Hop", "hip hop"},
            {"R&B", "rnb"}, {"Jazz", "jazz"}, {"Classical", "classical"}, {"Dance", "dance"},
            {"Oldies", "oldies"}, {"80s", "80s"}, {"Christian", "christian"}, {"Blues", "blues"},
            {"Reggae", "reggae"}, {"Latin", "latin"}, {"News", "news"}, {"Talk", "talk"}};
    private static final int POPULAR_ROWS = 8;
    private static final long POPULAR_FOR_MS = 30 * 60 * 1000L;

    /** The popular stations, kept for a while across visits to the tab. */
    private static List<MediaItem> popular;
    private static long popularAt;

    private Radio radio;
    private ScrollView page;
    private LinearLayout content;
    private boolean loadingPopular;
    private String popularError;
    /** The station or song playing when the page was last built (its tile is highlighted). */
    private String builtFor;

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
    public String backTitle() {
        return "Radio";
    }

    @Override
    public void onStart() {
        super.onStart();
        radio.addListener(this);
        MyStations.get(getActivity()).addListener(this);
        Player.get(getActivity()).addListener(this);
        build();
    }

    @Override
    public void onStop() {
        radio.removeListener(this);
        MyStations.get(getActivity()).removeListener(this);
        Player.get(getActivity()).removeListener(this);
        Bg.cancelMain(rebuild);
        super.onStop();
    }

    @Override
    public void onStationsChanged() {
        onRadioChanged();
    }

    @Override
    public void onPlayerChanged() {
        // Only when another station starts (not on every new song title).
        MediaItem now = Player.get(getActivity()).current();
        String key = now != null ? now.key() : null;
        if (key == null ? builtFor != null : !key.equals(builtFor)) onRadioChanged();
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
        MediaItem now = Player.get(c).current();
        builtFor = now != null ? now.key() : null;
        content.removeAllViews();
        content.addView(Ui.header(c, null, "Radio", null));

        // The first time the tab opens, Grapefruit looks for an FM radio in the background.
        if (!radio.known()) radio.find();
        boolean fm = radio.known() && !radio.unsupported();
        if (fm) {
            final Segmented which = new Segmented(c, new String[]{"FM Radio", "Internet Radio"},
                    Prefs.radioShowsInternet() ? 1 : 0);
            which.setListener(new Segmented.Listener() {
                @Override
                public void onSelected(int index) {
                    Prefs.setRadioShowsInternet(index == 1);
                    build();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.dp(c, 16);
            lp.rightMargin = Ui.dp(c, 16);
            lp.topMargin = Ui.dp(c, 4);
            lp.bottomMargin = Ui.dp(c, 4);
            content.addView(which, lp);
        }
        if (fm && !Prefs.radioShowsInternet()) addFm(c);
        else addInternet(c);
    }

    /** FM: listen and scan, presets as tiles, and the stations a scan found. */
    private void addFm(final Context c) {
        LinearLayout actions = Ui.actionRow(c);
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
        actions.addView(listen, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = Ui.dp(c, 8);
        actions.addView(scan, lp);
        content.addView(actions);
        if (!radio.headphones()) {
            // Not every tablet says when headphones are in, so this is a tip, not a block.
            LinearLayout tip = new LinearLayout(c);
            tip.setOrientation(LinearLayout.HORIZONTAL);
            tip.setGravity(Gravity.CENTER_VERTICAL);
            tip.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), 0);
            tip.addView(Ui.icon(c, Icons.HEADPHONES, Ui.color(c, R.attr.gfTextSecondary), 18));
            TextView t = Ui.text(c, 13, R.attr.gfTextSecondary, false);
            t.setSingleLine(false);
            t.setText(radio.canUseSpeaker() ? "Headphones are the radio's antenna: keep them plugged in. You can "
                    + "still listen through the speaker: choose it in Now Playing."
                    : "Headphones are the radio's antenna, and on this tablet the radio plays through them: keep "
                    + "them plugged in.");
            t.setPadding(Ui.dp(c, 8), 0, 0, 0);
            tip.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            content.addView(tip);
        }

        content.addView(Ui.sectionTitle(c, "Presets"));
        List<Radio.Preset> presets = radio.presets();
        if (presets.isEmpty()) {
            TextView empty = Ui.text(c, 15, R.attr.gfTextSecondary, false);
            empty.setSingleLine(false);
            empty.setText("No presets yet. Tune in a station and tap + in Now Playing to save it here.");
            empty.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
            content.addView(empty);
        } else {
            List<View> tiles = new ArrayList<View>();
            for (Radio.Preset p : presets) tiles.add(tile(c, p));
            addTiles(c, tiles, 170);
            TextView hint = Ui.text(c, 13, R.attr.gfTextSecondary, false);
            hint.setSingleLine(false);
            hint.setText("Touch and hold a preset to edit its label or remove it.");
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
    }

    /**
     * Internet radio: search and add by link, the listener's own stations, genres, and what's
     * popular where they are.
     */
    private void addInternet(final Context c) {
        LinearLayout actions = Ui.actionRow(c);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 4));
        LinearLayout search = Ui.pill(c, Icons.SEARCH, "Search Stations");
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                main().push(StationsFragment.newInstance(StationsFragment.SEARCH, null, "Search Stations", "Radio"));
            }
        });
        actions.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        LinearLayout link = Ui.pill(c, Icons.PLUS, "Add by Link");
        link.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addByLink(c);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = Ui.dp(c, 8);
        actions.addView(link, lp);
        content.addView(actions);
        boolean online = Http.isOnline(c);
        if (!online) addNote(c, "You're offline. Internet radio plays when you're connected to Wi-Fi.");

        content.addView(Ui.sectionTitle(c, "My Stations"));
        final List<MediaItem> mine = MyStations.get(c).all();
        if (mine.isEmpty()) {
            addNote(c, "Stations you keep show up here. Touch and hold a station, or tap \u22ef beside it, "
                    + "and choose Add to My Stations.");
        } else {
            List<View> tiles = new ArrayList<View>();
            MediaItem now = Player.get(c).current();
            for (int i = 0; i < mine.size(); i++) tiles.add(stationTile(c, mine, i, now));
            addTiles(c, tiles, 170);
            addNote(c, "Touch and hold a station to remove it.");
        }

        content.addView(Ui.sectionTitle(c, "Genres"));
        List<View> genres = new ArrayList<View>();
        for (String[] g : GENRES) genres.add(genreTile(c, g[0], g[1]));
        addTiles(c, genres, 140);

        content.addView(Ui.sectionTitle(c, "Popular Stations"));
        if (popular != null) {
            int shown = Math.min(POPULAR_ROWS, popular.size());
            content.addView(Ui.separator(c, 16));
            for (int i = 0; i < shown; i++) addStationRow(c, popular, i);
            if (popular.size() > shown) {
                TextView all = Ui.text(c, 17, R.attr.gfAccent, false);
                all.setText("See All");
                all.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12));
                all.setBackgroundResource(Ui.resource(c, R.attr.gfRowBackground));
                all.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        main().push(StationsFragment.newInstance(StationsFragment.TOP, null, "Popular Stations",
                                "Radio"));
                    }
                });
                content.addView(all);
            }
        } else if (popularError != null) {
            addNote(c, popularError);
        } else if (online) {
            addNote(c, "Loading\u2026");
        }
        if (online && (popular == null || System.currentTimeMillis() - popularAt > POPULAR_FOR_MS)) loadPopular();
    }

    private void loadPopular() {
        if (loadingPopular) return;
        loadingPopular = true;
        bg(new Bg.Work<List<MediaItem>>() {
            @Override
            public List<MediaItem> run() throws Exception {
                return RadioBrowser.top(Locale.getDefault().getCountry());
            }
        }, new Bg.Done<List<MediaItem>>() {
            @Override
            public void done(List<MediaItem> found, Exception error) {
                loadingPopular = false;
                if (error == null) {
                    popular = found;
                    popularAt = System.currentTimeMillis();
                    popularError = null;
                } else if (popular == null) {
                    popularError = "Can't reach the station directory right now.";
                }
                if (content != null) build();
            }
        });
    }

    /** Gray text under a section. */
    private void addNote(Context c, String text) {
        TextView t = Ui.text(c, 15, R.attr.gfTextSecondary, false);
        t.setSingleLine(false);
        t.setText(text);
        t.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
        content.addView(t);
    }

    /** One of My Stations: its logo, its name; tap to listen, hold to remove. */
    private View stationTile(final Context c, final List<MediaItem> stations, final int index, MediaItem now) {
        final MediaItem m = stations.get(index);
        LinearLayout tile = new LinearLayout(c);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setBackgroundResource(Ui.resource(c, R.attr.gfRowBackground));
        ArtworkView art = Ui.artwork(c, 0);
        art.setCornerRadiusDp(8);
        ArtworkLoader.get(c).into(art, m, Ui.dp(c, 200));
        tile.addView(art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        boolean current = now != null && now.key().equals(m.key());
        TextView name = Ui.text(c, 15, current ? R.attr.gfAccent : R.attr.gfTextPrimary, current);
        name.setText(m.title);
        name.setPadding(0, Ui.dp(c, 6), 0, 0);
        tile.addView(name);
        tile.setContentDescription(m.title);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StationsFragment.play(c, stations, index);
            }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Ui.dialog(c)
                        .setTitle(m.title)
                        .setItems(new CharSequence[]{"Play Next", "Remove from My Stations"},
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (which == 0) Player.get(c).playNext(m);
                                        else MyStations.get(c).remove(m);
                                    }
                                })
                        .show();
                return true;
            }
        });
        return tile;
    }

    /** A genre: its name on a coloured button. */
    private View genreTile(final Context c, final String name, final String tag) {
        TextView t = Ui.text(c, 16, R.attr.gfTextPrimary, true);
        t.setTextColor(0xffffffff);
        t.setText(name);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(Ui.dp(c, 54));
        int[] colors = RadioArt.colorsFor("genre:" + name);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        bg.setCornerRadius(Ui.dp(c, 10));
        t.setBackground(bg);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                main().push(StationsFragment.newInstance(StationsFragment.GENRE, tag, name, "Radio"));
            }
        });
        return t;
    }

    /** A popular station: logo, name and what it plays; tap to listen. */
    private void addStationRow(final Context c, final List<MediaItem> stations, final int index) {
        final MediaItem m = stations.get(index);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(c, 56));
        row.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        row.setBackgroundResource(Ui.resource(c, R.attr.gfRowBackground));
        ArtworkView art = Ui.artwork(c, 44);
        ArtworkLoader.get(c).into(art, m, Ui.dp(c, 44));
        row.addView(art);
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 6), 0);
        MediaItem now = Player.get(c).current();
        boolean current = now != null && now.key().equals(m.key());
        TextView title = Ui.text(c, 16, current ? R.attr.gfAccent : R.attr.gfTextPrimary, false);
        title.setText(m.title);
        texts.addView(title);
        TextView about = Ui.text(c, 13, R.attr.gfTextSecondary, false);
        about.setText(m.artist);
        texts.addView(about);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final boolean kept = MyStations.get(c).contains(m);
        IconView add = new IconView(c);
        add.setIcon(kept ? Icons.CHECK : Icons.PLUS);
        add.setColor(Ui.accent(c));
        int pad = Ui.dp(c, 11);
        add.setPadding(pad, pad, pad, pad);
        add.setContentDescription(kept ? "In My Stations" : "Add to My Stations");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (kept) MyStations.get(c).remove(m);
                else MyStations.get(c).add(m);
            }
        });
        row.addView(add, new LinearLayout.LayoutParams(Ui.dp(c, 46), Ui.dp(c, 46)));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StationsFragment.play(c, stations, index);
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                TrackActions.show(RadioFragment.this, m, Sources.get(c).byId(InternetRadioSource.ID));
                return true;
            }
        });
        content.addView(row);
        content.addView(Ui.separator(c, 72));
    }

    /** Add by Link: a station Grapefruit's directory doesn't have, from its stream's address. */
    private void addByLink(final Context c) {
        LinearLayout fields = new LinearLayout(c);
        fields.setOrientation(LinearLayout.VERTICAL);
        final android.widget.EditText name = Ui.field(c, 16, false, "Station name");
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        fields.addView(name);
        final android.widget.EditText url = Ui.field(c, 16, false, "http://\u2026");
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 10);
        fields.addView(url, lp);
        Ui.dialog(c)
                .setTitle("Add by Link")
                .setMessage("Paste the link to the station's stream (or its .pls or .m3u playlist). Most stations "
                        + "list it on their website, often as \u201cListen\u201d or \u201cStream URL\u201d.")
                .setView(fields)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String link = url.getText().toString().trim();
                        if (!link.startsWith("http://") && !link.startsWith("https://")) {
                            Notice.showLong(c, "That isn't a link. It should start with http:// or https://.");
                            return;
                        }
                        String title = name.getText().toString().trim();
                        if (title.length() == 0) title = Uri.parse(link).getHost();
                        if (title == null || title.length() == 0) title = "Radio Station";
                        MyStations.get(c).addLink(title, link);
                        Notice.show(c, "Added " + title + " to My Stations");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Tiles (presets, stations, genres): three or more across, more when the screen is wider. */
    private void addTiles(Context c, List<View> tiles, int widthDp) {
        int width = getResources().getConfiguration().screenWidthDp;
        int columns = Math.max(3, width / widthDp);
        int gap = Ui.dp(c, 14);
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(Ui.dp(c, 16) - gap / 2, 0, Ui.dp(c, 16) - gap / 2, Ui.dp(c, 12));
                content.addView(row);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.leftMargin = gap / 2;
            lp.rightMargin = gap / 2;
            row.addView(tiles.get(i), lp);
        }
        // Keep the last row's tiles the same size as the others.
        int rest = tiles.size() % columns;
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
        art.setBitmap(RadioArt.of(p.frequency, p.name(), Ui.dp(c, 200)));
        tile.addView(art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        boolean current = radio.inSession() && radio.frequency() == p.frequency;
        TextView name = Ui.text(c, 15, current ? R.attr.gfAccent : R.attr.gfTextPrimary, current);
        name.setText(p.label());
        name.setPadding(0, Ui.dp(c, 6), 0, 0);
        tile.addView(name);
        if (p.name() != null) {
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
                .setItems(new CharSequence[]{"Edit Label", "Remove"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) PresetLabel.edit(c, p);
                        else radio.removePreset(p.frequency);
                    }
                })
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
                else PresetLabel.add(c, f);
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
