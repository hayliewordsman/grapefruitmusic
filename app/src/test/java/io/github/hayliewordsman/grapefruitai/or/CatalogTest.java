package io.github.hayliewordsman.grapefruitai.or;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import io.github.hayliewordsman.grapefruitai.TestFiles;

public class CatalogTest {
    private List<OrModel> models;

    @Before
    public void load() throws Exception {
        models = OrModel.parseList(TestFiles.read("models-sample.json"));
    }

    @Test
    public void filtersByKind() {
        Catalog c = new Catalog();
        c.kind = Catalog.IMAGE;
        List<OrModel> images = c.apply(models);
        for (OrModel m : images) assertTrue(m.id, m.outputsImage());
        assertEquals(6, images.size());
        c.kind = Catalog.CODING;
        for (OrModel m : c.apply(models)) assertTrue(m.supportsTools());
        c.kind = Catalog.CHAT;
        for (OrModel m : c.apply(models)) assertTrue(m.isChat());
    }

    @Test
    public void searchesAllWords() {
        Catalog c = new Catalog();
        c.query = "google flash-image";
        List<OrModel> r = c.apply(models);
        assertEquals(1, r.size());
        assertEquals("google/gemini-2.5-flash-image", r.get(0).id);
    }

    @Test
    public void sortsByInputPriceWithUnlistedLast() {
        Catalog c = new Catalog();
        c.kind = Catalog.CHAT;
        c.sort = Catalog.SORT_INPUT_PRICE;
        List<OrModel> r = c.apply(models);
        for (int i = 1; i < r.size(); i++) assertTrue(r.get(i - 1).sortInputPrice() <= r.get(i).sortInputPrice());
        assertEquals("openrouter/auto", r.get(r.size() - 1).id);
    }

    @Test
    public void sortsByContextDescending() {
        Catalog c = new Catalog();
        c.sort = Catalog.SORT_CONTEXT;
        List<OrModel> r = c.apply(models);
        assertEquals("openrouter/auto", r.get(0).id);
    }

    @Test
    public void capabilityFilters() {
        Catalog c = new Catalog();
        c.freeOnly = true;
        assertEquals(1, c.apply(models).size());
        c.freeOnly = false;
        c.needVision = true;
        for (OrModel m : c.apply(models)) assertTrue(m.acceptsImages());
    }

    @Test
    public void sortsByProviderThenName() {
        Catalog c = new Catalog();
        c.sort = Catalog.SORT_PROVIDER;
        List<OrModel> r = c.apply(models);
        for (int i = 1; i < r.size(); i++) {
            assertTrue(r.get(i - 1).provider.compareToIgnoreCase(r.get(i).provider) <= 0);
        }
    }
}
