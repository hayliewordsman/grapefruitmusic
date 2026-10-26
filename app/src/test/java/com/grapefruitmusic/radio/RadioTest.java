package com.grapefruitmusic.radio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class RadioTest {
    @Test
    public void formatsFrequencies() {
        assertEquals("98.7", Radio.format(9870));
        assertEquals("87.5", Radio.format(8750));
        assertEquals("108.0", Radio.format(10800));
        assertEquals("101.1", Radio.format(10110));
        // Between tenths (50 kHz steps).
        assertEquals("98.75", Radio.format(9875));
    }

    @Test
    public void snapsAndClampsToTheBand() {
        assertEquals(9870, Radio.snap(9874));
        assertEquals(9880, Radio.snap(9876));
        assertEquals(Radio.LOWEST, Radio.clamp(8000));
        assertEquals(Radio.HIGHEST, Radio.clamp(11000));
        assertTrue(Radio.inBand(9870));
        assertFalse(Radio.inBand(7600));
        assertFalse(Radio.inBand(10810));
    }

    @Test
    public void readsChipFrequenciesInAnyUnit() {
        assertEquals(9870, MediatekTuner.fromChip(98.7f));
        assertEquals(8750, MediatekTuner.fromChip(87.5f));
        assertEquals(9870, MediatekTuner.fromChip(987f));
        assertEquals(9870, MediatekTuner.fromChip(9870f));
    }

    @Test
    public void scanResultsBecomeStationsInOrder() {
        // 100 kHz units, out of order, repeated, with a blank and one out of the band.
        assertArrayEquals(new int[]{8750, 9870, 10110},
                MediatekTuner.stations(new short[]{987, 875, 1011, 987, 0, 760}));
        // 10 kHz units.
        assertArrayEquals(new int[]{9870, 10110}, MediatekTuner.stations(new short[]{9870, 10110}));
        assertEquals(0, MediatekTuner.stations(new short[0]).length);
    }

    @Test
    public void cleansRdsText() {
        assertEquals("KISS FM", MediatekTuner.text("KISS FM \0\0\0".getBytes()));
        assertEquals("Now: Adele - Hello", MediatekTuner.text("Now:  Adele\r- Hello   ".getBytes()));
        assertEquals("", MediatekTuner.text(new byte[8]));
    }

    @Test
    public void presetsRoundTrip() {
        List<Radio.Preset> list = new ArrayList<Radio.Preset>();
        list.add(new Radio.Preset(9870, "GRAPE FM"));
        list.add(new Radio.Preset(10110, null));
        List<Radio.Preset> back = Radio.parsePresets(Radio.serializePresets(list));
        assertEquals(2, back.size());
        assertEquals(9870, back.get(0).frequency);
        assertEquals("GRAPE FM", back.get(0).name);
        assertEquals("GRAPE FM", back.get(0).label());
        assertEquals(10110, back.get(1).frequency);
        assertNull(back.get(1).name);
        assertEquals("101.1 FM", back.get(1).label());
    }

    @Test
    public void namesKeepTheirCharactersButNotLineBreaks() {
        List<Radio.Preset> list = new ArrayList<Radio.Preset>();
        list.add(new Radio.Preset(9870, "Rock\tand\nRoll 98.7"));
        List<Radio.Preset> back = Radio.parsePresets(Radio.serializePresets(list));
        assertEquals(1, back.size());
        assertEquals("Rock and Roll 98.7", back.get(0).name);
    }

    @Test
    public void unreadablePresetsAreSkipped() {
        assertEquals(0, Radio.parsePresets("not a preset").size());
        assertEquals(0, Radio.parsePresets(null).size());
        // Out of the band, or the same station twice: dropped.
        assertEquals(0, Radio.parsePresets("500").size());
        List<Radio.Preset> some = Radio.parsePresets("garbage\n9870\tOne\n9870\tTwo\n10110");
        assertEquals(2, some.size());
        assertEquals("One", some.get(0).name);
        assertEquals(10110, some.get(1).frequency);
    }

    @Test
    public void namesProgrammeTypesByRegion() {
        assertEquals("Top 40", Radio.programTypeName(9, "US"));
        assertEquals("Pop Music", Radio.programTypeName(10, "GB"));
        assertEquals("Country", Radio.programTypeName(10, "CA"));
        assertNull(Radio.programTypeName(0, "US"));
        assertNull(Radio.programTypeName(27, "US"));
        assertNull(Radio.programTypeName(40, "US"));
    }

    private static byte[] resource(String name) throws Exception {
        java.io.InputStream in = RadioTest.class.getClassLoader().getResourceAsStream(name);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    @Test
    public void readsNativeFunctionsAndAidlFromAnAppsCode() throws Exception {
        // radio/sample.dex: a stand-in for a vendor radio app (see its classes in the assertions).
        DexInfo dex = DexInfo.read(resource("radio/sample.dex"));
        List<String> natives = new ArrayList<String>();
        for (DexInfo.Method m : dex.natives) natives.add(m.className + " " + m);
        assertEquals(3, natives.size());
        assertTrue(natives.toString(), natives.contains("com.rk.FmRadio.FmRadioService enableFmFacility_native (Z)Z"));
        assertTrue(natives.toString(), natives.contains("com.rk.FmRadio.FmRadioService static setFreq_native (I)I"));
        assertTrue(natives.toString(),
                natives.contains("com.rk.FmRadio.FmRadioService scan_native (FLjava/lang/String;[J)[I"));
        // Only interfaces with a Stub (AIDL) count.
        assertEquals(1, dex.aidl.size());
        List<String> aidl = new ArrayList<String>();
        for (DexInfo.Method m : dex.aidl.get("com.rk.FmRadio.IFmRadio")) aidl.add(m.toString());
        assertTrue(aidl.toString(), aidl.contains("powerUp (F)Z"));
        assertTrue(aidl.toString(), aidl.contains("seek (IZ)I"));
        assertTrue(dex.strings.contains("rockchip_radio_jni"));
        assertTrue(dex.strings.contains("fm_route=on"));
        assertTrue(dex.classes.contains("com.rk.FmRadio.Plain"));
    }

    @Test
    public void readsTheDexInsideAnOdex() throws Exception {
        byte[] dex = resource("radio/sample.dex");
        // An optimised dex: "dey\n036\0", then the dex's offset and length, then the dex.
        byte[] odex = new byte[40 + dex.length];
        System.arraycopy("dey\n036\0".getBytes("US-ASCII"), 0, odex, 0, 8);
        odex[8] = 40;
        odex[12] = (byte) dex.length;
        odex[13] = (byte) (dex.length >> 8);
        System.arraycopy(dex, 0, odex, 40, dex.length);
        assertEquals(3, DexInfo.read(odex).natives.size());
    }

    @Test
    public void findsJniClassesAndFunctionsInALibrary() throws Exception {
        String rodata = "\u0001\u0002\0com/mediatek/FMRadio/FMRadioNative\0opendev\0()Z\0powerup\0(F)Z\0"
                + "seek\0(FZ)F\0java/lang/String\0/dev/fm\0x\0";
        File f = File.createTempFile("libfmjni", ".so");
        f.deleteOnExit();
        FileOutputStream out = new FileOutputStream(f);
        out.write(rodata.getBytes("US-ASCII"));
        out.close();
        StringBuilder sb = new StringBuilder();
        RadioReport.describeLibrary(f, sb);
        String report = sb.toString();
        assertTrue(report, report.contains("classes: com/mediatek/FMRadio/FMRadioNative\n"));
        assertTrue(report, report.contains("functions: opendev ()Z, powerup (F)Z, seek (FZ)F\n"));
    }
}
