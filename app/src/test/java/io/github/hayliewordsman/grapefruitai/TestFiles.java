package io.github.hayliewordsman.grapefruitai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class TestFiles {
    private TestFiles() {
    }

    public static String read(String name) throws IOException {
        InputStream in = TestFiles.class.getClassLoader().getResourceAsStream(name);
        if (in == null) throw new IOException("missing " + name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n);
        in.close();
        return out.toString("UTF-8");
    }
}
