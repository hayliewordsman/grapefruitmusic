package io.github.hayliewordsman.grapefruitai.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import io.github.hayliewordsman.grapefruitai.App;
import io.github.hayliewordsman.grapefruitai.md.MdRender;
import io.github.hayliewordsman.grapefruitai.or.OpenRouter;
import io.github.hayliewordsman.grapefruitai.secure.Secrets;
import io.github.hayliewordsman.grapefruitai.util.Prefs;

/**
 * Runs the app on Robolectric's Android 4.2 (API 17) framework: the activity, all four tabs in
 * all four themes, both orientations, markdown views and the API-17 key storage path.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 17, application = App.class)
public class Api17Test {
    private static List<TextView> texts(View v, List<TextView> out) {
        if (v instanceof TextView) out.add((TextView) v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) texts(g.getChildAt(i), out);
        }
        return out;
    }

    private static boolean shows(View root, String text) {
        for (TextView t : texts(root, new ArrayList<TextView>())) {
            if (t.getText().toString().contains(text)) return true;
        }
        return false;
    }

    @Test
    public void runsOnJellyBeanMr1() {
        assertEquals(17, Build.VERSION.SDK_INT);
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        View root = a.getWindow().getDecorView();
        assertTrue(shows(root, "Chat"));
        for (int tab = 0; tab < 4; tab++) {
            a.showTab(tab);
            assertNotNull(a.page(tab).view().getParent());
        }
        assertTrue(shows(root, "OpenRouter"));
        assertTrue(shows(root, "Geist"));
        c.pause().stop().destroy();
    }

    @Test
    public void everyThemeBuildsEveryPage() {
        for (int theme = 0; theme < 4; theme++) {
            Prefs.put(Prefs.THEME, theme);
            ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
            assertEquals(Palette.of(theme).background, Ui.p().background);
            for (int tab = 0; tab < 4; tab++) c.get().showTab(tab);
            c.pause().stop().destroy();
        }
        assertEquals("Victory Blue", Palette.NAMES[Palette.VICTORY_BLUE]);
    }

    @Test
    @Config(sdk = 17, qualifiers = "land")
    public void landscapeUsesRail() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        assertTrue(Ui.wide(c.get()));
        c.get().showTab(MainActivity.TAB_CODE);
        assertTrue(shows(c.get().getWindow().getDecorView(), "Connect an OpenCode server"));
        c.pause().stop().destroy();
    }

    @Test
    public void codeModeRequiresHttpsAndPassword() {
        Prefs.put(Prefs.OC_URL, "http://example.com:4096");
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        c.get().showTab(MainActivity.TAB_CODE);
        assertTrue(shows(c.get().getWindow().getDecorView(), "Only HTTPS"));
        c.pause().stop().destroy();
    }

    /** Regression: the password saved after the Code tab was opened must be used by Test connection. */
    @Test
    public void passwordSavedLaterIsUsed() throws Exception {
        Secrets.clearAll();
        Prefs.put(Prefs.OC_URL, "https://opencode.invalid");
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        a.showTab(MainActivity.TAB_CODE);
        CodePage code = (CodePage) a.page(MainActivity.TAB_CODE);
        assertTrue(code.configError(), code.configError().contains("password"));
        a.showTab(MainActivity.TAB_SETTINGS);
        Secrets.put(Secrets.OPENCODE_PASSWORD, "secret");
        a.secretsChanged();
        assertNull(code.configError());
        code.reconnect(true);
        assertNull(code.configError());
        Secrets.clearAll();
        c.pause().stop().destroy();
    }

    /** Test connection from Settings before the Code tab was ever opened. */
    @Test
    public void testConnectionBeforeCodeTabWasShown() throws Exception {
        Prefs.put(Prefs.OC_URL, "https://opencode.invalid");
        Secrets.put(Secrets.OPENCODE_PASSWORD, "secret");
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        a.showTab(MainActivity.TAB_SETTINGS);
        CodePage code = (CodePage) a.page(MainActivity.TAB_CODE);
        code.reconnect(true);
        assertNull(code.configError());
        Secrets.clearAll();
        c.pause().stop().destroy();
    }

    @Test
    public void markdownCodeBlocksHaveCopy() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        LinearLayout box = new LinearLayout(c.get());
        box.setOrientation(LinearLayout.VERTICAL);
        MdRender.render(c.get(), box, "# Hi\n\n**bold** and `code`\n\n```java\nclass A {}\n```\n\n- item", 16);
        assertEquals(3, box.getChildCount());
        assertTrue(shows(box, "class A {}"));
        assertTrue(shows(box, "Copy"));
        assertFalse(shows(box, "**"));
        c.pause().stop().destroy();
    }

    @Test
    public void sessionOnlyByDefaultAndPassphraseWorksOnApi17() throws Exception {
        assertFalse(Secrets.keystoreAvailable());
        assertEquals(Secrets.SESSION, Secrets.mode());
        Secrets.put(Secrets.OPENROUTER_KEY, "sk-or-v1-test");
        assertEquals("sk-or-v1-test", Secrets.get(Secrets.OPENROUTER_KEY));
        assertFalse(App.get().getSharedPreferences("vault", 0).contains("data"));
        Secrets.usePassphrase("long enough pass".toCharArray());
        String stored = App.get().getSharedPreferences("vault", 0).getString("data", "");
        assertFalse(stored.contains("sk-or"));
        assertTrue(stored.length() > 20);
        // A fresh start: locked until the passphrase is given.
        Secrets.init(App.get());
        assertTrue(Secrets.isLocked());
        Secrets.unlock("long enough pass".toCharArray());
        assertEquals("sk-or-v1-test", Secrets.get(Secrets.OPENROUTER_KEY));
        Secrets.clearAll();
        assertNull(Secrets.get(Secrets.OPENROUTER_KEY));
        assertFalse(App.get().getSharedPreferences("vault", 0).contains("data"));
    }

    @Test
    public void dataUrls() {
        String u = OpenRouter.DataUrl.of("image/png", new byte[]{1, 2, 3});
        assertEquals("data:image/png;base64,AQID", u);
        OpenRouter.DataUrl d = OpenRouter.DataUrl.parse(u);
        assertEquals("image/png", d.mime);
        assertEquals(3, d.bytes.length);
        assertNull(OpenRouter.DataUrl.parse("https://example.com/x.png"));
    }

    @Test
    public void wordmarkFontHasTheName() {
        assertNotNull(Fonts.wordmark(App.get()));
        assertEquals("sk-or-v1-…cdef", SettingsPage.mask("sk-or-v1-0123456789abcdef"));
    }
}
