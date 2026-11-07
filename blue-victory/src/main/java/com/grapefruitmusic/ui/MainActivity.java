package com.grapefruitmusic.ui;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.radio.Radio;
import com.grapefruitmusic.radio.RadioArt;
import com.grapefruitmusic.radio.StationText;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.ui.widget.ArtworkView;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.ArtworkLoader;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Notice;
import com.grapefruitmusic.util.Prefs;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** One activity: tab bar, a stack of pages, the mini player and the Now Playing card. */
public final class MainActivity extends Activity implements Player.Listener, Radio.Listener, Notice.Sink {
    public static final String EXTRA_OPEN_NOW_PLAYING = "open_now_playing";

    private static final int TAB_LIBRARY = 0;
    private static final int TAB_RADIO = 2;
    private static final int TAB_PODCASTS = 3;
    static final int TAB_SEARCH = 4;
    private static final int TAB_SETTINGS = 5;
    static final int TAB_AUDIOBOOKS = 6;
    /** Not a page here: Grapefruit TV & Movies (or Grapefruit TV), opened when it's installed. */
    private static final int TAB_TV = 7;
    private static final int[] TAB_IDS = {TAB_LIBRARY, TAB_RADIO, TAB_PODCASTS, TAB_AUDIOBOOKS, TAB_TV, TAB_SEARCH,
            TAB_SETTINGS};
    private static final String[] TAB_NAMES = {"Music", "Radio", "Podcasts", "Audiobooks", "TV & Movies", "Search",
            "Settings"};
    private static final int[] TAB_ICONS = {Icons.MUSIC, Icons.RADIO, Icons.PODCASTS, Icons.BOOK, Icons.TV,
            Icons.SEARCH, Icons.SETTINGS};
    /** Grapefruit TV & Movies' package, while it's installed (the TV & Movies tab shows then). */
    private String tvPackage;

    /** Grapefruit's open windows (normally one), for the notification's close button. Main thread. */
    private static final Map<MainActivity, Boolean> LIVE = new WeakHashMap<MainActivity, Boolean>();

    private Player player;
    private Radio radio;
    private TvFeature television;
    private View tvMini;

    TvFeature television(){return television;}
    private NowPlaying nowPlaying;
    private int tab = TAB_LIBRARY;
    /** The current tab's sections, by number, while it's open (see showSection). */
    private final android.util.SparseArray<Fragment> sections = new android.util.SparseArray<Fragment>();
    /** Whose sections those are: the Library's for one source, or Podcasts'. */
    private String sectionsOwner;
    private final IconView[] tabIcons = new IconView[TAB_NAMES.length];
    private final TextView[] tabLabels = new TextView[TAB_NAMES.length];
    private TextView miniArtist;

    private View mini;
    private View miniSeparator;
    private ArtworkView miniArt;
    private com.grapefruitmusic.ui.widget.MarqueeText miniTitle;
    private IconView miniPlay;
    private TextView notice;
    private final Runnable hideNotice = new Runnable() {
        @Override
        public void run() {
            if (notice == null) return;
            notice.animate().alpha(0f).translationY(Ui.dp(MainActivity.this, 8)).setDuration(200)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            notice.setVisibility(View.GONE);
                        }
                    }).start();
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        Ui.applyTheme(this);
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
        Fonts.applyAll(findViewById(R.id.root));
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            findViewById(R.id.root).setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                    android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                            | android.view.WindowInsets.Type.displayCutout());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                }
            });
        }
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            java.util.List<String> needed = new java.util.ArrayList<String>();
            String media = android.os.Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_AUDIO"
                    : "android.permission.READ_EXTERNAL_STORAGE";
            if (checkSelfPermission(media) != android.content.pm.PackageManager.PERMISSION_GRANTED) needed.add(media);
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) needed.add("android.permission.POST_NOTIFICATIONS");
            if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[needed.size()]), 40);
        }
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            boolean light = Ui.theme(this) == com.grapefruitmusic.util.Prefs.THEME_GRAPEFRUIT_LIGHT;
            int flags = light ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0;
            if (android.os.Build.VERSION.SDK_INT >= 26 && light) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        player = Player.get(this);
        radio = Radio.get(this);
        television = TvFeature.create(this);
        LIVE.put(this, Boolean.TRUE);

        buildTabBar();
        mini = findViewById(R.id.mini_player);
        miniSeparator = findViewById(R.id.mini_separator);
        miniArt = (ArtworkView) findViewById(R.id.mini_art);
        miniArt.setPlaceholderColors(Ui.color(this, R.attr.gfFill), Ui.color(this, R.attr.gfTextSecondary));
        miniTitle = (com.grapefruitmusic.ui.widget.MarqueeText) findViewById(R.id.mini_title);
        // A title too long for the mini player scrolls by, three times a song (MarqueeText: a
        // TextView's marquee started over after a letter); not while Now Playing covers it.
        miniTitle.setTextSize(15);
        miniTitle.setTextColor(Ui.color(this, R.attr.gfTextPrimary));
        miniTitle.setTypeface(Fonts.semiBold());
        miniTitle.setRepeats(3);
        miniArtist = (TextView) findViewById(R.id.mini_artist);
        miniArtist.setTypeface(Fonts.regular());
        // A pill above the tabs, a shade darker while pressed; the artwork round in its end.
        miniArt.setCornerRadiusDp(22);
        mini.setBackground(Ui.pressable(this, Ui.color(this, R.attr.gfSurface), Ui.PILL, true));
        miniPlay = (IconView) findViewById(R.id.mini_play);
        IconView miniNext = (IconView) findViewById(R.id.mini_next);
        int primary = Ui.color(this, R.attr.gfTextPrimary);
        Ui.styleCircle(miniPlay, true);
        miniNext.setColor(primary);
        miniNext.setIcon(Icons.NEXT);
        miniNext.setContentDescription("Next");
        miniPlay.setContentDescription("Play or pause");
        mini.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nowPlaying.open();
            }
        });
        miniPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (radio.inSession()) radio.togglePlay();
                else player.togglePlay();
            }
        });
        miniNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (radio.inSession()) radio.seek(true);
                else player.next();
            }
        });

        nowPlaying = new NowPlaying(this, findViewById(R.id.root));
        if (television != null) {
            tvMini = television.mini();
            tvMini.setVisibility(View.GONE);
            ((FrameLayout)findViewById(R.id.column)).addView(tvMini,
                    new FrameLayout.LayoutParams(Ui.dp(this,220),Ui.dp(this,124),Gravity.TOP|Gravity.LEFT));
        }
        fitBars();
        floatBars();

        if (saved == null) {
            showTab(TAB_LIBRARY);
        } else {
            tab = saved.getInt("tab", TAB_LIBRARY);
            boolean visible = false;
            for (int id : TAB_IDS) if (id == tab && (id != TAB_TV || television != null)) visible = true;
            if (!visible) showTab(TAB_LIBRARY);
            else highlightTab();
            if (saved.getBoolean("now_playing_open")) nowPlaying.openImmediately();
        }
        handleIntent(getIntent());
        splash();
        offerCrashReport();
    }

    /**
     * After Grapefruit closed because of a fault, offers the report it kept (CrashReport). It goes
     * nowhere unless shared: the person picks where, and sees it there first.
     */
    private void offerCrashReport() {
        final String report = com.grapefruitmusic.util.CrashReport.pending(this);
        if (report == null) return;
        // Once the splash screen has gone.
        Bg.mainDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                final android.content.Context c = MainActivity.this;
                Ui.dialog(c)
                        .setTitle(com.grapefruitmusic.util.Brand.text("Grapefruit closed unexpectedly"))
                        .setMessage(com.grapefruitmusic.util.Brand.text("Last time, a fault in Grapefruit closed it. It kept a short report: the app")
                                + com.grapefruitmusic.util.Brand.text(" and Android versions, this device's model, and where in Grapefruit's code it")
                                + " stopped. Nothing has been sent. Sharing it with the developer helps get it fixed.")
                        .setPositiveButton("Share Report", (d, w) -> {
                            com.grapefruitmusic.util.CrashReport.clear(c);
                            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_SUBJECT, com.grapefruitmusic.util.Brand.text("Grapefruit crash report"))
                                    .putExtra(Intent.EXTRA_TEXT, report);
                            try {
                                startActivity(Intent.createChooser(send, "Share Report"));
                            } catch (android.content.ActivityNotFoundException e) {
                                Notice.show(c, "No app here can share text.");
                            }
                        })
                        .setNegativeButton("Dismiss", (d, w) -> com.grapefruitmusic.util.CrashReport.clear(c))
                        .show();
            }
        }, 1300);
    }

    /** Whether this run of the app has shown its splash screen. */
    private static boolean splashShown;

    /**
     * The splash screen: the logo on the theme's background (also the window's background, which
     * Android shows while Grapefruit starts), for a moment when the app starts, then fading away.
     */
    private void splash() {
        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+ draws its own splash screen first: in this theme's colours next time.
            int t = Ui.theme(this);
            getSplashScreen().setSplashScreenTheme(t == Prefs.THEME_PITCH_BLACK ? R.style.Splash_Dark
                    : t == Prefs.THEME_GRAPEFRUIT_DARK ? R.style.Splash_GrapefruitDark : R.style.Splash_GrapefruitLight);
        }
        if (splashShown) {
            dropWindowBackground();
            return;
        }
        splashShown = true;
        final View cover = new View(this);
        cover.setBackgroundResource(Ui.resource(this, R.attr.gfSplash));
        cover.setClickable(true); // nothing underneath answers a tap meanwhile
        final FrameLayout root = (FrameLayout) findViewById(R.id.root);
        root.addView(cover, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        Bg.mainDelayed(new Runnable() {
            @Override
            public void run() {
                cover.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        root.removeView(cover);
                        dropWindowBackground();
                    }
                }).start();
            }
        }, Build.VERSION.SDK_INT >= 31 ? 500 : 800); // shorter after Android 12's own splash screen
    }

    /** Pages cover the window: its background (the splash screen) needn't be drawn under them. */
    private void dropWindowBackground() {
        getWindow().setBackgroundDrawable(null);
    }

    /** Show a tab's name beside its icon when it's chosen (when there's room across). */
    private boolean tabNames;

    private String tabName(int index) {
        return getResources().getBoolean(R.bool.blue_victory) && TAB_IDS[index] == TAB_TV ? "TV" : TAB_NAMES[index];
    }

    /** Grapefruit TV & Movies' (or Grapefruit TV's) package, if it's installed, else null. */
    static String findTvApp(android.content.Context c) {
        android.content.pm.PackageManager pm = c.getPackageManager();
        // Grapefruit FAST, Grapefruit TV and Grapefruit TV & Movies have kept this id.
        String known = "io.github.hayliewordsman.grapefruitfast";
        if (pm.getLaunchIntentForPackage(known) != null) return known;
        // In case a later version takes another: any of the developer's apps called Grapefruit TV…
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        try {
            for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(main, 0)) {
                String pkg = r.activityInfo.packageName;
                if (!pkg.startsWith("io.github.hayliewordsman.") || pkg.equals(c.getPackageName())) continue;
                CharSequence label = r.loadLabel(pm);
                if (label != null && label.toString().startsWith("Grapefruit TV")) return pkg;
            }
        } catch (RuntimeException ignored) {
            // The package manager went away (the system restarting): no tab this time.
        }
        return null;
    }

    void showTvSection(String section) {
        if(television==null)return;
        tab=TAB_TV;television.selected(section);highlightTab();sections.clear();
        FragmentManager fm=getFragmentManager();
        if(fm.getBackStackEntryCount()>0)fm.popBackStackImmediate(null,FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fm.beginTransaction().replace(R.id.content,television.page(section)).commit();
    }

    void showTvNowPlaying(){
        if(television==null)return;
        if(nowPlaying.isOpen())nowPlaying.close();
        showTvSection(television.selected());
        push(television.watchPage());
    }

    private void openTv() {
        if(television!=null){showTvSection(television.selected());return;}
        if (tvPackage == null) return;
        Intent launch = getPackageManager().getLaunchIntentForPackage(tvPackage);
        if (launch == null && Build.VERSION.SDK_INT >= 21) {
            launch = getPackageManager().getLeanbackLaunchIntentForPackage(tvPackage);
        }
        if (launch == null) {
            updateTvTab();
            return;
        }
        try {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (android.content.ActivityNotFoundException e) {
            updateTvTab();
        }
    }

    /** Shows or hides the TV & Movies tab as Grapefruit TV & Movies is installed or removed. */
    private void updateTvTab() {
        if(television!=null)return;
        String found = findTvApp(this);
        boolean changed = found == null ? tvPackage != null : !found.equals(tvPackage);
        tvPackage = found;
        if (!changed) return;
        for (int i = 0; i < TAB_IDS.length; i++) {
            if (TAB_IDS[i] == TAB_TV) ((View) tabIcons[i].getParent()).setVisibility(found != null ? View.VISIBLE : View.GONE);
        }
        fitBars();
    }

    private void buildTabBar() {
        tvPackage = findTvApp(this);
        LinearLayout bar = (LinearLayout) findViewById(R.id.tab_bar);
        // A floating pill: the page shows around it.
        bar.setBackground(Ui.shape(this, Ui.color(this, R.attr.gfSurface), Ui.PILL, true, false));
        tabNames = false;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int index = TAB_IDS[i];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
            item.setContentDescription(tabName(i));
            IconView icon = Ui.icon(this, TAB_ICONS[i], 0, 22);
            item.addView(icon);
            TextView label = Ui.text(this, 14, R.attr.gfTextSecondary, false);
            label.setTypeface(Fonts.medium());
            label.setText(tabName(i));
            label.setPadding(Ui.dp(this, 8), 0, 0, 0);
            label.setVisibility(View.GONE);
            item.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            if (index == TAB_TV && television == null) {
                // Opens Grapefruit TV & Movies; no page, no menu here.
                item.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openTv();
                    }
                });
                item.setVisibility(tvPackage != null ? View.VISIBLE : View.GONE);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        Ui.dp(this, 48));
                tlp.leftMargin = Ui.dp(this, 2);
                bar.addView(item, tlp);
                tabIcons[i] = icon;
                tabLabels[i] = label;
                continue;
            }
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (nowPlaying.isOpen()) {
                        nowPlaying.close();
                        if (index == tab) return;
                    }
                    // The tab showing, tapped again: its menu (Settings: back to its first page).
                    if (index == tab && index != TAB_SETTINGS) showTabMenu(index, v);
                    else showTab(index);
                }
            });
            if (index != TAB_SETTINGS) {
                // Touch and hold any tab but Settings, from anywhere: its menu.
                item.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (nowPlaying.isOpen()) nowPlaying.close();
                        showTabMenu(index, v);
                        return true;
                    }
                });
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    Ui.dp(this, 48));
            if (i > 0) lp.leftMargin = Ui.dp(this, 2);
            bar.addView(item, lp);
            tabIcons[i] = icon;
            tabLabels[i] = label;
        }
    }

    /** The tab showing: its icon (and name, if there's room) on the accent pill; the others plain. */
    private void highlightTab() {
        int inactive = Ui.color(this, R.attr.gfTextSecondary);
        int on = Ui.onAccent(this);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            boolean chosen = TAB_IDS[i] == tab && (TAB_IDS[i] != TAB_TV || television != null);
            View item = (View) tabIcons[i].getParent();
            item.setBackground(chosen ? Ui.accentFill(this, Ui.dp(this, 24), false) : null);
            item.setSelected(chosen);
            tabIcons[i].setColor(chosen ? on : inactive);
            tabLabels[i].setTextColor(on);
            tabLabels[i].setVisibility(chosen && tabNames ? View.VISIBLE : View.GONE);
        }
    }

    /** Room at the bottom of a page for the mini player and tabs floating over it (px). */
    private int barsInset;

    /**
     * The mini player and the tabs float over the bottom of the page, on a fade from the page's
     * colour, and pages scroll out from under them (fitPage).
     */
    private void floatBars() {
        View bars = findViewById(R.id.bottom_bars);
        int page = Ui.color(this, R.attr.gfBackground) & 0x00ffffff;
        bars.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{page | 0xcc000000, page | 0x66000000, page}));
        // The fade is drawn, but taps go through it to the page (the bars themselves take theirs).
        bars.setClickable(false);
        bars.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int height = b - t;
                if (height == barsInset) return;
                barsInset = height;
                // After this layout pass: the page's new padding lays it out again.
                v.post(new Runnable() {
                    @Override
                    public void run() {
                        android.app.Fragment page = getFragmentManager().findFragmentById(R.id.content);
                        if (page != null) fitPage(page.getView());
                    }
                });
            }
        });
    }

    /** Lets a page's scrolling views scroll out from under the floating mini player and tabs. */
    void fitPage(View page) {
        if (page != null) inset(page, barsInset);
    }

    private static void inset(View v, int inset) {
        if (v instanceof android.widget.AbsListView || v instanceof android.widget.ScrollView
                || v instanceof com.grapefruitmusic.ui.widget.HorizontalGrid) {
            Object own = v.getTag(R.id.own_bottom_padding);
            int base = own instanceof Integer ? (Integer) own : v.getPaddingBottom();
            if (own == null) v.setTag(R.id.own_bottom_padding, base);
            ((ViewGroup) v).setClipToPadding(false);
            if (v.getPaddingBottom() != base + inset) {
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), base + inset);
            }
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) inset(g.getChildAt(i), inset);
        }
    }

    /** Each tab's section as last shown (Library's and Podcasts'), for the tick in their menus. */
    private final int[] shownSection = new int[TAB_TV + 1];

    /**
     * A tab's menu, from its button (TabMenu): the tab's chips as rows, the one showing ticked.
     * Choosing one goes to the tab (if it isn't showing) and does what the chip does.
     */
    private void showTabMenu(final int index, View button) {
        TabMenu.Builder b = new TabMenu.Builder();
        boolean here = index == tab;
        if (index == TAB_LIBRARY) {
            b.add("Search", Icons.SEARCH, false, new Runnable() {
                @Override
                public void run() {
                    showTab(TAB_SEARCH);
                }
            });
            b.add(Sources.get(this).current().name(), Icons.CHEVRON_DOWN, false, new Runnable() {
                @Override
                public void run() {
                    if (tab != TAB_LIBRARY) showLibrary(SectionMenu.FIRST);
                    SectionMenu.pickSource(MainActivity.this);
                }
            });
            b.rule();
            for (int i = 0; i < SectionMenu.LIBRARY.length; i++) {
                final int section = i;
                b.add(SectionMenu.LIBRARY[i], TabMenu.NO_ICON, here && shownSection[TAB_LIBRARY] == i, new Runnable() {
                    @Override
                    public void run() {
                        showLibrary(section);
                    }
                });
            }
        } else if (index == TAB_PODCASTS) {
            b.add("Search", Icons.SEARCH, false, new Runnable() {
                @Override
                public void run() {
                    if (tab != TAB_PODCASTS) showPodcasts(shownSection[TAB_PODCASTS]);
                    push(PodcastsFragment.search());
                }
            });
            b.rule();
            for (int i = 0; i < SectionMenu.PODCASTS.length; i++) {
                final int section = i;
                b.add(SectionMenu.PODCASTS[i], TabMenu.NO_ICON, here && shownSection[TAB_PODCASTS] == i, new Runnable() {
                    @Override
                    public void run() {
                        showPodcasts(section);
                    }
                });
            }
        } else if (index == TAB_RADIO) {
            // As the top of the Radio tab: FM or Internet Radio (on a device with FM), then that one's buttons.
            boolean fm = radio.known() && !radio.unsupported();
            boolean internet = !fm || Prefs.radioShowsInternet();
            if (fm) {
                b.add("FM Radio", TabMenu.NO_ICON, here && !internet, radioAction(new RadioStep() {
                    @Override
                    public void on(RadioFragment page) {
                        page.showInternet(false);
                    }
                }));
                b.add("Internet Radio", TabMenu.NO_ICON, here && internet, radioAction(new RadioStep() {
                    @Override
                    public void on(RadioFragment page) {
                        page.showInternet(true);
                    }
                }));
                b.rule();
            }
            if (internet) {
                b.add("Search Stations", Icons.SEARCH, false, radioAction(new RadioStep() {
                    @Override
                    public void on(RadioFragment page) {
                        page.searchStations();
                    }
                }));
                b.add("Add by Link", Icons.PLUS, false, radioAction(new RadioStep() {
                    @Override
                    public void on(RadioFragment page) {
                        page.addByLink();
                    }
                }));
            } else {
                boolean on = radio.wantsToPlay();
                b.add(on ? "Stop" : "Listen to " + Radio.format(radio.frequency()), on ? Icons.STOP : Icons.PLAY, false,
                        radioAction(new RadioStep() {
                            @Override
                            public void on(RadioFragment page) {
                                page.listen();
                            }
                        }));
                b.add("Find Stations", Icons.SEARCH, false, radioAction(new RadioStep() {
                    @Override
                    public void on(RadioFragment page) {
                        page.findStations();
                    }
                }));
            }
        } else if (index == TAB_AUDIOBOOKS) {
            // As the top of the Audiobooks tab: Add, and where to buy them.
            b.add("Add", Icons.PLUS, false, new Runnable() {
                @Override
                public void run() {
                    AudiobooksFragment page = (AudiobooksFragment) rootOf(TAB_AUDIOBOOKS, AudiobooksFragment.class);
                    if (page != null) AudiobooksFragment.add(page);
                }
            });
            b.add("Libro.fm", Icons.BOOK, false, new Runnable() {
                @Override
                public void run() {
                    AudiobooksFragment.openLibroFm(MainActivity.this);
                }
            });
        } else if (index == TAB_TV && television != null) {
            for(final String section:television.sections()) {
                b.add(section,TabMenu.NO_ICON,here && section.equals(television.selected()),()->showTvSection(section));
            }
        } else if (index == TAB_SEARCH) {
            // Search has no chips: its menu chooses what it looks for.
            for (int i = 0; i < SearchFragment.FILTERS.length; i++) {
                final int filter = i;
                b.add(SearchFragment.FILTERS[i], TabMenu.NO_ICON, SearchFragment.filter() == i, new Runnable() {
                    @Override
                    public void run() {
                        SearchFragment page = (SearchFragment) rootOf(TAB_SEARCH, SearchFragment.class);
                        if (page != null) page.setFilter(filter);
                    }
                });
            }
        } else {
            return;
        }
        TabMenu.show(this, button, Ui.wide(getResources().getConfiguration()), b.items);
    }

    private interface RadioStep {
        void on(RadioFragment page);
    }

    /** Goes to the Radio tab's own page (if it isn't showing), then does step there. */
    private Runnable radioAction(final RadioStep step) {
        return new Runnable() {
            @Override
            public void run() {
                RadioFragment page = (RadioFragment) rootOf(TAB_RADIO, RadioFragment.class);
                if (page != null) step.on(page);
            }
        };
    }

    /** The tab's own first page, showing: switched to (or back to, from a page opened on it) first. */
    private Fragment rootOf(int index, Class<? extends Fragment> type) {
        FragmentManager fm = getFragmentManager();
        Fragment showing = fm.findFragmentById(R.id.content);
        if (tab != index || !type.isInstance(showing) || fm.getBackStackEntryCount() > 0) {
            showTab(index);
            fm.executePendingTransactions();
            showing = fm.findFragmentById(R.id.content);
        }
        return type.isInstance(showing) && showing.getView() != null ? showing : null;
    }

    /** Switches tab, to its first page (tapping the tab showing opens its menu instead, but for Settings). */
    void showTab(int index) {
        if (index == TAB_TV) {
            openTv();
            return;
        }
        if (index == TAB_LIBRARY || index == TAB_PODCASTS) {
            showSection(index, 0, true, false);
            return;
        }
        tab = index;
        highlightTab();
        sections.clear();
        FragmentManager fm = getFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
        Fragment root;
        if (index == TAB_RADIO) root = new RadioFragment();
        else if (index == TAB_AUDIOBOOKS) root = new AudiobooksFragment();
        else if (index == TAB_SEARCH) root = new SearchFragment();
        else root = new SettingsFragment();
        fm.beginTransaction().replace(R.id.content, root).commit();
    }

    /** A Library section (SectionMenu.FIRST: Recently Added, or BrowseFragment.PLAYLISTS to SONGS) under its chips. */
    void showLibrary(int section) {
        showSection(TAB_LIBRARY, section, false, true);
    }

    /** A Podcasts section (SectionMenu.FIRST: Discover, SUBSCRIPTIONS or DOWNLOADS) under its chips. */
    void showPodcasts(int section) {
        showSection(TAB_PODCASTS, section, false, true);
    }

    /**
     * Shows one of a tab's sections in place of the one showing (no slide, nothing to go Back
     * to: the chips stay put and the page under them changes). Sections are kept while their tab
     * is open, so each keeps what it loaded and where it was scrolled; the Library's are for one
     * source, and start afresh for another.
     *
     * @param afresh drop the kept sections first (the tab was tapped)
     * @param now    switch before returning (a chip was tapped), so quick taps can't land out of order
     */
    private void showSection(int tabIndex, int section, boolean afresh, boolean now) {
        tab = tabIndex;
        shownSection[tabIndex] = section;
        highlightTab();
        FragmentManager fm = getFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
        String owner = tabIndex == TAB_LIBRARY ? "library:" + Sources.get(this).current().id() : "podcasts";
        if (afresh || !owner.equals(sectionsOwner)) {
            sections.clear();
            sectionsOwner = owner;
        }
        Fragment showing = fm.findFragmentById(R.id.content);
        Fragment page = sections.get(section);
        if (page != null && page == showing) return;
        if (page == null || page.isAdded()) {
            if (tabIndex == TAB_PODCASTS) page = PodcastsFragment.section(section);
            else page = section == SectionMenu.FIRST ? new LibraryFragment() : BrowseFragment.section(section);
            sections.put(section, page);
        }
        fm.beginTransaction().replace(R.id.content, page).commit();
        if (now) fm.executePendingTransactions();
    }

    /** Opens a page on top of the current one, sliding in like iOS. */
    void push(Fragment page) {
        getFragmentManager().beginTransaction()
                .setCustomAnimations(R.animator.push_enter, R.animator.push_exit, R.animator.pop_enter, R.animator.pop_exit)
                .replace(R.id.content, page)
                .addToBackStack(null)
                .commit();
    }

    /** Whether the mini player's title may scroll (not while Now Playing covers it). */
    void setMiniTitleScrolls(boolean scrolls) {
        if (miniTitle != null) miniTitle.setScrolls(scrolls);
    }

    void openNowPlaying() {
        nowPlaying.open();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if(television!=null && intent.hasExtra("open_tv_section")) {
            String section=intent.getStringExtra("open_tv_section");intent.removeExtra("open_tv_section");showTvSection(section);
        }
        if(television!=null && intent.getBooleanExtra("open_tv_player",false)) {
            intent.removeExtra("open_tv_player");showTvNowPlaying();
        }
        if (intent != null && intent.getBooleanExtra("open_podcast_downloads", false)) {
            intent.removeExtra("open_podcast_downloads");
            showSection(TAB_PODCASTS, SectionMenu.DOWNLOADS, true, false);
        }
        if (intent != null && intent.getBooleanExtra("open_podcasts", false)) {
            intent.removeExtra("open_podcasts"); showTab(TAB_PODCASTS);
        }
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false)) {
            intent.removeExtra(EXTRA_OPEN_NOW_PLAYING);
            if (player.current() != null || radio.inSession()) nowPlaying.openImmediately();
        }
    }

    /** The notification's close button: Grapefruit goes away (playback has already stopped). */
    public static void closeIfOpen() {
        for (MainActivity a : new ArrayList<MainActivity>(LIVE.keySet())) {
            if (!a.isFinishing()) a.finish();
        }
    }

    @Override
    protected void onDestroy() {
        LIVE.remove(this);
        if(television!=null)television.destroy();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(television!=null)television.foreground(true);
        player.addListener(this);
        radio.addListener(this);
        onPlayerChanged();
        nowPlaying.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.grapefruitmusic.podcast.PodcastStore.get(this).resumeDownloads();
        Notice.setSink(this);
        com.grapefruitmusic.download.Downloads.get(this).startWorker();
        // Grapefruit TV & Movies may have been installed or removed meanwhile.
        updateTvTab();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 40) com.grapefruitmusic.source.Sources.get(this).notifyChanged();
    }

    @Override
    protected void onPause() {
        TabMenu.dismiss();
        Notice.clearSink(this);
        super.onPause();
    }

    /** A rounded message above the tab bar (and mini player) that fades away by itself. */
    @Override
    public void showNotice(CharSequence text, boolean longer) {
        if (notice == null) {
            notice = Ui.text(this, 15, R.attr.gfBackground, false);
            notice.setId(R.id.notice);
            notice.setSingleLine(false);
            notice.setMaxLines(3);
            notice.setGravity(Gravity.CENTER);
            notice.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor((Ui.color(this, R.attr.gfTextPrimary) & 0x00ffffff) | 0xe6000000);
            bg.setCornerRadius(Ui.PILL);
            notice.setBackground(bg);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            int side = Ui.dp(this, 24);
            lp.leftMargin = side;
            lp.rightMargin = side;
            ((FrameLayout) findViewById(R.id.root)).addView(notice, lp);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) notice.getLayoutParams();
        int above = findViewById(R.id.bottom_bars).getHeight();
        lp.bottomMargin = above + Ui.dp(this, 14);
        notice.setLayoutParams(lp);
        notice.setText(text);
        Bg.cancelMain(hideNotice);
        notice.animate().cancel();
        if (notice.getVisibility() != View.VISIBLE) {
            notice.setVisibility(View.VISIBLE);
            notice.setAlpha(0f);
            notice.setTranslationY(Ui.dp(this, 8));
        }
        notice.animate().alpha(1f).translationY(0).setDuration(180)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(null).start();
        Bg.mainDelayed(hideNotice, longer ? 3500 : 2000);
    }

    @Override
    protected void onStop() {
        if(television!=null)television.foreground(false);
        player.removeListener(this);
        radio.removeListener(this);
        nowPlaying.onStop();
        super.onStop();
    }

    /** Turning the screen doesn't restart the activity (the manifest says so); pages adapt instead. */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        TabMenu.dismiss();
        fitBars();
        nowPlaying.configurationChanged();
    }

    /**
     * The tabs: a pill along the bottom, or on a wide or square screen (a phone on its side, a tablet,
     * a Unihertz Titan) a rail up the left, so pages keep their height. On a short screen the mini
     * player and tabs take less room.
     */
    private void fitBars() {
        Configuration config = getResources().getConfiguration();
        boolean rail = Ui.wide(config);
        boolean compact = config.screenHeightDp < 500;
        LinearLayout bar = (LinearLayout) findViewById(R.id.tab_bar);
        LinearLayout frame = (LinearLayout) findViewById(R.id.frame);
        LinearLayout bottom = (LinearLayout) findViewById(R.id.bottom_bars);
        ViewGroup parent = (ViewGroup) bar.getParent();
        if (rail && parent != frame) {
            parent.removeView(bar);
            frame.addView(bar, 0);
        } else if (!rail && parent != bottom) {
            parent.removeView(bar);
            bottom.addView(bar);
        }
        bar.setOrientation(rail ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        int pad = Ui.dp(this, 6);
        bar.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                rail ? ViewGroup.LayoutParams.WRAP_CONTENT : Ui.dp(this, compact ? 52 : 60));
        lp.gravity = rail ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL;
        if (rail) {
            lp.leftMargin = Ui.dp(this, 10);
            lp.rightMargin = Ui.dp(this, 2);
        } else {
            lp.topMargin = Ui.dp(this, compact ? 4 : 8);
            lp.bottomMargin = Ui.dp(this, compact ? 6 : 10);
        }
        bar.setLayoutParams(lp);
        // Along the bottom: as much room around each icon as fits (up to 14dp a side), with the
        // chosen tab's name beside its icon if that fits too. Five tabs, or seven with Audiobooks and
        // TV & Movies, on a 320dp phone or a tablet.
        int shown = 0;
        float widestName = 0;
        float scaled = getResources().getDisplayMetrics().scaledDensity / getResources().getDisplayMetrics().density;
        for (int i = 0; i < TAB_IDS.length; i++) {
            if (TAB_IDS[i] == TAB_TV && tvPackage == null && television == null) continue;
            shown++;
            if (TAB_IDS[i] != TAB_TV) {
                widestName = Math.max(widestName, tabLabels[i].getPaint().measureText(tabName(i))
                        / getResources().getDisplayMetrics().density);
            }
        }
        int room = config.screenWidthDp - 16 - 12;
        int name = Math.round(8 + widestName * Math.max(1f, scaled));
        tabNames = !rail;
        int tabPad = 14;
        while (tabPad > 7 && width(shown, tabPad, tabNames ? name : 0) > room) tabPad--;
        if (tabNames && width(shown, tabPad, name) > room) {
            tabNames = false;
            tabPad = 14;
            while (tabPad > 7 && width(shown, tabPad, 0) > room) tabPad--;
        }
        int item = Ui.dp(this, compact ? 44 : 48);
        for (int i = 0; i < bar.getChildCount(); i++) {
            View tab = bar.getChildAt(i);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                    rail ? item : ViewGroup.LayoutParams.WRAP_CONTENT, item);
            if (i > 0) {
                if (rail) ip.topMargin = Ui.dp(this, compact ? 2 : 6);
                else ip.leftMargin = Ui.dp(this, 2);
            }
            tab.setPadding(rail ? 0 : Ui.dp(this, tabPad), 0, rail ? 0 : Ui.dp(this, tabPad), 0);
            tab.setMinimumWidth(rail ? 0 : Ui.dp(this, 22 + 2 * tabPad));
            tab.setLayoutParams(ip);
        }
        LinearLayout.LayoutParams mp = (LinearLayout.LayoutParams) mini.getLayoutParams();
        mp.height = Ui.dp(this, compact ? 56 : 64);
        // With the tabs at the side, the mini player sits at the bottom with a little room under it.
        mp.bottomMargin = rail ? Ui.dp(this, 10) : 0;
        mp.topMargin = Ui.dp(this, rail ? 6 : 4);
        mini.setLayoutParams(mp);
        highlightTab();
    }

    /** The tabs' width along the bottom (dp): icons, room around them, gaps, and the chosen one's name. */
    private static int width(int tabs, int pad, int name) {
        return tabs * (22 + 2 * pad) + (tabs - 1) * 2 + name;
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putBoolean("now_playing_open", nowPlaying.isOpen());
    }

    @Override
    public void onRadioChanged() {
        onPlayerChanged();
    }

    private void setMiniArtist(String text) {
        miniArtist.setText(text == null ? "" : text);
        miniArtist.setVisibility(text == null || text.length() == 0 ? View.GONE : View.VISIBLE);
    }

    /** The same text again leaves it scrolling where it is (MarqueeText.setText). */
    private void setMiniTitle(String text) {
        miniTitle.setText(text);
    }

    @Override
    public void onPlayerChanged() {
        boolean video = television != null && television.active();
        if(tvMini!=null){
            tvMini.setVisibility(video && !television.watchVisible()?View.VISIBLE:View.GONE);
            television.mini();
        }
        boolean onRadio = radio.inSession();
        // The volume keys turn the radio's own volume while it's on.
        setVolumeControlStream(onRadio ? radio.volumeStream() : AudioManager.STREAM_MUSIC);
        MediaItem item = player.current();
        boolean show = !video && (item != null || onRadio);
        mini.setVisibility(show ? View.VISIBLE : View.GONE);
        miniSeparator.setVisibility(show ? View.VISIBLE : View.GONE);
        if (onRadio) {
            setMiniTitle(radio.title());
            setMiniArtist(radio.subtitle());
            miniPlay.setIcon(radio.wantsToPlay() ? Icons.STOP : Icons.PLAY);
            miniArt.setTag(R.id.art_request, null);
            miniArt.setBitmap(RadioArt.of(radio.frequency(), radio.name(), Ui.dp(this, 44)));
        } else if (item != null) {
            String[] station = item.live ? StationText.nowPlaying(item.title, item.artist, player.liveTitle()) : null;
            setMiniTitle(item.live ? station[0] : item.title);
            setMiniArtist(item.live ? station[1] : item.artist);
            miniPlay.setIcon(player.wantsToPlay() ? (item.live ? Icons.STOP : Icons.PAUSE) : Icons.PLAY);
            ArtworkLoader.get(this).into(miniArt, item, Ui.dp(this, 44));
        }
        nowPlaying.update();
    }

    /** Plays a radio station and shows it in Now Playing. */
    void playRadio(int freq) {
        com.grapefruitmusic.playback.PlaybackSwitch.audioRequested();
        if (radio.play(freq)) nowPlaying.open();
    }

    /** The volume keys turn the radio's own volume, on tablets where it has one (the chip's). */
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if(television instanceof BlueTelevision&&television.active()&&(keyCode==android.view.KeyEvent.KEYCODE_CHANNEL_UP||keyCode==android.view.KeyEvent.KEYCODE_CHANNEL_DOWN)){
            BlueTelevision tv=(BlueTelevision)television;if(tv.session.current!=null&&tv.session.current.live()){tv.session.next(keyCode==android.view.KeyEvent.KEYCODE_CHANNEL_UP?1:-1);return true;}
        }
        if ((keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
                && radio.inSession() && radio.ownVolumeMax() > 0 && radio.wantsToPlay()) {
            radio.stepVolume(keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP);
            Notice.show(this, "Radio volume " + radio.ownVolume() + " of " + radio.ownVolumeMax());
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        Fragment page = getFragmentManager().findFragmentById(R.id.content);
        if (nowPlaying.isOpen()) {
            nowPlaying.close();
        } else if (page instanceof BaseFragment && ((BaseFragment) page).onBackPressed()) {
            return;
        } else if (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStack();
        } else if (tab != TAB_LIBRARY) {
            showTab(TAB_LIBRARY);
        } else {
            super.onBackPressed();
        }
    }
}
