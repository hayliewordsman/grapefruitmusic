package com.grapefruitmusic.ui;
import java.io.*;
import java.net.*;
import java.util.*;

/** On Demand file downloads and finite, unencrypted HLS downloads, adapted from TV 3.0.
 * Progressive files resume by HTTP Range. Live HLS playlists are explicitly rejected. */
final class TvFileDownload {
    private TvFileDownload() {}
    interface Progress {
        /** Bytes saved so far, and the file's size when known (0 otherwise). */
        void saved(long bytes,long total);
        /** Whether to stop now (the viewer stopped it, or the download must wait for Wi-Fi). */
        boolean stopped();
    }
    static final class Refused extends IOException { Refused(String why){super(why);} }

    /** Downloads a film or episode; there is deliberately no live-recording API. */
    static void save(String url,String ua,boolean added,File out,int maxHeight,Progress progress) throws IOException {
        if(LivePlayer.hls(url))hls(url,ua,added,out,maxHeight,progress);else file(url,ua,added,out,progress);
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
        if(best==null)throw new Refused("This video keeps its sound separately, which cannot be downloaded yet.");
        return best;
    }
    private static void hls(String url,String ua,boolean added,File out,int maxHeight,Progress progress) throws IOException {
        URL at=new URL(url);Playlist p=parse(text(url,ua,added),at);
        if(p.master()){at=new URL(choose(p,maxHeight).uri);p=parse(text(at.toString(),ua,added),at);}
        if(!p.ended)throw new Refused("Only films and episodes can be downloaded; this playlist is live.");
        if(p.key!=null)throw new Refused("This video's encrypted download format isn't supported.");
        File part=new File(out.getPath()+".part");long saved=0;
        try(OutputStream file=new BufferedOutputStream(new FileOutputStream(part),256*1024)){
            if(p.map!=null)saved+=copy(p.map,ua,added,file);
            for(Segment segment:p.segments){
                if(progress.stopped())return;
                saved+=copy(segment.uri,ua,added,file);progress.saved(saved,0);
            }
        }
        if(!progress.stopped()&&!part.renameTo(out))throw new IOException("Couldn't save the video.");
    }

    private static void file(String url,String ua,boolean added,File out,Progress progress) throws IOException {
        File part=new File(out.getPath()+".part");int failures=0;byte[] buf=new byte[64*1024];
        while(!progress.stopped()){
            long have=part.exists()?part.length():0;HttpURLConnection c=null;
            try{
                c=connect(url,ua,added,have);
                int code=c.getResponseCode();
                if(code==416){String range=c.getHeaderField("Content-Range");if(have>0&&("bytes */"+have).equals(range))break;throw new Refused("The saved partial file no longer matches this video. Delete the download and try again.");} // nothing left to fetch
                boolean resumed=code==206;if(resumed){String range=c.getHeaderField("Content-Range");if(range==null||!range.startsWith("bytes "+have+"-"))throw new Refused("The service returned an invalid download range.");}if(!resumed)have=0;
                long length=contentLength(c);long total=length<0?0:resumed?have+length:length;
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
    private static long copy(String url,String ua,boolean added,OutputStream out) throws IOException {
        HttpURLConnection c=connect(url,ua,added,-1);
        try{
            InputStream in=c.getInputStream();byte[] buf=new byte[64*1024];int n;long total=0;
            while((n=in.read(buf))!=-1){out.write(buf,0,n);total+=n;}
            in.close();return total;
        }finally{c.disconnect();}
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
            c.setRequestProperty("Accept-Encoding","identity");
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
    private static long contentLength(HttpURLConnection c){try{return Long.parseLong(c.getHeaderField("Content-Length"));}catch(Exception e){return -1;}}
    private static long number(String s){try{return (long)Double.parseDouble(s.trim());}catch(Exception e){return 0;}}
    private static void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
