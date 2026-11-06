package com.grapefruitmusic.source;

import android.app.Application;
import android.content.Context;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.util.Prefs;
import com.grapefruitmusic.tags.SongTags;
import com.grapefruitmusic.lyrics.LyricsRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.io.IOException;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 28, 35, 37}, application = Application.class)
public class BundledSourceTest {
    private Context context;
    private BundledSource source;
    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Prefs.init(context);
        source = new BundledSource(context);
    }
    @Test public void browsesAndPlaysOnlyPrivateCopiesInAlbumOrder() throws Exception {
        List<MediaItem> songs = source.songs();
        assertEquals(5, songs.size());
        assertEquals("Blue Tsunami (Bonus Bundled Content)", songs.get(0).title);
        assertEquals("Eyes On The Prize (Bonus Bundled Content)", songs.get(4).title);
        MediaItem album = source.albums().get(0);
        assertEquals(5, album.count);
        assertEquals(1, source.artistAlbums(source.artists().get(0)).size());
        assertEquals(5, source.tracks(album).size());
        for (int i = 0; i < songs.size(); i++) {
            MediaItem song = songs.get(i);
            File privateFile = source.fileFor(song);
            assertTrue(privateFile.isFile());
            assertTrue(privateFile.getCanonicalPath().startsWith(context.getFilesDir().getCanonicalPath() + "/"));
            assertTrue(privateFile.getName().startsWith(String.format(java.util.Locale.US, "%02d ", i + 1)));
            assertEquals(album.id, song.albumId);
            assertEquals("Poppy Peppers", song.albumArtist);
            assertEquals(i + 1, song.trackNumber);
            assertNull(SongTags.target(context, song));
            assertNull(LyricsRepository.load(context, song));
            assertNull(source.download(song, 0));
            assertFalse(song.sameAs(new MediaItem(MediaItem.TRACK, LocalSource.ID, song.id, song.title)));
        }
        assertEquals(1, source.search("blue tsunami").size());
        assertFalse(source.canDownload());
        assertFalse(source.canEditPlaylists());
        assertFalse(source.isRemote());
    }
    @Test public void refusesUnknownTracksAndPlaylistMutation() throws Exception {
        MediaItem song = source.songs().get(0);
        try {
            source.fileFor(new MediaItem(MediaItem.TRACK, "another-app", song.id, song.title));
            fail("A track from another source was accepted");
        } catch (IOException expected) { }
        try {
            source.createPlaylist("Export", source.songs());
            fail("The bundled source should be read-only");
        } catch (IOException expected) { }
    }
}
