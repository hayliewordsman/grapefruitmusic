package com.grapefruitmusic.ui;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.ui.widget.ArtworkView;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.ArtworkLoader;

/** One activity: tab bar, a stack of pages, the mini player and the Now Playing card. */
public final class MainActivity extends Activity implements Player.Listener {
    public static final String EXTRA_OPEN_NOW_PLAYING = "open_now_playing";

    private static final int TAB_LIBRARY = 0;
    private static final int TAB_SEARCH = 1;
    private static final int TAB_SETTINGS = 2;
    private static final String[] TAB_NAMES = {"Library", "Search", "Settings"};
    private static final int[] TAB_ICONS = {Icons.LIBRARY, Icons.SEARCH, Icons.SETTINGS};

    private Player player;
    private NowPlaying nowPlaying;
    private int tab = TAB_LIBRARY;
    private final IconView[] tabIcons = new IconView[3];
    private final TextView[] tabLabels = new TextView[3];

    private View mini;
    private View miniSeparator;
    private ArtworkView miniArt;
    private TextView miniTitle;
    private IconView miniPlay;

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
        for (int i = 0; i < 3; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 2));
            IconView icon = Ui.icon(this, TAB_ICONS[i], 0, 26);
            item.addView(icon);
            TextView label = Ui.text(this, 10, R.attr.gfTextSecondary, false);
            label.setText(TAB_NAMES[i]);
            item.addView(label);
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
        for (int i = 0; i < 3; i++) {
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
        if (index == TAB_SEARCH) root = new SearchFragment();
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
        if (nowPlaying.isOpen()) {
            nowPlaying.close();
        } else if (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStack();
        } else if (tab != TAB_LIBRARY) {
            showTab(TAB_LIBRARY);
        } else {
            super.onBackPressed();
        }
    }
}
