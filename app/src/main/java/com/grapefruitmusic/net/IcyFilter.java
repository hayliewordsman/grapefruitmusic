package com.grapefruitmusic.net;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

/**
 * Takes the song titles out of a SHOUTcast / Icecast stream. Asked for them ("Icy-MetaData: 1"),
 * a station sends a block of text every icy-metaint bytes of sound: one byte giving its length in
 * 16s, then "StreamTitle='Artist - Song';..." padded with zeros. The sound goes on to the player
 * without them, and each block's title goes to the listener.
 */
public final class IcyFilter {
    public interface Listener {
        /** A block arrived: the title it carries, or null when it has none. */
        void onTitle(String title);
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    private final int interval;
    private final Listener listener;
    /** Sound bytes left before the next block's length byte. */
    private int soundLeft;
    /** The block being read, or null between blocks. */
    private byte[] block;
    private int blockRead;

    public IcyFilter(int interval, Listener listener) {
        if (interval <= 0) throw new IllegalArgumentException("interval " + interval);
        this.interval = interval;
        this.listener = listener;
        soundLeft = interval;
    }

    /** Passes len bytes of the stream on: the sound to sound, the titles to the listener. */
    public void write(byte[] b, int off, int len, OutputStream sound) throws IOException {
        int end = off + len;
        while (off < end) {
            if (block != null) {
                int n = Math.min(block.length - blockRead, end - off);
                System.arraycopy(b, off, block, blockRead, n);
                blockRead += n;
                off += n;
                if (blockRead == block.length) {
                    byte[] done = block;
                    block = null;
                    soundLeft = interval;
                    listener.onTitle(streamTitle(done));
                }
            } else if (soundLeft == 0) {
                int length = (b[off++] & 0xff) * 16;
                if (length == 0) {
                    soundLeft = interval;
                } else {
                    block = new byte[length];
                    blockRead = 0;
                }
            } else {
                int n = Math.min(soundLeft, end - off);
                sound.write(b, off, n);
                off += n;
                soundLeft -= n;
            }
        }
    }

    /** The StreamTitle in a block ("StreamTitle='...';"), or null if it's missing or empty. */
    public static String streamTitle(byte[] block) {
        int len = block.length;
        while (len > 0 && block[len - 1] == 0) len--;
        String text = decode(block, len);
        String key = "StreamTitle='";
        int start = text.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        // Titles can hold apostrophes ("Don't Stop"): the value ends at "';".
        int end = text.indexOf("';", start);
        if (end < 0) end = text.lastIndexOf('\'');
        if (end < start) end = text.length();
        String title = text.substring(start, end).trim();
        return title.length() == 0 ? null : title;
    }

    /** Most stations send UTF-8; older ones Latin-1. */
    private static String decode(byte[] b, int len) {
        try {
            return UTF8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b, 0, len)).toString();
        } catch (CharacterCodingException e) {
            return new String(b, 0, len, LATIN1);
        }
    }
}
