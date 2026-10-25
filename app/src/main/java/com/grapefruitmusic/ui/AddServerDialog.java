package com.grapefruitmusic.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.source.Account;
import com.grapefruitmusic.source.JellyfinSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.source.SubsonicSource;
import com.grapefruitmusic.util.Bg;

/** Sign in to a Navidrome/Subsonic or Jellyfin server. */
final class AddServerDialog {
    private AddServerDialog() {
    }

    static void show(final Activity activity) {
        final Context c = activity;
        ScrollView scroll = new ScrollView(c);
        LinearLayout form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 16);
        form.setPadding(pad, pad / 2, pad, 0);
        scroll.addView(form);

        final RadioGroup type = new RadioGroup(c);
        type.setOrientation(RadioGroup.VERTICAL);
        final RadioButton subsonic = new RadioButton(c);
        subsonic.setText("Navidrome / Subsonic");
        subsonic.setId(View.generateViewId());
        final RadioButton jellyfin = new RadioButton(c);
        jellyfin.setText("Jellyfin");
        jellyfin.setId(View.generateViewId());
        type.addView(subsonic);
        type.addView(jellyfin);
        type.check(subsonic.getId());
        form.addView(type);

        final EditText url = field(c, "Server address, e.g. http://192.168.1.10:4533",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText user = field(c, "Username", InputType.TYPE_CLASS_TEXT);
        final EditText pass = field(c, "Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText name = field(c, "Name (optional), e.g. Home", InputType.TYPE_CLASS_TEXT);
        form.addView(url);
        form.addView(user);
        form.addView(pass);
        form.addView(name);
        final TextView error = new TextView(c);
        error.setTextColor(0xffe0484e);
        error.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
        error.setVisibility(View.GONE);
        form.addView(error);

        type.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                url.setHint(checkedId == jellyfin.getId()
                        ? "Server address, e.g. http://192.168.1.10:8096"
                        : "Server address, e.g. http://192.168.1.10:4533");
            }
        });

        final AlertDialog dialog = Ui.dialog(c)
                .setTitle("Add a Server")
                .setView(scroll)
                .setPositiveButton("Sign In", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                final View signIn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                signIn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final boolean isJellyfin = type.getCheckedRadioButtonId() == jellyfin.getId();
                        final String base = Http.normalizeBaseUrl(url.getText().toString());
                        final String username = user.getText().toString().trim();
                        final String password = pass.getText().toString();
                        String label = name.getText().toString().trim();
                        if (url.getText().toString().trim().length() == 0 || username.length() == 0) {
                            error.setText("Enter the server address and your username.");
                            error.setVisibility(View.VISIBLE);
                            return;
                        }
                        if (label.length() == 0) label = isJellyfin ? "Jellyfin" : "Navidrome";
                        final String finalLabel = label;
                        signIn.setEnabled(false);
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
                                    error.setText(explain(e));
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
                                Toast.makeText(c, "Connected to " + finalLabel, Toast.LENGTH_SHORT).show();
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

    private static EditText field(Context c, String hint, int inputType) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(inputType);
        return e;
    }
}
