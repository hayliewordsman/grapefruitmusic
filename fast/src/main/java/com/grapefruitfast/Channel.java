package com.grapefruitfast;
import java.util.Locale;
public final class Channel {
    public final String id, name, country, category, description, website, provenance;
    public final String[] streams;
    public Channel(String id, String name, String country, String category, String description,
                   String website, String provenance, String[] streams) {
        this.id=id; this.name=name; this.country=country; this.category=category;
        this.description=description; this.website=website; this.provenance=provenance; this.streams=streams.clone();
    }
    public boolean matches(String query) {
        return (name+" "+country+" "+category+" "+description).toLowerCase(Locale.US)
            .contains(query.trim().toLowerCase(Locale.US));
    }
    public String initials() {
        if (name.startsWith("Global")) return "G";
        if (name.startsWith("CityNews")) return "City";
        if (name.startsWith("NBC")) return "NBC";
        if (name.startsWith("ABC")) return "ABC";
        if (name.startsWith("CBS")) return "CBS";
        String[] words=name.replace("The ", "").split(" ");
        return words[0].substring(0,1)+(words.length>1?words[1].substring(0,1):"");
    }
}
