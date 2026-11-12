package io.github.hayliewordsman.grapefruitai.secure;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.security.GeneralSecurityException;

public class CryptoTest {
    @Test
    public void roundTrip() throws Exception {
        byte[] keys = Crypto.random(64);
        byte[] plain = "sk-or-v1-secret".getBytes("UTF-8");
        byte[] enc = Crypto.encrypt(keys, plain);
        assertEquals(Crypto.VERSION, enc[0]);
        assertArrayEquals(plain, Crypto.decrypt(keys, enc));
    }

    @Test
    public void tamperingAndWrongKeysFail() throws Exception {
        byte[] keys = Crypto.random(64);
        byte[] enc = Crypto.encrypt(keys, "hello".getBytes("UTF-8"));
        enc[20] ^= 1;
        try {
            Crypto.decrypt(keys, enc);
            fail();
        } catch (GeneralSecurityException expected) {
            // Good.
        }
        try {
            Crypto.decrypt(Crypto.random(64), Crypto.encrypt(keys, "x".getBytes("UTF-8")));
            fail();
        } catch (GeneralSecurityException expected) {
            // Good.
        }
    }

    @Test
    public void passphraseKeysAreDeterministicPerSalt() throws Exception {
        byte[] salt = Crypto.random(16);
        byte[] a = Crypto.deriveKeys("correct horse".toCharArray(), salt, 1000);
        byte[] b = Crypto.deriveKeys("correct horse".toCharArray(), salt, 1000);
        assertArrayEquals(a, b);
        assertEquals(64, a.length);
        byte[] enc = Crypto.encrypt(a, "k".getBytes("UTF-8"));
        try {
            Crypto.decrypt(Crypto.deriveKeys("wrong horse".toCharArray(), salt, 1000), enc);
            fail();
        } catch (GeneralSecurityException expected) {
            // Good.
        }
    }
}
