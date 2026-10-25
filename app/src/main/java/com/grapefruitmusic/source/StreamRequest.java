package com.grapefruitmusic.source;

import java.util.HashMap;
import java.util.Map;

/** Where to fetch a track's audio. A null url with a path means "play this local file". */
public final class StreamRequest {
    public final String url;
    public final Map<String, String> headers;
    /** File extension to use if the server doesn't say. */
    public final String extension;

    public StreamRequest(String url, Map<String, String> headers, String extension) {
        this.url = url;
        this.headers = headers == null ? new HashMap<String, String>() : headers;
        this.extension = extension;
    }
}
