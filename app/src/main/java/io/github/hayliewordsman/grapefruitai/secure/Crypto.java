package io.github.hayliewordsman.grapefruitai.secure;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Authenticated encryption that works on Android 4.2.2's own providers: AES-256-CBC, then
 * HMAC-SHA256 over version, IV and ciphertext (encrypt-then-MAC), with separate 32-byte keys.
 * Passphrase keys come from PBKDF2-HMAC-SHA1 (the strongest PBKDF2 Android 4.2 ships).
 *
 * Format: 1 version byte | 16 IV | ciphertext | 32 MAC.
 */
public final class Crypto {
    static final byte VERSION = 1;
    public static final int PBKDF2_ITERATIONS = 60000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Crypto() {
    }

    public static byte[] random(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    /** 64 bytes: AES key, then MAC key. */
    public static byte[] deriveKeys(char[] passphrase, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, iterations, 512);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    public static byte[] encrypt(byte[] keys, byte[] plain) throws GeneralSecurityException {
        check(keys);
        byte[] iv = random(16);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys, 0, 32, "AES"), new IvParameterSpec(iv));
        byte[] ct = cipher.doFinal(plain);
        byte[] out = new byte[1 + 16 + ct.length + 32];
        out[0] = VERSION;
        System.arraycopy(iv, 0, out, 1, 16);
        System.arraycopy(ct, 0, out, 17, ct.length);
        byte[] mac = mac(keys, out, 0, 17 + ct.length);
        System.arraycopy(mac, 0, out, 17 + ct.length, 32);
        return out;
    }

    public static byte[] decrypt(byte[] keys, byte[] data) throws GeneralSecurityException {
        check(keys);
        if (data == null || data.length < 1 + 16 + 16 + 32 || data[0] != VERSION) {
            throw new GeneralSecurityException("Unrecognised data");
        }
        int macAt = data.length - 32;
        byte[] expected = mac(keys, data, 0, macAt);
        byte[] actual = new byte[32];
        System.arraycopy(data, macAt, actual, 0, 32);
        if (!MessageDigest.isEqual(expected, actual)) throw new GeneralSecurityException("Wrong passphrase or damaged data");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keys, 0, 32, "AES"), new IvParameterSpec(data, 1, 16));
        return cipher.doFinal(data, 17, macAt - 17);
    }

    private static byte[] mac(byte[] keys, byte[] data, int off, int len) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(keys, 32, 32, "HmacSHA256"));
        mac.update(data, off, len);
        return mac.doFinal();
    }

    private static void check(byte[] keys) throws GeneralSecurityException {
        if (keys == null || keys.length != 64) throw new GeneralSecurityException("Bad key");
    }
}
