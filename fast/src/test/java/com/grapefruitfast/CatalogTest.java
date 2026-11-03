package com.grapefruitfast;
import org.junit.Test;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;
public final class CatalogTest {
    private String bundled() throws Exception {return new String(Files.readAllBytes(Paths.get("src/main/assets/channels.json")),"UTF-8");}
    @Test public void bundledCatalogIncludesRequestedRegionsAndNoCp24() throws Exception {
        List<Channel> channels=Catalog.parse(bundled());Set<String> ids=new HashSet<>();
        for(Channel c:channels){ids.add(c.id);assertFalse(c.name.toLowerCase(Locale.US).contains("cp24"));assertFalse(c.provenance.isEmpty());}
        for(String id:new String[]{"citynews-toronto","citynews-west","citynews-vancouver","global-national","global-toronto","global-winnipeg","global-edmonton","nbc-news-now","abc-news-live","cbs-news"})assertTrue(id,ids.contains(id));
        assertEquals(14,channels.stream().filter(c->c.id.startsWith("global-")).count());
        assertTrue(channels.stream().anyMatch(c->c.category.equals("Series")));
        assertTrue(channels.stream().anyMatch(c->c.category.equals("Reality")));
    }
    @Test public void searchesCitiesAndDescriptionsWithoutCaseSensitivity() throws Exception {
        Channel toronto=Catalog.parse(bundled()).get(0);
        assertTrue(toronto.matches(" TORONTO "));assertTrue(toronto.matches("weather"));assertFalse(toronto.matches("not-a-channel"));
    }
    @Test public void rejectsDuplicateIdsAndInsecureStreams() throws Exception {
        JSONObject root=new JSONObject(bundled());JSONArray channels=root.getJSONArray("channels");channels.put(channels.getJSONObject(0));
        rejects(root.toString());
        root=new JSONObject(bundled());root.getJSONArray("channels").getJSONObject(0).getJSONArray("streams").put(0,"http://example.com/live.m3u8");rejects(root.toString());
        root=new JSONObject(bundled());root.getJSONArray("channels").getJSONObject(0).put("country","GB");rejects(root.toString());
    }
    @Test public void rejectsUnknownSchemaAndEmptyCatalog() throws Exception {
        rejects("{\"schema\":2,\"channels\":[]}");rejects("{\"schema\":1,\"channels\":[]}");
    }
    @Test public void capsDownloadedDataAndClosesInputOnFailure() throws Exception {
        final boolean[] closed={false};
        InputStream stream=new ByteArrayInputStream(new byte[Catalog.MAX_BYTES+1]){public void close(){closed[0]=true;}};
        try{Catalog.read(stream);fail("Oversized catalog accepted");}catch(IOException expected){assertTrue(closed[0]);}
    }
    private void rejects(String json) throws Exception {try{Catalog.parse(json);fail("Bad catalog accepted");}catch(IOException expected){}}
}
