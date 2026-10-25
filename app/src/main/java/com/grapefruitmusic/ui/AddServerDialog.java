package com.grapefruitmusic.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.source.Account;
import com.grapefruitmusic.source.JellyfinSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.source.SubsonicSource;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Notice;

/** Sign in to a Navidrome/Subsonic or Jellyfin server. */
final class AddServerDialog {
    private AddServerDialog() {
    }

    static void show(final Activity activity) {
        final Context c = activity;
        ScrollView scroll = new ScrollView(c);
        LinearLayout form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(form);

        final Segmented type = new Segmented(c, new String[]{"Navidrome / Subsonic", "Jellyfin"}, 0);
        form.addView(type);

        final EditText url = field(c, form, "Server address, e.g. http://192.168.1.10:4533",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText user = field(c, form, "Username", InputType.TYPE_CLASS_TEXT);
        final EditText pass = field(c, form, "Password",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText name = field(c, form, "Name (optional), e.g. Home", InputType.TYPE_CLASS_TEXT);
        final TextView error = Ui.text(c, 14, R.attr.gfTextSecondary, false);
        error.setSingleLine(false);
        error.setPadding(0, Ui.dp(c, 10), 0, 0);
        error.setVisibility(View.GONE);
        form.addView(error);

        type.setListener(new Segmented.Listener() {
            @Override
            public void onSelected(int index) {
                url.setHint(index == 1
                        ? "Server address, e.g. http://192.168.1.10:8096"
                        : "Server address, e.g. http://192.168.1.10:4533");
            }
        });

        final GrapefruitDialog dialog = Ui.dialog(c)
                .setTitle("Add a Server")
                .setView(scroll)
                .setPositiveButton("Sign In", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                final View signIn = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                signIn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final boolean isJellyfin = type.selected() == 1;
                        final String base = Http.normalizeBaseUrl(url.getText().toString());
                        final String username = user.getText().toString().trim();
                        final String password = pass.getText().toString();
                        String label = name.getText().toString().trim();
                        if (url.getText().toString().trim().length() == 0 || username.length() == 0) {
                            showError(error, "Enter the server address and your username.");
                            return;
                        }
                        if (label.length() == 0) label = isJellyfin ? "Jellyfin" : "Navidrome";
                        final String finalLabel = label;
                        signIn.setEnabled(false);
                        error.setTextColor(Ui.color(c, R.attr.gfTextSecondary));
                        error.setText("Connecting…");
                        error.setVisibility(View.VISIBLE);
                        Bg.run(new Bg.Work<String[]>() {
                            @Override
                            public String[] run() throws Exception {
                                if (isJellyfin) return JellyfinSource.login(base, username, password);
                                Account probe = new Account();
                                probe.url = base;
                                probe.username = username;
                                probe.password = password;
                                new SubsonicSource(probe).ping();
                                return null;
                            }
                        }, new Bg.Done<String[]>() {
                            @Override
                            public void done(String[] session, Exception e) {
                                signIn.setEnabled(true);
                                if (e != null) {
                                    showError(error, explain(e));
                                    return;
                                }
                                Sources sources = Sources.get(c);
                                if (isJellyfin) {
                                    sources.addAccount(Account.JELLYFIN, finalLabel, base, username, null,
                                            session[0], session[1]);
                                } else {
                                    sources.addAccount(Account.SUBSONIC, finalLabel, base, username, password,
                                            null, null);
                                }
                                Notice.show(c, "Connected to " + finalLabel);
                                dialog.dismiss();
                            }
                        });
                    }
                });
            }
        });
        dialog.show();
    }

    private static String explain(Exception e) {
        if (e instanceof Http.StatusException) {
            int code = ((Http.StatusException) e).code;
            if (code == 401 || code == 403) return "Wrong username or password.";
            if (code == 404) return "That address doesn't look like a music server.";
            return "The server answered with an error (" + code + ").";
        }
        if (e instanceof java.net.UnknownHostException) return "Can't find that server. Check the address.";
        if (e instanceof java.net.ConnectException || e instanceof java.net.SocketTimeoutException) {
            return "Can't connect. Is the server running, and is the phone on the same network?";
        }
        if (e instanceof javax.net.ssl.SSLException) {
            return "Secure connection failed (" + e.getMessage() + "). Self-signed certificates aren't supported.";
        }
        return e.getMessage() != null ? e.getMessage() : "Couldn't connect.";
    }

    private static void showError(TextView error, String text) {
        error.setTextColor(0xffff3b30);
        error.setText(text);
        error.setVisibility(View.VISIBLE);
    }

    private static EditText field(Context c, LinearLayout form, String hint, int inputType) {
        EditText e = Ui.field(c, 16, false, hint);
        e.setSingleLine(true);
        e.setInputType(inputType);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 10);
        form.addView(e, lp);
        return e;
    }
}
