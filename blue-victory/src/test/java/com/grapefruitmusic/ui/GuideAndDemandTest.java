package com.grapefruitmusic.ui;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.*;
import java.net.URL;
import java.util.*;
import static org.junit.Assert.*;

/** 3.0: the guide, the Movies catalog, On Demand, the recorder and the DVR's schedule. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, application=android.app.Application.class)
public class GuideAndDemandTest {
    private TimeZone zone;
    @Before public void utc(){zone=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("UTC"));}
    @After public void back(){TimeZone.setDefault(zone);}
    private static Channel channel(String id,String name,String category){
        return new Channel(id,name,"CA",category,"","https://example.com","",new String[]{"https://example.com/a.m3u8"},"");
    }
    private static final long T=1_793_736_000_000L; // 2026-11-03 20:00 UTC, on the hour

    /** No published guide: hour blocks on news channels, half hours elsewhere, on the clock, labelled. */
    @Test public void placeholderBlocksWhereThereIsNoGuide() {
        List<Guide.Program> news=Guide.fill(channel("n","CBC News Network","News"),null,T+10*Guide.MINUTE,T+3*Guide.HOUR);
        assertEquals(3,news.size());
        for(int i=0;i<3;i++){
            Guide.Program p=news.get(i);
            assertTrue(p.placeholder);assertEquals(Guide.UNAVAILABLE,p.title);
            assertEquals(T+i*Guide.HOUR,p.start);assertEquals(T+(i+1)*Guide.HOUR,p.end);
        }
        List<Guide.Program> other=Guide.fill(channel("o","Mayday: Air Disaster","Series"),null,T,T+2*Guide.HOUR);
        assertEquals(4,other.size());assertEquals(30*Guide.MINUTE,other.get(0).end-other.get(0).start);
        assertEquals(T+90*Guide.MINUTE,other.get(3).start);
        // A channel with "news" or "headlines" in its name counts as news, whatever its genre.
        assertEquals(Guide.HOUR,Guide.block(channel("h","CNN Headlines","Series")));
    }
    /** Known programmes keep their times; the gaps around them are filled with placeholder blocks to the clock. */
    @Test public void gapsBetweenProgrammesAreFilled() {
        Guide.Program show=new Guide.Program(T+20*Guide.MINUTE,T+50*Guide.MINUTE,"The National","",false);
        List<Guide.Program> got=Guide.fill(channel("o","Some Channel","Series"),Collections.singletonList(show),T,T+Guide.HOUR+1);
        assertEquals(T,got.get(0).start);assertEquals(T+20*Guide.MINUTE,got.get(0).end);assertTrue(got.get(0).placeholder);
        assertSame(show,got.get(1));
        assertEquals(T+50*Guide.MINUTE,got.get(2).start);assertEquals(T+Guide.HOUR,got.get(2).end);assertTrue(got.get(2).placeholder);
        assertEquals(T+Guide.HOUR,got.get(3).start);assertEquals(T+90*Guide.MINUTE,got.get(3).end);
    }
    @Test public void readsAnXtreamShortEpg() throws Exception {
        String b64=android.util.Base64.encodeToString("Evening News".getBytes("UTF-8"),android.util.Base64.NO_WRAP);
        String json="{\"epg_listings\":[{\"title\":\""+b64+"\",\"description\":\"\",\"start_timestamp\":\""+(T/1000)+"\",\"stop_timestamp\":\""+(T/1000+1800)+"\"}]}";
        List<Guide.Program> got=Guide.parseShortEpg(json);
        assertEquals(1,got.size());assertEquals("Evening News",got.get(0).title);assertEquals(T,got.get(0).start);assertEquals(T+30*Guide.MINUTE,got.get(0).end);
    }
    @Test public void readsAnXmltvGuide() throws Exception {
        String xml="<?xml version=\"1.0\"?><tv><channel id=\"one\"><display-name>One</display-name></channel>"
            +"<programme start=\"20261103200000 +0000\" stop=\"20261103210000 +0000\" channel=\"one\"><title lang=\"en\">The Hour</title><desc>All the news.</desc></programme>"
            +"<programme start=\"20201103200000 +0000\" stop=\"20201103210000 +0000\" channel=\"one\"><title>Long ago</title></programme></tv>";
        Map<String,List<Guide.Program>> got=Guide.parseXmltv(new ByteArrayInputStream(xml.getBytes("UTF-8")),T-Guide.HOUR,T+24*Guide.HOUR);
        assertEquals(1,got.get("one").size());
        Guide.Program p=got.get("one").get(0);
        assertEquals("The Hour",p.title);assertEquals("All the news.",p.description);assertEquals(T,p.start);assertEquals(T+Guide.HOUR,p.end);
    }


    @Test public void loginFilmsSeriesAndEpisodes() throws Exception {
        UserSources.Source s=new UserSources.Source("xyz","login","My TV","http://tv.example.com:8080","me","secret");
        String json="{\"vodCategories\":[{\"category_id\":\"1\",\"category_name\":\"Drama\"}],"
            +"\"vod\":[{\"stream_id\":5,\"name\":\"A Film\",\"container_extension\":\"mp4\",\"category_id\":\"1\",\"stream_icon\":\"https://e.x/p.jpg\",\"year\":\"2019\"}],"
            +"\"seriesCategories\":[],\"series\":[{\"series_id\":9,\"name\":\"A Show\",\"cover\":\"https://e.x/c.jpg\"}]}";
        List<Channel> got=OnDemand.parse(s,json);
        assertEquals(2,got.size());
        assertEquals("http://tv.example.com:8080/movie/me/secret/5.mp4",got.get(0).streams[0]);assertEquals("Drama",got.get(0).description);assertEquals(2019,got.get(0).year);
        assertEquals(Channel.SERIES,got.get(1).kind);
        org.json.JSONObject info=new org.json.JSONObject("{\"episodes\":{\"1\":[{\"id\":\"31\",\"episode_num\":2,\"title\":\"Pilot, part 2\",\"container_extension\":\"mkv\",\"info\":{\"duration_secs\":2700}}]}}");
        Map<Integer,List<Channel>> eps=OnDemand.parseEpisodes(s,got.get(1),info);
        Channel e=eps.get(1).get(0);
        assertEquals("http://tv.example.com:8080/series/me/secret/31.mkv",e.streams[0]);assertEquals(Channel.EPISODE,e.kind);assertEquals(2700,e.seconds);
    }

}
