package com.grapefruitmusic.tags;

/** A song's tags, as the tag editor shows them. Empty text and 0 mean "none". */
public final class Tags {
    public String title = "";
    public String artist = "";
    public String album = "";
    public String albumArtist = "";
    public String genre = "";
    public int year;
    public int track;
    public int trackTotal;
    public int disc;
    public int discTotal;
    /** The front cover (JPEG or PNG), or null for none. */
    public byte[] picture;

    /** "image/jpeg" or "image/png" for picture, from its first bytes. */
    public static String mimeOf(byte[] image) {
        if (image != null && image.length > 8 && (image[0] & 0xff) == 0x89 && image[1] == 'P' && image[2] == 'N'
                && image[3] == 'G') {
            return "image/png";
        }
        return "image/jpeg";
    }

    /** A picture's width and height in pixels (PNG or JPEG), or {0, 0} if they can't be read. */
    public static int[] sizeOf(byte[] b) {
        if (b == null) return new int[]{0, 0};
        if ("image/png".equals(mimeOf(b)) && b.length >= 24) {
            return new int[]{be32(b, 16), be32(b, 20)};
        }
        // JPEG: the frame header (SOF0 to SOF15, but not DHT, JPG or DAC) has the size.
        int i = 2;
        while (i + 9 < b.length) {
            if ((b[i] & 0xff) != 0xff) {
                i++;
                continue;
            }
            int marker = b[i + 1] & 0xff;
            if (marker == 0xff) {
                i++;
                continue;
            }
            int len = ((b[i + 2] & 0xff) << 8) | (b[i + 3] & 0xff);
            if (marker >= 0xc0 && marker <= 0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc) {
                int h = ((b[i + 5] & 0xff) << 8) | (b[i + 6] & 0xff);
                int w = ((b[i + 7] & 0xff) << 8) | (b[i + 8] & 0xff);
                return new int[]{w, h};
            }
            i += 2 + len;
        }
        return new int[]{0, 0};
    }

    /** The number at the start of "3/12" (3), or 0. */
    static int number(String s) {
        if (s == null) return 0;
        s = s.trim();
        int end = 0;
        while (end < s.length() && Character.isDigit(s.charAt(end))) end++;
        if (end == 0 || end > 9) return 0;
        return Integer.parseInt(s.substring(0, end));
    }

    /** The number after the slash in "3/12" (12), or 0. */
    static int total(String s) {
        if (s == null) return 0;
        int slash = s.indexOf('/');
        return slash < 0 ? 0 : number(s.substring(slash + 1));
    }

    /** "3/12", "3" or "" for a number and total. */
    static String numberText(int n, int total) {
        if (n <= 0) return "";
        return total > 0 ? n + "/" + total : String.valueOf(n);
    }

    /** The year in "2019-05-01" or "2019" (2019), or 0. */
    static int year(String s) {
        int y = number(s);
        return y >= 1000 && y <= 9999 ? y : 0;
    }

    static int be32(byte[] b, int at) {
        return ((b[at] & 0xff) << 24) | ((b[at + 1] & 0xff) << 16) | ((b[at + 2] & 0xff) << 8) | (b[at + 3] & 0xff);
    }

    static boolean empty(String s) {
        return s == null || s.trim().length() == 0;
    }
}
