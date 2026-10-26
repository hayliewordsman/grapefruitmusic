package com.grapefruitmusic.ui;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.radio.Radio;
import com.grapefruitmusic.radio.RadioFiles;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Notice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Radio › Radio Details: what Grapefruit found out about this tablet's FM radio, to copy or save
 * and send on, so a radio that doesn't work yet can be supported. Also opens the tablet's own
 * radio app, if it has one.
 */
public final class RadioDetailsFragment extends BaseFragment {
    private String report;
    private TextView text;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        final Context c = getActivity();
        ScrollView page = new ScrollView(c);
        page.setFillViewport(true);
        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(c, 24));
        page.addView(content);
        content.addView(Ui.header(c, "Radio", "Radio Details", goBack()));

        TextView intro = Ui.text(c, 15, R.attr.gfTextSecondary, false);
        intro.setSingleLine(false);
        intro.setText("If the radio doesn't work on this tablet, tap Save Radio Files and send "
                + "Grapefruit/radio-files.zip: it holds these details and the tablet's own radio app and "
                + "libraries, which is what support for this radio is built from. Copy or Save Details "
                + "keeps just the text. Nothing here is sent anywhere by Grapefruit.");
        intro.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
        content.addView(intro);

        LinearLayout actions = new LinearLayout(c);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 8));
        LinearLayout copy = Ui.pill(c, Icons.QUEUE, "Copy");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (report == null) return;
                ClipboardManager clipboard = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Radio details", report));
                Notice.show(c, "Copied the radio details");
            }
        });
        actions.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout save = Ui.pill(c, Icons.DOWNLOAD, "Save Details");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save(c);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = Ui.dp(c, 12);
        actions.addView(save, lp);
        content.addView(actions);

        LinearLayout files = Ui.pill(c, Icons.DOWNLOAD, "Save Radio Files");
        files.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveFiles(c);
            }
        });
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
        content.addView(files, flp);

        for (final PackageInfo app : radioApps(c)) {
            CharSequence name = app.applicationInfo != null
                    ? c.getPackageManager().getApplicationLabel(app.applicationInfo) : app.packageName;
            LinearLayout open = Ui.pill(c, Icons.RADIO, "Open " + name);
            open.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent launch = c.getPackageManager().getLaunchIntentForPackage(app.packageName);
                    try {
                        if (launch != null) startActivity(launch);
                    } catch (ActivityNotFoundException e) {
                        Notice.show(c, "That app didn't open");
                    }
                }
            });
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            olp.setMargins(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 4));
            content.addView(open, olp);
        }

        text = Ui.text(c, 12, R.attr.gfTextPrimary, false);
        text.setSingleLine(false);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        text.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), 0);
        text.setText(report != null ? report : "Looking at this tablet's radio…");
        content.addView(text);

        if (report == null) {
            final Radio radio = Radio.get(c);
            bg(new Bg.Work<String>() {
                @Override
                public String run() {
                    return radio.report();
                }
            }, new Bg.Done<String>() {
                @Override
                public void done(String result, Exception error) {
                    report = result != null ? result : "Couldn't look: " + error;
                    if (text != null) text.setText(report);
                }
            });
        }
        return page;
    }

    @Override
    public void onDestroyView() {
        text = null;
        super.onDestroyView();
    }

    /** Other apps that look like an FM radio and can be opened. */
    private static List<PackageInfo> radioApps(Context c) {
        List<PackageInfo> out = new ArrayList<PackageInfo>();
        PackageManager pm = c.getPackageManager();
        for (PackageInfo p : pm.getInstalledPackages(0)) {
            String name = p.packageName.toLowerCase(Locale.US);
            if (p.packageName.equals(c.getPackageName())) continue;
            if ((name.contains("fmradio") || name.contains(".fm") || name.endsWith("radio"))
                    && pm.getLaunchIntentForPackage(p.packageName) != null) {
                out.add(p);
            }
        }
        return out;
    }

    /** The details and the tablet's own radio files, zipped for sending. */
    private void saveFiles(final Context c) {
        if (report == null) {
            Notice.show(c, "Still looking at this tablet's radio…");
            return;
        }
        final String body = report;
        Notice.show(c, "Saving the radio files…");
        bg(new Bg.Work<RadioFiles.Saved>() {
            @Override
            public RadioFiles.Saved run() throws IOException {
                return RadioFiles.save(c, body);
            }
        }, new Bg.Done<RadioFiles.Saved>() {
            @Override
            public void done(RadioFiles.Saved saved, Exception error) {
                Notice.showLong(c, saved != null ? "Saved " + saved.files + (saved.files == 1 ? " file to " : " files to ")
                        + saved.zip.getPath() : "Couldn't save: " + error.getMessage());
            }
        });
    }

    private void save(final Context c) {
        if (report == null) return;
        final String body = report;
        bg(new Bg.Work<File>() {
            @Override
            public File run() throws IOException {
                File dir = new File(Environment.getExternalStorageDirectory(), "Grapefruit");
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("can't make " + dir);
                File f = new File(dir, "radio-details.txt");
                FileOutputStream out = new FileOutputStream(f);
                try {
                    out.write(body.getBytes("UTF-8"));
                } finally {
                    out.close();
                }
                return f;
            }
        }, new Bg.Done<File>() {
            @Override
            public void done(File f, Exception error) {
                Notice.showLong(c, f != null ? "Saved to " + f.getPath() : "Couldn't save: " + error.getMessage());
            }
        });
    }
}
