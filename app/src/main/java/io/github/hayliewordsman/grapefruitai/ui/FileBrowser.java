package io.github.hayliewordsman.grapefruitai.ui;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.github.hayliewordsman.grapefruitai.md.MdRender;
import io.github.hayliewordsman.grapefruitai.oc.OpenCode;
import io.github.hayliewordsman.grapefruitai.util.Bg;

/**
 * The OpenCode project's files through the V2 API: browse (GET /api/fs/list), uncommitted changes
 * (GET /api/vcs/status), files this session changed, find (GET /api/fs/find), and view or
 * download files (GET /api/fs/read/…).
 */
final class FileBrowser {
    private FileBrowser() {
    }

    static void show(final Activity a, final OpenCode c, final String path, final List<String> sessionChanges) {
        final Sheet s = new Sheet(a, path.length() == 0 ? "Project files" : path);
        final TextView loading = s.row("Loading…", null, false, null);
        if (path.length() > 0) {
            final String up = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
            s.row("Up one level", up.length() == 0 ? "project root" : up, false, new Runnable() {
                public void run() {
                    show(a, c, up, sessionChanges);
                }
            });
        } else {
            if (sessionChanges != null && !sessionChanges.isEmpty()) {
                s.row("Changed in this session", sessionChanges.size() + " files", false, new Runnable() {
                    public void run() {
                        sessionChanges(a, c, sessionChanges);
                    }
                });
            }
            s.row("Uncommitted changes", "The project's working tree (Git)", false, new Runnable() {
                public void run() {
                    changed(a, c);
                }
            });
            s.row("Find APKs and build outputs", "Search the project for .apk, .aab and .zip files", false, new Runnable() {
                public void run() {
                    find(a, c, ".apk");
                }
            });
        }
        final EditText search = Ui.field(a, "Find a file by name", false);
        s.view(search);
        s.button("Find", false, new Runnable() {
            public void run() {
                String q = search.getText().toString().trim();
                if (q.length() > 0) find(a, c, q);
            }
        });
        s.button("Close", true, null);
        s.show();
        Bg.run(new Runnable() {
            public void run() {
                try {
                    final JSONArray list = c.listFiles(path);
                    Bg.ui(new Runnable() {
                        public void run() {
                            List<JSONObject> items = new ArrayList<JSONObject>();
                            for (int i = 0; i < list.length(); i++) if (list.optJSONObject(i) != null) items.add(list.optJSONObject(i));
                            Collections.sort(items, new Comparator<JSONObject>() {
                                public int compare(JSONObject x, JSONObject y) {
                                    boolean dx = "directory".equals(x.optString("type"));
                                    boolean dy = "directory".equals(y.optString("type"));
                                    if (dx != dy) return dx ? -1 : 1;
                                    return name(x.optString("path")).compareToIgnoreCase(name(y.optString("path")));
                                }
                            });
                            loading.setText(items.isEmpty() ? "Empty" : items.size() + " items");
                            for (final JSONObject f : items) {
                                final boolean dir = "directory".equals(f.optString("type"));
                                final String p = f.optString("path");
                                s.row(name(p) + (dir ? "/" : ""), null, false, new Runnable() {
                                    public void run() {
                                        if (dir) show(a, c, p, sessionChanges);
                                        else open(a, c, p);
                                    }
                                });
                            }
                        }
                    });
                } catch (final IOException e) {
                    Bg.ui(new Runnable() {
                        public void run() {
                            loading.setText("Couldn't list: " + CodePage.describe(e));
                        }
                    });
                }
            }
        });
    }

    static String name(String path) {
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return p.contains("/") ? p.substring(p.lastIndexOf('/') + 1) : p;
    }

    private static void sessionChanges(final Activity a, final OpenCode c, List<String> files) {
        Sheet s = new Sheet(a, "Changed in this session");
        for (final String f : files) {
            s.row(f, null, false, new Runnable() {
                public void run() {
                    open(a, c, f);
                }
            });
        }
        s.button("Close", true, null).show();
    }

    private static void changed(final Activity a, final OpenCode c) {
        Bg.run(new Runnable() {
            public void run() {
                try {
                    final JSONArray list = c.vcsStatus();
                    Bg.ui(new Runnable() {
                        public void run() {
                            Sheet s = new Sheet(a, "Uncommitted changes");
                            if (list.length() == 0) s.message("No uncommitted changes.");
                            for (int i = 0; i < list.length(); i++) {
                                final JSONObject f = list.optJSONObject(i);
                                if (f == null) continue;
                                s.row(f.optString("file"), f.optString("status") + " · +" + f.optInt("additions") + " / -"
                                        + f.optInt("deletions"), false, new Runnable() {
                                    public void run() {
                                        if (!"deleted".equals(f.optString("status"))) open(a, c, f.optString("file"));
                                    }
                                });
                            }
                            s.button("Close", true, null).show();
                        }
                    });
                } catch (final IOException e) {
                    fail(a, e);
                }
            }
        });
    }

    private static void find(final Activity a, final OpenCode c, final String query) {
        Bg.run(new Runnable() {
            public void run() {
                try {
                    final List<String> found = new ArrayList<String>();
                    boolean artifacts = query.equals(".apk");
                    String[] queries = artifacts ? new String[]{".apk", ".aab", ".zip"} : new String[]{query};
                    for (String q : queries) {
                        JSONArray list = c.findFiles(q);
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject e = list.optJSONObject(i);
                            String p = e != null ? e.optString("path") : list.optString(i);
                            if (artifacts && !(p.endsWith(".apk") || p.endsWith(".aab") || p.endsWith(".zip"))) continue;
                            if (!found.contains(p)) found.add(p);
                        }
                    }
                    Bg.ui(new Runnable() {
                        public void run() {
                            Sheet s = new Sheet(a, "Found");
                            if (found.isEmpty()) s.message("Nothing matched \"" + query + "\".");
                            for (final String p : found) {
                                s.row(p, null, false, new Runnable() {
                                    public void run() {
                                        open(a, c, p);
                                    }
                                });
                            }
                            s.button("Close", true, null).show();
                        }
                    });
                } catch (final IOException e) {
                    fail(a, e);
                }
            }
        });
    }

    /** UTF-8 text without NUL bytes in its first 8 KB counts as text. */
    static boolean looksLikeText(byte[] b) {
        int n = Math.min(b.length, 8192);
        for (int i = 0; i < n; i++) if (b[i] == 0) return false;
        try {
            java.nio.charset.Charset.forName("UTF-8").newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(b, 0, n == b.length ? n : Math.max(0, n - 4)));
            return true;
        } catch (java.nio.charset.CharacterCodingException e) {
            return false;
        }
    }

    /** Shows a text file (with Copy and Download), or offers to download a binary one. */
    static void open(final Activity a, final OpenCode c, final String path) {
        Ui.toast(a, "Opening " + path);
        Bg.run(new Runnable() {
            public void run() {
                try {
                    final byte[] bytes = c.readFile(path);
                    Bg.ui(new Runnable() {
                        public void run() {
                            final String name = name(path);
                            final boolean text = looksLikeText(bytes);
                            final String mime = text ? "text/plain" : Attachments.guessMime(name);
                            Sheet s = new Sheet(a, name);
                            if (!text) {
                                s.message("Binary file, " + io.github.hayliewordsman.grapefruitai.util.Format.bytes(bytes.length) + ".");
                            } else {
                                String content;
                                try {
                                    content = new String(bytes, "UTF-8");
                                } catch (java.io.UnsupportedEncodingException e) {
                                    content = "";
                                }
                                String shown = content.length() > 60000 ? content.substring(0, 60000) + "\n… (truncated)" : content;
                                View code = MdRender.codeBlock(a, shown, name);
                                s.view(code);
                            }
                            s.button("Download", false, new Runnable() {
                                public void run() {
                                    Saver.saveFile(a, bytes, name, mime);
                                }
                            });
                            s.button("Close", true, null).show();
                        }
                    });
                } catch (final IOException e) {
                    fail(a, e);
                }
            }
        });
    }

    private static void fail(final Activity a, final IOException e) {
        Bg.ui(new Runnable() {
            public void run() {
                Sheet.alert(a, "Files", CodePage.describe(e));
            }
        });
    }
}
