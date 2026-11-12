package io.github.hayliewordsman.grapefruitai.ui;

import android.app.Activity;
import android.util.Base64;
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
 * The OpenCode project's files: browse directories (GET /file), see what changed (GET
 * /file/status), find files and build artifacts (GET /find/file), view text and download files
 * (GET /file/content) to the device.
 */
final class FileBrowser {
    private FileBrowser() {
    }

    static void show(final Activity a, final OpenCode c, final String path) {
        final Sheet s = new Sheet(a, path.length() == 0 ? "Project files" : path);
        final TextView loading = s.row("Loading…", null, false, null);
        if (path.length() > 0) {
            String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
            final String up = parent;
            s.row("Up one level", up.length() == 0 ? "project root" : up, false, new Runnable() {
                public void run() {
                    show(a, c, up);
                }
            });
        } else {
            s.row("Changed files", "What the agent (or anyone) changed in the working tree", false, new Runnable() {
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
                                    return x.optString("name").compareToIgnoreCase(y.optString("name"));
                                }
                            });
                            loading.setText(items.isEmpty() ? "Empty" : items.size() + " items");
                            for (final JSONObject f : items) {
                                final boolean dir = "directory".equals(f.optString("type"));
                                final String p = f.optString("path");
                                s.row(f.optString("name") + (dir ? "/" : ""), f.optBoolean("ignored") ? "ignored by git" : null,
                                        false, new Runnable() {
                                            public void run() {
                                                if (dir) show(a, c, p);
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

    private static void changed(final Activity a, final OpenCode c) {
        Bg.run(new Runnable() {
            public void run() {
                try {
                    final JSONArray list = c.fileStatus();
                    Bg.ui(new Runnable() {
                        public void run() {
                            Sheet s = new Sheet(a, "Changed files");
                            if (list.length() == 0) s.message("No changes in the working tree.");
                            for (int i = 0; i < list.length(); i++) {
                                final JSONObject f = list.optJSONObject(i);
                                if (f == null) continue;
                                s.row(f.optString("path"), f.optString("status") + " · +" + f.optInt("added") + " / -"
                                        + f.optInt("removed"), false, new Runnable() {
                                    public void run() {
                                        if (!"deleted".equals(f.optString("status"))) open(a, c, f.optString("path"));
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
                    String[] queries = query.equals(".apk") ? new String[]{".apk", ".aab", ".zip"} : new String[]{query};
                    for (String q : queries) {
                        JSONArray list = c.findFiles(q);
                        for (int i = 0; i < list.length(); i++) {
                            String p = list.optString(i);
                            if (query.equals(".apk") && !(p.endsWith(".apk") || p.endsWith(".aab") || p.endsWith(".zip"))) continue;
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
                                        if (p.endsWith("/")) show(a, c, p.substring(0, p.length() - 1));
                                        else open(a, c, p);
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

    /** Shows a text file (with Copy and Download), or offers to download a binary one. */
    static void open(final Activity a, final OpenCode c, final String path) {
        Ui.toast(a, "Opening " + path);
        Bg.run(new Runnable() {
            public void run() {
                try {
                    final JSONObject f = c.readFile(path);
                    Bg.ui(new Runnable() {
                        public void run() {
                            final String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
                            final String content = f.optString("content", "");
                            final boolean base64 = "base64".equals(f.optString("encoding"))
                                    || "binary".equals(f.optString("type"));
                            final String mime = f.optString("mimeType", base64 ? "application/octet-stream" : "text/plain");
                            Sheet s = new Sheet(a, name);
                            if (base64) {
                                s.message("Binary file (" + mime + ").");
                            } else {
                                String shown = content.length() > 60000 ? content.substring(0, 60000) + "\n… (truncated)" : content;
                                View code = MdRender.codeBlock(a, shown, mime);
                                s.view(code);
                                String diff = f.optString("diff", "");
                                if (diff.length() > 0) {
                                    TextView t = Ui.text(a, 14, Ui.p().accent, true);
                                    t.setText("Uncommitted changes");
                                    t.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 4));
                                    s.view(t);
                                    s.view(MdRender.codeBlock(a, diff, "diff"));
                                }
                            }
                            s.button("Download", false, new Runnable() {
                                public void run() {
                                    byte[] bytes;
                                    try {
                                        bytes = base64 ? Base64.decode(content, Base64.DEFAULT) : content.getBytes("UTF-8");
                                    } catch (Exception e) {
                                        Ui.toast(a, "Couldn't decode the file");
                                        return;
                                    }
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
