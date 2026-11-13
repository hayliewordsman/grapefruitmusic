package io.github.hayliewordsman.grapefruitai.or;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.github.hayliewordsman.grapefruitai.TestFiles;
import io.github.hayliewordsman.grapefruitai.chat.Message;
import io.github.hayliewordsman.grapefruitai.net.Http;

/** Where image requests go, and what they carry, on the API 17 framework. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 17)
public class ImageRequestTest {
    static final class Fake implements OpenRouter.Transport {
        final List<String> urls = new ArrayList<String>();
        final List<String> bodies = new ArrayList<String>();
        String answer;

        @Override
        public String post(String url, Map<String, String> headers, String json, Http[] holder) {
            assertEquals("Bearer sk-or-v1-test", headers.get("Authorization"));
            urls.add(url);
            bodies.add(json);
            return answer;
        }
    }

    private Fake fake;

    @Before
    public void setUp() throws Exception {
        Models.setForTest(OrModel.parseList(TestFiles.read("models-sample.json")), null);
        fake = new Fake();
        fake.answer = "{\"created\":1,\"data\":[{\"b64_json\":\"iVBORw0KGgo=\",\"media_type\":\"image/png\"}],"
                + "\"usage\":{\"cost\":0.01}}";
    }

    @Test
    public void museImageEditGoesToTheImagesApiWithTheUploadedImage() throws Exception {
        String upload = OpenRouter.DataUrl.of("image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        OpenRouter.ImageResult r = new OpenRouter("sk-or-v1-test", fake)
                .generateImage("meta/muse-image", "Make the sky purple", null, null,
                        Collections.singletonList(upload), null);
        assertEquals(Collections.singletonList("https://openrouter.ai/api/v1/images"), fake.urls);
        JSONObject body = new JSONObject(fake.bodies.get(0));
        assertEquals("meta/muse-image", body.getString("model"));
        assertEquals("Make the sky purple", body.getString("prompt"));
        JSONArray refs = body.getJSONArray("input_references");
        assertEquals(1, refs.length());
        assertEquals("image_url", refs.getJSONObject(0).getString("type"));
        assertEquals(upload, refs.getJSONObject(0).getJSONObject("image_url").getString("url"));
        assertEquals(1, r.images.size());
        assertEquals(0.01, r.cost, 1e-9);
    }

    @Test
    public void textToImageSendsNoReferencesOrUnlistedOptions() throws Exception {
        new OpenRouter("sk-or-v1-test", fake).generateImage("meta/muse-image", "A grapefruit", null, null,
                new ArrayList<String>(), null);
        JSONObject body = new JSONObject(fake.bodies.get(0));
        assertTrue(!body.has("input_references") && !body.has("aspect_ratio") && !body.has("resolution"));
    }

    @Test
    public void museImageIsNeverSentToChat() throws Exception {
        Message m = new Message(Message.USER);
        m.text = "edit this";
        try {
            new OpenRouter("sk-or-v1-test", fake).chat("meta/muse-image", Collections.singletonList(m), false,
                    null, null, null);
            fail("sent to chat");
        } catch (Friendly.ModelRouteException expected) {
            assertEquals(Routing.USE_IMAGES_TAB, expected.getMessage());
        }
        try {
            new OpenRouter("sk-or-v1-test", fake).generateImageViaChat("meta/muse-image", "x", null, null, null);
            fail("sent to chat");
        } catch (IOException expected) {
            assertTrue(expected instanceof Friendly.ModelRouteException);
        }
        assertTrue(fake.urls.isEmpty());
    }

    @Test
    public void chatImageModelsUseChatCompletionsOnlyWhenAsked() throws Exception {
        fake.answer = "{\"choices\":[{\"message\":{\"content\":\"\",\"images\":[{\"type\":\"image_url\","
                + "\"image_url\":{\"url\":\"data:image/png;base64,iVBORw0KGgo=\"}}]}}]}";
        new OpenRouter("sk-or-v1-test", fake).generateImageViaChat("google/gemini-2.5-flash-image", "x", "16:9",
                null, null);
        assertEquals("https://openrouter.ai/api/v1/chat/completions", fake.urls.get(0));
        JSONObject body = new JSONObject(fake.bodies.get(0));
        assertEquals("image", body.getJSONArray("modalities").getString(0));
    }
}
