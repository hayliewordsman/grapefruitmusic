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

/**
 * The Free Music Archive songs Karaoke Mode learned from, with their artists and licences (most
 * ask to be credited). Tap one to open its page; touch and hold to copy the link.
 */
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
        intro.setText("Karaoke Mode learned from these songs on the Free Music Archive (freemusicarchive.org), "
                + "which Demucs (MIT License) split into voice and backing, and from the MUSDB18 excerpts "
                + "(CC BY 4.0). Thank you to the artists.");
        intro.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        header.addView(intro);
        list.addHeaderView(header, null, false);
        final TextView status = Ui.status(c);
        list.addFooterView(status, null, false);
        final Adapter adapter = new Adapter(c);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String[] s = adapter.song(position - list.getHeaderViewsCount());
                if (s != null) open(s[3]);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                String[] s = adapter.song(position - list.getHeaderViewsCount());
                if (s != null) copy(s[3]);
                return true;
            }
        });
        if (songs != null) {
            adapter.set(songs);
            status.setText(songs.size() + " songs");
        } else {
            status.setText("Loading…");
            final Context app = c.getApplicationContext();
            bg(new Bg.Work<List<String[]>>() {
                @Override
                public List<String[]> run() throws Exception {
                    List<String[]> out = new ArrayList<String[]>();
                    BufferedReader in = new BufferedReader(new InputStreamReader(
                            app.getAssets().open("karaoke_credits.txt"), "UTF-8"));
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
            }, new Bg.Done<List<String[]>>() {
                @Override
                public void done(List<String[]> result, Exception error) {
                    songs = result;
                    if (result == null) {
                        status.setText("Couldn't read the credits.");
                        return;
                    }
                    adapter.set(result);
                    status.setText(result.size() + " songs");
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
        ClipboardManager clipboard = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
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
