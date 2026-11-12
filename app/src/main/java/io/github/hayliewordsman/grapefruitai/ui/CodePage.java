package io.github.hayliewordsman.grapefruitai.ui;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.github.hayliewordsman.grapefruitai.md.MdRender;
import io.github.hayliewordsman.grapefruitai.net.Http;
import io.github.hayliewordsman.grapefruitai.net.HttpException;
import io.github.hayliewordsman.grapefruitai.oc.OpenCode;
import io.github.hayliewordsman.grapefruitai.oc.Risk;
import io.github.hayliewordsman.grapefruitai.oc.Transcript;
import io.github.hayliewordsman.grapefruitai.secure.Secrets;
import io.github.hayliewordsman.grapefruitai.util.Bg;
import io.github.hayliewordsman.grapefruitai.util.Format;
import io.github.hayliewordsman.grapefruitai.util.Prefs;

/**
 * Coding / OpenCode Agent: a remote frontend for an `opencode serve` backend. Sessions, prompts,
 * live agent output and tool status from the server's event stream, permission requests to
 * approve or reject, project files, artifacts and usage.
 */
public final class CodePage extends Page {
    private static final String[][] TEMPLATES = {
            {"Android app", "Create a complete, buildable Android application in this repository: <describe the app>. "
                    + "Use Gradle with a GitHub Actions workflow that builds a debug APK on GitHub-hosted runners "
                    + "and uploads it as an artifact. Include the full project (manifest, resources, sources, tests, "
                    + "README), not snippets. Ask before any git push."},
            {"Fix the build", "Find out why the build fails, fix it, and run the tests. Explain the cause. Don't push."},
            {"GitHub Actions", "Check the latest GitHub Actions workflow runs for this repository (gh run list / gh run "
                    + "view --log-failed). Summarise failures and propose fixes."},
            {"Get the APK", "Find the most recent successful GitHub Actions run, download its APK artifact into "
                    + "./artifacts with gh run download, and tell me the file path."},
            {"Commit", "Review the changes (git status, git diff), propose a commit message, and commit. Then propose "
                    + "the push command but ask me before pushing."},
    };

    private OpenCode client;
    private String configError;
    private LinearLayout statusPill;
    private TextView modelChip, agentChip, sessionTitle, usageLine;
    private LinearLayout transcriptBox, permissionBox;
    private ScrollView scroll;
    private EditText input;
    private IconView send;
    private JSONObject providers;
    private JSONArray agentsList;
    private String agent = "build";
    private Transcript transcript;
    private String version;
    private boolean connected;
    private volatile boolean eventsRunning;
    private Http[] eventHttp;
    private Thread eventThread;
    private boolean refreshQueued;
    private final Map<String, Object[]> partViews = new HashMap<String, Object[]>();

    public CodePage(MainActivity a) {
        super(a);
    }

    @Override
    protected View build() {
        Palette p = Ui.p();
        LinearLayout root = Ui.vertical(a);
        root.setBackgroundColor(p.background);

        LinearLayout header = Ui.horizontal(a);
        header.setPadding(Ui.dp(a, 16), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 4));
        header.addView(Ui.title(a, "Code"), Ui.weight(1));
        IconView files = Ui.circle(a, IconView.FOLDER, 40, false, "Project files");
        files.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ready()) FileBrowser.show(a, client, "");
            }
        });
        header.addView(files);
        IconView sessions = Ui.circle(a, IconView.HISTORY, 40, false, "Sessions");
        ((LinearLayout.LayoutParams) sessions.getLayoutParams()).leftMargin = Ui.dp(a, 8);
        sessions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSessions();
            }
        });
        header.addView(sessions);
        IconView fresh = Ui.circle(a, IconView.PLUS, 40, false, "New session");
        ((LinearLayout.LayoutParams) fresh.getLayoutParams()).leftMargin = Ui.dp(a, 8);
        fresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newSession(null);
            }
        });
        header.addView(fresh);
        root.addView(header, Ui.matchWrap());

        HorizontalScrollView chips = Ui.chipRow(a);
        statusPill = Ui.statusPill(a);
        statusPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                explainBackend();
            }
        });
        Ui.addChip(chips, statusPill);
        modelChip = Ui.chip(a, "Model", false);
        modelChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickModel();
            }
        });
        Ui.addChip(chips, modelChip);
        agentChip = Ui.chip(a, "Agent: build", false);
        agentChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickAgent();
            }
        });
        Ui.addChip(chips, agentChip);
        root.addView(chips, Ui.matchWrap());

        sessionTitle = Ui.text(a, 13, p.secondary, false);
        sessionTitle.setPadding(Ui.dp(a, 18), 0, Ui.dp(a, 18), 0);
        root.addView(sessionTitle, Ui.matchWrap());

        scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        transcriptBox = Ui.vertical(a);
        transcriptBox.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 12));
        scroll.addView(transcriptBox);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        permissionBox = Ui.vertical(a);
        root.addView(permissionBox, Ui.matchWrap());

        usageLine = Ui.text(a, 12, p.secondary, false);
        usageLine.setPadding(Ui.dp(a, 18), Ui.dp(a, 2), Ui.dp(a, 18), 0);
        root.addView(usageLine, Ui.matchWrap());

        HorizontalScrollView templates = Ui.chipRow(a);
        for (final String[] t : TEMPLATES) {
            TextView chip = Ui.chip(a, t[0], false);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    input.setText(t[1]);
                    input.setSelection(input.getText().length());
                    input.requestFocus();
                }
            });
            Ui.addChip(templates, chip);
        }
        root.addView(templates, Ui.matchWrap());

        LinearLayout composer = Ui.horizontal(a);
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 6));
        input = Ui.field(a, "Ask the agent", true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(6);
        LinearLayout.LayoutParams ilp = Ui.weight(1);
        ilp.rightMargin = Ui.dp(a, 8);
        composer.addView(input, ilp);
        send = Ui.circle(a, IconView.SEND, 44, true, "Send");
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (transcript != null && transcript.busy()) abort();
                else sendPrompt();
            }
        });
        composer.addView(send);
        root.addView(composer, Ui.matchWrap());

        configChanged();
        return root;
    }

    // --- Configuration and connection ---

    /** Settings changed: rebuild the client and reconnect. */
    public void configChanged() {
        stopEvents();
        client = null;
        configError = null;
        connected = false;
        String url = Prefs.str(Prefs.OC_URL, "");
        if (url.length() == 0) {
            configError = "No server set";
        } else if (Secrets.isLocked()) {
            configError = "Saved password is locked";
        } else {
            try {
                client = new OpenCode(url, Prefs.str(Prefs.OC_USER, "opencode"),
                        Secrets.get(Secrets.OPENCODE_PASSWORD), Prefs.str(Prefs.OC_DIRECTORY, ""));
            } catch (IOException e) {
                configError = e.getMessage();
            }
        }
        if (transcriptBox == null) return;
        refreshChrome();
        if (client == null) {
            showSetup();
        } else {
            testConnection(false);
        }
    }

    private boolean ready() {
        if (client != null) return true;
        if (Secrets.isLocked()) {
            SettingsPage.unlockDialog(a, new Runnable() {
                public void run() {
                    configChanged();
                }
            });
        } else {
            showSetup();
            Ui.toast(a, configError != null ? configError : "Set up the OpenCode backend in Settings");
        }
        return false;
    }

    private void showSetup() {
        transcriptBox.removeAllViews();
        partViews.clear();
        Palette p = Ui.p();
        LinearLayout card = Ui.vertical(a);
        card.setBackground(Ui.shape(a, p.surface, Ui.dp(a, 22), true, false));
        card.setPadding(Ui.dp(a, 18), Ui.dp(a, 16), Ui.dp(a, 18), Ui.dp(a, 16));
        TextView t = Ui.text(a, 18, p.text, true);
        t.setText("Connect an OpenCode server");
        card.addView(t);
        TextView body = Ui.body(a, 14, p.secondary);
        body.setText((configError != null ? configError + ".\n\n" : "")
                + "Coding mode drives an OpenCode agent running on your own server (`opencode serve`). The app "
                + "needs:\n• an HTTPS URL (a TLS reverse proxy in front of opencode serve)\n• the server password "
                + "(OPENCODE_SERVER_PASSWORD) and username\n• optionally, the project directory on the server\n\n"
                + "The agent uses the provider keys configured on the server. Your OpenRouter key in this app is "
                + "not sent to the server.");
        body.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 10));
        card.addView(body);
        card.addView(Ui.button(a, "Open backend settings", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.showTab(MainActivity.TAB_SETTINGS);
            }
        }), Ui.wrap());
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.setMargins(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), 0);
        transcriptBox.addView(card, lp);
    }

    public void testConnection(final boolean report) {
        if (client == null) {
            if (report) Sheet.alert(a, "OpenCode backend", configError != null ? configError : "Not configured");
            return;
        }
        final OpenCode c = client;
        Ui.setStatus(statusPill, Ui.STATUS_BUSY, "Backend: connecting…");
        Bg.run(new Runnable() {
            @Override
            public void run() {
                String error = null;
                JSONObject health = null;
                JSONObject prov = null;
                JSONArray ag = null;
                try {
                    health = c.health();
                    prov = c.providers();
                    try {
                        ag = c.agents();
                    } catch (IOException ignored) {
                        // Older servers.
                    }
                } catch (IOException e) {
                    error = describe(e);
                }
                final String err = error;
                final JSONObject h = health, pr = prov;
                final JSONArray agl = ag;
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (c != client) return;
                        if (err != null) {
                            connected = false;
                            Ui.setStatus(statusPill, Ui.STATUS_ERROR, "Backend: offline");
                            if (report) Sheet.alert(a, "Connection failed", err);
                            else if (transcript == null) showMessage("Couldn't reach the OpenCode server: " + err);
                            scheduleRetry();
                            return;
                        }
                        connected = true;
                        version = h.optString("version", "");
                        providers = pr;
                        agentsList = agl;
                        Ui.setStatus(statusPill, Ui.STATUS_OK, "Backend: connected" + (version.length() > 0 ? " " + version : ""));
                        refreshChrome();
                        if (report) {
                            Sheet.alert(a, "Connected", "OpenCode " + version + " at " + Prefs.str(Prefs.OC_URL, "")
                                    + "\nProviders configured on the server: " + providerNames());
                        }
                        startEvents();
                        if (transcript == null) openLatestSession();
                        else reload();
                    }
                });
            }
        });
    }

    private boolean retryScheduled;

    private void scheduleRetry() {
        if (retryScheduled) return;
        retryScheduled = true;
        Bg.later(new Runnable() {
            @Override
            public void run() {
                retryScheduled = false;
                if (client != null && !connected) testConnection(false);
            }
        }, 15000);
    }

    static String describe(IOException e) {
        if (e instanceof HttpException && ((HttpException) e).code == 401) {
            return "The server refused the username or password (HTTP 401).";
        }
        return ChatPage.describe(e);
    }

    private String providerNames() {
        if (providers == null) return "unknown";
        JSONArray list = providers.optJSONArray("providers");
        List<String> names = new ArrayList<String>();
        for (int i = 0; list != null && i < list.length(); i++) names.add(list.optJSONObject(i).optString("id"));
        return names.isEmpty() ? "none" : Format.join(names, ", ");
    }

    private void explainBackend() {
        StringBuilder b = new StringBuilder();
        b.append("Server: ").append(Prefs.str(Prefs.OC_URL, "not set")).append('\n');
        b.append("Status: ").append(client == null ? configError : connected ? "connected" : "offline").append('\n');
        if (version != null) b.append("OpenCode version: ").append(version).append('\n');
        b.append("Project: ").append(Prefs.str(Prefs.OC_DIRECTORY, "").length() > 0
                ? Prefs.str(Prefs.OC_DIRECTORY, "") : "server default").append('\n');
        b.append("Live updates: ").append(eventsRunning ? "event stream open" : "not streaming").append('\n');
        b.append("Server providers: ").append(providerNames()).append("\n\n");
        b.append("Billing: agent runs use the provider credentials configured on the OpenCode server and are billed "
                + "to that account. OpenCode's server API has no per-request API key, so this app cannot run the agent "
                + "on your OpenRouter key, and it doesn't send that key to the server or change server secrets.");
        new Sheet(a, "OpenCode backend").message(b.toString())
                .button("Reconnect", false, new Runnable() {
                    @Override
                    public void run() {
                        configChanged();
                    }
                }).button("OK", true, null).show();
    }

    // --- Event stream ---

    private void startEvents() {
        if (eventsRunning || client == null) return;
        eventsRunning = true;
        final OpenCode c = client;
        final Http[] holder = new Http[1];
        eventHttp = holder;
        eventThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int backoff = 2000;
                while (eventsRunning && c == client) {
                    long started = System.currentTimeMillis();
                    try {
                        c.events(new OpenCode.EventListener() {
                            @Override
                            public boolean onEvent(final String type, final JSONObject props) {
                                if (!eventsRunning) return false;
                                Bg.ui(new Runnable() {
                                    @Override
                                    public void run() {
                                        onServerEvent(type, props);
                                    }
                                });
                                return true;
                            }
                        }, holder);
                    } catch (IOException e) {
                        // Dropped: reconnect below.
                    }
                    if (!eventsRunning) break;
                    if (System.currentTimeMillis() - started > 30000) backoff = 2000;
                    Bg.ui(new Runnable() {
                        @Override
                        public void run() {
                            Ui.setStatus(statusPill, Ui.STATUS_BUSY, "Backend: reconnecting…");
                        }
                    });
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException e) {
                        break;
                    }
                    backoff = Math.min(backoff * 2, 30000);
                }
            }
        }, "opencode-events");
        eventThread.start();
    }

    private void stopEvents() {
        eventsRunning = false;
        Http[] h = eventHttp;
        if (h != null && h[0] != null) h[0].cancel();
        if (eventThread != null) eventThread.interrupt();
        eventThread = null;
    }

    private void onServerEvent(String type, JSONObject props) {
        if ("server.connected".equals(type)) {
            connected = true;
            Ui.setStatus(statusPill, Ui.STATUS_OK, "Backend: connected" + (version != null && version.length() > 0 ? " " + version : ""));
            reload();
            return;
        }
        if (transcript != null && transcript.apply(type, props)) {
            if ("session.error".equals(type) && transcript.lastError != null) {
                Ui.toast(a, transcript.lastError);
            }
            queueRefresh();
        }
        if ("session.updated".equals(type) && transcript != null) {
            JSONObject info = props.optJSONObject("info");
            if (info != null && transcript.sessionId.equals(info.optString("id"))) {
                sessionTitle.setText(info.optString("title", ""));
            }
        }
    }

    // --- Sessions ---

    private void openLatestSession() {
        final OpenCode c = client;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONArray list = c.sessions();
                    Bg.ui(new Runnable() {
                        @Override
                        public void run() {
                            JSONObject latest = null;
                            for (int i = 0; i < list.length(); i++) {
                                JSONObject s = list.optJSONObject(i);
                                if (s == null || s.has("parentID")) continue;
                                if (latest == null || updated(s) > updated(latest)) latest = s;
                            }
                            if (latest != null) open(latest);
                            else showMessage("No sessions yet. Write a request below to start one.");
                        }
                    });
                } catch (final IOException e) {
                    Bg.ui(new Runnable() {
                        @Override
                        public void run() {
                            showMessage("Couldn't list sessions: " + describe(e));
                        }
                    });
                }
            }
        });
    }

    private static long updated(JSONObject s) {
        JSONObject t = s.optJSONObject("time");
        return t != null ? t.optLong("updated", 0) : 0;
    }

    private void showSessions() {
        if (!ready()) return;
        final OpenCode c = client;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONArray list = c.sessions();
                    Bg.ui(new Runnable() {
                        @Override
                        public void run() {
                            Sheet s = new Sheet(a, "Sessions");
                            List<JSONObject> sorted = new ArrayList<JSONObject>();
                            for (int i = 0; i < list.length(); i++) {
                                JSONObject o = list.optJSONObject(i);
                                if (o != null && !o.has("parentID")) sorted.add(o);
                            }
                            java.util.Collections.sort(sorted, new java.util.Comparator<JSONObject>() {
                                public int compare(JSONObject x, JSONObject y) {
                                    long a1 = updated(y), b1 = updated(x);
                                    return a1 < b1 ? -1 : a1 > b1 ? 1 : 0;
                                }
                            });
                            if (sorted.isEmpty()) s.message("No sessions on the server for this project.");
                            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
                            for (final JSONObject o : sorted) {
                                String detail = df.format(new Date(updated(o))) + " · " + o.optString("directory", "");
                                s.row(o.optString("title", o.optString("id")), detail,
                                        transcript != null && transcript.sessionId.equals(o.optString("id")),
                                        new Runnable() {
                                            public void run() {
                                                open(o);
                                            }
                                        });
                            }
                            if (transcript != null) {
                                s.button("Delete current", false, new Runnable() {
                                    public void run() {
                                        deleteCurrent();
                                    }
                                });
                            }
                            s.button("Close", true, null).show();
                        }
                    });
                } catch (final IOException e) {
                    Bg.ui(new Runnable() {
                        public void run() {
                            Sheet.alert(a, "Sessions", describe(e));
                        }
                    });
                }
            }
        });
    }

    private void deleteCurrent() {
        final Transcript t = transcript;
        if (t == null) return;
        Sheet.confirm(a, "Delete this session on the server?", "OpenCode deletes the session and all its messages. "
                + "Files the agent changed are not reverted.", "Delete", true, new Runnable() {
            @Override
            public void run() {
                final OpenCode c = client;
                Bg.run(new Runnable() {
                    public void run() {
                        try {
                            c.deleteSession(t.sessionId);
                            Bg.ui(new Runnable() {
                                public void run() {
                                    transcript = null;
                                    partViews.clear();
                                    showMessage("Session deleted.");
                                    refreshChrome();
                                }
                            });
                        } catch (final IOException e) {
                            Bg.ui(new Runnable() {
                                public void run() {
                                    Ui.toast(a, describe(e));
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    private void open(JSONObject session) {
        transcript = new Transcript(session.optString("id"));
        partViews.clear();
        transcriptBox.removeAllViews();
        sessionTitle.setText(session.optString("title", ""));
        reload();
        refreshChrome();
    }

    /** Creates a session, then (if text is given) sends it. */
    private void newSession(final String firstPrompt) {
        if (!ready()) return;
        final OpenCode c = client;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONObject s = c.createSession(null);
                    Bg.ui(new Runnable() {
                        public void run() {
                            open(s);
                            if (firstPrompt != null) sendText(firstPrompt);
                        }
                    });
                } catch (final IOException e) {
                    Bg.ui(new Runnable() {
                        public void run() {
                            Sheet.alert(a, "Couldn't create a session", describe(e));
                        }
                    });
                }
            }
        });
    }

    /** Reloads the open session's messages and pending permissions. */
    private void reload() {
        final Transcript t = transcript;
        final OpenCode c = client;
        if (t == null || c == null) return;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONArray messages = c.messages(t.sessionId);
                    JSONArray pending = null;
                    try {
                        pending = c.pendingPermissions();
                    } catch (IOException ignored) {
                        // Older servers: permissions arrive as events only.
                    }
                    JSONObject statuses = null;
                    try {
                        statuses = c.sessionStatus();
                    } catch (IOException ignored) {
                        // Optional.
                    }
                    t.load(messages);
                    if (pending != null) t.setPermissions(pending);
                    if (statuses != null && statuses.optJSONObject(t.sessionId) != null) {
                        t.apply("session.status", new JSONObject().put("sessionID", t.sessionId)
                                .put("status", statuses.optJSONObject(t.sessionId)));
                    } else if (statuses != null) {
                        t.status = "idle";
                    }
                    Bg.ui(new Runnable() {
                        public void run() {
                            if (t == transcript) {
                                refresh();
                                scrollToEnd();
                            }
                        }
                    });
                } catch (final Exception e) {
                    Bg.ui(new Runnable() {
                        public void run() {
                            Ui.toast(a, "Couldn't load the session: " + (e instanceof IOException
                                    ? describe((IOException) e) : e.getMessage()));
                        }
                    });
                }
            }
        });
    }

    // --- Models and agents ---

    /** "providerID/modelID" chosen for agent runs, or null for the server default. */
    private String[] model() {
        String v = Prefs.str(Prefs.OC_MODEL, "");
        int slash = v.indexOf('/');
        if (slash <= 0) return null;
        return new String[]{v.substring(0, slash), v.substring(slash + 1)};
    }

    private void pickModel() {
        if (!ready()) return;
        if (providers == null) {
            Ui.toast(a, "Not connected yet");
            return;
        }
        Sheet s = new Sheet(a, "Agent model");
        s.message("Models the OpenCode server has configured. OpenRouter-backed models are listed first. Prices "
                + "are as the server reports them (USD per million tokens), billed to the server's provider account.");
        String[] current = model();
        s.row("Server default", defaultModels(), current == null, new Runnable() {
            public void run() {
                Prefs.put(Prefs.OC_MODEL, "");
                refreshChrome();
            }
        });
        JSONArray list = providers.optJSONArray("providers");
        List<JSONObject> ordered = new ArrayList<JSONObject>();
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject pr = list.optJSONObject(i);
            if (pr == null) continue;
            if ("openrouter".equals(pr.optString("id"))) ordered.add(0, pr);
            else ordered.add(pr);
        }
        if (ordered.isEmpty() || !"openrouter".equals(ordered.get(0).optString("id"))) {
            s.message("No OpenRouter provider is configured on this server. Configure it on the server (for "
                    + "example `opencode auth login` with an OpenRouter key) to use OpenRouter models here.");
        }
        for (JSONObject pr : ordered) {
            final String pid = pr.optString("id");
            JSONObject models = pr.optJSONObject("models");
            if (models == null) continue;
            Iterator<String> it = models.keys();
            while (it.hasNext()) {
                final String mid = it.next();
                JSONObject m = models.optJSONObject(mid);
                if (m == null) continue;
                StringBuilder d = new StringBuilder(pid);
                JSONObject limit = m.optJSONObject("limit");
                if (limit != null && limit.optLong("context") > 0) {
                    d.append(" · ").append(Format.tokens(limit.optLong("context"))).append(" context");
                }
                JSONObject cost = m.optJSONObject("cost");
                if (cost != null && cost.has("input")) {
                    d.append(" · USD in $").append(cost.optDouble("input")).append(" / out $")
                            .append(cost.optDouble("output")).append(" per M");
                }
                JSONObject caps = m.optJSONObject("capabilities");
                if (caps != null && caps.optBoolean("toolcall")) d.append(" · tools");
                else if (m.optBoolean("tool_call", false)) d.append(" · tools");
                boolean chosen = current != null && current[0].equals(pid) && current[1].equals(mid);
                s.row(m.optString("name", mid), d.toString(), chosen, new Runnable() {
                    public void run() {
                        Prefs.put(Prefs.OC_MODEL, pid + "/" + mid);
                        refreshChrome();
                    }
                });
            }
        }
        s.button("Close", true, null).show();
    }

    private String defaultModels() {
        JSONObject d = providers != null ? providers.optJSONObject("default") : null;
        if (d == null || d.length() == 0) return "Whatever the server is configured to use";
        List<String> parts = new ArrayList<String>();
        Iterator<String> it = d.keys();
        while (it.hasNext()) {
            String k = it.next();
            parts.add(k + "/" + d.optString(k));
        }
        return Format.join(parts, ", ");
    }

    private void pickAgent() {
        Sheet s = new Sheet(a, "Agent");
        s.message("OpenCode agents defined on the server. \"plan\" only reads and proposes; \"build\" can edit "
                + "and run commands (subject to the server's permission rules).");
        List<String> names = new ArrayList<String>();
        for (int i = 0; agentsList != null && i < agentsList.length(); i++) {
            JSONObject o = agentsList.optJSONObject(i);
            if (o == null || o.optBoolean("hidden", false) || "subagent".equals(o.optString("mode"))) continue;
            names.add(o.optString("name"));
        }
        if (names.isEmpty()) {
            names.add("build");
            names.add("plan");
        }
        for (final String n : names) {
            s.row(n, null, n.equals(agent), new Runnable() {
                public void run() {
                    agent = n;
                    refreshChrome();
                }
            });
        }
        s.button("Close", true, null).show();
    }

    private void refreshChrome() {
        if (modelChip == null) return;
        String[] m = model();
        modelChip.setText((m == null ? "Server default model" : m[0] + "/" + m[1]) + "  ▼");
        agentChip.setText("Agent: " + agent);
        if (client == null) Ui.setStatus(statusPill, Ui.STATUS_ERROR, "Backend: not set up");
        boolean busy = transcript != null && transcript.busy();
        send.setIcon(busy ? IconView.STOP : IconView.SEND);
        send.setContentDescription(busy ? "Stop the agent" : "Send");
        if (transcript != null) {
            long[] t = transcript.tokens();
            usageLine.setText("Session: " + Format.count(t[0]) + " in · " + Format.count(t[1]) + " out · "
                    + Format.count(t[2]) + " reasoning · cache " + Format.count(t[3]) + "/" + Format.count(t[4])
                    + " tokens · " + Format.cost(transcript.cost()) + " USD (server-reported)"
                    + (busy ? " · working…" : "")
                    + (transcript.statusDetail.length() > 0 ? " · " + transcript.statusDetail : ""));
            usageLine.setVisibility(View.VISIBLE);
        } else {
            usageLine.setVisibility(View.GONE);
        }
    }

    // --- Prompting ---

    private void sendPrompt() {
        String text = input.getText().toString().trim();
        if (text.length() == 0 || !ready()) return;
        input.setText("");
        if (transcript == null) newSession(text);
        else sendText(text);
    }

    private void sendText(final String text) {
        final Transcript t = transcript;
        final OpenCode c = client;
        final String[] m = model();
        final String ag = agent;
        if (t == null || c == null) return;
        t.status = "busy";
        refreshChrome();
        Bg.run(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONArray parts = new JSONArray().put(new JSONObject().put("type", "text").put("text", text));
                    c.prompt(t.sessionId, parts, m != null ? m[0] : null, m != null ? m[1] : null, ag);
                    if (!eventsRunning) {
                        Bg.ui(new Runnable() {
                            public void run() {
                                startEvents();
                            }
                        });
                    }
                } catch (final Exception e) {
                    Bg.ui(new Runnable() {
                        public void run() {
                            t.status = "idle";
                            refreshChrome();
                            input.setText(text);
                            Sheet.alert(a, "Not sent", e instanceof IOException ? describe((IOException) e)
                                    : String.valueOf(e.getMessage()));
                        }
                    });
                }
            }
        });
    }

    private void abort() {
        final Transcript t = transcript;
        final OpenCode c = client;
        if (t == null || c == null) return;
        Bg.run(new Runnable() {
            public void run() {
                try {
                    c.abort(t.sessionId);
                } catch (final IOException e) {
                    Bg.ui(new Runnable() {
                        public void run() {
                            Ui.toast(a, "Couldn't stop: " + describe(e));
                        }
                    });
                }
            }
        });
    }

    // --- Permissions ---

    private List<String> protectedPaths() {
        List<String> out = new ArrayList<String>();
        for (String s : Prefs.str(Prefs.OC_PROTECTED, "").split(",")) if (s.trim().length() > 0) out.add(s.trim());
        return out;
    }

    private void refreshPermissions() {
        permissionBox.removeAllViews();
        if (transcript == null) return;
        Palette p = Ui.p();
        for (final JSONObject req : transcript.permissions()) {
            final Risk risk = Risk.of(req, protectedPaths());
            LinearLayout card = Ui.vertical(a);
            card.setBackground(Ui.shape(a, p.surface, Ui.dp(a, 20), true, false));
            card.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 12));
            TextView title = Ui.text(a, 15, risk.level == Risk.DESTRUCTIVE ? p.danger : p.accent, true);
            title.setText("Permission needed: " + req.optString("permission", "action")
                    + (risk.level != Risk.NORMAL ? " · " + risk.label() : ""));
            card.addView(title);
            TextView what = Ui.body(a, 13, p.text);
            what.setTypeface(android.graphics.Typeface.MONOSPACE);
            String desc = Risk.describeText(req);
            what.setText(desc.length() > 1200 ? desc.substring(0, 1200) + "…" : desc);
            what.setTextIsSelectable(true);
            what.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
            card.addView(what);
            if (!risk.reasons.isEmpty()) {
                TextView why = Ui.body(a, 13, p.secondary);
                why.setText("• " + Format.join(risk.reasons, "\n• "));
                card.addView(why);
            }
            LinearLayout buttons = Ui.horizontal(a);
            buttons.setPadding(0, Ui.dp(a, 8), 0, 0);
            buttons.addView(Ui.button(a, "Reject", false, new View.OnClickListener() {
                public void onClick(View v) {
                    reply(req, "reject");
                }
            }), Ui.weight(1));
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.leftMargin = Ui.dp(a, 8);
            buttons.addView(Ui.button(a, "Allow once", true, new View.OnClickListener() {
                public void onClick(View v) {
                    if (risk.level == Risk.DESTRUCTIVE) confirmDestructive(req, risk);
                    else reply(req, "once");
                }
            }), lp);
            if (risk.level == Risk.NORMAL) {
                LinearLayout.LayoutParams lp2 = Ui.weight(1);
                lp2.leftMargin = Ui.dp(a, 8);
                buttons.addView(Ui.button(a, "Always", false, new View.OnClickListener() {
                    public void onClick(View v) {
                        reply(req, "always");
                    }
                }), lp2);
            }
            card.addView(buttons, Ui.matchWrap());
            LinearLayout.LayoutParams clp = Ui.matchWrap();
            clp.setMargins(Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 4));
            permissionBox.addView(card, clp);
        }
    }

    private void confirmDestructive(final JSONObject req, Risk risk) {
        Sheet.confirm(a, "Allow this action?", "This looks destructive or publishes something:\n\n• "
                + Format.join(risk.reasons, "\n• ") + "\n\n" + Risk.describeText(req)
                + "\n\nIt runs on the server with the agent's permissions.", "Allow once", true, new Runnable() {
            public void run() {
                reply(req, "once");
            }
        });
    }

    private void reply(final JSONObject req, final String answer) {
        final OpenCode c = client;
        final Transcript t = transcript;
        if (c == null || t == null) return;
        Bg.run(new Runnable() {
            public void run() {
                try {
                    c.replyPermission(req.optString("sessionID", t.sessionId), req.optString("id"), answer);
                    Bg.ui(new Runnable() {
                        public void run() {
                            try {
                                t.apply("permission.replied", new JSONObject().put("sessionID", t.sessionId)
                                        .put("requestID", req.optString("id")).put("reply", answer));
                            } catch (JSONException ignored) {
                                // Event will follow.
                            }
                            refresh();
                        }
                    });
                } catch (final IOException e) {
                    Bg.ui(new Runnable() {
                        public void run() {
                            Sheet.alert(a, "Couldn't answer", describe(e));
                        }
                    });
                }
            }
        });
    }

    // --- Rendering ---

    private void showMessage(String text) {
        transcriptBox.removeAllViews();
        partViews.clear();
        TextView t = Ui.status(a);
        t.setText(text);
        transcriptBox.addView(t, Ui.matchWrap());
    }

    private void queueRefresh() {
        if (refreshQueued) return;
        refreshQueued = true;
        Bg.later(new Runnable() {
            public void run() {
                refreshQueued = false;
                boolean end = isNearEnd();
                refresh();
                if (end) scrollToEnd();
            }
        }, 200);
    }

    private boolean isNearEnd() {
        View child = scroll.getChildAt(0);
        return child == null || child.getBottom() - (scroll.getScrollY() + scroll.getHeight()) < Ui.dp(a, 320);
    }

    private void scrollToEnd() {
        scroll.post(new Runnable() {
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    /** Rebuilds the transcript, reusing views of parts that haven't changed. */
    private void refresh() {
        if (transcript == null) return;
        refreshChrome();
        refreshPermissions();
        List<Transcript.Msg> messages = transcript.messages();
        transcriptBox.removeAllViews();
        if (messages.isEmpty()) {
            TextView t = Ui.status(a);
            t.setText("Empty session. Describe what the agent should do: a whole Android app, a fix, a review…");
            transcriptBox.addView(t, Ui.matchWrap());
        }
        Map<String, Object[]> keep = new HashMap<String, Object[]>();
        for (Transcript.Msg m : messages) {
            for (JSONObject part : m.parts.values()) {
                String id = part.optString("id");
                String sig = part.toString();
                Object[] cached = partViews.get(id);
                View v;
                if (cached != null && sig.equals(cached[0])) {
                    v = (View) cached[1];
                } else {
                    v = PartView.build(a, m, part);
                }
                if (v == null) continue;
                if (v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
                keep.put(id, new Object[]{sig, v});
                transcriptBox.addView(v, Ui.matchWrap());
            }
            if (!m.isUser()) {
                View footer = PartView.footer(a, m);
                if (footer != null) transcriptBox.addView(footer, Ui.matchWrap());
            }
        }
        partViews.clear();
        partViews.putAll(keep);
    }

    /** Settings' "Project" row: the server's projects, or a typed path. */
    public void chooseProject() {
        final Sheet s = new Sheet(a, "Project");
        s.message("The directory on the server the agent works in (OpenCode's `directory` parameter).");
        s.row("Server default", "The directory opencode serve was started in", Prefs.str(Prefs.OC_DIRECTORY, "").length() == 0,
                new Runnable() {
                    public void run() {
                        Prefs.put(Prefs.OC_DIRECTORY, "");
                        configChanged();
                    }
                });
        final OpenCode c = client;
        if (c != null) {
            final TextView loading = s.row("Loading projects…", null, false, null);
            Bg.run(new Runnable() {
                public void run() {
                    try {
                        final JSONArray list = c.projects();
                        Bg.ui(new Runnable() {
                            public void run() {
                                loading.setText(list.length() == 0 ? "No projects reported" : "Projects on the server:");
                                for (int i = 0; i < list.length(); i++) {
                                    final String wt = list.optJSONObject(i).optString("worktree", "");
                                    if (wt.length() == 0 || wt.equals("/")) continue;
                                    s.row(wt, list.optJSONObject(i).optString("name", ""),
                                            wt.equals(Prefs.str(Prefs.OC_DIRECTORY, "")), new Runnable() {
                                                public void run() {
                                                    Prefs.put(Prefs.OC_DIRECTORY, wt);
                                                    configChanged();
                                                }
                                            });
                                }
                            }
                        });
                    } catch (final IOException e) {
                        Bg.ui(new Runnable() {
                            public void run() {
                                loading.setText("Couldn't list projects: " + describe(e));
                            }
                        });
                    }
                }
            });
        }
        final EditText path = Ui.field(a, "/home/me/projects/my-app", false);
        path.setText(Prefs.str(Prefs.OC_DIRECTORY, ""));
        s.view(path);
        s.button("Cancel", false, null);
        s.button("Use path", true, new Runnable() {
            public void run() {
                Prefs.put(Prefs.OC_DIRECTORY, path.getText().toString().trim());
                configChanged();
            }
        });
        s.show();
    }

    @Override
    public void onShow() {
        if (client == null && transcriptBox != null) configChanged();
        else if (client != null && !connected) testConnection(false);
    }

    @Override
    public void onDestroy() {
        stopEvents();
    }
}
