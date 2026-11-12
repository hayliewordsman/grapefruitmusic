package io.github.hayliewordsman.grapefruitai.secure;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * The OpenRouter API key and the OpenCode server password. They live in memory; by default
 * ({@link #SESSION}) nothing is written anywhere and they are gone when Android ends the app.
 *
 * Optional persistence, encrypted with {@link Crypto}:
 * <ul>
 *   <li>{@link #KEYSTORE} (Android 4.3+): the encryption keys are wrapped by a key in the Android
 *   Keystore, so copying the app's files off the device is not enough to read them. It does not
 *   protect against anyone who can run code as this app (a rooted device, malware with root).
 *   Android 4.2.2 has no app-usable Keystore, so this choice is not offered there.</li>
 *   <li>{@link #PASSPHRASE} (every version, including 4.2.2): the keys come from a passphrase
 *   you type after each launch (PBKDF2, 60,000 rounds). Only as strong as the passphrase.</li>
 * </ul>
 * Secrets are never logged and never included in backups (allowBackup=false).
 */
public final class Secrets {
    public static final int SESSION = 0;
    public static final int KEYSTORE = 1;
    public static final int PASSPHRASE = 2;

    public static final String OPENROUTER_KEY = "openrouter";
    public static final String OPENCODE_PASSWORD = "opencode_password";

    private static final String PREFS = "vault";
    private static final String ALIAS = "grapefruit_ai_vault";

    private static SharedPreferences prefs;
    private static final JSONObject memory = new JSONObject();
    private static int mode = SESSION;
    private static boolean locked;
    /** The unwrapped 64-byte keys while unlocked (persistent modes only). */
    private static byte[] keys;

    private Secrets() {
    }

    public static synchronized void init(Context c) {
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        mode = prefs.getInt("mode", SESSION);
        if (mode == KEYSTORE) {
            try {
                keys = unwrapWithKeystore(Base64.decode(prefs.getString("wrapped", ""), Base64.NO_WRAP));
                load();
            } catch (Exception e) {
                // Keystore reset (e.g. the lock screen changed on old Android): the data is unreadable.
                locked = false;
                wipeStored();
                mode = SESSION;
            }
        } else if (mode == PASSPHRASE) {
            locked = prefs.contains("data");
        }
    }

    public static boolean keystoreAvailable() {
        return Build.VERSION.SDK_INT >= 18;
    }

    public static synchronized int mode() {
        return mode;
    }

    public static synchronized boolean isLocked() {
        return locked;
    }

    public static synchronized String get(String name) {
        String v = memory.optString(name, "");
        return v.length() > 0 ? v : null;
    }

    public static synchronized boolean has(String name) {
        return get(name) != null;
    }

    public static synchronized void put(String name, String value) throws GeneralSecurityException {
        try {
            if (value == null || value.length() == 0) memory.remove(name);
            else memory.put(name, value);
        } catch (JSONException e) {
            throw new GeneralSecurityException(e.getMessage());
        }
        if (mode != SESSION && !locked) save();
    }

    public static synchronized void clear(String name) throws GeneralSecurityException {
        put(name, null);
    }

    /** Forgets everything, in memory and on storage, and returns to session-only. */
    public static synchronized void clearAll() {
        java.util.Iterator<String> it = memory.keys();
        java.util.List<String> names = new java.util.ArrayList<String>();
        while (it.hasNext()) names.add(it.next());
        for (String n : names) memory.remove(n);
        wipeStored();
        mode = SESSION;
        locked = false;
    }

    /** Session-only: deletes what was stored; secrets stay in memory until the app ends. */
    public static synchronized void useSessionOnly() {
        wipeStored();
        mode = SESSION;
        locked = false;
    }

    public static synchronized void useKeystore() throws GeneralSecurityException {
        if (!keystoreAvailable()) throw new GeneralSecurityException("Needs Android 4.3 or later");
        if (locked) throw new GeneralSecurityException("Unlock first");
        byte[] fresh = Crypto.random(64);
        byte[] wrapped = wrapWithKeystore(fresh);
        keys = fresh;
        mode = KEYSTORE;
        prefs.edit().clear().putInt("mode", KEYSTORE)
                .putString("wrapped", Base64.encodeToString(wrapped, Base64.NO_WRAP)).commit();
        save();
    }

    /** Slow (PBKDF2): call off the main thread. */
    public static synchronized void usePassphrase(char[] passphrase) throws GeneralSecurityException {
        if (locked) throw new GeneralSecurityException("Unlock first");
        if (passphrase == null || passphrase.length < 8) {
            throw new GeneralSecurityException("Use at least 8 characters");
        }
        byte[] salt = Crypto.random(16);
        keys = Crypto.deriveKeys(passphrase, salt, Crypto.PBKDF2_ITERATIONS);
        mode = PASSPHRASE;
        deleteKeystoreKey();
        prefs.edit().clear().putInt("mode", PASSPHRASE)
                .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putInt("iterations", Crypto.PBKDF2_ITERATIONS).commit();
        save();
    }

    /** Slow (PBKDF2): call off the main thread. */
    public static synchronized void unlock(char[] passphrase) throws GeneralSecurityException {
        if (mode != PASSPHRASE || !locked) return;
        byte[] salt = Base64.decode(prefs.getString("salt", ""), Base64.NO_WRAP);
        byte[] candidate = Crypto.deriveKeys(passphrase, salt, prefs.getInt("iterations", Crypto.PBKDF2_ITERATIONS));
        byte[] plain = Crypto.decrypt(candidate, Base64.decode(prefs.getString("data", ""), Base64.NO_WRAP));
        keys = candidate;
        merge(plain);
        locked = false;
    }

    private static void load() throws GeneralSecurityException {
        String data = prefs.getString("data", null);
        if (data == null) return;
        merge(Crypto.decrypt(keys, Base64.decode(data, Base64.NO_WRAP)));
    }

    private static void merge(byte[] plain) throws GeneralSecurityException {
        try {
            JSONObject stored = new JSONObject(new String(plain, "UTF-8"));
            java.util.Iterator<String> it = stored.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (!memory.has(k)) memory.put(k, stored.getString(k));
            }
        } catch (Exception e) {
            throw new GeneralSecurityException("Damaged data");
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    private static void save() throws GeneralSecurityException {
        if (keys == null) throw new GeneralSecurityException("Locked");
        try {
            byte[] enc = Crypto.encrypt(keys, memory.toString().getBytes("UTF-8"));
            prefs.edit().putString("data", Base64.encodeToString(enc, Base64.NO_WRAP)).commit();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new GeneralSecurityException(e.getMessage());
        }
    }

    private static void wipeStored() {
        if (keys != null) Arrays.fill(keys, (byte) 0);
        keys = null;
        if (prefs != null) prefs.edit().clear().putInt("mode", SESSION).commit();
        deleteKeystoreKey();
    }

    private static void deleteKeystoreKey() {
        if (!keystoreAvailable()) return;
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS);
        } catch (Exception ignored) {
            // Nothing to delete.
        }
    }

    // --- Android Keystore wrapping: AES-GCM on Android 6+, RSA-PKCS1 on Android 4.3-5.1. ---

    private static byte[] wrapWithKeystore(byte[] secret) throws GeneralSecurityException {
        if (Build.VERSION.SDK_INT >= 23) return wrapAes(secret);
        return wrapRsa(secret);
    }

    private static byte[] unwrapWithKeystore(byte[] wrapped) throws GeneralSecurityException {
        if (wrapped.length == 0) throw new GeneralSecurityException("Nothing stored");
        if (wrapped[0] == 'A') return unwrapAes(wrapped);
        return unwrapRsa(wrapped);
    }

    @TargetApi(23)
    private static byte[] wrapAes(byte[] secret) throws GeneralSecurityException {
        deleteKeystoreKey();
        KeyGenerator kg = KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore");
        kg.init(new android.security.keystore.KeyGenParameterSpec.Builder(ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
                        | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        SecretKey key = kg.generateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] ct = cipher.doFinal(secret);
        byte[] out = new byte[2 + iv.length + ct.length];
        out[0] = 'A';
        out[1] = (byte) iv.length;
        System.arraycopy(iv, 0, out, 2, iv.length);
        System.arraycopy(ct, 0, out, 2 + iv.length, ct.length);
        return out;
    }

    @TargetApi(23)
    private static byte[] unwrapAes(byte[] wrapped) throws GeneralSecurityException {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            SecretKey key = (SecretKey) ks.getKey(ALIAS, null);
            if (key == null) throw new GeneralSecurityException("Key gone");
            int ivLen = wrapped[1];
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, wrapped, 2, ivLen));
            return cipher.doFinal(wrapped, 2 + ivLen, wrapped.length - 2 - ivLen);
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException(e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    @TargetApi(18)
    private static byte[] wrapRsa(byte[] secret) throws GeneralSecurityException {
        deleteKeystoreKey();
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        end.add(Calendar.YEAR, 30);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
        kpg.initialize(new android.security.KeyPairGeneratorSpec.Builder(io.github.hayliewordsman.grapefruitai.App.get())
                .setAlias(ALIAS)
                .setSubject(new javax.security.auth.x500.X500Principal("CN=" + ALIAS))
                .setSerialNumber(BigInteger.ONE)
                .setStartDate(start.getTime())
                .setEndDate(end.getTime())
                .build());
        java.security.KeyPair pair = kpg.generateKeyPair();
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidOpenSSL");
        cipher.init(Cipher.ENCRYPT_MODE, pair.getPublic());
        byte[] ct = cipher.doFinal(secret);
        byte[] out = new byte[1 + ct.length];
        out[0] = 'R';
        System.arraycopy(ct, 0, out, 1, ct.length);
        return out;
    }

    @TargetApi(18)
    private static byte[] unwrapRsa(byte[] wrapped) throws GeneralSecurityException {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            KeyStore.Entry entry = ks.getEntry(ALIAS, null);
            if (!(entry instanceof KeyStore.PrivateKeyEntry)) throw new GeneralSecurityException("Key gone");
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidOpenSSL");
            cipher.init(Cipher.DECRYPT_MODE, ((KeyStore.PrivateKeyEntry) entry).getPrivateKey());
            return cipher.doFinal(wrapped, 1, wrapped.length - 1);
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException(e.getMessage());
        }
    }

    public static String describeMode(int m) {
        switch (m) {
            case KEYSTORE: return "Saved, Android Keystore";
            case PASSPHRASE: return "Saved, passphrase";
            default: return "This session only";
        }
    }
}
