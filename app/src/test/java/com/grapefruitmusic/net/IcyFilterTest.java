package com.grapefruitmusic.net;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class IcyFilterTest {
    /** A stream with a title block after every `interval` bytes of sound. */
    private static byte[] stream(byte[] sound, int interval, String[] titles) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int block = 0;
        for (int i = 0; i < sound.length; i += interval) {
            out.write(sound, i, Math.min(interval, sound.length - i));
            if (i + interval > sound.length) break;
            String t = block < titles.length ? titles[block] : null;
            block++;
            if (t == null) {
                out.write(0);
                continue;
            }
            byte[] text = t.getBytes("UTF-8");
            int len = (text.length + 15) / 16;
            out.write(len);
            out.write(text);
            for (int p = text.length; p < len * 16; p++) out.write(0);
        }
        return out.toByteArray();
    }

    @Test
    public void takesTitlesOutOfTheSound() throws Exception {
        byte[] sound = new byte[1000];
        for (int i = 0; i < sound.length; i++) sound[i] = (byte) (i * 7);
        byte[] in = stream(sound, 100, new String[]{"StreamTitle='Band - First Song';", null,
                "StreamTitle='Singer - Don't Stop';StreamUrl='';"});
        final List<String> heard = new ArrayList<String>();
        IcyFilter f = new IcyFilter(100, new IcyFilter.Listener() {
            @Override
            public void onTitle(String title) {
                heard.add(title);
            }
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // In awkward pieces, as the network delivers them.
        int[] sizes = {1, 7, 99, 100, 101, 3, 250, 13};
        int off = 0;
        for (int i = 0; off < in.length; i++) {
            int n = Math.min(sizes[i % sizes.length], in.length - off);
            f.write(in, off, n, out);
            off += n;
        }
        assertArrayEquals(sound, out.toByteArray());
        assertEquals("Band - First Song", heard.get(0));
        assertEquals("Singer - Don't Stop", heard.get(1));
        assertEquals(2, heard.size());
    }

    @Test
    public void readsTitlesInEitherEncoding() throws Exception {
        assertEquals("Café del Mar - Ñu", IcyFilter.streamTitle("StreamTitle='Café del Mar - Ñu';\0\0".getBytes("UTF-8")));
        assertEquals("Café", IcyFilter.streamTitle("StreamTitle='Café';".getBytes("ISO-8859-1")));
        assertNull(IcyFilter.streamTitle("StreamTitle='';".getBytes("UTF-8")));
        assertNull(IcyFilter.streamTitle("StreamUrl='http://x';".getBytes("UTF-8")));
        assertEquals("No end", IcyFilter.streamTitle("StreamTitle='No end".getBytes("UTF-8")));
    }
}
