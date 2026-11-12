package io.github.hayliewordsman.grapefruitai.or;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import io.github.hayliewordsman.grapefruitai.TestFiles;
import io.github.hayliewordsman.grapefruitai.util.Format;

/** Against a sample of OpenRouter's live GET /api/v1/models?output_modalities=all response. */
public class OrModelTest {
    private List<OrModel> models;

    @Before
    public void load() throws Exception {
        models = OrModel.parseList(TestFiles.read("models-sample.json"));
    }

    private OrModel get(String id) {
        OrModel m = Catalog.find(models, id);
        assertNotNull(id, m);
        return m;
    }

    @Test
    public void parsesEveryModel() {
        assertEquals(10, models.size());
    }

    @Test
    public void chatModelWithTools() {
        OrModel m = get("google/gemini-4-hydrogen");
        assertTrue(m.isChat());
        assertTrue(m.isCoding());
        assertFalse(m.isImage());
        assertEquals("Google", m.provider);
        assertEquals(1048576, m.contextLength);
        assertEquals("$1.50 / M tokens", Format.perMillion(m.pricePrompt));
        assertEquals("$7.50 / M tokens", Format.perMillion(m.priceCompletion));
    }

    @Test
    public void imageApiModel() {
        OrModel m = get("bytedance-seed/seedream-5-5");
        assertTrue(m.isImage());
        assertTrue(m.servesImageApi());
        assertFalse(m.isChat());
        assertFalse(m.isFree());
        assertTrue(m.acceptsImages());
        assertEquals("0.008", m.priceImage);
    }

    @Test
    public void chatImageModel() {
        OrModel m = get("google/gemini-2.5-flash-image");
        assertTrue(m.isImage());
        assertTrue(m.isChat());
    }

    @Test
    public void embeddingsAreNotChat() {
        OrModel m = get("cohere/embed-5-fast");
        assertFalse(m.isChat());
        assertFalse(m.isImage());
    }

    @Test
    public void routerPricesAreVariableNotInvented() {
        OrModel m = get("openrouter/auto");
        assertEquals("variable", Format.perMillion(m.pricePrompt));
        assertEquals(Double.MAX_VALUE, m.sortInputPrice(), 0);
    }

    @Test
    public void freeModel() {
        OrModel m = get("baidu/ernie-5.5-flash-preview:free");
        assertTrue(m.isFree());
        assertEquals("Free (USD 0)", Format.modelPrices(m));
    }

    @Test
    public void missingPricesStayMissing() throws Exception {
        OrModel m = OrModel.parse(new JSONObject("{\"id\":\"x/y\",\"name\":\"Y\",\"pricing\":{\"prompt\":\"0.000001\"}}"));
        assertNull(m.priceCompletion);
        assertEquals(Format.NOT_LISTED, Format.perMillion(m.priceCompletion));
        assertTrue(Format.modelDetails(m).contains("Output: not listed"));
        assertEquals("unknown", Format.tokens(m.contextLength));
    }

    @Test
    public void legacyModalityString() throws Exception {
        OrModel m = OrModel.parse(new JSONObject("{\"id\":\"a/b\",\"architecture\":{\"modality\":\"text+image->text\"}}"));
        assertTrue(m.acceptsImages());
        assertTrue(m.outputsText());
    }

    @Test
    public void detailsAreLabelledUsd() {
        String d = Format.modelDetails(get("openai/gpt-image-1"));
        assertTrue(d, d.contains("US dollars"));
        assertTrue(d, d.contains("Output image tokens: $40.00 / M tokens"));
        assertTrue(d, d.contains("actual charge"));
    }

    @Test
    public void imageParamsFromImageApi() throws Exception {
        JSONObject root = new JSONObject(TestFiles.read("image-models-sample.json"));
        ImageParams p = ImageParams.parse(root.getJSONArray("data").getJSONObject(0));
        assertEquals("bytedance-seed/seedream-5-5", p.id);
        assertTrue(p.aspectRatios.contains("16:9"));
        assertEquals(3, p.resolutions.size());
        assertEquals(10, p.maxReferences);
    }
}
