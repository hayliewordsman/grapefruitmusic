package io.github.hayliewordsman.grapefruitai.ui;

import android.app.Activity;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Arrays;

import io.github.hayliewordsman.grapefruitai.BuildConfig;
import io.github.hayliewordsman.grapefruitai.or.Models;
import io.github.hayliewordsman.grapefruitai.or.OpenRouter;
import io.github.hayliewordsman.grapefruitai.secure.Secrets;
import io.github.hayliewordsman.grapefruitai.util.Bg;
import io.github.hayliewordsman.grapefruitai.util.Format;
import io.github.hayliewordsman.grapefruitai.util.Prefs;

/** Settings as Grapefruit's grouped list: appearance, OpenRouter, OpenCode backend, security, about. */
public final class SettingsPage extends Page {
    private ScrollView page;

    public SettingsPage(MainActivity a) {
        super(a);
    }

    @Override
    protected View build() {
        page = Grouped.page(a);
        fill();
        return page;
    }

    @Override
    public void onShow() {
        if (page != null) fill();
    }

    private void fill() {
        Grouped g = Grouped.on(page);
        TextView title = Ui.title(a, "Settings");
        title.setPadding(Ui.dp(a, 18), Ui.dp(a, 14), Ui.dp(a, 18), 0);
        g.add(title);

        g.section("Appearance");
        final int theme = Prefs.integer(Prefs.THEME, Palette.GRAPEFRUIT_DARK);
        g.value("Theme", Palette.NAMES[Math.max(0, Math.min(3, theme))], new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Sheet s = new Sheet(a, "Theme");
                for (int i = 0; i < Palette.NAMES.length; i++) {
                    final int id = i;
                    s.row(Palette.NAMES[i], null, i == theme, new Runnable() {
                        @Override
                        public void run() {
                            if (id != theme) a.applyTheme(id);
                        }
                    });
                }
                s.button("Close", true, null).show();
            }
        });
        g.footer("The four Grapefruit themes. If Grapefruit Settings is installed, its universal theme is followed.");

        g.section("OpenRouter");
        String keyState = Secrets.isLocked() ? "Locked" : Secrets.has(Secrets.OPENROUTER_KEY)
                ? mask(Secrets.get(Secrets.OPENROUTER_KEY)) : "Not set";
        g.value("API key", keyState, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Secrets.isLocked()) unlockDialog(a, new Runnable() {
                    public void run() {
                        fill();
                    }
                });
                else editKey();
            }
        });
        if (Secrets.has(Secrets.OPENROUTER_KEY)) {
            g.action("Check key and credit", false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checkKey();
                }
            });
            g.action("Clear API key", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Sheet.confirm(a, "Clear the OpenRouter key?", "It's removed from memory and storage.", "Clear",
                            true, new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Secrets.clear(Secrets.OPENROUTER_KEY);
                                    } catch (Exception e) {
                                        Ui.toast(a, e.getMessage());
                                    }
                                    fill();
                                }
                            });
                }
            });
        }
        g.value("Key storage", Secrets.describeMode(Secrets.mode()), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseStorage();
            }
        });
        g.footer("Your own key, used only for requests from this device to openrouter.ai. It is never built into "
                + "the app, never written to logs, and never sent to your OpenCode server. By default it is kept in "
                + "memory for this session only.");
        g.value("Model list", Models.all().size() + " models", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.toast(a, "Refreshing…");
                Models.load(a, true, new Models.Listener() {
                    @Override
                    public void onModels(java.util.List<io.github.hayliewordsman.grapefruitai.or.OrModel> models,
                                         String error) {
                        Ui.toast(a, error != null ? "Couldn't refresh: " + error : models.size() + " models");
                        fill();
                    }
                });
            }
        });
        g.toggle("Web search in new chats", Prefs.bool(Prefs.WEB_SEARCH, false), new Toggle.Listener() {
            @Override
            public void onChanged(boolean on) {
                Prefs.put(Prefs.WEB_SEARCH, on);
            }
        });
        g.footer("Web search uses OpenRouter's web plugin and is charged by OpenRouter per search (USD). With it "
                + "off, models have no live internet access.");

        g.section("OpenCode backend");
        final String url = Prefs.str(Prefs.OC_URL, "");
        g.value("Server URL", url.length() > 0 ? url : "Not set", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editText("OpenCode server URL", "https://agent.example.com", Prefs.OC_URL, InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_URI, "Must be HTTPS. Put opencode serve behind a TLS reverse "
                        + "proxy; don't expose its port directly.");
            }
        });
        g.value("Username", Prefs.str(Prefs.OC_USER, "opencode"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editText("Username", "opencode", Prefs.OC_USER, InputType.TYPE_CLASS_TEXT, "OPENCODE_SERVER_USERNAME "
                        + "on the server (default: opencode).");
            }
        });
        g.value("Password", Secrets.isLocked() ? "Locked" : Secrets.has(Secrets.OPENCODE_PASSWORD) ? "Set" : "Not set",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Secrets.isLocked()) {
                            unlockDialog(a, null);
                            return;
                        }
                        editSecret("Server password", Secrets.OPENCODE_PASSWORD,
                                "OPENCODE_SERVER_PASSWORD on the server (HTTP basic auth). Stored like the API key.");
                    }
                });
        final String dir = Prefs.str(Prefs.OC_DIRECTORY, "");
        g.value("Project", dir.length() > 0 ? dir : "Server default", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((CodePage) a.page(MainActivity.TAB_CODE)).chooseProject();
            }
        });
        g.value("Protected projects", Prefs.str(Prefs.OC_PROTECTED, "").length() > 0 ? "Set" : "None",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        editText("Protected projects", "/srv/production, /home/me/release-app", Prefs.OC_PROTECTED,
                                InputType.TYPE_CLASS_TEXT, "Comma-separated paths. Any agent request touching them "
                                        + "is treated as destructive and needs a second confirmation.");
                    }
                });
        g.action("Test connection", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((CodePage) a.page(MainActivity.TAB_CODE)).reconnect(true);
            }
        });
        g.footer("Agent runs use the provider keys configured on the OpenCode server, billed to the server's "
                + "account. This app does not send your OpenRouter key to the server and does not change the "
                + "server's secrets: OpenCode's server API has no per-request key (BYOK) option.");

        g.section("Security");
        g.footer("HTTPS only, with certificate and host-name checks always on (TLS 1.2+ through Conscrypt on "
                + "Android 4.2.2, plus a few bundled root certificates the old system lacks). No analytics. "
                + "Conversations stay on this device and are excluded from backups.\n\n"
                + "Saved keys (optional): \"Android Keystore\" (Android 4.3+) encrypts them with a key held by the "
                + "system keystore; it stops someone copying the app's files, but not someone with root on the "
                + "device. Android 4.2.2 has no app keystore, so there the option is a passphrase you type after "
                + "each launch (AES-256 + HMAC, key from PBKDF2); it is only as strong as the passphrase.");
        g.action("Forget all keys and passwords", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Sheet.confirm(a, "Forget everything?", "Removes the OpenRouter key and the server password from "
                        + "memory and storage.", "Forget", true, new Runnable() {
                    @Override
                    public void run() {
                        Secrets.clearAll();
                        a.secretsChanged();
                        fill();
                    }
                });
            }
        });

        g.section("About");
        g.add(wordmark());
        g.value("Version", BuildConfig.VERSION_NAME, null);
        g.footer("Geist and Geist Pixel by Vercel (SIL Open Font License 1.1). Conscrypt (Apache License 2.0). "
                + "Not affiliated with OpenRouter or OpenCode.");
    }

    private View wordmark() {
        LinearLayout box = Ui.vertical(a);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 8));
        android.widget.ImageView icon = new android.widget.ImageView(a);
        icon.setImageResource(io.github.hayliewordsman.grapefruitai.R.mipmap.ic_launcher);
        box.addView(icon, new LinearLayout.LayoutParams(Ui.dp(a, 72), Ui.dp(a, 72)));
        TextView mark = new TextView(a);
        mark.setTypeface(Fonts.wordmark(a));
        mark.setTextSize(30);
        mark.setTextColor(Ui.p().accent);
        mark.setText("Grapefruit AI");
        mark.setPadding(0, Ui.dp(a, 8), 0, 0);
        box.addView(mark);
        return box;
    }

    static String mask(String key) {
        if (key == null) return "";
        if (key.length() <= 12) return "••••";
        return key.substring(0, 9) + "…" + key.substring(key.length() - 4);
    }

    private void editKey() {
        editSecret("OpenRouter API key", Secrets.OPENROUTER_KEY, "Create a key at openrouter.ai/keys. It looks "
                + "like sk-or-v1-…. You can set a credit limit on the key there.");
    }

    private void editSecret(String title, final String name, String help) {
        final EditText field = Ui.field(a, "Paste here", false);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setTypeface(Fonts.regular());
        new Sheet(a, title).message(help).view(field)
                .button("Cancel", false, null)
                .button("Save", true, new Runnable() {
                    @Override
                    public void run() {
                        String v = field.getText().toString().trim();
                        if (v.length() == 0) return;
                        try {
                            Secrets.put(name, v);
                            Ui.toast(a, Secrets.mode() == Secrets.SESSION ? "Kept for this session" : "Saved, encrypted");
                            if (Secrets.OPENCODE_PASSWORD.equals(name)) a.secretsChanged();
                        } catch (Exception e) {
                            Ui.toast(a, "Couldn't save: " + e.getMessage());
                        }
                        fill();
                    }
                }).show();
    }

    private void editText(String title, String hint, final String pref, int inputType, String help) {
        final EditText field = Ui.field(a, hint, false);
        field.setInputType(inputType);
        field.setText(Prefs.str(pref, pref.equals(Prefs.OC_USER) ? "opencode" : ""));
        new Sheet(a, title).message(help).view(field)
                .button("Cancel", false, null)
                .button("Save", true, new Runnable() {
                    @Override
                    public void run() {
                        String v = field.getText().toString().trim();
                        if (pref.equals(Prefs.OC_URL) && v.length() > 0) {
                            try {
                                v = io.github.hayliewordsman.grapefruitai.net.Http.requireHttps(v);
                            } catch (java.io.IOException e) {
                                Sheet.alert(a, "Not saved", e.getMessage());
                                return;
                            }
                        }
                        Prefs.put(pref, v);
                        ((CodePage) a.page(MainActivity.TAB_CODE)).configChanged();
                        fill();
                    }
                }).show();
    }

    private void chooseStorage() {
        final int mode = Secrets.mode();
        Sheet s = new Sheet(a, "Key storage");
        s.message("Where the OpenRouter key and the server password are kept.");
        s.row("This session only (recommended)", "Nothing is written to storage. Enter the key again after "
                + "Android closes the app.", mode == Secrets.SESSION, new Runnable() {
            @Override
            public void run() {
                Secrets.useSessionOnly();
                fill();
            }
        });
        if (Secrets.keystoreAvailable()) {
            s.row("Saved, Android Keystore", "Encrypted with a key held by Android's keystore. Protects against "
                    + "copying the app's files; not against a rooted device.", mode == Secrets.KEYSTORE, new Runnable() {
                @Override
                public void run() {
                    try {
                        Secrets.useKeystore();
                        Ui.toast(a, "Saved, encrypted");
                    } catch (Exception e) {
                        Sheet.alert(a, "Keystore unavailable", String.valueOf(e.getMessage()));
                    }
                    fill();
                }
            });
        } else {
            s.message("Android Keystore storage needs Android 4.3 or later; this device runs "
                    + android.os.Build.VERSION.RELEASE + ".");
        }
        s.row("Saved, passphrase", "Encrypted with a key made from a passphrase (8+ characters) you enter after "
                + "each launch. Works on every Android version.", mode == Secrets.PASSPHRASE, new Runnable() {
            @Override
            public void run() {
                setPassphrase();
            }
        });
        s.button("Close", true, null).show();
    }

    private void setPassphrase() {
        final EditText field = Ui.field(a, "Passphrase", false);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setTypeface(Fonts.regular());
        new Sheet(a, "Choose a passphrase").message("At least 8 characters. If you forget it, clear the keys and "
                + "enter them again.").view(field)
                .button("Cancel", false, null)
                .button("Save", true, new Runnable() {
                    @Override
                    public void run() {
                        final char[] pass = field.getText().toString().toCharArray();
                        field.setText("");
                        Ui.toast(a, "Encrypting…");
                        Bg.run(new Runnable() {
                            @Override
                            public void run() {
                                String error = null;
                                try {
                                    Secrets.usePassphrase(pass);
                                } catch (Exception e) {
                                    error = e.getMessage();
                                } finally {
                                    Arrays.fill(pass, '\0');
                                }
                                final String err = error;
                                Bg.ui(new Runnable() {
                                    @Override
                                    public void run() {
                                        Ui.toast(a, err != null ? "Not saved: " + err : "Saved, encrypted");
                                        fill();
                                    }
                                });
                            }
                        });
                    }
                }).show();
    }

    /** Asks for the passphrase and unlocks the saved keys. */
    public static void unlockDialog(final Activity a, final Runnable then) {
        final EditText field = Ui.field(a, "Passphrase", false);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setTypeface(Fonts.regular());
        new Sheet(a, "Unlock saved keys").message("Enter the passphrase that protects your saved keys.").view(field)
                .button("Cancel", false, null)
                .button("Unlock", true, new Runnable() {
                    @Override
                    public void run() {
                        final char[] pass = field.getText().toString().toCharArray();
                        Bg.run(new Runnable() {
                            @Override
                            public void run() {
                                String error = null;
                                try {
                                    Secrets.unlock(pass);
                                } catch (Exception e) {
                                    error = "Wrong passphrase";
                                } finally {
                                    Arrays.fill(pass, '\0');
                                }
                                final String err = error;
                                Bg.ui(new Runnable() {
                                    @Override
                                    public void run() {
                                        Ui.toast(a, err != null ? err : "Unlocked");
                                        if (err == null && a instanceof MainActivity) ((MainActivity) a).secretsChanged();
                                        if (err == null && then != null) then.run();
                                    }
                                });
                            }
                        });
                    }
                }).show();
    }

    private void checkKey() {
        final String key = Secrets.get(Secrets.OPENROUTER_KEY);
        if (key == null) return;
        Ui.toast(a, "Checking…");
        Bg.run(new Runnable() {
            @Override
            public void run() {
                String text;
                try {
                    JSONObject d = new OpenRouter(key).keyInfo();
                    StringBuilder b = new StringBuilder();
                    b.append("Key: ").append(d.optString("label", "?")).append('\n');
                    b.append("Usage (all time): ").append(usd(d, "usage")).append('\n');
                    b.append("Today: ").append(usd(d, "usage_daily")).append(" · this month: ")
                            .append(usd(d, "usage_monthly")).append('\n');
                    if (d.isNull("limit")) b.append("Credit limit on this key: none\n");
                    else b.append("Credit limit: ").append(usd(d, "limit")).append(", remaining ")
                            .append(usd(d, "limit_remaining")).append('\n');
                    if (d.optBoolean("is_free_tier", false)) b.append("Free tier account\n");
                    b.append("\nAmounts in US dollars, as reported by OpenRouter.");
                    text = b.toString();
                } catch (Exception e) {
                    text = "Check failed: " + e.getMessage();
                }
                final String t = text;
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        Sheet.alert(a, "OpenRouter key", t);
                    }
                });
            }
        });
    }

    private static String usd(JSONObject d, String key) {
        if (!d.has(key) || d.isNull(key)) return "not reported";
        return Format.cost(d.optDouble(key, 0));
    }
}
