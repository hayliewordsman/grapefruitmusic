package com.grapefruitfast;
import java.util.Locale;
public final class Channel {
    /** Catalog country codes and the regions the filters show. */
    public static final String[] COUNTRIES={"CA","US","GB","DE","AU"};
    public static final String[] REGIONS={"Canada","United States","UK","Europe","Australia"};
    public final String id, name, country, category, description, website, provenance, availability;
    public final String[] streams;
    public Channel(String id, String name, String country, String category, String description,
                   String website, String provenance, String[] streams, String availability) {
        this.id=id; this.name=name; this.country=country; this.category=category;
        this.description=description; this.website=website; this.provenance=provenance; this.streams=streams.clone();
        this.availability=availability;
    }
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
        if (name.startsWith("Global")) return "G";
        if (name.startsWith("CityNews")) return "City";
        if (name.startsWith("NBC")) return "NBC";
        if (name.startsWith("ABC")) return "ABC";
        if (name.startsWith("CBS")) return "CBS";
        if (name.startsWith("CBC")) return "CBC";
        if (name.startsWith("BBC")) return "BBC";
        if (name.startsWith("Sky")) return "Sky";
        if (name.startsWith("DW")) return "DW";
        String[] words=name.replace("The ", "").split(" ");
        return words[0].substring(0,1)+(words.length>1?words[1].substring(0,1):"");
    }
}
