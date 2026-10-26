package com.grapefruitmusic.ui;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.util.Notice;

/** Settings › About Grapefruit Music. */
public final class AboutFragment extends BaseFragment {
    static final String COPYRIGHT = "Copyright © 2026 MagicFlute LLC";
    static final String DEVELOPER = "Haylie";
    static final String TELEGRAM = "afishinauto";
    static final String PAYPAL = "paypal.me/fishglub";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        final Context c = getActivity();
        ScrollView page = Grouped.page(c);
        Grouped g = Grouped.on(page);
        g.add(Ui.header(c, "Settings", null, goBack()));

        LinearLayout hero = new LinearLayout(c);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
        ImageView icon = new ImageView(c);
        // Drawn at exactly 88dp for each screen density, so it's never scaled up and blurry.
        icon.setImageResource(R.drawable.ic_about);
        hero.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 88), Ui.dp(c, 88)));
        TextView name = Ui.text(c, 24, R.attr.gfTextPrimary, true);
        name.setText(R.string.app_name);
        name.setPadding(0, Ui.dp(c, 10), 0, 0);
        TextView version = Ui.text(c, 15, R.attr.gfTextSecondary, false);
        version.setText("Version " + SettingsFragment.versionName(c));
        TextView copyright = Ui.text(c, 13, R.attr.gfTextSecondary, false);
        copyright.setText(COPYRIGHT);
        copyright.setPadding(0, Ui.dp(c, 6), 0, 0);
        for (TextView t : new TextView[]{name, version, copyright}) {
            // Full-width lines with centered text (a plain addView would left-align them).
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            hero.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        g.add(hero);

        g.section("Developer");
        g.value("Developer", DEVELOPER, null);
        linkRow(g, "Telegram", "@" + TELEGRAM, "https://t.me/" + TELEGRAM);
        linkRow(g, "Donate (PayPal)", PAYPAL, "https://" + PAYPAL);
        g.footer("Tap to open, or touch and hold to copy.");

        g.section("Acknowledgements");
        g.value("Conscrypt", "Apache License 2.0", null);
        g.value("Synced lyrics", "LRCLIB", null);
        g.value("Karaoke Mode", "MUSDB18, CC BY 4.0", null);
        g.footer("Conscrypt (github.com/google/conscrypt) provides secure connections on older Android "
                + "versions. Online lyrics come from LRCLIB (lrclib.net), a community database. Karaoke "
                + "Mode's model was trained by Grapefruit on the MUSDB18 7-second excerpts (Rafii, Liutkus, "
                + "Stöter, Mimilakis and Bittner, zenodo.org/records/3270814), licensed CC BY 4.0.");
        return page;
    }

    /** A row showing text in the accent color: tap opens url, touch and hold copies the text. */
    private void linkRow(Grouped g, String label, final String shown, final String url) {
        Context c = g.c;
        LinearLayout row = g.row(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(url, shown);
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                copy(shown);
                return true;
            }
        });
        TextView l = Ui.text(c, 17, R.attr.gfTextPrimary, false);
        l.setText(label);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView value = Ui.text(c, 16, R.attr.gfAccent, false);
        value.setText(shown);
        value.setPadding(Ui.dp(c, 8), 0, 0, 0);
        row.addView(value);
        g.addRow(row, true);
    }

    private void open(String url, String fallbackCopy) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            // Nothing on this phone can open links.
            copy(fallbackCopy);
        }
    }

    private void copy(String text) {
        Context c = getActivity();
        ClipboardManager clipboard = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(text, text));
        Notice.show(c, "Copied " + text);
    }
}
