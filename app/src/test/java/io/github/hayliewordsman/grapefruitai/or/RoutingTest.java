package io.github.hayliewordsman.grapefruitai.or;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.hayliewordsman.grapefruitai.TestFiles;
import io.github.hayliewordsman.grapefruitai.util.Format;

/** Routing from OpenRouter's live model and Images API metadata (fixtures fetched 2026-11-13). */
public class RoutingTest {
    private List<OrModel> models;
    private final Map<String, ImageParams> params = new HashMap<String, ImageParams>();

    @Before
    public void load() throws Exception {
        models = OrModel.parseList(TestFiles.read("models-sample.json"));
        JSONArray data = new JSONObject(TestFiles.read("image-models-sample.json")).getJSONArray("data");
        for (int i = 0; i < data.length(); i++) {
            ImageParams p = ImageParams.parse(data.getJSONObject(i));
            params.put(p.id, p);
        }
    }

    private OrModel m(String id) {
        OrModel m = Catalog.find(models, id);
        assertNotNull(id, m);
        return m;
    }

    @Test
    public void museImageIsImageOnlyAndNeverChat() {
        OrModel muse = m("meta/muse-image");
        assertEquals(Routing.Kind.IMAGE_ONLY, Routing.kind(muse));
        assertEquals(Routing.USE_IMAGES_TAB, Routing.chatBlock(muse));
        assertEquals(Routing.ImageRoute.IMAGES_API, Routing.imageRoute(muse));
        assertEquals("Images API", Routing.apiLabel(muse));
        assertTrue(Format.modelDetails(muse).contains("Uses: OpenRouter Images API"));
    }

    @Test
    public void kinds() {
        assertEquals(Routing.Kind.CHAT, Routing.kind(m("baidu/ernie-light-dt-1.0-intl")));
        assertEquals(Routing.Kind.VISION_CHAT, Routing.kind(m("google/gemini-4-hydrogen")));
        assertEquals(Routing.Kind.CHAT_AND_IMAGE, Routing.kind(m("google/gemini-2.5-flash-image")));
        assertEquals(Routing.Kind.OTHER, Routing.kind(m("cohere/embed-5-fast")));
        assertNull(Routing.chatBlock(m("google/gemini-2.5-flash-image")));
        assertNotNull(Routing.chatBlock(m("cohere/embed-5-fast")));
        assertNull(Routing.chatBlock(null));
        assertEquals("Chat API and Images API", Routing.apiLabel(m("google/gemini-2.5-flash-image")));
        assertEquals("Chat API", Routing.apiLabel(m("google/gemini-4-hydrogen")));
        // Gemini image models are served by the Images API too: the Images tab uses it.
        assertEquals(Routing.ImageRoute.IMAGES_API, Routing.imageRoute(m("google/gemini-2.5-flash-image")));
        assertEquals(Routing.ImageRoute.NONE, Routing.imageRoute(m("google/gemini-4-hydrogen")));
    }

    @Test
    public void chatPickerNeverListsImageOnlyModels() {
        Catalog c = new Catalog();
        c.restrict = Catalog.CHAT;
        for (OrModel x : c.apply(models)) assertNull(x.id, Routing.chatBlock(x));
        assertNull(Catalog.find(c.apply(models), "meta/muse-image"));
        c.restrict = Catalog.IMAGE;
        assertNotNull(Catalog.find(c.apply(models), "meta/muse-image"));
        for (OrModel x : c.apply(models)) assertTrue(x.isImage());
    }

    @Test
    public void editingFromImageApiMetadata() {
        // Muse Image accepts image input but OpenRouter lists no parameters: editing allowed, explained.
        Routing.Editing muse = Routing.editing(m("meta/muse-image"), params.get("meta/muse-image"));
        assertTrue(muse.supported);
        assertEquals(Routing.UNLISTED_LIMIT, muse.maxImages);
        assertTrue(muse.note, muse.note.contains("doesn't list a limit"));
        // Seedream lists input_references up to 10.
        Routing.Editing seedream = Routing.editing(m("bytedance-seed/seedream-5-5"),
                params.get("bytedance-seed/seedream-5-5"));
        assertTrue(seedream.supported);
        assertEquals(10, seedream.maxImages);
        // Recraft V4.1 Flash takes text only: generation, no editing.
        Routing.Editing recraft = Routing.editing(m("recraft/recraft-v4.1-flash"),
                params.get("recraft/recraft-v4.1-flash"));
        assertFalse(recraft.supported);
        assertTrue(recraft.note, recraft.note.contains("text only"));
        assertFalse(Routing.editing(m("google/gemini-4-hydrogen"), null).supported);
    }
}
