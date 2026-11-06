package com.grapefruitmusic.source;

import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.lyrics.Lyrics;
import java.io.IOException;
import java.util.*;

/** One device library combining MediaStore music and the private bonus EP. Original source IDs
 * remain on every track/album, so browsing never exports private music or merges purchased copies. */
public final class OnDeviceSource implements MusicSource {
    private final MusicSource local, included;
    public OnDeviceSource(MusicSource local, MusicSource included) { this.local=local;this.included=included; }
    public String id(){return LocalSource.ID;}
    public String name(){return "On This Device";}
    public String typeLabel(){return "Device music and included music";}
    public boolean isRemote(){return false;}
    public boolean canDownload(){return false;}
    public boolean canEditPlaylists(){return local.canEditPlaylists();}
    private List<MediaItem> local(int kind) throws IOException {
        try { return kind==MediaItem.TRACK?local.songs():kind==MediaItem.ALBUM?local.albums():kind==MediaItem.ARTIST?local.artists():local.playlists(); }
        catch(SecurityException e){return new ArrayList<MediaItem>();}
        catch(IOException e){return new ArrayList<MediaItem>();}
    }
    private static String artistKey(String name){return name==null?"":name.trim().toLowerCase(Locale.US);}
    private static List<MediaItem> alphabetical(List<MediaItem> items){
        Collections.sort(items,(a,b)->String.CASE_INSENSITIVE_ORDER.compare(a.title==null?"":a.title,b.title==null?"":b.title));return items;
    }
    public List<MediaItem> songs() throws IOException {List<MediaItem> all=new ArrayList<MediaItem>(local(MediaItem.TRACK));all.addAll(included.songs());return alphabetical(all);}
    public List<MediaItem> albums() throws IOException {List<MediaItem> all=new ArrayList<MediaItem>(local(MediaItem.ALBUM));all.addAll(included.albums());return alphabetical(all);}
    public List<MediaItem> playlists() throws IOException {return local(MediaItem.PLAYLIST);}
    public List<MediaItem> artists() throws IOException {
        Map<String,MediaItem> merged=new LinkedHashMap<String,MediaItem>();
        List<MediaItem> all=new ArrayList<MediaItem>(local(MediaItem.ARTIST));all.addAll(included.artists());
        for(MediaItem original:all){String key=artistKey(original.title);MediaItem a=merged.get(key);
            if(a==null){a=new MediaItem(MediaItem.ARTIST,id(),"device-artist:"+key,original.title);a.artKey=original.artKey;merged.put(key,a);}
            a.count+=original.count;
        }
        return alphabetical(new ArrayList<MediaItem>(merged.values()));
    }
    public List<MediaItem> artistAlbums(MediaItem artist) throws IOException {
        if(!artist.id.startsWith("device-artist:"))return local.artistAlbums(artist);
        String key=artist.id.substring("device-artist:".length());List<MediaItem> found=new ArrayList<MediaItem>();
        for(MediaItem a:local(MediaItem.ARTIST))if(artistKey(a.title).equals(key))try{found.addAll(local.artistAlbums(a));}catch(SecurityException|IOException e){/* private EP remains browsable */}
        for(MediaItem a:included.artists())if(artistKey(a.title).equals(key))found.addAll(included.artistAlbums(a));
        return alphabetical(found);
    }
    public List<MediaItem> recentAlbums(int limit) throws IOException {
        List<MediaItem> all=albums();Collections.sort(all,(a,b)->Long.compare(b.added,a.added));
        return new ArrayList<MediaItem>(all.subList(0,Math.max(0,Math.min(limit,all.size()))));
    }
    public List<MediaItem> tracks(MediaItem container) throws IOException {return BundledSource.ID.equals(container.sourceId)?included.tracks(container):local.tracks(container);}
    public List<MediaItem> search(String query) throws IOException {
        String needle=query.toLowerCase(Locale.US);List<MediaItem> all=new ArrayList<MediaItem>();
        for(List<MediaItem> list:Arrays.asList(artists(),albums(),songs()))for(MediaItem m:list)
            if(((m.title==null?"":m.title)+" "+(m.artist==null?"":m.artist)+" "+(m.album==null?"":m.album)).toLowerCase(Locale.US).contains(needle))all.add(m);
        return all;
    }
    public String artworkUrl(String key,int px){return local.artworkUrl(key,px);}
    public StreamRequest stream(MediaItem track,int quality){return BundledSource.owns(track)?included.stream(track,quality):local.stream(track,quality);}
    public StreamRequest download(MediaItem track,int quality){return null;}
    public Lyrics lyrics(MediaItem track) throws IOException {return BundledSource.owns(track)?null:local.lyrics(track);}
    private static void sharedOnly(List<MediaItem> tracks) throws IOException {for(MediaItem t:tracks)if(BundledSource.owns(t))throw new IOException("Included bonus tracks stay in Blue Grapefruit's private library.");}
    public void addToPlaylist(MediaItem p,List<MediaItem> t) throws IOException {sharedOnly(t);local.addToPlaylist(p,t);}
    public void createPlaylist(String n,List<MediaItem> t) throws IOException {sharedOnly(t);local.createPlaylist(n,t);}
    public void deletePlaylist(MediaItem p) throws IOException {local.deletePlaylist(p);}
    public boolean editPlaylist(MediaItem p,String n,String d,List<MediaItem> r) throws IOException {return local.editPlaylist(p,n,d,r);}
}
