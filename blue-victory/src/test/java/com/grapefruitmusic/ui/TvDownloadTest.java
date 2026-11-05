package com.grapefruitmusic.ui;

import android.app.Application;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class)
public class TvDownloadTest {
    static TvFileDownload.Progress progress(){return new TvFileDownload.Progress(){public boolean stopped(){return false;}public void saved(long n,long total){}};}
    @Test public void progressiveDownloadResumesAtExactByteOffset() throws Exception {
        File out=new File(RuntimeEnvironment.getApplication().getFilesDir(),"test.mp4");Files.write(new File(out+".part").toPath(),"abc".getBytes("UTF-8"));
        try(ServerSocket socket=new ServerSocket(0)){
            ExecutorService worker=Executors.newSingleThreadExecutor();
            Future<String> request=worker.submit(()->{try(Socket client=socket.accept()){
                BufferedReader input=new BufferedReader(new InputStreamReader(client.getInputStream(),"UTF-8"));StringBuilder headers=new StringBuilder();String line;while((line=input.readLine())!=null&&!line.isEmpty())headers.append(line).append('\n');
                client.getOutputStream().write("HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 3-5/6\r\nContent-Length: 3\r\nConnection: close\r\n\r\ndef".getBytes("UTF-8"));return headers.toString();
            }});
            try{TvFileDownload.save("http://127.0.0.1:"+socket.getLocalPort()+"/film.mp4","",true,out,0,progress());assertTrue(request.get(5,TimeUnit.SECONDS).contains("Range: bytes=3-"));assertEquals("abcdef",new String(Files.readAllBytes(out.toPath()),"UTF-8"));}finally{worker.shutdownNow();}
        }
    }
    @Test public void refusesLiveHlsWithoutCreatingADownload() throws Exception {
        File out=new File(RuntimeEnvironment.getApplication().getFilesDir(),"live.ts");
        try(ServerSocket socket=new ServerSocket(0)){
            ExecutorService worker=Executors.newSingleThreadExecutor();Future<?> server=worker.submit(()->{try(Socket client=socket.accept()){
                BufferedReader input=new BufferedReader(new InputStreamReader(client.getInputStream()));String line;while((line=input.readLine())!=null&&!line.isEmpty()){}
                byte[] body="#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment.ts\n".getBytes("UTF-8");client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));client.getOutputStream().write(body);
            }catch(Exception e){throw new RuntimeException(e);}});
            try{try{TvFileDownload.save("http://127.0.0.1:"+socket.getLocalPort()+"/live.m3u8","",true,out,0,progress());fail("Live capture must be unavailable");}catch(TvFileDownload.Refused expected){assertFalse(out.exists());}server.get(5,TimeUnit.SECONDS);}finally{worker.shutdownNow();}
        }
    }
    @Test public void hlsChoosesDownloadableVariantAndKeepsFinitePlaylistFlag() throws Exception {
        URL base=new URL("https://example.test/master.m3u8");
        TvFileDownload.Playlist p=TvFileDownload.parse("#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud\",URI=\"audio.m3u8\"\n#EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080,AUDIO=\"aud\"\n1080.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720\n720.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\n360.m3u8\n",base);
        assertEquals("https://example.test/720.m3u8",TvFileDownload.choose(p,1080).uri);assertEquals("https://example.test/360.m3u8",TvFileDownload.choose(p,480).uri);
        TvFileDownload.Playlist finite=TvFileDownload.parse("#EXTM3U\n#EXTINF:6,\none.ts\n#EXT-X-ENDLIST\n",base);assertTrue(finite.ended);assertEquals(1,finite.segments.size());
    }
    @Test public void liveChannelsCannotBeQueuedAndDeletedJobsStayDeleted() throws Exception {
        android.content.Context c=RuntimeEnvironment.getApplication();
        Channel live=new Channel("live","Live","","News","","","",new String[]{"https://example.test/live.m3u8"},"");
        try{TvDownloads.enqueue(c,live);fail();}catch(IOException expected){}
        TvDownloads.Item item=new TvDownloads.Item();item.id="deleted";item.file=new File(c.getFilesDir(),"movie.mp4").getPath();TvDownloads.put(c,item);TvDownloads.remove(c,item.id);TvDownloads.updateExisting(c,item);assertNull(TvDownloads.get(c,item.id));
        assertTrue(Connections.take("abc",1));assertFalse(Connections.canWatch("abc",1));assertFalse(Connections.take("abc",1));Connections.give("abc");assertTrue(Connections.canWatch("abc",1));
    }
}
