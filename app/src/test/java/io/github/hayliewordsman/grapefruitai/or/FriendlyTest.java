package io.github.hayliewordsman.grapefruitai.or;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.UnknownHostException;

import io.github.hayliewordsman.grapefruitai.net.HttpException;

public class FriendlyTest {
    @Test
    public void imageModelOnChatEndpoint() {
        // The error OpenRouter returned for meta/muse-image on chat/completions.
        HttpException e = new HttpException(404, "{\"error\":{\"code\":404,\"message\":\"meta/muse-image is an image "
                + "generation model and cannot be used with the chat/completions endpoint. Use the /api/v1/images "
                + "endpoint instead.\"}}");
        Friendly.Error f = Friendly.of(e);
        assertEquals(Routing.USE_IMAGES_TAB, f.message);
        assertTrue(f.detail, f.detail.contains("cannot be used with the chat/completions endpoint"));
    }

    @Test
    public void commonErrors() {
        assertTrue(Friendly.of(new HttpException(401, "{\"error\":{\"message\":\"No auth credentials found\"}}"))
                .message.contains("API key"));
        assertTrue(Friendly.of(new HttpException(402, "{}")).message.contains("credit"));
        assertTrue(Friendly.of(new HttpException(429, "{}")).message.contains("Too many"));
        assertTrue(Friendly.of(new HttpException(404, "{\"error\":{\"message\":\"No endpoints found that support "
                + "image input\"}}")).message.contains("No provider"));
        assertTrue(Friendly.of(new HttpException(502, "<html/>")).message.contains("provider"));
        assertTrue(Friendly.of(new UnknownHostException("openrouter.ai")).message.contains("No connection"));
    }

    @Test
    public void routingRefusalsHaveNoDetail() {
        Friendly.Error f = Friendly.of(new Friendly.ModelRouteException(Routing.USE_IMAGES_TAB));
        assertEquals(Routing.USE_IMAGES_TAB, f.message);
        assertNull(f.detail);
    }
}
