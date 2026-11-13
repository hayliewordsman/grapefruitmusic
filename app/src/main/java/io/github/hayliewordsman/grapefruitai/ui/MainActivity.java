package io.github.hayliewordsman.grapefruitai.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.hayliewordsman.grapefruitai.util.Prefs;

/**
 * The one activity: four tabs (Chat, Images, Code, Settings) in Grapefruit's floating pill tab bar,
 * which becomes a rail at the side on wide screens. Pages are plain views built in code (light
 * enough for old tablets) and survive rotation (configChanges in the manifest).
 */
public final class MainActivity extends Activity {
    public static final int TAB_CHAT = 0, TAB_IMAGES = 1, TAB_CODE = 2, TAB_SETTINGS = 3;
    private static final String[] TAB_NAMES = {"Chat", "Images", "Code", "Settings"};
    private static final int[] TAB_ICONS = {IconView.CHAT, IconView.IMAGE, IconView.CODE, IconView.SETTINGS};

    private final Page[] pages = new Page[4];
    private int tab = TAB_CHAT;
    private FrameLayout content;
    private LinearLayout tabBar;
    private final IconView[] tabIcons = new IconView[4];
    private final TextView[] tabLabels = new TextView[4];
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        prefs = Prefs.get();
        UniversalTheme.sync(this, prefs);
        Palette p = Palette.of(prefs.getInt(Prefs.THEME, Palette.GRAPEFRUIT_DARK));
        Ui.setPalette(p);
        setTheme(p.windowTheme());
        super.onCreate(state);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(p.background);
            getWindow().setNavigationBarColor(p.background);
        }
        if (android.os.Build.VERSION.SDK_INT >= 23 && p.light) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        pages[TAB_CHAT] = new ChatPage(this);
        pages[TAB_IMAGES] = new ImagePage(this);
        pages[TAB_CODE] = new CodePage(this);
        pages[TAB_SETTINGS] = new SettingsPage(this);
        tab = state != null ? state.getInt("tab", prefs.getInt(Prefs.MODE, TAB_CHAT)) : prefs.getInt(Prefs.MODE, TAB_CHAT);
        if (tab < 0 || tab > 3) tab = TAB_CHAT;
        layout();
        showTab(tab);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (UniversalTheme.sync(this, prefs)) recreate();
    }

    /** Builds the shell: content beside a rail (wide) or above a floating pill bar. */
    private void layout() {
        Palette p = Ui.p();
        boolean wide = Ui.wide(this);
        if (content != null) content.removeAllViews();
        content = new FrameLayout(this);
        content.setBackgroundColor(p.background);
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(wide ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        tabBar.setGravity(Gravity.CENTER);
        tabBar.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        tabBar.setBackground(Ui.shape(this, p.surface, Ui.PILL, true, false));
        for (int i = 0; i < 4; i++) {
            final int index = i;
            LinearLayout item = Ui.horizontal(this);
            item.setGravity(Gravity.CENTER);
            item.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
            item.setContentDescription(TAB_NAMES[i]);
            IconView icon = Ui.icon(this, TAB_ICONS[i], p.secondary, 22);
            item.addView(icon);
            TextView label = Ui.text(this, 14, p.onAccent, false);
            label.setTypeface(Fonts.medium());
            label.setText(TAB_NAMES[i]);
            label.setPadding(Ui.dp(this, 8), 0, 0, 0);
            item.addView(label);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTab(index);
                }
            });
            LinearLayout.LayoutParams lp = wide
                    ? new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52))
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 48));
            if (i > 0) {
                if (wide) lp.topMargin = Ui.dp(this, 6);
                else lp.leftMargin = Ui.dp(this, 2);
            }
            tabBar.addView(item, lp);
            tabIcons[i] = icon;
            tabLabels[i] = label;
        }
        LinearLayout root;
        if (wide) {
            root = Ui.horizontal(this);
            root.setGravity(Gravity.NO_GRAVITY);
            FrameLayout railBox = new FrameLayout(this);
            railBox.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8));
            railBox.addView(tabBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            root.addView(railBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(content, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        } else {
            root = Ui.vertical(this);
            root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            FrameLayout barBox = new FrameLayout(this);
            barBox.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 8));
            barBox.addView(tabBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
            root.addView(barBox, Ui.matchWrap());
        }
        root.setBackgroundColor(p.background);
        root.setFitsSystemWindows(true);
        setContentView(root);
    }

    public void showTab(int index) {
        if (index != tab && pages[tab] != null) pages[tab].onHide();
        tab = index;
        prefs.edit().putInt(Prefs.MODE, index).apply();
        View v = pages[index].view();
        if (v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
        content.removeAllViews();
        content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        highlight();
        pages[index].onShow();
    }

    private void highlight() {
        Palette p = Ui.p();
        boolean wide = Ui.wide(this);
        for (int i = 0; i < 4; i++) {
            boolean chosen = i == tab;
            View item = (View) tabIcons[i].getParent();
            item.setBackground(chosen ? Ui.accentFill(this, Ui.PILL, false) : null);
            item.setSelected(chosen);
            tabIcons[i].setColor(chosen ? p.onAccent : p.secondary);
            // Names show beside the chosen tab where there's room (not on the rail).
            boolean roomy = getResources().getConfiguration().screenWidthDp >= 360;
            tabLabels[i].setVisibility(chosen && !wide && roomy ? View.VISIBLE : View.GONE);
        }
    }

    public Page page(int index) {
        return pages[index];
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        layout();
        showTab(tab);
        for (Page page : pages) page.onConfigurationChanged();
    }

    @Override
    public void onBackPressed() {
        if (pages[tab].onBack()) return;
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        int owner = request / 100;
        if (owner >= 0 && owner < pages.length) pages[owner].onResult(request % 100, result, data);
    }

    /** Request codes encode the page: page * 100 + code. */
    public void startForResult(Page page, Intent intent, int code) {
        int index = 0;
        for (int i = 0; i < pages.length; i++) if (pages[i] == page) index = i;
        try {
            startActivityForResult(intent, index * 100 + code);
        } catch (android.content.ActivityNotFoundException e) {
            Ui.toast(this, "No app on this device can do that");
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        Saver.onPermissionResult(this, request, results);
    }

    /** The server password changed (set, unlocked or cleared): the Code tab reconnects with it. */
    public void secretsChanged() {
        ((CodePage) pages[TAB_CODE]).configChanged();
    }

    /** Applies a new theme: everything is rebuilt in its colours. */
    public void applyTheme(int id) {
        prefs.edit().putInt(Prefs.THEME, id).commit();
        recreate();
    }

    @Override
    protected void onDestroy() {
        for (Page page : pages) if (page != null) page.onDestroy();
        super.onDestroy();
    }
}
