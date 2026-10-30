package com.grapefruitmusic.ui;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Notice;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** Training and validation excerpt attribution, plus the shipped separator's provenance. */
public final class KaraokeCreditsFragment extends BaseFragment {
    private List<String[]> songs;

    static KaraokeCreditsFragment newInstance(String back) {
        KaraokeCreditsFragment f = new KaraokeCreditsFragment();
        f.setArguments(args(null, null, back));
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        final Context c = getActivity();
        final ListView list = new ListView(c);
        RowAdapter.style(list);
        LinearLayout header = Ui.header(c, backLabel(), "Karaoke Mode Credits", goBack());
        TextView intro = Ui.text(c, 15, R.attr.gfTextSecondary, false);
        intro.setSingleLine(false);
        intro.setText(
                "Stereo separator v1 processes audio on this device during playback. Its code and"
                    + " model weights are GPL-3.0-or-later. Training used 84 seven-second"
                    + " SiSEC18-MUS excerpts; 10 separate excerpts selected the model, and 50 were"
                    + " reserved for evaluation. The dataset is CC BY 4.0, by Fabian-Robert Stöter,"
                    + " Antoine Liutkus and Nobutaka Ito. The training and validation excerpts are"
                    + " credited below. Audio was decoded and remixed for training; source"
                    + " recordings are not included in the app. The dataset's vocal stems include"
                    + " lead and backing vocals, so the model does not promise to remove only the"
                    + " lead singer. PyTorch (BSD-3-Clause) was used for training; the phone runs"
                    + " Grapefruit's own lightweight processor.");
        intro.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        header.addView(intro);
        LinearLayout.LayoutParams datasetButton = new LinearLayout.LayoutParams(-1, -2);
        datasetButton.topMargin = Ui.dp(c, 8);
        header.addView(
                Ui.button(
                        c,
                        "Training dataset & licence",
                        v -> open("https://zenodo.org/records/3270814")),
                datasetButton);
        LinearLayout.LayoutParams sourceButton = new LinearLayout.LayoutParams(-1, -2);
        sourceButton.topMargin = Ui.dp(c, 8);
        header.addView(
                Ui.button(
                        c,
                        "Source & training recipe",
                        v ->
                                open(
                                        "https://"
                                                + AboutFragment.SOURCE
                                                + "/tree/codex/grapefruit-v4/tools/karaoke")),
                sourceButton);
        list.addHeaderView(header, null, false);
        final TextView status = Ui.status(c);
        list.addFooterView(status, null, false);
        final Adapter adapter = new Adapter(c);
        list.setAdapter(adapter);
        list.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(
                            AdapterView<?> parent, View view, int position, long id) {
                        String[] s = adapter.song(position - list.getHeaderViewsCount());
                        if (s != null) open(s[3]);
                    }
                });
        list.setOnItemLongClickListener(
                new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(
                            AdapterView<?> parent, View view, int position, long id) {
                        String[] s = adapter.song(position - list.getHeaderViewsCount());
                        if (s != null) copy(s[3]);
                        return true;
                    }
                });
        if (songs != null) {
            adapter.set(songs);
            status.setText(songs.size() + " credited excerpts");
        } else {
            status.setText("Loading…");
            final Context app = c.getApplicationContext();
            bg(
                    new Bg.Work<List<String[]>>() {
                        @Override
                        public List<String[]> run() throws Exception {
                            List<String[]> out = new ArrayList<String[]>();
                            BufferedReader in =
                                    new BufferedReader(
                                            new InputStreamReader(
                                                    app.getAssets().open("karaoke_credits.txt"),
                                                    "UTF-8"));
                            try {
                                String line;
                                while ((line = in.readLine()) != null) {
                                    String[] parts = line.split("\t");
                                    if (parts.length == 4) out.add(parts);
                                }
                            } finally {
                                in.close();
                            }
                            return out;
                        }
                    },
                    new Bg.Done<List<String[]>>() {
                        @Override
                        public void done(List<String[]> result, Exception error) {
                            songs = result;
                            if (result == null) {
                                status.setText("Couldn't read the credits.");
                                return;
                            }
                            adapter.set(result);
                            status.setText(result.size() + " credited excerpts");
                        }
                    });
        }
        return list;
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            copy(url);
        }
    }

    private void copy(String text) {
        Context c = getActivity();
        ClipboardManager clipboard =
                (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(text, text));
        Notice.show(c, "Copied " + text);
    }

    private static final class Adapter extends BaseAdapter {
        private final Context c;
        private List<String[]> songs = new ArrayList<String[]>();

        Adapter(Context c) {
            this.c = c;
        }

        void set(List<String[]> s) {
            songs = s;
            notifyDataSetChanged();
        }

        String[] song(int i) {
            return i >= 0 && i < songs.size() ? songs.get(i) : null;
        }

        @Override
        public int getCount() {
            return songs.size();
        }

        @Override
        public Object getItem(int position) {
            return songs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = (LinearLayout) convertView;
            if (row == null) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
                row.setBackgroundResource(Ui.resource(c, R.attr.gfRowBackground));
                row.addView(Ui.text(c, 16, R.attr.gfTextPrimary, false));
                row.addView(Ui.text(c, 13, R.attr.gfTextSecondary, false));
            }
            String[] s = songs.get(position);
            ((TextView) row.getChildAt(0)).setText(s[0]);
            ((TextView) row.getChildAt(1)).setText(s[1] + " · " + s[2]);
            return row;
        }
    }
}
