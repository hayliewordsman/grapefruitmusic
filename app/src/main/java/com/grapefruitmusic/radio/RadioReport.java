package com.grapefruitmusic.radio;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.ActivityInfo;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
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

    static String collect(Context c, String attempts, String headset) {
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
        sb.append("\nHeadphones: Android says ").append(wiredHeadsetOn(audio) ? "in" : "not in")
                .append("; last plug broadcast: ").append(headset).append('\n');
        String switches = switches();
        sb.append("Switches: ").append(switches.length() > 0 ? switches : "(none)").append('\n');
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

        sb.append("Grapefruit runs as: ").append(firstLine(exec("id"))).append('\n');
        sb.append("Superuser (su): ").append(superuser()).append('\n');

        sb.append("\nWhat happened:\n").append(attempts.length() > 0 ? attempts : "(the radio wasn't tried yet)\n");

        sb.append("\nRadio libraries:\n");
        int listed = 0;
        for (File f : radioLibraries()) {
            listed++;
            sb.append(f.getPath()).append(" (").append(f.length()).append(" bytes)\n");
            if (f.getName().endsWith(".so") && f.length() < 512 * 1024) describeLibrary(f, sb);
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
                String ls = firstLine(exec("ls", "-l", f.getPath()));
                if (ls.length() > 0) sb.append("  ").append(ls).append('\n');
                listed++;
            }
        }
        if (listed == 0) sb.append(dev == null ? "(can't list /dev)\n" : "(none)\n");

        sb.append("\nRadio apps:\n");
        listed = 0;
        PackageManager pm = c.getPackageManager();
        for (PackageInfo p : radioApps(c)) {
            boolean system = p.applicationInfo != null && (p.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            sb.append(p.packageName).append(' ').append(p.versionName).append(system ? " (system)" : "")
                    .append(pm.getLaunchIntentForPackage(p.packageName) != null ? ", opens\n" : "\n");
            describeApp(c, p.packageName, sb);
            listed++;
        }
        if (listed == 0) sb.append("(none)\n");

        sb.append("\nDevice permissions (ueventd/init):\n");
        listed = 0;
        for (String rc : rcFiles()) {
            String text = readText(new File(rc), 256 * 1024);
            if (text == null) continue;
            for (String line : text.split("\n")) {
                String l = line.toLowerCase(Locale.US);
                if (l.contains("fm") || l.contains("radio") || l.contains("8035")) {
                    sb.append(rc).append(": ").append(line.trim()).append('\n');
                    listed++;
                }
            }
        }
        if (listed == 0) sb.append("(none readable)\n");

        sb.append("\nPermission groups (platform.xml):\n").append(permissionGroups());
        sb.append("\nAudio constants about FM:\n").append(audioConstants());

        List<String> logs = ownLog();
        if (!logs.isEmpty()) {
            sb.append("\nLog:\n");
            for (String line : logs) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static final String[] LIBRARY_DIRS = {"/system/lib", "/system/lib/hw", "/vendor/lib", "/vendor/lib/hw",
            "/system/vendor/lib"};

    /** Libraries named like radio: "fm" or "radio" in the name. */
    static List<File> radioLibraries() {
        List<File> out = new ArrayList<File>();
        for (String dir : LIBRARY_DIRS) {
            File[] files = new File(dir).listFiles();
            if (files == null) continue;
            Arrays.sort(files);
            for (File f : files) {
                String name = f.getName().toLowerCase(Locale.US);
                if (name.contains("fm") || name.contains("radio")) out.add(f);
            }
        }
        return out;
    }

    /** Installed apps that look like radio ("fm" or "radio" in the package name), not Grapefruit. */
    static List<PackageInfo> radioApps(Context c) {
        List<PackageInfo> out = new ArrayList<PackageInfo>();
        for (PackageInfo p : c.getPackageManager().getInstalledPackages(0)) {
            String name = p.packageName.toLowerCase(Locale.US);
            if (p.packageName.equals(c.getPackageName())) continue;
            if (name.contains("fm") || name.contains("radio")) out.add(p);
        }
        return out;
    }

    /** Files that set up /dev permissions (ueventd) and services (init), where readable. */
    static List<String> rcFiles() {
        List<String> out = new ArrayList<String>();
        String hw = Build.HARDWARE;
        for (String name : new String[]{"/ueventd.rc", "/ueventd." + hw + ".rc", "/init.rc", "/init." + hw + ".rc"}) {
            out.add(name);
        }
        return out;
    }

    /**
     * An app in depth: who it runs as, its services and permissions, and its code: the native
     * functions it declares (what a vendor JNI library attaches to), its AIDL service interfaces,
     * and its strings about radio. Read from the files; none of its code runs.
     */
    private static void describeApp(Context c, String pkg, StringBuilder sb) {
        PackageManager pm = c.getPackageManager();
        PackageInfo p;
        try {
            p = pm.getPackageInfo(pkg, PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                    | PackageManager.GET_PERMISSIONS | PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            return;
        } catch (RuntimeException e) {
            // Too big to pass in one go: without the activities.
            try {
                p = pm.getPackageInfo(pkg, PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                        | PackageManager.GET_PERMISSIONS);
            } catch (Exception again) {
                sb.append("  (details unavailable: ").append(again).append(")\n");
                return;
            }
        }
        ApplicationInfo ai = p.applicationInfo;
        File apk = new File(ai.sourceDir);
        File odex = new File(apk.getParent(), apk.getName().replace(".apk", ".odex"));
        sb.append("  code: ").append(ai.sourceDir).append(" (").append(apk.length()).append(" bytes)")
                .append(odex.exists() ? ", " + odex.getPath() + " (" + odex.length() + " bytes)" : "").append('\n');
        sb.append("  runs as uid ").append(ai.uid).append(p.sharedUserId != null ? ", shared user " + p.sharedUserId : "")
                .append(", process ").append(ai.processName).append(", libraries ").append(ai.nativeLibraryDir).append('\n');
        if (p.permissions != null) {
            for (PermissionInfo perm : p.permissions) {
                sb.append("  defines permission ").append(perm.name).append(" (level ").append(perm.protectionLevel)
                        .append(")\n");
            }
        }
        if (p.requestedPermissions != null) {
            sb.append("  asks for: ").append(join(Arrays.asList(p.requestedPermissions))).append('\n');
        }
        if (p.services != null) {
            for (ServiceInfo s : p.services) {
                sb.append("  service ").append(s.name).append(s.exported ? " (exported" : " (not exported")
                        .append(s.permission != null ? ", needs " + s.permission : "")
                        .append(s.processName != null && !s.processName.equals(ai.processName) ? ", process " + s.processName : "")
                        .append(")\n");
            }
        }
        if (p.receivers != null) {
            for (ActivityInfo r : p.receivers) {
                sb.append("  receiver ").append(r.name).append(r.exported ? " (exported)" : " (not exported)").append('\n');
            }
        }
        if (p.activities != null) {
            for (ActivityInfo a : p.activities) {
                sb.append("  activity ").append(a.name).append(a.exported ? " (exported)" : "").append('\n');
            }
        }
        DexInfo dex;
        try {
            byte[] code = DexInfo.codeOf(apk);
            if (code == null) {
                sb.append("  (no code found next to the app)\n");
                return;
            }
            if (code.length > 24 * 1024 * 1024) {
                sb.append("  (code too big to read here: ").append(code.length).append(" bytes)\n");
                return;
            }
            dex = DexInfo.read(code);
        } catch (IOException e) {
            sb.append("  (can't read its code: ").append(e.getMessage()).append(")\n");
            return;
        } catch (OutOfMemoryError e) {
            sb.append("  (code too big to read here)\n");
            return;
        }
        sb.append("  classes: ").append(dex.classes.size()).append('\n');
        int shown = 0;
        String lastClass = null;
        for (DexInfo.Method m : dex.natives) {
            if (shown++ >= 80) {
                sb.append("  ... ").append(dex.natives.size() - 80).append(" more native functions\n");
                break;
            }
            if (!m.className.equals(lastClass)) {
                sb.append("  native functions in ").append(m.className).append(":\n");
                lastClass = m.className;
            }
            sb.append("    ").append(m).append('\n');
        }
        shown = 0;
        for (java.util.Map.Entry<String, List<DexInfo.Method>> e : dex.aidl.entrySet()) {
            if (shown++ >= 12) break;
            sb.append("  service interface ").append(e.getKey()).append(":\n");
            for (DexInfo.Method m : e.getValue()) sb.append("    ").append(m).append('\n');
        }
        List<String> interesting = new ArrayList<String>();
        for (String str : dex.strings) {
            if (str.length() < 3 || str.length() > 100) continue;
            if (INTERESTING.matcher(str).find()) interesting.add(str);
            if (interesting.size() >= 200) break;
        }
        if (!interesting.isEmpty()) sb.append("  strings: ").append(join(interesting)).append('\n');
    }

    private static final Pattern INTERESTING = Pattern.compile(
            "(?i)fm|radio|freq|tune|seek|scan|rds|antenna|audio|speaker|headset|route|mute|volume|/dev/|setparam|=|\\.so");

    /** Which permission gives which Linux group (that's how apps get to open devices). */
    private static String permissionGroups() {
        StringBuilder sb = new StringBuilder();
        File[] files = new File("/system/etc/permissions").listFiles();
        if (files == null) return "(can't read)\n";
        Arrays.sort(files);
        Pattern perm = Pattern.compile("<permission\\s+name=\"([^\"]+)\"\\s*>(.*?)</permission>", Pattern.DOTALL);
        Pattern gid = Pattern.compile("<group\\s+gid=\"([^\"]+)\"");
        for (File f : files) {
            String text = readText(f, 256 * 1024);
            if (text == null) continue;
            Matcher m = perm.matcher(text);
            while (m.find()) {
                Matcher g = gid.matcher(m.group(2));
                List<String> gids = new ArrayList<String>();
                while (g.find()) gids.add(g.group(1));
                if (!gids.isEmpty()) sb.append(m.group(1)).append(" -> ").append(join(gids)).append('\n');
            }
        }
        return sb.length() > 0 ? sb.toString() : "(none)\n";
    }

    /** Hidden AudioSystem and AudioManager constants with FM in the name (a vendor's radio routing). */
    private static String audioConstants() {
        StringBuilder sb = new StringBuilder();
        for (String cls : new String[]{"android.media.AudioSystem", "android.media.AudioManager"}) {
            try {
                for (Field f : Class.forName(cls).getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) || !f.getName().toUpperCase(Locale.US).contains("FM")) continue;
                    f.setAccessible(true);
                    sb.append(cls.substring(cls.lastIndexOf('.') + 1)).append('.').append(f.getName()).append(" = ")
                            .append(f.get(null)).append('\n');
                }
            } catch (Exception e) {
                sb.append(cls).append(": ").append(e).append('\n');
            }
        }
        return sb.length() > 0 ? sb.toString() : "(none)\n";
    }

    private static String superuser() {
        for (String path : new String[]{"/system/xbin/su", "/system/bin/su", "/sbin/su", "/su/bin/su"}) {
            if (new File(path).exists()) return "yes (" + path + ")";
        }
        return "no";
    }

    /** A command's output (its first 40 lines), or "" if it can't run. */
    private static String exec(String... cmd) {
        java.lang.Process p = null;
        try {
            p = Runtime.getRuntime().exec(cmd);
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            int n = 0;
            while ((line = r.readLine()) != null && n++ < 40) sb.append(line).append('\n');
            return sb.toString();
        } catch (IOException e) {
            return "";
        } finally {
            if (p != null) p.destroy();
        }
    }

    private static String firstLine(String text) {
        int nl = text.indexOf('\n');
        return (nl >= 0 ? text.substring(0, nl) : text).trim();
    }

    /** A small text file, or null if it can't be read. */
    static String readText(File f, int limit) {
        if (!f.canRead() || f.length() > limit) return null;
        try {
            return new String(read(f), "UTF-8");
        } catch (IOException e) {
            return null;
        }
    }

    /** The kernel's headphone switch (/sys/class/switch/h2w/state: 0 out, 1 or 2 in), or -1. */
    static int headphoneSwitch() {
        String s = readLine(new File("/sys/class/switch/h2w/state"));
        if (s == null) return -1;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Every kernel switch and its state ("h2w=1, hdmi=0"): the headphone jack is usually one. */
    private static String switches() {
        File[] dirs = new File("/sys/class/switch").listFiles();
        if (dirs == null) return "";
        Arrays.sort(dirs);
        StringBuilder sb = new StringBuilder();
        for (File d : dirs) {
            String state = readLine(new File(d, "state"));
            if (state == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(d.getName()).append('=').append(state.trim());
        }
        return sb.toString();
    }

    private static String readLine(File f) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "US-ASCII"));
            try {
                return r.readLine();
            } finally {
                r.close();
            }
        } catch (IOException e) {
            return null;
        }
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
        if (f.length() <= 64 * 1024) {
            // A small library: every string, which shows its JNI names, devices and HAL calls.
            List<String> all = new ArrayList<String>();
            for (String s : strings) {
                if (s.length() >= 3 && all.size() < 250) all.add(s);
            }
            sb.append("  strings: ").append(join(all)).append('\n');
        }
    }

    private static String join(java.util.Collection<String> items) {
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
