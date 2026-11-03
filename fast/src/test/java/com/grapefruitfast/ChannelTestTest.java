package com.grapefruitfast;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class ChannelTestTest {
    private static final String MASTER="#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow/index.m3u8\n";
    private static final String MEDIA="#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nseg1.ts\n";
    /** Answers from a table: URL -> status and body. */
    private static ChannelTest.Fetcher fake(final Map<String,Object[]> table) {
        return (url,limit,range)->{Object[] r=table.get(url);if(r==null)throw new java.io.IOException("no route");
            return new ChannelTest.Response((Integer)r[0],url,((String)r[1]).getBytes("UTF-8"));};
    }
    private static Channel channel(String... streams){return new Channel("c","C","US","News","","https://x","https://x",streams,"United States (Xumo)");}
    private static Map<String,Object[]> table(Object... rows){Map<String,Object[]> m=new HashMap<>();for(int i=0;i<rows.length;i+=3)m.put((String)rows[i],new Object[]{rows[i+1],rows[i+2]});return m;}

    @Test public void playsWhenMasterRenditionAndVideoAnswer() {
        ChannelTest t=new ChannelTest(fake(table("https://a/master.m3u8",200,MASTER,"https://a/low/index.m3u8",200,MEDIA,"https://a/low/seg1.ts",206,"VIDEO")));
        assertEquals(ChannelTest.Result.PLAYS,t.test(channel("https://a/master.m3u8")).result);
    }
    @Test public void aRegionBlockOnTheVideoIsARefusal() {
        ChannelTest.Outcome o=new ChannelTest(fake(table("https://a/master.m3u8",200,MASTER,"https://a/low/index.m3u8",200,MEDIA,"https://a/low/seg1.ts",403,""))).test(channel("https://a/master.m3u8"));
        assertEquals(ChannelTest.Result.REFUSED,o.result);assertEquals("HTTP 403 (video)",o.detail);
    }
    @Test public void serverFaultsAndEndedStreamsAreFailuresNotRefusals() {
        assertEquals(ChannelTest.Result.FAILED,new ChannelTest(fake(table("https://a/m.m3u8",503,""))).test(channel("https://a/m.m3u8")).result);
        assertEquals(ChannelTest.Result.FAILED,new ChannelTest(fake(table("https://a/m.m3u8",200,MEDIA+"#EXT-X-ENDLIST\n"))).test(channel("https://a/m.m3u8")).result);
        assertEquals(ChannelTest.Result.FAILED,new ChannelTest(fake(table())).test(channel("https://a/m.m3u8")).result);
    }
    @Test public void aChannelPlaysIfAnyOfItsSourcesDoes() {
        ChannelTest t=new ChannelTest(fake(table("https://a/m.m3u8",403,"","https://b/m.m3u8",200,MEDIA,"https://b/seg1.ts",200,"VIDEO")));
        assertEquals(ChannelTest.Result.PLAYS,t.test(channel("https://a/m.m3u8","https://b/m.m3u8")).result);
    }
    @Test public void unlockedChannelsAreOfferedOutsideTheUs() {
        Channel xumo=channel("https://a/m.m3u8");
        assertFalse(Region.offered(xumo,"CA"));
        assertTrue(Region.offered(xumo,"CA",new HashSet<>(Collections.singletonList("c"))));
        assertEquals("unlocked_CA",Region.unlockedKey("CA"));
    }
}
