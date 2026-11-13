package io.github.hayliewordsman.grapefruitai.or;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

import javax.net.ssl.SSLException;

import io.github.hayliewordsman.grapefruitai.net.HttpException;

/**
 * OpenRouter errors in plain words. The raw server message is kept as the detail, shown only when
 * the user expands it.
 */
public final class Friendly {
    public static final class Error {
        public final String message;
        /** The raw error, or null. */
        public final String detail;

        Error(String message, String detail) {
            this.message = message;
            this.detail = detail;
        }
    }

    private Friendly() {
    }

    public static Error of(IOException e) {
        String raw = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (e instanceof ModelRouteException) return new Error(raw, null);
        if (e instanceof HttpException) {
            HttpException h = (HttpException) e;
            String text = (raw + " " + (h.body != null ? h.body : "")).toLowerCase(Locale.US);
            if (text.contains("chat/completions") && (text.contains("image generation") || text.contains("/api/v1/images"))
                    || text.contains("cannot be used with the chat")) {
                return new Error(Routing.USE_IMAGES_TAB, raw);
            }
            if (text.contains("/api/v1/chat/completions") && text.contains("images endpoint")) {
                return new Error(Routing.USE_IMAGES_TAB, raw);
            }
            if (text.contains("is not an image") || text.contains("cannot be used with the images")
                    || text.contains("does not support image generation")) {
                return new Error("This model can't generate images. Choose a model in the Images tab's list.", raw);
            }
            if (text.contains("no endpoints found")) {
                return new Error("No provider on OpenRouter can run this model with this request right now (for "
                        + "example with image input). Try another model.", raw);
            }
            if (text.contains("image input") && (text.contains("not support") || text.contains("unsupported"))) {
                return new Error("This model can't take images. Choose a model that accepts image input.", raw);
            }
            switch (h.code) {
                case 400:
                    return new Error("OpenRouter couldn't accept this request (a setting or attachment the model "
                            + "doesn't support).", raw);
                case 401:
                    return new Error("OpenRouter refused the API key. Check it in Settings.", raw);
                case 402:
                    return new Error("Not enough OpenRouter credit for this request.", raw);
                case 403:
                    return new Error("OpenRouter declined this request (for example its content moderation).", raw);
                case 404:
                    return new Error("OpenRouter doesn't offer this model for this kind of request. Choose another "
                            + "model.", raw);
                case 408:
                    return new Error("The request timed out. Try again.", raw);
                case 413:
                    return new Error("The request is too large (try smaller or fewer images).", raw);
                case 429:
                    return new Error("Too many requests right now. Wait a moment and try again.", raw);
                default:
                    if (h.code >= 500) {
                        return new Error("The model's provider failed to answer. Try again, or choose another model.",
                                raw);
                    }
                    return new Error("OpenRouter returned an error (HTTP " + h.code + ").", raw);
            }
        }
        if (e instanceof UnknownHostException) {
            return new Error("No connection to openrouter.ai. Check the network and retry.", raw);
        }
        if (e instanceof SocketTimeoutException) return new Error("The connection timed out. Retry to reconnect.", raw);
        if (e instanceof SSLException) return new Error("The secure connection to OpenRouter failed.", raw);
        if (raw.startsWith("Provider error")) {
            return new Error("The model's provider stopped with an error. Try again.", raw);
        }
        return new Error(raw, null);
    }

    /** Thrown before any request when a model can't be used with an endpoint. */
    public static final class ModelRouteException extends IOException {
        public ModelRouteException(String message) {
            super(message);
        }
    }
}
