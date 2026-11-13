package io.github.hayliewordsman.grapefruitai.or;

/**
 * Which OpenRouter API a model can be used with, from its live metadata: the endpoints it serves
 * (models list `endpoints`), its input and output modalities, and the Images API's parameters.
 * Image-only models such as meta/muse-image are never sent to /api/v1/chat/completions.
 */
public final class Routing {
    public static final String USE_IMAGES_TAB =
            "This model is for image generation/editing. Use the Images tab instead.";

    public enum Kind {
        /** Text chat (text input). */
        CHAT,
        /** Chat that also takes images. */
        VISION_CHAT,
        /** Chat models that can also output images (Gemini image models). */
        CHAT_AND_IMAGE,
        /** Image generation/editing only: the Images API. */
        IMAGE_ONLY,
        /** Embeddings, speech, video, rerank… : not usable in this app. */
        OTHER
    }

    /** How the Images tab sends a request. */
    public enum ImageRoute {
        IMAGES_API, CHAT_COMPLETIONS, NONE
    }

    private Routing() {
    }

    public static Kind kind(OrModel m) {
        boolean chat = m.isChat();
        boolean image = m.isImage();
        if (chat && image) return Kind.CHAT_AND_IMAGE;
        if (chat) return m.acceptsImages() ? Kind.VISION_CHAT : Kind.CHAT;
        if (image) return Kind.IMAGE_ONLY;
        return Kind.OTHER;
    }

    /**
     * Null if the model can be used in Chat (chat/completions); otherwise a friendly reason. An
     * unknown model (not in the catalog yet) is let through: OpenRouter will answer for it.
     */
    public static String chatBlock(OrModel m) {
        if (m == null) return null;
        switch (kind(m)) {
            case IMAGE_ONLY:
                return USE_IMAGES_TAB;
            case OTHER:
                return m.name + " can't be used for chat (it isn't served by OpenRouter's Chat API). "
                        + "Choose a chat model.";
            default:
                return null;
        }
    }

    /** The Images API when OpenRouter serves the model there, else chat image output, else none. */
    public static ImageRoute imageRoute(OrModel m) {
        if (m == null) return ImageRoute.IMAGES_API;
        if (!m.outputsImage()) return ImageRoute.NONE;
        if (m.servesImageApi()) return ImageRoute.IMAGES_API;
        if (m.servesChat()) return ImageRoute.CHAT_COMPLETIONS;
        return ImageRoute.NONE;
    }

    /** "Chat API", "Images API", "Chat API and Images API", or the other endpoints it serves. */
    public static String apiLabel(OrModel m) {
        boolean chat = m.endpoints.isEmpty() ? m.outputsText() : m.servesChat();
        boolean images = m.servesImageApi();
        if (chat && images) return "Chat API and Images API";
        if (images) return "Images API";
        if (chat) return "Chat API";
        if (m.endpoints.isEmpty()) return "unknown";
        StringBuilder b = new StringBuilder();
        for (String e : m.endpoints) {
            if (b.length() > 0) b.append(", ");
            b.append(e);
        }
        return b.toString();
    }

    /** What the Images tab can do with uploaded images for this model. */
    public static final class Editing {
        public final boolean supported;
        /** Most images the user may add. */
        public final int maxImages;
        /** Shown under the editing controls, or why editing isn't available. */
        public final String note;

        Editing(boolean supported, int maxImages, String note) {
            this.supported = supported;
            this.maxImages = maxImages;
            this.note = note;
        }
    }

    /** Up to this many images when OpenRouter lists image input but no reference limit. */
    static final int UNLISTED_LIMIT = 4;

    public static Editing editing(OrModel m, ImageParams params) {
        if (m == null) return new Editing(false, 0, "Choose an image model to see whether it can edit images.");
        ImageRoute route = imageRoute(m);
        if (route == ImageRoute.NONE) {
            return new Editing(false, 0, m.name + " doesn't generate images. Choose an image model.");
        }
        if (!m.acceptsImages()) {
            return new Editing(false, 0, m.name + " takes text only: it can generate new images but can't edit "
                    + "an uploaded one. Choose a model that accepts image input to edit.");
        }
        if (route == ImageRoute.IMAGES_API && params != null && params.listsReferences) {
            if (params.maxReferences <= 0) {
                return new Editing(false, 0, m.name + " accepts no reference images in OpenRouter's Images API, "
                        + "so it can't edit uploaded images.");
            }
            return new Editing(true, params.maxReferences, "Add up to " + params.maxReferences + " image"
                    + (params.maxReferences == 1 ? "" : "s") + " to edit or use as references.");
        }
        return new Editing(true, UNLISTED_LIMIT, "This model accepts image input. OpenRouter doesn't list a limit "
                + "for it, so up to " + UNLISTED_LIMIT + " images can be added to edit or use as references.");
    }
}
