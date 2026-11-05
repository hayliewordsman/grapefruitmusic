package com.grapefruitmusic.source;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import static org.junit.Assert.*;

/** A fixed bundle produced by the Python packaging tool, not by the Java decryptor. */
public class BundleCryptoTest {
    private static final byte[] KEY = Base64.getDecoder().decode(
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4v");
    private static final byte[] BUNDLE = Base64.getDecoder().decode(
            "QkdWMQABAgMEBQYHCAkKCwwNDg9akdOnUwUbe7EIzbQ8ch+adPSwmFgln+xpgoN0gEhDbxzjy9VNo2m5MhtlOVn0MysHEVQKeUrEsNHPmlgpzpNMfsbd2E6iQ9EzrcVAryHv+A==");

    @Test public void opensPythonBundle() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BundleCrypto.decrypt(new ByteArrayInputStream(BUNDLE), output, BUNDLE.length, KEY);
        assertEquals("Blue Grapefruit cross-language test\n", output.toString("UTF-8"));
    }
    @Test public void rejectsTamperedCiphertextAndMac() throws Exception {
        for (int offset : new int[]{4, 25, BUNDLE.length - 1}) {
            byte[] broken = BUNDLE.clone();
            broken[offset] ^= 1;
            rejected(broken, broken.length, KEY);
        }
    }
    @Test public void rejectsWrongKeyTruncationAndTrailingData() throws Exception {
        byte[] key = KEY.clone(); key[47] ^= 1;
        rejected(BUNDLE, BUNDLE.length, key);
        rejected(java.util.Arrays.copyOf(BUNDLE, BUNDLE.length - 1), BUNDLE.length, KEY);
        rejected(java.util.Arrays.copyOf(BUNDLE, BUNDLE.length + 1), BUNDLE.length, KEY);
        rejected(BUNDLE, -1, KEY);
        rejected(BUNDLE, BUNDLE.length, new byte[16]);
    }
    private static void rejected(byte[] data, long length, byte[] key) throws Exception {
        try {
            BundleCrypto.decrypt(new ByteArrayInputStream(data), new ByteArrayOutputStream(), length, key);
            fail("Untrusted or incomplete bundle accepted");
        } catch (IOException expected) { }
    }
}
