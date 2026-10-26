package com.grapefruitmusic.radio;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Process;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What Grapefruit can find out about a tablet's FM radio: the libraries, devices and apps that
 * look like radio, and what happened when it tried to switch it on. With this, support for a
 * radio that doesn't work yet can be added.
 */
final class RadioReport {
    private static final Pattern CLASS_PATH = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*(/[A-Za-z0-9_$]+){2,}");
    private static final Pattern SIGNATURE = Pattern.compile("\\([\\[A-Za-z0-9_/;$]*\\)[\\[A-Za-z0-9_/;$]+");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][A-Za-z0-9_]{1,40}");

    private RadioReport() {
    }

    static String collect(Context c, String attempts) {
        StringBuilder sb = new StringBuilder();
        String version = "?";
        try {
            version = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            // Can't happen for our own package.
        }
        sb.append("Grapefruit Music ").append(version).append(": FM radio details\n\n");
        sb.append("Tablet: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" (device ").append(Build.DEVICE).append(", board ").append(Build.BOARD)
                .append(", hardware ").append(Build.HARDWARE).append(")\n");
        sb.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT)
                .append("), build ").append(Build.DISPLAY).append('\n');
        for (String key : new String[]{"ro.board.platform", "ro.mediatek.platform", "ro.mediatek.version.release",
                "ro.product.board", "ro.hardware"}) {
            String v = property(key);
            if (v.length() > 0) sb.append(key).append(" = ").append(v).append('\n');
        }

        AudioManager audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        sb.append("\nHeadphones in: ").append(wiredHeadsetOn(audio)).append('\n');
        boolean fmStream = false;
        try {
            AudioManager.class.getField("STREAM_FM");
            fmStream = true;
        } catch (NoSuchFieldException e) {
            // Not MediaTek's Jelly Bean.
        }
        sb.append("Radio volume stream: ").append(fmStream ? "yes" : "no").append('\n');
        for (String perm : new String[]{"android.permission.ACCESS_MTK_MMHW", "android.permission.MODIFY_AUDIO_SETTINGS"}) {
            boolean granted = c.checkCallingOrSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
            sb.append(perm.substring(perm.lastIndexOf('.') + 1)).append(": ").append(granted ? "granted" : "not granted")
                    .append('\n');
        }

        sb.append("\nWhat happened:\n").append(attempts.length() > 0 ? attempts : "(the radio wasn't tried yet)\n");

        sb.append("\nRadio libraries:\n");
        int listed = 0;
        for (String dir : new String[]{"/system/lib", "/vendor/lib", "/system/vendor/lib"}) {
            File[] files = new File(dir).listFiles();
            if (files == null) continue;
            Arrays.sort(files);
            for (File f : files) {
                String name = f.getName().toLowerCase(Locale.US);
                if (!name.contains("fm") && !name.contains("radio")) continue;
                listed++;
                sb.append(f.getPath()).append(" (").append(f.length()).append(" bytes)\n");
                if (name.endsWith(".so") && name.contains("fm") && f.length() < 4 * 1024 * 1024) describeLibrary(f, sb);
            }
        }
        if (listed == 0) sb.append("(none)\n");

        sb.append("\nRadio devices:\n");
        String[] dev = new File("/dev").list();
        listed = 0;
        if (dev != null) {
            Arrays.sort(dev);
            for (String d : dev) {
                String name = d.toLowerCase(Locale.US);
                if (!name.contains("fm") && !name.contains("radio")) continue;
                File f = new File("/dev", d);
                sb.append(f.getPath()).append(f.canRead() ? " readable" : " not readable")
                        .append(f.canWrite() ? ", writable\n" : ", not writable\n");
                listed++;
            }
        }
        if (listed == 0) sb.append(dev == null ? "(can't list /dev)\n" : "(none)\n");

        sb.append("\nRadio apps:\n");
        listed = 0;
        PackageManager pm = c.getPackageManager();
        for (PackageInfo p : pm.getInstalledPackages(0)) {
            String name = p.packageName.toLowerCase(Locale.US);
            if (!name.contains("fm") && !name.contains("radio")) continue;
            boolean system = p.applicationInfo != null && (p.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            sb.append(p.packageName).append(' ').append(p.versionName).append(system ? " (system)" : "")
                    .append(pm.getLaunchIntentForPackage(p.packageName) != null ? ", opens\n" : "\n");
            listed++;
        }
        if (listed == 0) sb.append("(none)\n");

        List<String> logs = ownLog();
        if (!logs.isEmpty()) {
            sb.append("\nLog:\n");
            for (String line : logs) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    @SuppressWarnings("deprecation")
    private static boolean wiredHeadsetOn(AudioManager audio) {
        return audio.isWiredHeadsetOn();
    }

    /** A system property (android.os.SystemProperties isn't public), or "". */
    private static String property(String key) {
        try {
            Method get = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
            Object v = get.invoke(null, key);
            return v != null ? v.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * The Java classes a JNI library attaches to, and its functions with their signatures (a best
     * guess from the strings in the file, where they usually sit in name, signature order).
     */
    static void describeLibrary(File f, StringBuilder sb) {
        List<String> strings;
        try {
            strings = strings(read(f));
        } catch (IOException e) {
            sb.append("  can't read: ").append(e.getMessage()).append('\n');
            return;
        }
        Set<String> classes = new LinkedHashSet<String>();
        Set<String> functions = new LinkedHashSet<String>();
        for (int i = 0; i < strings.size(); i++) {
            String s = strings.get(i);
            if (CLASS_PATH.matcher(s).matches() && !s.startsWith("java/") && !s.startsWith("system/")
                    && !s.startsWith("dev/") && !s.startsWith("data/") && !s.startsWith("proc/")) {
                classes.add(s);
            } else if (SIGNATURE.matcher(s).matches()) {
                String name = i > 0 && IDENTIFIER.matcher(strings.get(i - 1)).matches() ? strings.get(i - 1) + " " : "";
                functions.add(name + s);
            }
        }
        sb.append("  classes: ").append(classes.isEmpty() ? "(none)" : join(classes)).append('\n');
        sb.append("  functions: ").append(functions.isEmpty() ? "(none)" : join(functions)).append('\n');
    }

    private static String join(Set<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }

    private static byte[] read(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) f.length());
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** Runs of printable ASCII ending in a zero byte (C strings), 2 to 120 characters long. */
    static List<String> strings(byte[] data) {
        List<String> out = new ArrayList<String>();
        int start = 0;
        for (int i = 0; i < data.length; i++) {
            int b = data[i] & 0xff;
            if (b >= 0x20 && b < 0x7f) continue;
            int len = i - start;
            if (b == 0 && len >= 2 && len <= 120) out.add(new String(data, start, len, java.nio.charset.Charset.forName("US-ASCII")));
            start = i + 1;
        }
        return out;
    }

    /** This app's own recent log lines about the radio and native libraries (apps may read their own). */
    private static List<String> ownLog() {
        List<String> out = new ArrayList<String>();
        String pid = "(" + Process.myPid() + ")";
        String pidPadded = "( " + Process.myPid() + ")";
        java.lang.Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "brief", "-t", "600"});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.contains(pid) && !line.contains(pidPadded)) continue;
                String l = line.toLowerCase(Locale.US);
                if (l.contains("fm") || l.contains("radio") || l.contains("jni") || l.contains("native")
                        || l.contains("mediaplayer")) {
                    out.add(line);
                    if (out.size() > 60) out.remove(0);
                }
            }
        } catch (IOException e) {
            // No logcat here.
        } finally {
            if (p != null) p.destroy();
        }
        return out;
    }
}
