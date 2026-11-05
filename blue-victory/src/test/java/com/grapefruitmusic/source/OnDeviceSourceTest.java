package com.grapefruitmusic.source;

import android.app.Application;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.util.Prefs;
import java.io.IOException;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class)
public class OnDeviceSourceTest {
    @Test public void purchasedAndAdvanceAlbumsCoexistAndArtistsMerge() throws Exception {
        Prefs.init(RuntimeEnvironment.getApplication());
        BundledSource advance=new BundledSource(RuntimeEnvironment.getApplication());
        final MediaItem official=new MediaItem(MediaItem.ALBUM,"device","official","Victory Lights: Sounds of Resistance, Hope, and Change");official.artist="Poppy Peppers";
        final MediaItem artist=new MediaItem(MediaItem.ARTIST,"device","artist","Poppy Peppers");artist.count=1;
        MusicSource local=(MusicSource)java.lang.reflect.Proxy.newProxyInstance(MusicSource.class.getClassLoader(),new Class<?>[]{MusicSource.class},(proxy,method,args)->{
            switch(method.getName()){
                case "albums":case "artistAlbums":return Collections.singletonList(official);
                case "artists":return Collections.singletonList(artist);
                case "songs":case "playlists":case "tracks":return Collections.emptyList();
                case "canEditPlaylists":return true;
                default:throw new AssertionError(method.getName());
            }
        });
        OnDeviceSource combined=new OnDeviceSource(local,advance);
        assertEquals(2,combined.albums().size());assertEquals(5,combined.songs().size());
        assertEquals(1,combined.artists().size());assertEquals(2,combined.artistAlbums(combined.artists().get(0)).size());
        assertEquals(5,combined.tracks(advance.albums().get(0)).size());
        for(MediaItem song:combined.songs())assertEquals(BundledSource.ID,song.sourceId);
        try{combined.createPlaylist("Export",advance.songs());fail();}catch(IOException expected){}
        Sources sources=Sources.get(RuntimeEnvironment.getApplication());Prefs.setCurrentSource(BundledSource.ID);
        assertEquals("device",sources.current().id());
        for(MusicSource source:sources.all())assertNotEquals(BundledSource.ID,source.id());
        assertNotNull(sources.byId(BundledSource.ID));
    }
    @Test public void deniedLocalStorageStillShowsTheIncludedEp() throws Exception {
        MusicSource denied=(MusicSource)java.lang.reflect.Proxy.newProxyInstance(MusicSource.class.getClassLoader(),new Class<?>[]{MusicSource.class},(p,m,a)->{throw new SecurityException("Denied");});
        OnDeviceSource combined=new OnDeviceSource(denied,new BundledSource(RuntimeEnvironment.getApplication()));
        assertEquals(5,combined.songs().size());assertEquals(1,combined.albums().size());
    }
}
