package com.grapefruitmusic.ui;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
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
import com.grapefruitmusic.ui.widget.ArtworkView;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.ArtworkLoader;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Notice;

/** One activity: tab bar, a stack of pages, the mini player and the Now Playing card. */
public final class MainActivity extends Activity implements Player.Listener, Notice.Sink {
    public static final String EXTRA_OPEN_NOW_PLAYING = "open_now_playing";

    private static final int TAB_LIBRARY = 0;
    private static final int TAB_PLAYLISTS = 1;
    private static final int TAB_SEARCH = 2;
    private static final int TAB_SETTINGS = 3;
    private static final String[] TAB_NAMES = {"Library", "Playlists", "Search", "Settings"};
    private static final int[] TAB_ICONS = {Icons.LIBRARY, Icons.PLAYLIST, Icons.SEARCH, Icons.SETTINGS};

    private Player player;
    private NowPlaying nowPlaying;
    private int tab = TAB_LIBRARY;
    private final IconView[] tabIcons = new IconView[TAB_NAMES.length];
    private final TextView[] tabLabels = new TextView[TAB_NAMES.length];

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
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        player = Player.get(this);

        buildTabBar();
        mini = findViewById(R.id.mini_player);
        miniSeparator = findViewById(R.id.mini_separator);
        miniArt = (ArtworkView) findViewById(R.id.mini_art);
        miniArt.setPlaceholderColors(Ui.color(this, R.attr.gfFill), Ui.color(this, R.attr.gfTextSecondary));
        miniTitle = (TextView) findViewById(R.id.mini_title);
        miniPlay = (IconView) findViewById(R.id.mini_play);
        IconView miniNext = (IconView) findViewById(R.id.mini_next);
        int primary = Ui.color(this, R.attr.gfTextPrimary);
        miniPlay.setColor(primary);
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
                player.togglePlay();
            }
        });
        miniNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.next();
            }
        });

        nowPlaying = new NowPlaying(this, findViewById(R.id.root));

        if (saved == null) {
            showTab(TAB_LIBRARY);
        } else {
            tab = saved.getInt("tab", TAB_LIBRARY);
            highlightTab();
            if (saved.getBoolean("now_playing_open")) nowPlaying.openImmediately();
        }
        handleIntent(getIntent());
    }

    private void buildTabBar() {
        LinearLayout bar = (LinearLayout) findViewById(R.id.tab_bar);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 2));
            IconView icon = Ui.icon(this, TAB_ICONS[i], 0, 26);
            item.addView(icon);
            TextView label = Ui.text(this, 10, R.attr.gfTextSecondary, false);
            label.setText(TAB_NAMES[i]);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            item.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (nowPlaying.isOpen()) nowPlaying.close();
                    showTab(index);
                }
            });
            bar.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            tabIcons[i] = icon;
            tabLabels[i] = label;
        }
    }

    private void highlightTab() {
        int accent = Ui.accent(this);
        int inactive = Ui.color(this, R.attr.gfTextSecondary);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            tabIcons[i].setColor(i == tab ? accent : inactive);
            tabLabels[i].setTextColor(i == tab ? accent : inactive);
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
        if (index == TAB_PLAYLISTS) root = BrowseFragment.newInstance(null, BrowseFragment.PLAYLISTS, null, null);
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
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false)) {
            intent.removeExtra(EXTRA_OPEN_NOW_PLAYING);
            if (player.current() != null) nowPlaying.openImmediately();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        player.addListener(this);
        onPlayerChanged();
        nowPlaying.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Notice.setSink(this);
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
        nowPlaying.onStop();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putBoolean("now_playing_open", nowPlaying.isOpen());
    }

    @Override
    public void onPlayerChanged() {
        MediaItem item = player.current();
        boolean show = item != null;
        mini.setVisibility(show ? View.VISIBLE : View.GONE);
        miniSeparator.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            miniTitle.setText(item.title);
            miniPlay.setIcon(player.wantsToPlay() ? Icons.PAUSE : Icons.PLAY);
            ArtworkLoader.get(this).into(miniArt, item, Ui.dp(this, 44));
        }
        nowPlaying.update();
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
