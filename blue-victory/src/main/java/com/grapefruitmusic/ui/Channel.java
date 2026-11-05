package com.grapefruitmusic.ui;
import java.util.Locale;
public final class Channel {
    /**
     * What a Channel plays: a live channel, a film (Movies, or an IPTV service's films), a series (opens its
     * episodes), an episode, or a recording or download kept on this device. Everything that plays is a
     * Channel, so the watch page, the floating player and the one-stream rule treat them all alike.
     */
    public static final String LIVE="live", FILM="film", SERIES="series", EPISODE="episode";
    public String kind=LIVE;
    /** A poster or cover picture (films, series), or "". */
    public String poster="";
    public int year, seconds;
    /** The list row's second line, when it isn't the region and genre (recordings, episodes), or null. */
    public String meta;
    /** The programme guide's ID for this channel (an M3U's tvg-id), or "". */
    public String guideId="";
    /** Plays live (no seeking, no end). */
    public boolean live(){return LIVE.equals(kind);}
    /** A film, an episode or a recording: it has a beginning, an end, and a place to resume from. */
    public boolean onDemand(){return FILM.equals(kind)||EPISODE.equals(kind);}
    /** Kept on this device (a recording, or a finished download): plays with no connection. */
    public boolean local(){return streams.length>0&&streams[0].startsWith("file:");}
    /** News channels get hour-long blocks in the guide when it has no programme information. */
    public boolean news(){
        if("News".equals(category))return true;
        String words=(name+" "+description).toLowerCase(Locale.US);
        return words.contains("news")||words.contains("headlines");
    }
    /** Catalog country codes and the regions the filters show. */
    public static final String[] COUNTRIES={"CA","US","GB","DE","AU"};
    public static final String[] REGIONS={"Canada","United States","UK","Europe","Australia"};
    public final String id, name, country, category, description, website, provenance, availability;
    /** For a channel from one of the viewer's own sources: the source's name, and a user agent it asks for. */
    public final String source, userAgent;
    public final String[] streams;
    public Channel(String id, String name, String country, String category, String description,
                   String website, String provenance, String[] streams, String availability) {
        this(id,name,country,category,description,website,provenance,streams,availability,"","");
    }
    public Channel(String id, String name, String country, String category, String description,
                   String website, String provenance, String[] streams, String availability,
                   String source, String userAgent) {
        this.id=id; this.name=name; this.country=country; this.category=category;
        this.description=description; this.website=website; this.provenance=provenance; this.streams=streams.clone();
        this.availability=availability; this.source=source==null?"":source; this.userAgent=userAgent==null?"":userAgent;
    }
    /** From one of the viewer's own sources (Settings › Your Sources), not the built-in catalog. */
    public boolean added() { return !source.isEmpty(); }
    /** The filter region this channel is from: Canada, United States, UK, Europe (outside the UK) or Australia. */
    public String region() {
        for(int i=0;i<COUNTRIES.length;i++) if(COUNTRIES[i].equals(country)) return REGIONS[i];
        return country;
    }
    /** The country, in words, for a channel's details. */
    public String countryName() {
        switch(country){case "CA":return "Canada";case "US":return "United States";case "GB":return "United Kingdom";
            case "DE":return "Germany";case "AU":return "Australia";default:return country;}
    }
    public boolean matches(String query) {
        return (name+" "+countryName()+" "+region()+" "+category+" "+description).toLowerCase(Locale.US)
            .contains(query.trim().toLowerCase(Locale.US));
    }
    public String initials() {
        if (name.trim().isEmpty()) return "TV";
        if (name.startsWith("Global")) return "G";
        if (name.startsWith("CityNews")) return "City";
        if (name.startsWith("NBC")) return "NBC";
        if (name.startsWith("ABC")) return "ABC";
        if (name.startsWith("CBS")) return "CBS";
        if (name.startsWith("CBC")) return "CBC";
        if (name.startsWith("BBC")) return "BBC";
        if (name.startsWith("Sky")) return "Sky";
        if (name.startsWith("DW")) return "DW";
        if (name.equals("CPAC")) return "CPAC";
        String[] words=name.replace("The ", "").split(" ");
        return (words[0].substring(0,1)+(words.length>1&&!words[1].isEmpty()?words[1].substring(0,1):"")).toUpperCase(Locale.US);
    }
}
