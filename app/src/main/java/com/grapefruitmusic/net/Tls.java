package com.grapefruitmusic.net;

import android.content.Context;
import android.util.Log;

import com.grapefruitmusic.R;

import org.conscrypt.Conscrypt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Makes HTTPS work on Android 4.2.2: Conscrypt provides TLS 1.2/1.3 with modern ciphers, and
 * a handful of bundled root certificates (Let's Encrypt and friends) cover servers whose
 * certificate authority is newer than the phone.
 */
public final class Tls {
    private static final String TAG = "GrapefruitTls";

    private Tls() {
    }

    public static void install(Context context) {
        Provider conscrypt = null;
        try {
            conscrypt = Conscrypt.newProvider();
            Security.insertProviderAt(conscrypt, 1);
        } catch (Throwable t) {
            Log.w(TAG, "Conscrypt unavailable, falling back to the platform TLS stack", t);
        }
        try {
            X509TrustManager trust = new CompositeTrustManager(
                    systemTrustManager(), bundledTrustManager(context));
            SSLContext ssl = conscrypt != null
                    ? SSLContext.getInstance("TLS", conscrypt)
                    : SSLContext.getInstance("TLS");
            ssl.init(null, new TrustManager[]{trust}, null);
            SSLSocketFactory factory = ssl.getSocketFactory();
            if (conscrypt == null) factory = new ModernProtocols(factory);
            HttpsURLConnection.setDefaultSSLSocketFactory(factory);
        } catch (Exception e) {
            Log.e(TAG, "Could not set up TLS", e);
        }
    }

    private static X509TrustManager systemTrustManager() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);
        return firstX509(tmf.getTrustManagers());
    }

    private static X509TrustManager bundledTrustManager(Context context) throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        String pem = readRaw(context, R.raw.extra_roots);
        String begin = "-----BEGIN CERTIFICATE-----";
        String end = "-----END CERTIFICATE-----";
        int i = 0;
        int n = 0;
        while ((i = pem.indexOf(begin, i)) >= 0) {
            int j = pem.indexOf(end, i);
            if (j < 0) break;
            String one = pem.substring(i, j + end.length());
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(one.getBytes("US-ASCII")));
            store.setCertificateEntry("extra" + (n++), cert);
            i = j + end.length();
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store);
        return firstX509(tmf.getTrustManagers());
    }

    private static X509TrustManager firstX509(TrustManager[] managers) {
        for (TrustManager m : managers) {
            if (m instanceof X509TrustManager) return (X509TrustManager) m;
        }
        throw new IllegalStateException("No X509TrustManager");
    }

    private static String readRaw(Context context, int id) throws IOException {
        InputStream in = context.getResources().openRawResource(id);
        try {
            return Http.readAll(in);
        } finally {
            in.close();
        }
    }

    /** Trusts a chain if either the phone's or the bundled roots do. */
    static final class CompositeTrustManager implements X509TrustManager {
        private final X509TrustManager primary;
        private final X509TrustManager fallback;

        CompositeTrustManager(X509TrustManager primary, X509TrustManager fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            primary.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                primary.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                try {
                    fallback.checkServerTrusted(chain, authType);
                } catch (CertificateException ignored) {
                    throw e;
                }
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> all = new ArrayList<X509Certificate>();
            all.addAll(Arrays.asList(primary.getAcceptedIssuers()));
            all.addAll(Arrays.asList(fallback.getAcceptedIssuers()));
            return all.toArray(new X509Certificate[all.size()]);
        }
    }

    /** Without Conscrypt: switch on TLS 1.1/1.2, which Android 4.1-4.4 support but leave off. */
    static final class ModernProtocols extends SSLSocketFactory {
        private final SSLSocketFactory base;

        ModernProtocols(SSLSocketFactory base) {
            this.base = base;
        }

        private Socket patch(Socket s) {
            if (s instanceof SSLSocket) {
                SSLSocket ssl = (SSLSocket) s;
                List<String> wanted = new ArrayList<String>();
                for (String p : ssl.getSupportedProtocols()) {
                    if (p.equals("TLSv1.2") || p.equals("TLSv1.1") || p.equals("TLSv1")) wanted.add(p);
                }
                ssl.setEnabledProtocols(wanted.toArray(new String[wanted.size()]));
            }
            return s;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return base.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return base.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return patch(base.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return patch(base.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return patch(base.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return patch(base.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return patch(base.createSocket(address, port, localAddress, localPort));
        }
    }
}
