package com.grapefruitmusic.ui;

import android.app.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import com.grapefruitmusic.util.Prefs;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={23,37},application=Application.class)
public class TvDvrTest {
    private Application app;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();Prefs.init(app);Map<String,Integer> jobs=ReflectionHelpers.getStaticField(Connections.class,"jobs");jobs.clear();Connections.watch(null);Connections.stopWatching=null;}
    @After public void cleanup(){Connections.watch(null);Connections.stopWatching=null;}
    private Channel channel(){return new Channel("test-record","My Live Channel","","","","","",new String[]{"https://example.org/live.m3u8"},"");}
    @Test public void scheduledRecordingPersistsPaddingAndCanBeCancelledWithoutResurrection()throws Exception{
        long start=System.currentTimeMillis()+10*Guide.MINUTE,end=start+30*Guide.MINUTE;TvRecordings.Item item=TvRecordings.schedule(app,channel(),"My Programme",start,end);
        TvRecordings.Item saved=TvRecordings.get(app,item.id);assertEquals(start-Guide.MINUTE,saved.from);assertEquals(end+2*Guide.MINUTE,saved.to);assertEquals(TvRecordings.SCHEDULED,saved.state);assertEquals(Channel.RECORDING,saved.channel().kind);
        AlarmManager alarms=(AlarmManager)app.getSystemService(Application.ALARM_SERVICE);assertFalse(Shadows.shadowOf(alarms).getScheduledAlarms().isEmpty());
        TvRecordings.remove(app,item.id);item.state=TvRecordings.RECORDING;assertFalse(TvRecordings.updateExisting(app,item));assertTrue(TvRecordings.all(app).isEmpty());
    }
    @Test public void recordingsReserveOnlyTheirProvidersStreamsAndPreemptMatchingPlayback(){
        List<String> stopped=new ArrayList<String>();Connections.stopWatching=stopped::add;Connections.watch("first");assertTrue(Connections.take("second",1,true));assertTrue(stopped.isEmpty());
        assertTrue(Connections.take("first",1,true));assertEquals(Collections.singletonList("first"),stopped);assertFalse(Connections.canWatch("first",1));assertFalse(Connections.take("first",1));Connections.give("first");assertTrue(Connections.canWatch("first",1));Connections.give("second");
    }
    @Test public void onDemandAndPastWindowsCannotBeScheduled(){
        Channel film=channel();film.kind=Channel.FILM;long now=System.currentTimeMillis();
        for(Channel c:new Channel[]{film,channel()})try{TvRecordings.schedule(app,c,"Invalid",now,now-1);fail();}catch(IllegalArgumentException expected){}
        assertTrue(TvRecordings.all(app).isEmpty());
    }
    @Test public void dvrSavesCompleteSegmentsAndRejectsEncryptedStreams()throws Exception{
        Map<String,String> bodies=new HashMap<String,String>();bodies.put("/live.m3u8","#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2,\na.ts\n#EXTINF:2,\nb.ts\n#EXT-X-ENDLIST\n");bodies.put("/encrypted.m3u8","#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\"\n#EXTINF:2,\na.ts\n#EXT-X-ENDLIST\n");bodies.put("/a.ts","a");bodies.put("/b.ts","b");
        TestServer server=new TestServer(bodies);String root="http://127.0.0.1:"+server.socket.getLocalPort();
        File file=new File(app.getCacheDir(),"recorded.ts");TvRecorder.Progress progress=new TvRecorder.Progress(){public void saved(long bytes,long total){}public boolean stopped(){return false;}};
        try{TvRecorder.save(root+"/live.m3u8","Test",true,file,System.currentTimeMillis()+5000,0,progress);try(InputStream in=new FileInputStream(file)){assertEquals('a',in.read());assertEquals('b',in.read());assertEquals(-1,in.read());}
            try{TvRecorder.save(root+"/encrypted.m3u8","Test",true,new File(app.getCacheDir(),"encrypted.ts"),System.currentTimeMillis()+5000,0,progress);fail();}catch(TvRecorder.Refused expected){assertTrue(expected.getMessage().contains("encrypted"));}
            for(File cached:app.getCacheDir().listFiles())assertFalse(cached.getName().startsWith("dvr-segment-"));
        }finally{server.close();}
    }
    private static final class TestServer implements Closeable {
        final ServerSocket socket=new ServerSocket(0);final ExecutorService worker=Executors.newSingleThreadExecutor();
        TestServer(Map<String,String> bodies)throws IOException{worker.submit(()->{while(!socket.isClosed())try(Socket client=socket.accept()){
            BufferedReader input=new BufferedReader(new InputStreamReader(client.getInputStream(),"UTF-8"));String first=input.readLine(),line;while((line=input.readLine())!=null&&!line.isEmpty()){}String path=first.split(" ")[1];byte[] body=bodies.get(path).getBytes("UTF-8");OutputStream out=client.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));out.write(body);out.flush();
        }catch(IOException error){if(!socket.isClosed())throw new RuntimeException(error);}});}
        public void close()throws IOException{socket.close();worker.shutdownNow();}
    }
}
