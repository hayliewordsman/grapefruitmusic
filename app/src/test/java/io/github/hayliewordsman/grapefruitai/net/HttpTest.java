package io.github.hayliewordsman.grapefruitai.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;

public class HttpTest {
    private static void refused(String url) {
        try {
            Http.requireHttps(url);
            fail("accepted " + url);
        } catch (IOException expected) {
            // Good.
        }
    }

    @Test
    public void onlyHttpsWithoutCredentials() throws IOException {
        assertEquals("https://openrouter.ai/api/v1/models", Http.requireHttps("https://openrouter.ai/api/v1/models"));
        assertEquals("https://agent.example.com:8443/x", Http.requireHttps(" https://agent.example.com:8443/x "));
        refused("http://openrouter.ai/api/v1/models");
        refused("http://127.0.0.1:4096");
        refused("ftp://example.com");
        refused("https://user:pass@example.com/");
        refused("not a url");
        refused(null);
    }

    @Test
    public void constructorRefusesPlainHttp() {
        try {
            new Http("GET", "http://example.com");
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("HTTPS"));
        }
    }

    @Test
    public void errorMessagesUseServerText() {
        HttpException e = new HttpException(401, "{\"error\":{\"code\":401,\"message\":\"No auth credentials found\"}}");
        assertTrue(e.getMessage(), e.getMessage().contains("No auth credentials found"));
        assertTrue(new HttpException(402, "{}").getMessage().contains("credits"));
        HttpException html = new HttpException(502, "<html>bad gateway</html>");
        assertEquals("Bad gateway (the provider failed)", html.getMessage());
        HttpException oc = new HttpException(404, "{\"name\":\"NotFoundError\",\"data\":{\"message\":\"Session not found\"}}");
        assertTrue(oc.getMessage(), oc.getMessage().contains("Session not found"));
    }

    @Test
    public void encodesQueryValues() {
        assertEquals("a%20b%2Fc%26d", Http.encode("a b/c&d"));
    }
}
