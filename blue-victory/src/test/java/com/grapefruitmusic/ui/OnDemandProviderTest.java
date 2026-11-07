package com.grapefruitmusic.ui;

import android.app.Application;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=23,application=Application.class)
public class OnDemandProviderTest {
    private Application app;private UserSources.Source source;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();source=new UserSources.Source("test-vod",UserSources.LOGIN,"My IPTV","https://provider.example","u ser","p+ass");}
    private static byte[] data(String text)throws IOException{return text.getBytes("UTF-8");}
    @Test public void filmsStillAppearWhenTheProviderDoesNotSupportSeries()throws Exception{
        OnDemand demand=new OnDemand(app,url->{if(url.contains("get_series"))throw new IOException("HTTP 404");return data(url.contains("get_vod_streams")?"[{\"stream_id\":12,\"name\":\"Provider Film\",\"container_extension\":\"mp4\"}]":"[]");});
        List<Channel> films=demand.refresh(source);assertEquals(1,films.size());assertEquals("Provider Film",films.get(0).name);assertEquals(Channel.FILM,films.get(0).kind);assertTrue(films.get(0).streams[0].contains("u+ser/p%2Bass/12.mp4"));
        assertEquals(1,new OnDemand(app,url->{throw new IOException("Offline");}).cached(source).size());
    }
    @Test public void seriesOnlyProvidersAndSeasonCacheWorkOffline()throws Exception{
        OnDemand demand=new OnDemand(app,url->{if(url.contains("get_vod"))throw new IOException("HTTP 403");if(url.contains("get_series_info"))return data("{\"episodes\":{\"2\":[{\"id\":92,\"title\":\"Episode Two\",\"episode_num\":2,\"container_extension\":\"mkv\"}],\"1\":[{\"id\":91,\"title\":\"Episode One\",\"episode_num\":1}]}}");return data(url.contains("get_series_categories")?"[]":"[{\"series_id\":9,\"name\":\"Provider Series\"}]");});
        Channel series=demand.refresh(source).get(0);assertEquals(Channel.SERIES,series.kind);Map<Integer,List<Channel>> seasons=demand.episodesFor(source,series);assertEquals(Arrays.asList(1,2),new ArrayList<Integer>(seasons.keySet()));assertEquals(Channel.EPISODE,seasons.get(2).get(0).kind);
        OnDemand offline=new OnDemand(app,url->{throw new IOException("Offline");});assertEquals("Episode One",offline.episodesFor(source,series).get(1).get(0).name);
    }
    @Test public void authenticationAndMalformedRepliesCannotWipeTheSavedLibrary()throws Exception{
        new OnDemand(app,url->data(url.contains("get_vod_streams")?"[{\"stream_id\":12,\"name\":\"Saved Film\"}]":"[]")).refresh(source);
        for(OnDemand.Fetcher broken:new OnDemand.Fetcher[]{url->{throw new IOException("HTTP 401");},url->data("<html>Error</html>")}){
            OnDemand demand=new OnDemand(app,broken);try{demand.refresh(source);fail();}catch(IOException expected){}assertEquals("Saved Film",demand.cached(source).get(0).name);
        }
    }
    @Test public void m3uExplicitVodAndSeriesEntriesAreSeparatedFromLiveChannels()throws Exception{
        String playlist="#EXTM3U\n#EXTINF:-1 tvg-type=\"vod\",HLS Film\nhttps://provider.example/film.m3u8\n#EXTINF:-1,Show Episode\nhttps://provider.example/series/u/p/2.mp4\n#EXTINF:-1 group-title=\"Movies\",Live Cinema Channel\nhttps://provider.example/live/u/p/3.ts\n";
        List<Channel> channels=UserSources.parseM3u(source,playlist);assertEquals(Channel.FILM,channels.get(0).kind);assertEquals(Channel.EPISODE,channels.get(1).kind);assertTrue(channels.get(2).live());
    }
}
