package io.github.hayliewordsman.grapefruitai.or;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import io.github.hayliewordsman.grapefruitai.TestFiles;
import io.github.hayliewordsman.grapefruitai.util.Format;

/**
 * Against OpenRouter's live responses (2026-11-13): GET /api/v1/images/models/{id}/endpoints, the
 * models list, and the JSON-LD of openrouter.ai/meta/muse-image.
 */
public class ImagePricingTest {
    private static ImagePricing endpoints(String id) throws Exception {
        return ImagePricing.fromEndpoints(TestFiles.read("pricing/" + id.replace("/", "__") + ".json"));
    }

    private static OrModel model(String id) throws Exception {
        OrModel m = Catalog.find(OrModel.parseList(TestFiles.read("models-sample.json")), id);
        assertNotNull(id, m);
        return m;
    }

    @Test
    public void museImageFromItsModelPage() throws Exception {
        // The Images API lists no prices for Muse Image…
        assertTrue(endpoints("meta/muse-image").isEmpty());
        // …so the price comes from the model page's structured data.
        ImagePricing p = ImagePricing.fromModelPage("meta/muse-image",
                TestFiles.read("pricing/meta__muse-image.html"));
        assertEquals(ImagePricing.MODEL_PAGE, p.source);
        assertEquals("$0.01/image", p.generation());
        OrModel m = model("meta/muse-image");
        assertEquals("Image generation: $0.01/image", Format.modelPrices(m, p));
        String details = Format.modelDetails(m, p);
        assertTrue(details, details.contains("Image generation: $0.01/image"));
        assertFalse(details, details.contains("Input: Free"));
        assertFalse(details, details.contains("Output: Free"));
        assertTrue(details, details.contains("openrouter.ai page"));
    }

    @Test
    public void pageForAnotherModelIsIgnored() throws Exception {
        ImagePricing p = ImagePricing.fromModelPage("someone/else", TestFiles.read("pricing/meta__muse-image.html"));
        assertTrue(p.isEmpty());
    }

    @Test
    public void perImageWithVariantsAndInputs() throws Exception {
        ImagePricing p = endpoints("bytedance-seed/seedream-5-5");
        assertEquals("$0.02–$0.035/image by resolution or quality", p.generation());
        List<String> lines = p.lines();
        assertTrue(lines.toString(), lines.contains("Image generation: $0.02/image"));
        assertTrue(lines.toString(), lines.contains("Image generation, high resolution: $0.035/image"));
        assertTrue(lines.toString(), lines.contains("Input images: $0.008/image"));
        OrModel m = model("bytedance-seed/seedream-5-5");
        assertFalse(Format.modelPrices(m, p).contains("Free"));
        assertFalse(Format.modelDetails(m, p).contains("Input: Free"));
    }

    @Test
    public void perImageByResolution() throws Exception {
        ImagePricing p = endpoints("black-forest-labs/flux-3-image-klein");
        assertEquals("$0.008–$0.036/image by resolution or quality", p.generation());
        assertTrue(p.lines().contains("Image generation, 1K: $0.009/image"));
        assertTrue(p.lines().contains("Image generation, 4K: $0.036/image"));
    }

    @Test
    public void perMegapixel() throws Exception {
        assertEquals("$0.03/megapixel", endpoints("black-forest-labs/flux.2-pro").generation());
    }

    @Test
    public void perMillionTokens() throws Exception {
        ImagePricing p = endpoints("openai/gpt-image-1");
        assertEquals("$40.00 / M image tokens", p.generation());
        assertTrue(p.lines().contains("Prompt text: $5.00 / M tokens"));
        assertTrue(p.lines().contains("Input images: $10.00 / M image tokens"));
    }

    @Test
    public void requestSurchargesAreSeparate() throws Exception {
        ImagePricing p = endpoints("krea/k3-edit");
        assertEquals("$0.03–$0.06/image by resolution or quality", p.generation());
        assertTrue(p.lines().contains("Surcharge, high thinking surcharge: $0.02/request"));
    }

    @Test
    public void sameRateFromSeveralProvidersIsOnePrice() throws Exception {
        ImagePricing p = endpoints("google/gemini-nano-banana-2.1");
        assertEquals("$30.00 / M image tokens", p.generation());
        assertEquals(1, p.lines().size());
    }

    @Test
    public void cacheRoundTrip() throws Exception {
        ImagePricing p = endpoints("bytedance-seed/seedream-5-5");
        ImagePricing back = ImagePricing.fromJson(new JSONObject(p.toJson().toString()));
        assertEquals(p.generation(), back.generation());
        assertEquals(p.lines(), back.lines());
        assertEquals(ImagePricing.IMAGES_API, back.source);
    }

    @Test
    public void withoutUnitPricesFallsBackToListedRateNotFree() throws Exception {
        OrModel m = model("meta/muse-image");
        String row = Format.modelPrices(m, null);
        assertEquals("Image generation: $2.40 / M image tokens", row);
        assertFalse(Format.modelDetails(m, null).contains("Free"));
        assertNull(endpoints("meta/muse-image").generation());
    }

    @Test
    public void chatModelsKeepTokenPrices() throws Exception {
        OrModel m = model("google/gemini-4-hydrogen");
        assertEquals("USD in $1.50 / M tokens · out $7.50 / M tokens", Format.modelPrices(m, null));
        assertTrue(Format.modelDetails(m, null).contains("Input: $1.50 / M tokens"));
        assertEquals("Free (USD 0)", Format.modelPrices(model("baidu/ernie-5.5-flash-preview:free"), null));
    }

    @Test
    public void textAndImageModelShowsBoth() throws Exception {
        OrModel m = model("google/gemini-2.5-flash-image");
        String row = Format.modelPrices(m, null);
        assertTrue(row, row.startsWith("Image generation: $30.00 / M image tokens"));
        assertTrue(row, row.contains("text in $0.30 / M tokens"));
    }

    @Test
    public void modelIdsAreCheckedBeforeUseInUrls() {
        assertTrue(OpenRouter.validModelId("meta/muse-image"));
        assertTrue(OpenRouter.validModelId("black-forest-labs/flux.2-pro"));
        assertFalse(OpenRouter.validModelId("../../etc"));
        assertFalse(OpenRouter.validModelId("a/b?x=1"));
        assertFalse(OpenRouter.validModelId("a/b/c"));
    }
}
