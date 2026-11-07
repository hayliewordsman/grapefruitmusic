package com.grapefruitmusic.ui;
import com.grapefruitmusic.R;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Saves a stream to a file, as it plays: the DVR's recordings and On Demand's downloads.
 *
 * - HLS (.m3u8, as free channels send): picks one variant (up to the quality chosen), then fetches its
 *   playlist again and again, appending each new segment, until the recording's end (or, for a film,
 *   the playlist's end). MPEG-TS segments joined end to end play as one file.
 * - A direct stream (MPEG-TS, as most IPTV services send live) is copied as it arrives.
 * - A file (a film or an episode) downloads, resuming where a dropped connection left off.
 *
 * Streams that are encrypted or keep their sound separately aren't recorded: the first is protected,
 * the second can't be joined into one file here. Nothing is decrypted or got around.
 */
final class TvRecorder {
    private TvRecorder() {}
    interface Progress {
        /** Bytes saved so far, and the file's size when known (0 otherwise). */
        void saved(long bytes,long total);
        /** Whether to stop now (the viewer stopped it, or the download must wait for Wi-Fi). */
        boolean stopped();
    }
    static final class Refused extends IOException { Refused(String why){super(why);} }

    /** Records url into out until the time until (Long.MAX_VALUE: to the end, for films and episodes). */
    static void save(String url,String userAgent,boolean added,File out,long until,int maxHeight,Progress p) throws IOException {
        if(LivePlayer.hls(url))hls(url,userAgent,added,out,until,maxHeight,p);
        else if(until==Long.MAX_VALUE)file(url,userAgent,added,out,p);
        else stream(url,userAgent,added,out,until,p);
    }

    // ---- HLS ----

    static final class Segment { final long sequence;final String uri;final double seconds; Segment(long s,String u,double d){sequence=s;uri=u;seconds=d;} }
    static final class Variant { final long bandwidth;final int height;final String uri,audio; Variant(long b,int h,String u,String a){bandwidth=b;height=h;uri=u;audio=a;} }
    static final class Playlist {
        final List<Variant> variants=new ArrayList<>();final List<Segment> segments=new ArrayList<>();
        /** Audio groups with their own playlists (sound kept apart from the picture). */
        final Set<String> separateAudio=new HashSet<>();
        double target=6;boolean ended;String map,key;
        boolean master(){return !variants.isEmpty();}
    }
    static Playlist parse(String text,URL base) throws IOException {
        Playlist p=new Playlist();long sequence=0;double duration=0;
        String[] lines=text.split("\r?\n");
        if(lines.length==0||!lines[0].trim().startsWith("#EXTM3U"))throw new IOException("That isn't an HLS playlist.");
        for(int i=0;i<lines.length;i++){
            String line=lines[i].trim();
            if(line.startsWith("#EXT-X-STREAM-INF:")){
                String uri=null;
                while(++i<lines.length){String next=lines[i].trim();if(!next.isEmpty()&&!next.startsWith("#")){uri=next;break;}}
                if(uri==null)break;
                long bw=number(attr(line,"BANDWIDTH"));
                String res=attr(line,"RESOLUTION");int h=res.contains("x")?(int)number(res.substring(res.indexOf('x')+1)):0;
                p.variants.add(new Variant(bw,h,new URL(base,uri).toString(),attr(line,"AUDIO")));
            } else if(line.startsWith("#EXT-X-MEDIA:")&&"AUDIO".equals(attr(line,"TYPE"))&&!attr(line,"URI").isEmpty()){
                p.separateAudio.add(attr(line,"GROUP-ID"));
            } else if(line.startsWith("#EXT-X-MEDIA-SEQUENCE:")){
                sequence=number(line.substring(22));
            } else if(line.startsWith("#EXT-X-TARGETDURATION:")){
                p.target=Math.max(1,number(line.substring(22)));
            } else if(line.startsWith("#EXT-X-KEY:")){
                String method=attr(line,"METHOD");if(!method.isEmpty()&&!"NONE".equals(method))p.key=method;
            } else if(line.startsWith("#EXT-X-MAP:")){
                p.map=new URL(base,attr(line,"URI")).toString();
            } else if(line.startsWith("#EXTINF:")){
                String d=line.substring(8);int comma=d.indexOf(',');
                try{duration=Double.parseDouble((comma>=0?d.substring(0,comma):d).trim());}catch(NumberFormatException e){duration=p.target;}
            } else if(line.startsWith("#EXT-X-ENDLIST")){
                p.ended=true;
            } else if(!line.isEmpty()&&!line.startsWith("#")){
                p.segments.add(new Segment(sequence++,new URL(base,line).toString(),duration));
            }
        }
        return p;
    }
    /** The variant to save: the best up to maxHeight (0: any) whose sound comes with its picture. */
    static Variant choose(Playlist master,int maxHeight) throws Refused {
        Variant best=null,lowest=null;
        for(Variant v:master.variants){
            if(!v.audio.isEmpty()&&master.separateAudio.contains(v.audio))continue;
            if(lowest==null||v.bandwidth<lowest.bandwidth)lowest=v;
            if((maxHeight==0||v.height==0||v.height<=maxHeight)&&(best==null||v.bandwidth>best.bandwidth))best=v;
        }
        if(best==null)best=lowest;
        if(best==null)throw new Refused("This channel's stream keeps its sound separately, which can't be recorded yet.");
        return best;
    }
    private static void hls(String url,String ua,boolean added,File out,long until,int maxHeight,Progress progress) throws IOException {
        URL at=new URL(url);
        Playlist p=parse(text(at.toString(),ua,added),at);
        if(p.master()){at=new URL(choose(p,maxHeight).uri);p=parse(text(at.toString(),ua,added),at);}
        long saved=out.exists()?out.length():0,last=-1;int failures=0;boolean wroteMap=saved>0;
        OutputStream file=new BufferedOutputStream(new FileOutputStream(out,true),256*1024);
        try{
            while(!progress.stopped()&&System.currentTimeMillis()<until){
                if(p.key!=null)throw new Refused("This channel's stream is encrypted, so it can't be recorded.");
                if(p.map!=null&&!wroteMap){saved+=copy(p.map,ua,added,file,out.getParentFile());wroteMap=true;}
                boolean any=false,retry=false;
                // A live recording starts at the live edge, as watching would, not minutes back in the playlist.
                if(last<0&&!p.ended&&p.segments.size()>3)last=p.segments.get(p.segments.size()-4).sequence;
                for(Segment s:p.segments){
                    if(s.sequence<=last)continue;
                    if(progress.stopped()||System.currentTimeMillis()>=until)break;
                    try{saved+=copy(s.uri,ua,added,file,out.getParentFile());failures=0;}catch(Refused r){throw r;}catch(IOException e){if(++failures>8)throw e;retry=true;break;}
                    last=s.sequence;any=true;file.flush();progress.saved(saved,0);
                }
                if(retry){sleep(1000);continue;}
                if(p.ended&&!any)break;
                if(p.ended&&until==Long.MAX_VALUE)break;
                if(!any)sleep((long)(p.target*500));
                try{p=parse(text(at.toString(),ua,added),at);failures=0;}
                catch(IOException e){if(++failures>12)throw new IOException("The stream stopped answering.");sleep(2000);}
            }
        }finally{file.close();}
    }

    // ---- A direct stream (live), and a file (films, episodes). ----

    private static void stream(String url,String ua,boolean added,File out,long until,Progress progress) throws IOException {
        long saved=out.exists()?out.length():0;int failures=0;byte[] buf=new byte[64*1024];
        while(!progress.stopped()&&System.currentTimeMillis()<until){
            HttpURLConnection c=null;
            try{
                c=connect(url,ua,added,-1);
                InputStream in=c.getInputStream();OutputStream file=new FileOutputStream(out,true);
                try{
                    int n;long flushed=saved;
                    while(!progress.stopped()&&System.currentTimeMillis()<until&&(n=in.read(buf))!=-1){
                        file.write(buf,0,n);saved+=n;failures=0;
                        if(saved-flushed>1024*1024){flushed=saved;progress.saved(saved,0);}
                    }
                }finally{file.close();in.close();}
                progress.saved(saved,0);
            }catch(Refused r){throw r;}
            catch(IOException e){if(++failures>12)throw new IOException("The stream stopped answering.");sleep(3000);}
            finally{if(c!=null)c.disconnect();}
            if(System.currentTimeMillis()<until&&!progress.stopped())sleep(1000); // the stream ended early: reconnect
        }
    }
    private static void file(String url,String ua,boolean added,File out,Progress progress) throws IOException {
        File part=new File(out.getPath()+".part");int failures=0;byte[] buf=new byte[64*1024];
        while(!progress.stopped()){
            long have=part.exists()?part.length():0;HttpURLConnection c=null;
            try{
                c=connect(url,ua,added,have);
                int code=c.getResponseCode();
                if(code==416){if(have>0)break;throw new IOException("The service answered HTTP 416.");} // nothing left to fetch
                boolean resumed=code==206;if(!resumed)have=0;
                long total=resumed?have+c.getContentLength():c.getContentLength();
                InputStream in=c.getInputStream();OutputStream file=new FileOutputStream(part,resumed);
                try{
                    int n;long saved=have,shown=have;
                    while(!progress.stopped()&&(n=in.read(buf))!=-1){
                        file.write(buf,0,n);saved+=n;failures=0;
                        if(saved-shown>512*1024){shown=saved;progress.saved(saved,Math.max(0,total));}
                    }
                    progress.saved(saved,Math.max(0,total));
                    if(progress.stopped())return;
                    if(total<=0||saved>=total)break;
                }finally{file.close();in.close();}
            }catch(Refused r){throw r;}
            catch(IOException e){if(++failures>10)throw e;sleep(Math.min(30000,2000L*failures));}
            finally{if(c!=null)c.disconnect();}
        }
        if(progress.stopped())return;
        if(!part.renameTo(out))throw new IOException("Couldn't save the file.");
    }

    // ---- HTTP ----

    private static String text(String url,String ua,boolean added) throws IOException {
        return new String(UserSources.fetch(url,ua.isEmpty()?LivePlayer.USER_AGENT:ua),"UTF-8");
    }
    private static long copy(String url,String ua,boolean added,OutputStream out,File directory) throws IOException {
        HttpURLConnection c=connect(url,ua,added,-1);
        File pending=File.createTempFile("dvr-segment-",".part",directory);
        try{
            byte[] buf=new byte[64*1024];int n;long total=0;
            try(InputStream in=c.getInputStream();OutputStream file=new FileOutputStream(pending)){while((n=in.read(buf))!=-1){total+=n;if(total>32L*1024*1024)throw new IOException("A recording segment is too large.");file.write(buf,0,n);}}
            String length=c.getHeaderField("Content-Length");if(length!=null&&number(length)!=total)throw new EOFException("The recording segment was interrupted.");
            try(InputStream in=new FileInputStream(pending)){while((n=in.read(buf))!=-1)out.write(buf,0,n);}return total;
        }finally{c.disconnect();pending.delete();}
    }
    /** A GET (from a byte, for resuming), following redirects; built-in channels stay on HTTPS. */
    static HttpURLConnection connect(String url,String ua,boolean added,long from) throws IOException {
        String at=url;
        for(int hop=0;hop<5;hop++){
            URL u=new URL(at);
            if(!added&&!"https".equals(u.getProtocol())&&!"file".equals(u.getProtocol()))throw new IOException("Built-in channels use HTTPS.");
            HttpURLConnection c=(HttpURLConnection)u.openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent",ua.isEmpty()?LivePlayer.USER_AGENT:ua);
            if(from>0)c.setRequestProperty("Range","bytes="+from+"-");
            int code=c.getResponseCode();
            if(code>=300&&code<400&&c.getHeaderField("Location")!=null){at=new URL(u,c.getHeaderField("Location")).toString();c.disconnect();continue;}
            if(code==401||code==403||code==451){c.disconnect();throw new Refused("The service refused it (HTTP "+code+").");}
            if(code>=400&&code!=416){c.disconnect();throw new IOException("The service answered HTTP "+code+".");}
            return c;
        }
        throw new IOException("Too many redirects.");
    }
    private static String attr(String line,String key){
        int at=line.indexOf(key+"=");
        while(at>0&&line.charAt(at-1)!=','&&line.charAt(at-1)!=':')at=line.indexOf(key+"=",at+1);
        if(at<0)return "";
        int start=at+key.length()+1;
        if(start<line.length()&&line.charAt(start)=='"'){int end=line.indexOf('"',start+1);return end>start?line.substring(start+1,end):"";}
        int end=line.indexOf(',',start);return (end<0?line.substring(start):line.substring(start,end)).trim();
    }
    private static long number(String s){try{return (long)Double.parseDouble(s.trim());}catch(Exception e){return 0;}}
    private static void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
