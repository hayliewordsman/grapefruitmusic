package io.github.hayliewordsman.grapefruitai.oc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

import io.github.hayliewordsman.grapefruitai.net.HttpException;

public class ConnectionCheckTest {
    private static ConnectionCheck.Kind kind(IOException e) {
        return ConnectionCheck.classify(e, "/api/location", "/srv/app").kind;
    }

    @Test
    public void http() {
        assertEquals(ConnectionCheck.Kind.UNAUTHORIZED,
                kind(new HttpException(401, "{\"_tag\":\"UnauthorizedError\",\"message\":\"Unauthorized\"}")));
        assertEquals(ConnectionCheck.Kind.NOT_FOUND, kind(new HttpException(404, "Not Found")));
        assertEquals(ConnectionCheck.Kind.PROJECT_NOT_FOUND, kind(new HttpException(404,
                "{\"_tag\":\"LocationNotFoundError\",\"location\":{\"directory\":\"/srv/app\"},\"message\":\"x\"}")));
        assertEquals(ConnectionCheck.Kind.SERVER_ERROR, kind(new HttpException(502, "<html>Bad gateway</html>")));
        String detail = ConnectionCheck.classify(new HttpException(401, ""), "/api/location", null).detail;
        assertTrue(detail, detail.contains("OPENCODE_SERVER_PASSWORD"));
        assertTrue(ConnectionCheck.classify(new HttpException(404, ""), "/api/info", null).title.contains("404"));
    }

    @Test
    public void tls() {
        SSLHandshakeException untrusted = new SSLHandshakeException("java.security.cert.CertPathValidatorException: "
                + "Trust anchor for certification path not found.");
        ConnectionCheck.Result r = ConnectionCheck.classify(untrusted, "/api/info", null);
        assertEquals(ConnectionCheck.Kind.TLS, r.kind);
        assertTrue(r.detail, r.detail.contains("isn't trusted"));
        assertEquals(ConnectionCheck.Kind.TLS, kind(new SSLPeerUnverifiedException("Hostname x not verified")));
        assertTrue(ConnectionCheck.classify(new SSLPeerUnverifiedException("Hostname x not verified"), "/", null)
                .detail.contains("doesn't match"));
        IOException wrapped = new IOException("handshake", new CertificateException("expired"));
        assertEquals(ConnectionCheck.Kind.TLS, kind(wrapped));
    }

    @Test
    public void network() {
        assertEquals(ConnectionCheck.Kind.NETWORK, kind(new UnknownHostException("nope.example")));
        assertEquals(ConnectionCheck.Kind.NETWORK, kind(new ConnectException("refused")));
        assertEquals(ConnectionCheck.Kind.NETWORK, kind(new SocketTimeoutException("timeout")));
        assertTrue(ConnectionCheck.classify(new UnknownHostException("x"), "/", null).title.contains("not found"));
    }

    @Test
    public void notOpenCodeAndMissingSettings() {
        assertEquals(ConnectionCheck.Kind.NOT_OPENCODE, kind(new OpenCode.NotOpenCodeException("HTML")));
        assertEquals(ConnectionCheck.Kind.MISSING_SETTINGS, kind(new IOException("Only HTTPS connections are allowed")));
        assertEquals(ConnectionCheck.Kind.MISSING_SETTINGS, ConnectionCheck.missing("Server URL not set").kind);
    }
}
