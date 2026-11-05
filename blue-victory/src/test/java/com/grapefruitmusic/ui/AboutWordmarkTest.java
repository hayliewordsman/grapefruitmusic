package com.grapefruitmusic.ui;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.grapefruitmusic.util.Prefs;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AboutWordmarkTest {
    @Test public void retainsTheOriginalTriangleWordmarkWithBlueAccent() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        Prefs.init(app);
        com.grapefruitmusic.util.Brand.init(app);
        Fonts.init(app);
        try (org.robolectric.android.controller.ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class)) {
            Activity activity = controller.get();
            Ui.applyTheme(activity);
            controller.setup();
            activity.getFragmentManager().beginTransaction().replace(android.R.id.content, new AboutFragment()).commit();
            activity.getFragmentManager().executePendingTransactions();
            View root = activity.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 1920);
            // fitWordmark changes text size after its first layout; let that layout settle.
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 1920);
            root.getViewTreeObserver().dispatchOnPreDraw();
            TextView name = find(root, "Blue Grapefruit");
            assertNotNull(name);
            assertSame(Fonts.wordmark(activity), name.getTypeface());
            TextView subtitle = find(root, "Victory Edition");
            assertNotNull(subtitle);
            assertTrue("Victory Edition is the smaller line", subtitle.getTextSize() < name.getTextSize());
            Spanned text = (Spanned) name.getText();
            ForegroundColorSpan[] spans = text.getSpans(0, text.length(), ForegroundColorSpan.class);
            assertEquals(1, spans.length);
            assertEquals(Color.parseColor("#67C7FF"), spans[0].getForegroundColor());
            assertEquals("Blue".length(), text.getSpanEnd(spans[0]));
            File screenshot = new File("build/reports/blue-victory-about.png");
            screenshot.getParentFile().mkdirs();
            Bitmap bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(bitmap));
            try (FileOutputStream output = new FileOutputStream(screenshot)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            }
        }
    }

    private static TextView find(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = find(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
