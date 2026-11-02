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
    private static final int[] TAB_IDS = {TAB_LIBRARY, TAB_RADIO, TAB_PODCASTS, TAB_SEARCH, TAB_SETTINGS};
    private static final String[] TAB_NAMES = {"Library", "Radio", "Podcasts", "Search", "Settings"};
    private static final int[] TAB_ICONS = {Icons.LIBRARY, Icons.RADIO, Icons.HEADPHONES, Icons.SEARCH, Icons.SETTINGS};

    /** Grapefruit's open windows (normally one), for the notification's close button. Main thread. */
    private static final Map<MainActivity, Boolean> LIVE = new WeakHashMap<MainActivity, Boolean>();

    private Player player;
    private Radio radio;
    private NowPlaying nowPlaying;
    private int tab = TAB_LIBRARY;
    private final IconView[] tabIcons = new IconView[TAB_NAMES.length];
    private final TextView[] tabLabels = new TextView[TAB_NAMES.length];
    private TextView miniArtist;

    private View mini;
    private View miniSeparator;
    private ArtworkView miniArt;
    private TextView miniTitle;
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
            boolean light = Ui.theme(this) == com.grapefruitmusic.util.Prefs.THEME_STARLIGHT
                    || Ui.theme(this) == com.grapefruitmusic.util.Prefs.THEME_GRAPEFRUIT_LIGHT;
            int flags = light ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0;
            if (android.os.Build.VERSION.SDK_INT >= 26 && light) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        player = Player.get(this);
        radio = Radio.get(this);
        LIVE.put(this, Boolean.TRUE);

        buildTabBar();
        mini = findViewById(R.id.mini_player);
        miniSeparator = findViewById(R.id.mini_separator);
        miniArt = (ArtworkView) findViewById(R.id.mini_art);
        miniArt.setPlaceholderColors(Ui.color(this, R.attr.gfFill), Ui.color(this, R.attr.gfTextSecondary));
        miniTitle = (TextView) findViewById(R.id.mini_title);
        // A title too long for the mini player scrolls by (marquee, in the layout); Android only
        // scrolls a selected line.
        miniTitle.setSelected(true);
        miniTitle.setTypeface(Fonts.semiBold());
        miniArtist = (TextView) findViewById(R.id.mini_artist);
        miniArtist.setTypeface(Fonts.regular());
        miniArt.setCornerRadiusDp(10);
        // A card above the tabs, a shade darker while pressed.
        mini.setBackground(Ui.pressable(this, Ui.color(this, R.attr.gfSurface), Ui.dp(this, 18), true));
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
        fitBars();

        if (saved == null) {
            showTab(TAB_LIBRARY);
        } else {
            tab = saved.getInt("tab", TAB_LIBRARY);
            boolean visible = false;
            for (int id : TAB_IDS) if (id == tab) visible = true;
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
                        .setTitle("Grapefruit closed unexpectedly")
                        .setMessage("Last time, a fault in Grapefruit closed it. It kept a short report: the app"
                                + " and Android versions, this device's model, and where in Grapefruit's code it"
                                + " stopped. Nothing has been sent. Sharing it with the developer helps get it fixed.")
                        .setPositiveButton("Share Report", (d, w) -> {
                            com.grapefruitmusic.util.CrashReport.clear(c);
                            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_SUBJECT, "Grapefruit crash report")
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
            getSplashScreen().setSplashScreenTheme(t == Prefs.THEME_STARLIGHT ? R.style.Splash_Light
                    : t == Prefs.THEME_PITCH_BLACK ? R.style.Splash_Dark
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

    private void buildTabBar() {
        LinearLayout bar = (LinearLayout) findViewById(R.id.tab_bar);
        // A floating pill: the page shows around it.
        bar.setBackground(Ui.shape(this, Ui.color(this, R.attr.gfSurface), Ui.dp(this, 30), true, false));
        tabNames = getResources().getConfiguration().screenWidthDp >= 360;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int index = TAB_IDS[i];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER);
            item.setMinimumWidth(Ui.dp(this, tabNames ? 52 : 48));
            item.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
            item.setContentDescription(TAB_NAMES[i]);
            IconView icon = Ui.icon(this, TAB_ICONS[i], 0, 22);
            item.addView(icon);
            TextView label = Ui.text(this, 14, R.attr.gfTextSecondary, false);
            label.setTypeface(Fonts.medium());
            label.setText(TAB_NAMES[i]);
            label.setPadding(Ui.dp(this, 8), 0, 0, 0);
            label.setVisibility(View.GONE);
            item.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (nowPlaying.isOpen()) nowPlaying.close();
                    showTab(index);
                }
            });
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
            boolean chosen = TAB_IDS[i] == tab;
            View item = (View) tabIcons[i].getParent();
            item.setBackground(chosen ? Ui.accentFill(this, Ui.dp(this, 24), false) : null);
            item.setSelected(chosen);
            tabIcons[i].setColor(chosen ? on : inactive);
            tabLabels[i].setTextColor(on);
            tabLabels[i].setVisibility(chosen && tabNames ? View.VISIBLE : View.GONE);
        }
    }

    /** Switches tab; tapping the current tab returns to its first page. */
    void showTab(int index) {
        tab = index;
        highlightTab();
        FragmentManager fm = getFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
        Fragment root;
        if (index == TAB_RADIO) root = new RadioFragment();
        else if (index == TAB_PODCASTS) root = new PodcastsFragment();
        else if (index == TAB_SEARCH) root = new SearchFragment();
        else if (index == TAB_SETTINGS) root = new SettingsFragment();
        else root = new LibraryFragment();
        fm.beginTransaction().replace(R.id.content, root).commit();
    }

    /** Opens a page on top of the current one, sliding in like iOS. */
    void push(Fragment page) {
        getFragmentManager().beginTransaction()
                .setCustomAnimations(R.animator.push_enter, R.animator.push_exit, R.animator.pop_enter, R.animator.pop_exit)
                .replace(R.id.content, page)
                .addToBackStack(null)
                .commit();
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
        if (intent != null && intent.getBooleanExtra("open_podcast_downloads", false)) {
            intent.removeExtra("open_podcast_downloads");
            showTab(TAB_PODCASTS);
            push(PodcastsFragment.downloads());
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
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
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
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 40) com.grapefruitmusic.source.Sources.get(this).notifyChanged();
    }

    @Override
    protected void onPause() {
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
            bg.setCornerRadius(Ui.dp(this, 20));
            notice.setBackground(bg);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            int side = Ui.dp(this, 24);
            lp.leftMargin = side;
            lp.rightMargin = side;
            ((FrameLayout) findViewById(R.id.root)).addView(notice, lp);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) notice.getLayoutParams();
        int above = findViewById(R.id.tab_bar).getHeight() + (mini.getVisibility() == View.VISIBLE ? mini.getHeight() : 0);
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
        player.removeListener(this);
        radio.removeListener(this);
        nowPlaying.onStop();
        super.onStop();
    }

    /** Turning the screen doesn't restart the activity (the manifest says so); pages adapt instead. */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fitBars();
        nowPlaying.configurationChanged();
    }

    /** On a short screen (a phone turned sideways), the mini player and tabs take less height. */
    private void fitBars() {
        boolean compact = getResources().getConfiguration().screenHeightDp < 500;
        View bar = findViewById(R.id.tab_bar);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bar.getLayoutParams();
        lp.height = Ui.dp(this, compact ? 52 : 60);
        lp.topMargin = Ui.dp(this, compact ? 4 : 8);
        lp.bottomMargin = Ui.dp(this, compact ? 6 : 10);
        bar.setLayoutParams(lp);
        LinearLayout.LayoutParams mp = (LinearLayout.LayoutParams) mini.getLayoutParams();
        mp.height = Ui.dp(this, compact ? 56 : 64);
        mini.setLayoutParams(mp);
        int item = Ui.dp(this, compact ? 42 : 48);
        LinearLayout tabs = (LinearLayout) bar;
        for (int i = 0; i < tabs.getChildCount(); i++) {
            ViewGroup.LayoutParams ip = tabs.getChildAt(i).getLayoutParams();
            ip.height = item;
            tabs.getChildAt(i).setLayoutParams(ip);
        }
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

    /** Only when it changes: setting the same text again would start its scrolling over. */
    private void setMiniTitle(String text) {
        if (text == null) text = "";
        if (!text.equals(miniTitle.getText().toString())) miniTitle.setText(text);
    }

    @Override
    public void onPlayerChanged() {
        boolean onRadio = radio.inSession();
        // The volume keys turn the radio's own volume while it's on.
        setVolumeControlStream(onRadio ? radio.volumeStream() : AudioManager.STREAM_MUSIC);
        MediaItem item = player.current();
        boolean show = item != null || onRadio;
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
        if (radio.play(freq)) nowPlaying.open();
    }

    /** The volume keys turn the radio's own volume, on tablets where it has one (the chip's). */
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
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
