package io.github.hayliewordsman.grapefruitai.oc;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;

import io.github.hayliewordsman.grapefruitai.net.HttpException;

/**
 * The backend connection test: which setting is missing, or which step failed and why, in words
 * that say what to fix. Steps: /api/info (reachable, OpenCode 2), /api/location (password and
 * project), /api/model (models the server can use).
 */
public final class ConnectionCheck {
    public enum Kind {
        OK, MISSING_SETTINGS, UNAUTHORIZED, NOT_FOUND, PROJECT_NOT_FOUND, TLS, NETWORK, NOT_OPENCODE, SERVER_ERROR
    }

    public static final class Result {
        public final Kind kind;
        public final String title;
        public final String detail;
        public String version;
        public org.json.JSONArray models;
        public org.json.JSONArray agents;
        public org.json.JSONObject defaultModel;
        public String project;

        Result(Kind kind, String title, String detail) {
            this.kind = kind;
            this.title = title;
            this.detail = detail;
        }

        public boolean ok() {
            return kind == Kind.OK;
        }
    }

    private ConnectionCheck() {
    }

    public static Result missing(String what) {
        return new Result(Kind.MISSING_SETTINGS, "Backend settings incomplete", what);
    }

    /** Runs the checks in order and stops at the first failure. */
    public static Result run(OpenCode c) {
        String step = "/api/info";
        try {
            org.json.JSONObject info = c.info();
            step = "/api/location";
            org.json.JSONObject location = c.location();
            step = "/api/model";
            org.json.JSONArray models = c.models();
            org.json.JSONArray agents = null;
            org.json.JSONObject def = null;
            try {
                agents = c.agents();
                def = c.defaultModel();
            } catch (IOException optional) {
                // Agents and the default model are extras.
            }
            Result r = new Result(Kind.OK, "Connected", "OpenCode " + info.optString("version", "?"));
            r.version = info.optString("version", "");
            r.models = models;
            r.agents = agents;
            r.defaultModel = def;
            r.project = location.optString("directory", "");
            return r;
        } catch (IOException e) {
            return classify(e, step, c.directory());
        }
    }

    /** What went wrong at step (an API path), for people. */
    public static Result classify(IOException e, String step, String directory) {
        if (e instanceof HttpException) {
            HttpException h = (HttpException) e;
            String tag = h.tag();
            if (h.code == 401 || "UnauthorizedError".equals(tag)) {
                return new Result(Kind.UNAUTHORIZED, "Wrong username or password (HTTP 401)",
                        "The server refused the credentials at " + step + ". Check Username (default \"opencode\") "
                                + "and Password against OPENCODE_SERVER_USERNAME and OPENCODE_SERVER_PASSWORD on the "
                                + "server, then restart the service if you changed them.");
            }
            if (h.code == 404 && "LocationNotFoundError".equals(tag)) {
                return new Result(Kind.PROJECT_NOT_FOUND, "Project directory not found (HTTP 404)",
                        "The server has no directory " + (directory != null ? directory : "(server default)")
                                + ". Set Project in Backend settings to an absolute path on the server, or clear it.");
            }
            if (h.code == 404) {
                return new Result(Kind.NOT_FOUND, "OpenCode API not found (HTTP 404)",
                        "The server answered 404 for " + step + ". Check that the URL is the OpenCode server's own "
                                + "address (the tunnel's root, without a path) and that it runs OpenCode 2 with the V2 "
                                + "API (/api/…). OpenCode 1.x servers don't have it.");
            }
            if (h.code == 403) {
                return new Result(Kind.UNAUTHORIZED, "Access denied (HTTP 403)",
                        "The server or the tunnel in front of it refused access to " + step + ". If the tunnel has "
                                + "its own login, allow this app through it.");
            }
            if (h.code >= 500) {
                return new Result(Kind.SERVER_ERROR, "Server error (HTTP " + h.code + ")",
                        "The server failed at " + step + ": " + h.getMessage() + (h.code == 502 || h.code == 504
                                ? ". If you use a tunnel, check that the OpenCode service behind it is running." : ""));
            }
            return new Result(Kind.SERVER_ERROR, "Unexpected answer (HTTP " + h.code + ")", h.getMessage());
        }
        if (e instanceof OpenCode.NotOpenCodeException) {
            return new Result(Kind.NOT_OPENCODE, "Not an OpenCode server", e.getMessage()
                    + ". A tunnel's sign-in or error page can cause this.");
        }
        Throwable tls = find(e, SSLException.class, CertificateException.class);
        if (tls != null || isTls(e)) {
            String m = String.valueOf((tls != null ? tls : e).getMessage());
            String why;
            if (find(e, SSLPeerUnverifiedException.class) != null || m.toLowerCase(java.util.Locale.US).contains("hostname")) {
                why = "The certificate doesn't match the server's name.";
            } else if (m.contains("Trust anchor") || m.contains("certification path") || m.contains("PKIX")
                    || find(e, CertificateException.class) != null) {
                why = "The certificate isn't trusted (self-signed, expired, or from an authority this device "
                        + "doesn't know).";
            } else {
                why = "The secure connection couldn't be set up.";
            }
            return new Result(Kind.TLS, "Secure connection failed (TLS)", why + " Details: " + m
                    + ". Certificate checks are never turned off; use a certificate from a public authority "
                    + "such as Let's Encrypt.");
        }
        if (find(e, UnknownHostException.class) != null) {
            return new Result(Kind.NETWORK, "Server name not found",
                    "This device couldn't look up the server's name. Check the URL and that the tunnel is running "
                            + "and online.");
        }
        if (find(e, SocketTimeoutException.class) != null) {
            return new Result(Kind.NETWORK, "Connection timed out",
                    "The server didn't answer in time. Check the network, the tunnel and the OpenCode service.");
        }
        if (find(e, ConnectException.class, NoRouteToHostException.class) != null) {
            return new Result(Kind.NETWORK, "Couldn't connect",
                    "Nothing answered at that address. Check the URL and that the tunnel and service are running.");
        }
        if (find(e, SocketException.class) != null) {
            return new Result(Kind.NETWORK, "Network error", "The connection broke: " + e.getMessage());
        }
        String m = e.getMessage();
        if (m != null && (m.startsWith("Only HTTPS") || m.startsWith("Not a valid URL") || m.startsWith("Put credentials"))) {
            return missing(m);
        }
        return new Result(Kind.NETWORK, "Connection failed", m != null ? m : e.getClass().getSimpleName());
    }

    private static boolean isTls(Throwable e) {
        String n = e.getClass().getName();
        return n.startsWith("javax.net.ssl.") || n.contains("Conscrypt") && n.contains("SSL");
    }

    @SafeVarargs
    private static Throwable find(Throwable e, Class<? extends Throwable>... types) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            for (Class<? extends Throwable> c : types) if (c.isInstance(t)) return t;
            if (t.getCause() == t) break;
        }
        return null;
    }
}
