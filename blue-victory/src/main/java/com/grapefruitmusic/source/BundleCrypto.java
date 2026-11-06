package com.grapefruitmusic.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Authenticated AES-CBC bundle format, compatible with Android 4.2.2. This is not DRM:
 * the key travels in the APK and can be recovered by a determined recipient. */
public final class BundleCrypto {
    private static final byte[] MAGIC = {'B', 'G', 'V', '1'};
    private BundleCrypto() { }

    /** The caller must use a temporary output and publish it only after verification succeeds. */
    public static void decrypt(InputStream input, OutputStream output, long length, byte[] key) throws IOException {
        if (key.length != 48 || length < 68 || (length - 52) % 16 != 0)
            throw new IOException("Invalid Bonus Bundled Content bundle");
        byte[] header = new byte[20];
        readFully(input, header);
        if (!Arrays.equals(MAGIC, Arrays.copyOf(header, 4))) throw new IOException("Unknown EP bundle format");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Arrays.copyOfRange(key, 16, 48), "HmacSHA256"));
            mac.update(header);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Arrays.copyOf(key, 16), "AES"),
                    new IvParameterSpec(Arrays.copyOfRange(header, 4, 20)));
            long remaining = length - 52;
            byte[] buffer = new byte[32768];
            while (remaining > 0) {
                int n = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (n < 0) throw new IOException("Incomplete EP bundle");
                if (n == 0) continue;
                mac.update(buffer, 0, n);
                byte[] plain = cipher.update(buffer, 0, n);
                if (plain != null) output.write(plain);
                remaining -= n;
            }
            byte[] expected = new byte[32];
            readFully(input, expected);
            if (input.read() != -1 || !MessageDigest.isEqual(expected, mac.doFinal()))
                throw new IOException("EP bundle integrity check failed");
            output.write(cipher.doFinal());
        } catch (GeneralSecurityException e) { throw new IOException("Couldn't open the EP bundle", e); }
    }

    private static void readFully(InputStream input, byte[] data) throws IOException {
        int offset = 0;
        while (offset < data.length) {
            int n = input.read(data, offset, data.length - offset);
            if (n < 0) throw new IOException("Incomplete EP bundle");
            if (n > 0) offset += n;
        }
    }
}
