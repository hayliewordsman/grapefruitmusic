package com.grapefruitmusic.radio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads an app's compiled code (a .dex, or the .odex next to a system app) without loading or
 * running it: its strings, the native functions it declares (with JNI signatures, which is what a
 * vendor's JNI library expects to attach to), and its AIDL interfaces (how its services are called).
 */
final class DexInfo {
    private static final int ACC_INTERFACE = 0x200;
    private static final int ACC_NATIVE = 0x100;

    /** A method: "com.rk.FmRadio.FmRadioService", "setFreq", "(I)Z". */
    static final class Method {
        final String className;
        final String name;
        final String signature;
        final boolean isStatic;

        Method(String className, String name, String signature, boolean isStatic) {
            this.className = className;
            this.name = name;
            this.signature = signature;
            this.isStatic = isStatic;
        }

        @Override
        public String toString() {
            return (isStatic ? "static " : "") + name + " " + signature;
        }
    }

    final List<String> strings = new ArrayList<String>();
    final List<String> classes = new ArrayList<String>();
    final List<Method> natives = new ArrayList<Method>();
    /** AIDL interfaces (an interface with a nested $Stub class): name to its methods. */
    final Map<String, List<Method>> aidl = new LinkedHashMap<String, List<Method>>();

    private final byte[] d;
    private final int base;

    private DexInfo(byte[] data, int base) {
        this.d = data;
        this.base = base;
    }

    /** The code of an app: classes.dex inside the APK, or the .odex beside it. Null if neither. */
    static byte[] codeOf(File apk) throws IOException {
        if (apk.getName().endsWith(".apk")) {
            ZipFile zip = new ZipFile(apk);
            try {
                ZipEntry e = zip.getEntry("classes.dex");
                if (e != null) return readAll(zip.getInputStream(e));
            } finally {
                zip.close();
            }
            File odex = new File(apk.getParent(), apk.getName().substring(0, apk.getName().length() - 4) + ".odex");
            if (odex.canRead()) return readAll(new FileInputStream(odex));
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** Parses a .dex or .odex. */
    static DexInfo read(byte[] file) throws IOException {
        if (file.length < 0x70) throw new IOException("too short for a dex");
        int base;
        if (file[0] == 'd' && file[1] == 'e' && file[2] == 'x') {
            base = 0;
        } else if (file[0] == 'd' && file[1] == 'e' && file[2] == 'y') {
            // An optimised dex: a header, then the dex itself at dexOffset.
            base = u4(file, 8);
        } else {
            throw new IOException("not a dex");
        }
        DexInfo info = new DexInfo(file, base);
        try {
            info.parse();
        } catch (RuntimeException e) {
            throw new IOException("unreadable dex: " + e);
        }
        return info;
    }

    private static int u4(byte[] b, int at) {
        return (b[at] & 0xff) | (b[at + 1] & 0xff) << 8 | (b[at + 2] & 0xff) << 16 | (b[at + 3] & 0xff) << 24;
    }

    private int u4(int at) {
        return u4(d, base + at);
    }

    private int u2(int at) {
        return (d[base + at] & 0xff) | (d[base + at + 1] & 0xff) << 8;
    }

    /** Reads an unsigned LEB128 at pos[0] (an offset from the dex start) and moves past it. */
    private int uleb(int[] pos) {
        int result = 0;
        int shift = 0;
        while (true) {
            int b = d[base + pos[0]++] & 0xff;
            result |= (b & 0x7f) << shift;
            if (b < 0x80) return result;
            shift += 7;
        }
    }

    private void parse() {
        int stringCount = u4(56);
        int stringsOff = u4(60);
        for (int i = 0; i < stringCount; i++) strings.add(string(u4(stringsOff + 4 * i)));
        int typesOff = u4(68);
        int protosOff = u4(76);
        int methodsOff = u4(92);
        int classCount = u4(96);
        int classesOff = u4(100);

        List<String> stubs = new ArrayList<String>();
        List<Integer> interfaces = new ArrayList<Integer>();
        for (int c = 0; c < classCount; c++) {
            int def = classesOff + 32 * c;
            String name = type(typesOff, u4(def));
            classes.add(name);
            if (name.endsWith("$Stub")) stubs.add(name.substring(0, name.length() - 5));
            if ((u4(def + 4) & ACC_INTERFACE) != 0) interfaces.add(c);
            int data = u4(def + 24);
            if (data == 0) continue;
            int[] pos = {data};
            int staticFields = uleb(pos);
            int instanceFields = uleb(pos);
            int direct = uleb(pos);
            int virtual = uleb(pos);
            for (int f = 0; f < staticFields + instanceFields; f++) {
                uleb(pos);
                uleb(pos);
            }
            for (int pass = 0; pass < 2; pass++) {
                int count = pass == 0 ? direct : virtual;
                int index = 0;
                for (int m = 0; m < count; m++) {
                    index += uleb(pos);
                    int flags = uleb(pos);
                    uleb(pos);
                    if ((flags & ACC_NATIVE) != 0) natives.add(method(methodsOff, protosOff, typesOff, index, flags));
                }
            }
        }
        for (int c : interfaces) {
            String name = classes.get(c);
            if (!stubs.contains(name)) continue;
            List<Method> methods = new ArrayList<Method>();
            int data = u4(classesOff + 32 * c + 24);
            if (data != 0) {
                int[] pos = {data};
                int staticFields = uleb(pos);
                int instanceFields = uleb(pos);
                int direct = uleb(pos);
                int virtual = uleb(pos);
                for (int f = 0; f < staticFields + instanceFields; f++) {
                    uleb(pos);
                    uleb(pos);
                }
                int index = 0;
                for (int m = 0; m < direct + virtual; m++) {
                    if (m == direct) index = 0;
                    index += uleb(pos);
                    int flags = uleb(pos);
                    uleb(pos);
                    if (m >= direct) methods.add(method(methodsOff, protosOff, typesOff, index, flags));
                }
            }
            aidl.put(name, methods);
        }
    }

    private Method method(int methodsOff, int protosOff, int typesOff, int index, int flags) {
        int at = methodsOff + 8 * index;
        String owner = type(typesOff, u2(at));
        int proto = protosOff + 12 * u2(at + 2);
        String name = strings.get(u4(at + 4));
        StringBuilder sig = new StringBuilder("(");
        int params = u4(proto + 8);
        if (params != 0) {
            int n = u4(params);
            for (int i = 0; i < n; i++) sig.append(descriptor(typesOff, u2(params + 4 + 2 * i)));
        }
        sig.append(')').append(descriptor(typesOff, u4(proto + 4)));
        return new Method(owner, name, sig.toString(), (flags & 0x8) != 0);
    }

    private String descriptor(int typesOff, int type) {
        return strings.get(u4(typesOff + 4 * type));
    }

    /** "Lcom/rk/FmRadio/FmRadioService;" as "com.rk.FmRadio.FmRadioService". */
    private String type(int typesOff, int type) {
        String desc = descriptor(typesOff, type);
        if (desc.startsWith("L") && desc.endsWith(";")) return desc.substring(1, desc.length() - 1).replace('/', '.');
        return desc;
    }

    /** A string_data_item: its length (in UTF-16 units), then modified UTF-8. */
    private String string(int off) {
        int[] pos = {off};
        int length = uleb(pos);
        StringBuilder sb = new StringBuilder(length);
        int i = base + pos[0];
        while (sb.length() < length) {
            int b = d[i++] & 0xff;
            if (b < 0x80) {
                sb.append((char) b);
            } else if ((b & 0xe0) == 0xc0) {
                sb.append((char) (((b & 0x1f) << 6) | (d[i++] & 0x3f)));
            } else {
                sb.append((char) (((b & 0x0f) << 12) | ((d[i] & 0x3f) << 6) | (d[i + 1] & 0x3f)));
                i += 2;
            }
        }
        return sb.toString();
    }
}
