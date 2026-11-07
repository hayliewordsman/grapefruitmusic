package com.grapefruitmusic.ui;

import android.content.Context;
import android.util.AtomicFile;
import com.grapefruitmusic.net.Http;
import java.io.*;
import java.net.*;

/** Independently published result data, with an atomic, validated private offline copy. */
final class RollCallRepository {
    static final String URL="https://raw.githubusercontent.com/hayliewordsman/grapefruitmusic/blue-roll-call-data/roster.json";
    static final int MAX_BYTES=8*1024*1024;
    private static final Object LOCK=new Object();
    private final Context context;
    private final AtomicFile cache;
    private final Transport transport;
    interface Transport { Response fetch() throws IOException; }
    static final class Response implements Closeable {
        final InputStream body;final HttpURLConnection connection;
        Response(InputStream body){this(body,null);}
        Response(InputStream body,HttpURLConnection connection){this.body=body;this.connection=connection;}
        public void close()throws IOException{try{body.close();}finally{if(connection!=null)connection.disconnect();}}
    }
    static final class Result {
        final AcknowledgementsRoster roster;final String source;
        Result(AcknowledgementsRoster roster,String source){this.roster=roster;this.source=source;}
    }
    RollCallRepository(Context context){this(context,()->{
        HttpURLConnection connection=(HttpURLConnection)new java.net.URL(URL).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(12000);connection.setReadTimeout(20000);connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent",Http.USER_AGENT);connection.setRequestProperty("Accept","application/json");
        try{int code=Http.status(connection);if(code!=200)throw new IOException("Results service answered HTTP "+code);return new Response(connection.getInputStream(),connection);}
        catch(IOException error){connection.disconnect();throw error;}
    });}
    RollCallRepository(Context context,Transport transport){this.context=context.getApplicationContext();this.transport=transport;File dir=new File(context.getFilesDir(),"victory-roll-call");dir.mkdirs();cache=new AtomicFile(new File(dir,"roster.json"));}
    Result offline()throws Exception{
        synchronized(LOCK){try{return new Result(AcknowledgementsRoster.read(cache.openRead()),"Saved results");}catch(Exception ignored){return new Result(AcknowledgementsRoster.load(context),"Included offline snapshot");}}
    }
    Result refresh()throws Exception{
        // Download first; a slow server never holds the cache lock or blocks another view's read.
        File pending=File.createTempFile("roll-call-",".json",context.getCacheDir());
        try{
            try(Response response=transport.fetch();FileOutputStream out=new FileOutputStream(pending)){
                byte[] bytes=new byte[16384];int n,total=0;
                while((n=response.body.read(bytes))!=-1){total+=n;if(total>MAX_BYTES)throw new IOException("Results response is too large");out.write(bytes,0,n);}out.getFD().sync();
            }
            AcknowledgementsRoster roster=AcknowledgementsRoster.read(new FileInputStream(pending));
            if(roster.members.isEmpty()||roster.members.size()>15000)throw new IOException("Results response has no usable roster");
            java.util.Set<String> states=new java.util.HashSet<String>();for(AcknowledgementsRoster.Member member:roster.members)states.add(member.state);for(org.json.JSONObject gap:roster.unresolved)states.add(gap.optString("state"));
            if(!states.containsAll(java.util.Arrays.asList(AcknowledgementsRoster.STATE_CODES)))throw new IOException("Results response has incomplete state coverage");
            synchronized(LOCK){
                try{AcknowledgementsRoster saved=offline().roster;if(saved.updatedAt>roster.updatedAt)return new Result(saved,"Showing newer offline results");}catch(Exception ignored){}
                FileOutputStream out=null;try{
                out=cache.startWrite();try(InputStream in=new FileInputStream(pending)){byte[] bytes=new byte[16384];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}cache.finishWrite(out);
            }catch(IOException error){if(out!=null)cache.failWrite(out);throw error;}}
            return new Result(roster,"Latest available results");
        }finally{pending.delete();}
    }
}
