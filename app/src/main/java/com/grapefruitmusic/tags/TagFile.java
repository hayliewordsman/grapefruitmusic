package com.grapefruitmusic.tags;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Reads and writes the tags in a song file: MP3, M4A (AAC or ALAC) and FLAC. */
public final class TagFile {
    private static final int MP3 = 1;
    private static final int MP4 = 2;
    private static final int FLAC = 3;

    private TagFile() {
    }

    /** Can Grapefruit change this file's tags? (By its name; reading it says for sure.) */
    public static boolean supports(String path) {
        if (path == null) return false;
        String p = path.toLowerCase(Locale.US);
        return p.endsWith(".mp3") || p.endsWith(".m4a") || p.endsWith(".m4b") || p.endsWith(".mp4")
                || p.endsWith(".flac");
    }

    public static Tags read(File f) throws IOException {
        switch (kind(f)) {
            case MP4:
                return Mp4.read(f);
            case FLAC:
                return Flac.read(f);
            default:
                return Id3.read(f);
        }
    }

    /**
     * Writes tags into the file. The picture is changed (set, or removed when t.picture is null)
     * only when picture is true; otherwise the file keeps the one it has.
     */
    public static void write(File f, Tags t, boolean picture) throws IOException {
        if (!f.canWrite()) throw new IOException("Grapefruit isn't allowed to change " + f.getName());
        switch (kind(f)) {
            case MP4:
                Mp4.write(f, t, picture);
                break;
            case FLAC:
                Flac.write(f, t, picture);
                break;
            default:
                Id3.write(f, t, picture);
        }
    }

    private static int kind(File f) throws IOException {
        byte[] head = new byte[12];
        InputStream in = new FileInputStream(f);
        try {
            int n = 0;
            while (n < head.length) {
                int r = in.read(head, n, head.length - n);
                if (r < 0) break;
                n += r;
            }
            if (n < 4) throw new IOException(f.getName() + " is empty");
        } finally {
            in.close();
        }
        if (Mp4.starts(head)) return MP4;
        if (Flac.starts(head)) return FLAC;
        String name = f.getName().toLowerCase(Locale.US);
        // A FLAC file with an ID3 tag in front still goes to the FLAC writer.
        if (name.endsWith(".flac")) return FLAC;
        if (Id3.starts(head) || name.endsWith(".mp3")) return MP3;
        throw new IOException("Grapefruit can't change tags in this kind of file yet");
    }
}
