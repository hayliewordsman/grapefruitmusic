package com.grapefruitfast;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Settings › Test Channels: checks, from this device and wherever it is, whether each channel's stream
 * plays. For each source it follows the path playback takes: the master playlist, one quality's
 * playlist, then the first piece of video (a small byte range). A provider that blocks a region does it
 * at one of those steps. Only the channels' own servers are contacted; nothing is sent anywhere else.
 */
public final class ChannelTest {
    public enum Result { PLAYS, REFUSED, FAILED }
    /** One channel's outcome, with a short reason ("HTTP 403", "timed out"). */
    public static final class Outcome {
        public final Result result; public final String detail;
        Outcome(Result r,String d){result=r;detail=d;}
    }
    /** Fetches a URL (HTTPS only), returning its status and up to limit bytes. Swappable for tests. */
    public interface Fetcher { Response get(String url,int limit,boolean range) throws IOException; }
    public static final class Response {
        public final int status; public final String url; public final byte[] body;
        public Response(int status,String url,byte[] body){this.status=status;this.url=url;this.body=body;}
    }
    private static final int PLAYLIST_LIMIT=512*1024, SAMPLE=64*1024;
    private final Fetcher fetcher;
    public ChannelTest(Fetcher fetcher){this.fetcher=fetcher;}

    /** A channel plays if any of its sources does; otherwise refused (HTTP 4xx) or failed. */
    public Outcome test(Channel c) {
        Outcome last=new Outcome(Result.FAILED,"no stream");
        for(String source:c.streams) {
            last=testSource(source);
            if(last.result==Result.PLAYS) return last;
        }
        return last;
    }

    Outcome testSource(String master) {
        try {
            Response m=fetcher.get(master,PLAYLIST_LIMIT,false);
            if(m.status!=200) return refusal(m.status,"playlist");
            String text=new String(m.body,"UTF-8");
            if(!text.startsWith("#EXTM3U")) return new Outcome(Result.FAILED,"not a stream");
            String media=m.url, mediaText=text;
            if(text.contains("#EXT-X-STREAM-INF")) {
                String variant=firstUri(text,"#EXT-X-STREAM-INF");
                if(variant==null) return new Outcome(Result.FAILED,"no renditions");
                media=resolve(m.url,variant);
                Response v=fetcher.get(media,PLAYLIST_LIMIT,false);
                if(v.status!=200) return refusal(v.status,"rendition");
                media=v.url;mediaText=new String(v.body,"UTF-8");
            }
            if(mediaText.contains("#EXT-X-ENDLIST")) return new Outcome(Result.FAILED,"stream has ended");
            String segment=firstUri(mediaText,"#EXTINF");
            if(segment==null) return new Outcome(Result.FAILED,"no video yet");
            Response s=fetcher.get(resolve(media,segment),SAMPLE,true);
            if(s.status!=200&&s.status!=206) return refusal(s.status,"video");
            if(s.body.length==0) return new Outcome(Result.FAILED,"empty video");
            return new Outcome(Result.PLAYS,"plays");
        } catch(SocketTimeoutException e) {
            return new Outcome(Result.FAILED,"timed out");
        } catch(IOException|RuntimeException e) {
            return new Outcome(Result.FAILED,e.getMessage()==null?"couldn't connect":e.getMessage());
        }
    }

    /** 401/403/451 and other 4xx: the provider said no (often a region block). 5xx: a fault, try later. */
    private static Outcome refusal(int status,String step) {
        boolean no=status>=400&&status<500;
        return new Outcome(no?Result.REFUSED:Result.FAILED,"HTTP "+status+" ("+step+")");
    }

    /** The first URI line after a tag (#EXT-X-STREAM-INF, #EXTINF). */
    static String firstUri(String playlist,String tag) {
        boolean next=false;
        for(String line:playlist.split("\r?\n")) {
            line=line.trim();
            if(line.startsWith(tag)) {next=true;continue;}
            if(next&&!line.isEmpty()&&!line.startsWith("#")) return line;
        }
        return null;
    }
    static String resolve(String base,String uri) {
        try {return new URL(new URL(base),uri).toString();} catch(MalformedURLException e){throw new IllegalArgumentException("bad address");}
    }

    /** The real fetcher: HTTPS only, redirects followed only to HTTPS, honest app identity, 15 s timeouts. */
    public static Fetcher https(final String version) { return (url,limit,range)->{
        String at=url;
        for(int hop=0;hop<5;hop++) {
            URL u=new URL(at);
            if(!"https".equals(u.getProtocol())) throw new IOException("not HTTPS");
            HttpURLConnection c=(HttpURLConnection)u.openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent","GrapefruitFAST/"+version+" (Android; channel test)");
            if(range)c.setRequestProperty("Range","bytes=0-"+(limit-1));
            try {
                int status=c.getResponseCode();
                if(status>=300&&status<400&&c.getHeaderField("Location")!=null){at=new URL(u,c.getHeaderField("Location")).toString();continue;}
                if(status>=400) return new Response(status,at,new byte[0]);
                InputStream in=c.getInputStream();
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
                while(out.size()<limit&&(n=in.read(buf,0,Math.min(buf.length,limit-out.size())))!=-1)out.write(buf,0,n);
                in.close();
                return new Response(status,at,out.toByteArray());
            } finally {c.disconnect();}
        }
        throw new IOException("too many redirects");
    };}
}
