package io.github.hayliewordsman.grapefruitai.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.github.hayliewordsman.grapefruitai.App;
import io.github.hayliewordsman.grapefruitai.TestFiles;
import io.github.hayliewordsman.grapefruitai.chat.Attachment;
import io.github.hayliewordsman.grapefruitai.chat.Conversation;
import io.github.hayliewordsman.grapefruitai.chat.Message;
import io.github.hayliewordsman.grapefruitai.or.ImageParams;
import io.github.hayliewordsman.grapefruitai.or.Models;
import io.github.hayliewordsman.grapefruitai.or.OrModel;
import io.github.hayliewordsman.grapefruitai.or.Routing;
import io.github.hayliewordsman.grapefruitai.secure.Secrets;
import io.github.hayliewordsman.grapefruitai.util.Prefs;

/** Meta Muse Image chosen in Chat: no chat request, a friendly redirect, and editing in Images. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 17, application = App.class)
public class ImageModelUiTest {
    private ActivityController<MainActivity> c;

    @Before
    public void setUp() throws Exception {
        Map<String, ImageParams> params = new HashMap<String, ImageParams>();
        JSONArray data = new JSONObject(TestFiles.read("image-models-sample.json")).getJSONArray("data");
        for (int i = 0; i < data.length(); i++) {
            ImageParams p = ImageParams.parse(data.getJSONObject(i));
            params.put(p.id, p);
        }
        Models.setForTest(OrModel.parseList(TestFiles.read("models-sample.json")), params);
        Secrets.put(Secrets.OPENROUTER_KEY, "sk-or-v1-test");
        Prefs.put(Prefs.CHAT_MODEL, "meta/muse-image");
        Prefs.put(Prefs.MODE, MainActivity.TAB_CHAT);
        c = Robolectric.buildActivity(MainActivity.class).setup();
    }

    @After
    public void tearDown() {
        c.pause().stop().destroy();
        Secrets.clearAll();
    }

    private static String allText(View v) {
        StringBuilder b = new StringBuilder();
        if (v instanceof TextView) b.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(allText(((ViewGroup) v).getChildAt(i)));
        }
        return b.toString();
    }

    @Test
    public void chatRefusesAnImageOnlyModelWithAFriendlyMessage() {
        ChatPage chat = (ChatPage) c.get().page(MainActivity.TAB_CHAT);
        chat.send("Make the sky purple");
        assertNotNull("a friendly sheet", chat.lastSheet);
        String text = allText(chat.lastSheet.body());
        assertTrue(text, text.contains(Routing.USE_IMAGES_TAB));
        assertFalse(text, text.contains("chat/completions"));
        // Nothing was added to the conversation, so nothing was sent.
        assertTrue(allText(chat.view()).contains("Ask anything") || !allText(chat.view()).contains("Make the sky purple"));
    }

    @Test
    public void imagesTabRoutesMuseToTheImagesApiWithEditing() {
        ImagePage images = (ImagePage) c.get().page(MainActivity.TAB_IMAGES);
        Attachment upload = new Attachment("photo.jpg", "image/jpeg", "/nonexistent/photo.jpg", 3);
        images.prefill("meta/muse-image", "Make the sky purple", Collections.singletonList(upload));
        c.get().showTab(MainActivity.TAB_IMAGES);
        assertEquals("meta/muse-image", Prefs.str(Prefs.IMAGE_MODEL, ""));
        assertEquals("This model uses OpenRouter Images API.", images.apiLabel());
        assertEquals(1, images.references().size());
        assertEquals("Edit image", images.buttonLabel());
        assertTrue(images.editingNote(), images.editingNote().contains("image input"));
    }

    @Test
    public void textOnlyImageModelExplainsThatItCantEdit() {
        ImagePage images = (ImagePage) c.get().page(MainActivity.TAB_IMAGES);
        Attachment upload = new Attachment("photo.jpg", "image/jpeg", "/nonexistent/photo.jpg", 3);
        images.prefill("recraft/recraft-v4.1-flash", "A logo", Collections.singletonList(upload));
        assertEquals(0, images.references().size());
        assertEquals("Generate", images.buttonLabel());
        assertTrue(images.editingNote(), images.editingNote().contains("text only"));
    }

    @Test
    public void chatErrorShowsFriendlyTextAndHidesTheRawError() {
        Message m = new Message(Message.ASSISTANT);
        m.model = "meta/muse-image";
        m.error = Routing.USE_IMAGES_TAB;
        m.errorDetail = "Not found: meta/muse-image is an image generation model and cannot be used with the "
                + "chat/completions endpoint.";
        final boolean[] opened = new boolean[1];
        MessageView v = new MessageView(c.get(), m, new MessageView.Actions() {
            public void retry(Message failed) {
            }

            public void useImagesTab(Message failed) {
                opened[0] = true;
            }
        });
        String text = allText(v);
        assertTrue(text, text.contains(Routing.USE_IMAGES_TAB));
        assertTrue(text, text.contains("Show details"));
        assertTrue(text, text.contains("Open Images tab"));
        // The raw message is there but hidden until expanded.
        TextView detail = find(v, "cannot be used with the chat/completions");
        assertNotNull(detail);
        assertEquals(View.GONE, detail.getVisibility());
        find(v, "Open Images tab").performClick();
        assertTrue(opened[0]);
        assertEquals(Conversation.CHAT, "chat");
    }

    private static TextView find(View v, String contains) {
        if (v instanceof TextView && ((TextView) v).getText().toString().contains(contains)) return (TextView) v;
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                TextView t = find(((ViewGroup) v).getChildAt(i), contains);
                if (t != null) return t;
            }
        }
        return null;
    }
}
