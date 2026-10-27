package com.grapefruitmusic.radio;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Radio Details › Save Radio Files: the details, and the files behind this tablet's own radio
 * (its radio app, the radio libraries, the device permission settings) in one zip, to send on so
 * support for the radio can be built from them. Only reads; nothing on the tablet changes.
 */
public final class RadioFiles {
    private static final long BIG = 8L * 1024 * 1024;

    /** What was saved. */
    public static final class Saved {
        public final File zip;
        public final int files;

        Saved(File zip, int files) {
            this.zip = zip;
            this.files = files;
        }
    }

    private RadioFiles() {
    }

    /** Writes Grapefruit/radio-files.zip on the storage. Slow: not on the main thread. */
    public static Saved save(Context c, String report) throws IOException {
        File dir = new File(Environment.getExternalStorageDirectory(), "Grapefruit");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("can't make " + dir);
        Set<String> paths = new LinkedHashSet<String>();
        for (PackageInfo p : RadioReport.radioApps(c)) {
            ApplicationInfo ai = p.applicationInfo;
            if (ai == null) continue;
            File apk = new File(ai.sourceDir);
            // The tablet's own radio apps whole; big downloaded apps (NextRadio) would only bloat it.
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!system && apk.length() > BIG) continue;
            paths.add(apk.getPath());
            paths.add(new File(apk.getParent(), apk.getName().replace(".apk", ".odex")).getPath());
        }
        for (File f : RadioReport.radioLibraries()) {
            if (f.length() <= 2 * 1024 * 1024) paths.add(f.getPath());
        }
        // The audio driver and its settings: where the radio's sound can be sent.
        for (File f : RadioReport.audioDrivers()) paths.add(f.getPath());
        paths.add("/system/etc/audio_policy.conf");
        paths.add("/system/etc/asound.conf");
        paths.add("/system/etc/permissions/platform.xml");
        paths.add("/system/build.prop");
        paths.addAll(RadioReport.rcFiles());
        // The Radio Sound Test's clip of a station, if it recorded one.
        paths.add(RadioSoundTest.clipFile().getPath());

        File zip = new File(dir, "radio-files.zip");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip));
        int files = 0;
        StringBuilder skipped = new StringBuilder();
        try {
            put(out, "radio-details.txt", report.getBytes("UTF-8"));
            files++;
            for (String path : paths) {
                File f = new File(path);
                if (!f.isFile()) continue;
                if (!f.canRead()) {
                    skipped.append(path).append(" (not readable)\n");
                    continue;
                }
                try {
                    putFile(out, path.substring(1), f);
                    files++;
                } catch (IOException e) {
                    skipped.append(path).append(" (").append(e.getMessage()).append(")\n");
                }
            }
            if (skipped.length() > 0) put(out, "skipped.txt", skipped.toString().getBytes("UTF-8"));
        } finally {
            out.close();
        }
        return new Saved(zip, files);
    }

    private static void put(ZipOutputStream out, String name, byte[] data) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(data);
        out.closeEntry();
    }

    private static void putFile(ZipOutputStream out, String name, File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            out.putNextEntry(new ZipEntry(name));
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.closeEntry();
        } finally {
            in.close();
        }
    }
}
