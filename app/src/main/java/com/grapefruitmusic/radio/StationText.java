package com.grapefruitmusic.radio;

import java.util.Locale;

/** What Grapefruit says about an internet radio station, and which ones it can play. */
public final class StationText {
    private StationText() {
    }

    /**
     * Can Android 4.2's player play a stream like this? MP3, AAC and Ogg Vorbis, not HLS (a
     * playlist of short files, which the relay can't pass on) and not Opus or FLAC.
     */
    public static boolean playable(String codec, boolean hls) {
        if (hls) return false;
        String c = codec == null ? "" : codec.trim().toUpperCase(Locale.US);
        return c.equals("MP3") || c.equals("AAC") || c.equals("AAC+") || c.equals("OGG");
    }

    /** "Jazz, Smooth Jazz · United States": up to two of its tags, and where it is. */
    public static String describe(String tags, String country) {
        StringBuilder genres = new StringBuilder();
        int added = 0;
        if (tags != null) {
            for (String raw : tags.split(",")) {
                String tag = raw.trim();
                if (tag.length() == 0 || tag.length() > 24) continue;
                if (added > 0) genres.append(", ");
                genres.append(titleCase(tag));
                if (++added == 2) break;
            }
        }
        String place = country == null ? "" : country.trim();
        if (genres.length() > 0 && place.length() > 0) return genres + " · " + place;
        if (genres.length() > 0) return genres.toString();
        return place.length() > 0 ? place : "Internet Radio";
    }

    /** "smooth jazz" → "Smooth Jazz"; tags already in capitals ("BBC", "80s") stay. */
    static String titleCase(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean start = true;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            out.append(start ? Character.toUpperCase(ch) : ch);
            start = ch == ' ' || ch == '-' || ch == '/' || ch == '&';
        }
        return out.toString();
    }

    /**
     * Now Playing's two lines for a station: the song and "Artist — Station" when the station
     * says what's on ("Artist - Song"), otherwise the station and its description.
     */
    public static String[] nowPlaying(String station, String description, String streamTitle) {
        if (streamTitle == null || streamTitle.trim().length() == 0) {
            return new String[]{station, description == null ? "Live" : description};
        }
        String t = streamTitle.trim();
        int dash = t.indexOf(" - ");
        if (dash > 0 && dash + 3 < t.length()) {
            return new String[]{t.substring(dash + 3).trim(), t.substring(0, dash).trim() + " — " + station};
        }
        return new String[]{t, station};
    }
}
