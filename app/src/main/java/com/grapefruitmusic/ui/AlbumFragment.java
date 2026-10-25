package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.download.Downloads;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.source.DownloadedSource;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.ui.widget.ArtworkView;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.ArtworkLoader;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An album or playlist: artwork, Play / Shuffle, Download, and the track list. */
public final class AlbumFragment extends BaseFragment implements Downloads.Listener, Player.Listener {
    private ListView list;
    private TrackAdapter adapter;
    private TextView footer;
    private View downloadRow;
    private IconView downloadIcon;
    private TextView downloadLabel;
    private List<MediaItem> tracks = new ArrayList<MediaItem>();
    private Map<String, Integer> states = new HashMap<String, Integer>();

    static AlbumFragment newInstance(String sourceId, MediaItem container, String back) {
        AlbumFragment f = new AlbumFragment();
        f.setArguments(args(sourceId, container, back));
        return f;
    }

    private boolean isPlaylist() {
        return item().kind == MediaItem.PLAYLIST;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        final Context c = getActivity();
        final MediaItem album = item();
        list = new ListView(c);
        RowAdapter.style(list);

        LinearLayout header = Ui.header(c, backLabel(), null, goBack());

        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
        ArtworkView art = Ui.artwork(c, 136);
        ArtworkLoader.get(c).into(art, album, Ui.dp(c, 280));
        info.addView(art);
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(c, 14), Ui.dp(c, 2), 0, 0);
        TextView title = Ui.text(c, 20, R.attr.gfTextPrimary, true);
        title.setSingleLine(false);
        title.setMaxLines(3);
        title.setText(album.title);
        texts.addView(title);
        if (album.artist != null) {
            TextView artist = Ui.text(c, 18, R.attr.gfAccent, false);
            artist.setSingleLine(false);
            artist.setMaxLines(2);
            artist.setText(album.artist);
            artist.setPadding(0, Ui.dp(c, 2), 0, 0);
            artist.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openArtist();
                }
            });
            texts.addView(artist);
        }
        TextView meta = Ui.text(c, 13, R.attr.gfTextSecondary, false);
        meta.setText(isPlaylist() ? "Playlist" : (album.year > 0 ? String.valueOf(album.year) : ""));
        meta.setPadding(0, Ui.dp(c, 4), 0, 0);
        texts.addView(meta);
        info.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(info);

        LinearLayout buttons = new LinearLayout(c);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 4));
        LinearLayout playBtn = Ui.pill(c, Icons.PLAY, "Play");
        LinearLayout shuffleBtn = Ui.pill(c, Icons.SHUFFLE, "Shuffle");
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        r.leftMargin = Ui.dp(c, 12);
        buttons.addView(playBtn, l);
        buttons.addView(shuffleBtn, r);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!tracks.isEmpty()) Player.get(getActivity()).play(tracks, 0, false);
            }
        });
        shuffleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!tracks.isEmpty()) Player.get(getActivity()).play(tracks, -1, true);
            }
        });
        header.addView(buttons);

        LinearLayout dl = new LinearLayout(c);
        dl.setOrientation(LinearLayout.HORIZONTAL);
        dl.setGravity(Gravity.CENTER_VERTICAL);
        dl.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 6));
        dl.setBackgroundResource(Ui.resource(c, R.attr.gfRowBackground));
        downloadIcon = Ui.icon(c, Icons.DOWNLOAD, Ui.accent(c), 22);
        dl.addView(downloadIcon);
        downloadLabel = Ui.text(c, 16, R.attr.gfAccent, false);
        downloadLabel.setPadding(Ui.dp(c, 10), 0, 0, 0);
        dl.addView(downloadLabel);
        dl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDownloadTapped();
            }
        });
        dl.setVisibility(View.GONE);
        downloadRow = dl;
        header.addView(dl);

        list.addHeaderView(header, null, false);
        footer = Ui.status(c);
        footer.setGravity(Gravity.CENTER);
        list.addFooterView(footer, null, false);

        adapter = new TrackAdapter(c);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int i = position - list.getHeaderViewsCount();
                if (i >= 0 && i < tracks.size()) {
                    Player p = Player.get(getActivity());
                    p.play(tracks, i, p.isShuffle());
                }
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                int i = position - list.getHeaderViewsCount();
                if (i >= 0 && i < tracks.size()) TrackActions.show(AlbumFragment.this, tracks.get(i), source());
                return true;
            }
        });
        load();
        return list;
    }

    private void openArtist() {
        MediaItem album = item();
        MusicSource s = source();
        String id = album.artistId;
        if (s instanceof DownloadedSource) id = album.artist;
        if (id == null || isPlaylist()) return;
        MediaItem artist = new MediaItem(MediaItem.ARTIST, s.id(), id, album.artist);
        main().push(BrowseFragment.newInstance(s.id(), BrowseFragment.ARTIST_ALBUMS, artist, album.title));
    }

    private void load() {
        final MusicSource s = source();
        final MediaItem album = item();
        footer.setText("Loading…");
        bg(new Bg.Work<List<MediaItem>>() {
            @Override
            public List<MediaItem> run() throws Exception {
                return s.tracks(album);
            }
        }, new Bg.Done<List<MediaItem>>() {
            @Override
            public void done(List<MediaItem> result, Exception error) {
                if (error != null) {
                    footer.setText(describe(error, s));
                    return;
                }
                tracks = result;
                long total = 0;
                for (MediaItem t : result) total += t.durationMs;
                footer.setText(result.isEmpty() ? "No songs." : Format.songsSummary(result.size(), total));
                adapter.notifyDataSetChanged();
                refreshStates();
            }
        });
    }

    private void refreshStates() {
        final MusicSource s = source();
        if (s instanceof DownloadedSource) {
            downloadRow.setVisibility(tracks.isEmpty() ? View.GONE : View.VISIBLE);
            downloadIcon.setIcon(Icons.DOWNLOADED);
            downloadLabel.setText("Remove Download");
            return;
        }
        if (!s.canDownload() || tracks.isEmpty()) {
            downloadRow.setVisibility(View.GONE);
            return;
        }
        final Context app = getActivity().getApplicationContext();
        bg(new Bg.Work<Map<String, Integer>>() {
            @Override
            public Map<String, Integer> run() {
                return Downloads.get(app).states(s.id());
            }
        }, new Bg.Done<Map<String, Integer>>() {
            @Override
            public void done(Map<String, Integer> result, Exception error) {
                if (result == null) return;
                states = result;
                adapter.notifyDataSetChanged();
                int done = 0;
                int pending = 0;
                int failed = 0;
                for (MediaItem t : tracks) {
                    Integer st = states.get(t.id);
                    if (st == null) continue;
                    if (st == Downloads.DONE) done++;
                    else if (st == Downloads.FAILED) failed++;
                    else pending++;
                }
                downloadRow.setVisibility(View.VISIBLE);
                if (done == tracks.size()) {
                    downloadIcon.setIcon(Icons.DOWNLOADED);
                    downloadLabel.setText("Downloaded");
                } else if (pending > 0) {
                    downloadIcon.setIcon(Icons.DOWNLOAD);
                    downloadLabel.setText("Downloading… " + done + " of " + tracks.size());
                } else if (failed > 0) {
                    downloadIcon.setIcon(Icons.DOWNLOAD);
                    downloadLabel.setText("Download (" + failed + " failed, tap to retry)");
                } else {
                    downloadIcon.setIcon(Icons.DOWNLOAD);
                    downloadLabel.setText(done > 0 ? "Download remaining " + (tracks.size() - done) : "Download");
                }
            }
        });
    }

    private void onDownloadTapped() {
        final Context c = getActivity();
        final MusicSource s = source();
        final Downloads downloads = Downloads.get(c);
        if (s instanceof DownloadedSource) {
            Ui.dialog(c)
                    .setMessage("Remove “" + item().title + "” from this phone? You can download it again later.")
                    .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            TrackActions.removeDownloadedContainer(c, item(), tracks);
                            if (getFragmentManager() != null) getFragmentManager().popBackStack();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        boolean allDone = true;
        for (MediaItem t : tracks) {
            Integer st = states.get(t.id);
            if (st == null || st != Downloads.DONE) allDone = false;
        }
        if (allDone) {
            Ui.dialog(c)
                    .setMessage("Remove the downloaded copy of “" + item().title + "”?")
                    .setPositiveButton("Remove Download", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            for (MediaItem t : tracks) downloads.remove(t);
                            if (isPlaylist()) downloads.removePlaylist(s.id(), item().id);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            downloads.enqueue(tracks, isPlaylist() ? item() : null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Downloads.get(getActivity()).addListener(this);
        Player.get(getActivity()).addListener(this);
        adapter.notifyDataSetChanged();
        if (!tracks.isEmpty()) refreshStates();
    }

    @Override
    public void onPause() {
        Downloads.get(getActivity()).removeListener(this);
        Player.get(getActivity()).removeListener(this);
        super.onPause();
    }

    @Override
    public void onDownloadsChanged() {
        refreshStates();
    }

    @Override
    public void onPlayerChanged() {
        adapter.notifyDataSetChanged();
    }

    /** Numbered rows for albums; artwork rows for playlists. */
    private final class TrackAdapter extends BaseAdapter {
        private final Context context;
        private final int accent;
        private final int primary;
        private final int secondary;

        TrackAdapter(Context context) {
            this.context = context;
            accent = Ui.accent(context);
            primary = Ui.color(context, R.attr.gfTextPrimary);
            secondary = Ui.color(context, R.attr.gfTextSecondary);
        }

        @Override
        public int getCount() {
            return tracks.size();
        }

        @Override
        public Object getItem(int position) {
            return tracks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        private final class Holder {
            TextView number;
            IconView playing;
            ArtworkView art;
            TextView title;
            TextView subtitle;
            IconView state;
            TextView duration;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Ui.dp(context, 48));
                row.setPadding(Ui.dp(context, 16), Ui.dp(context, 6), Ui.dp(context, 16), Ui.dp(context, 6));
                row.setBackgroundResource(Ui.resource(context, R.attr.gfRowBackground));
                h = new Holder();
                h.number = Ui.text(context, 15, R.attr.gfTextSecondary, false);
                h.number.setGravity(Gravity.CENTER);
                row.addView(h.number, new LinearLayout.LayoutParams(Ui.dp(context, 28), ViewGroup.LayoutParams.WRAP_CONTENT));
                h.playing = Ui.icon(context, Icons.SPEAKER_HIGH, accent, 18);
                ((LinearLayout.LayoutParams) h.playing.getLayoutParams()).rightMargin = Ui.dp(context, 10);
                row.addView(h.playing);
                h.art = Ui.artwork(context, 44);
                row.addView(h.art);
                LinearLayout texts = new LinearLayout(context);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 8), 0);
                h.title = Ui.text(context, 16, R.attr.gfTextPrimary, false);
                h.subtitle = Ui.text(context, 13, R.attr.gfTextSecondary, false);
                texts.addView(h.title);
                texts.addView(h.subtitle);
                row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                h.state = Ui.icon(context, Icons.DOWNLOADED, secondary, 16);
                ((LinearLayout.LayoutParams) h.state.getLayoutParams()).rightMargin = Ui.dp(context, 8);
                row.addView(h.state);
                h.duration = Ui.text(context, 14, R.attr.gfTextSecondary, false);
                h.duration.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                row.addView(h.duration);
                row.setTag(h);
                convertView = row;
            } else {
                h = (Holder) convertView.getTag();
            }
            MediaItem t = tracks.get(position);
            MediaItem current = Player.get(context).current();
            boolean isCurrent = current != null && current.key().equals(t.key());
            boolean playlist = isPlaylist();

            h.art.setVisibility(playlist ? View.VISIBLE : View.GONE);
            if (playlist) ArtworkLoader.get(context).into(h.art, t, Ui.dp(context, 44));
            h.playing.setVisibility(isCurrent ? View.VISIBLE : View.GONE);
            h.number.setVisibility(!playlist && !isCurrent ? View.VISIBLE : View.GONE);
            if (playlist && isCurrent) h.playing.setVisibility(View.VISIBLE);
            int n = t.trackNumber > 0 ? t.trackNumber : position + 1;
            h.number.setText(String.valueOf(n));

            h.title.setText(t.title);
            h.title.setTextColor(isCurrent ? accent : primary);
            MediaItem album = item();
            boolean showArtist = playlist || (t.artist != null && album.artist != null
                    && !TextUtils.equals(t.artist, album.artist));
            h.subtitle.setText(showArtist && t.artist != null ? t.artist : "");
            h.subtitle.setVisibility(h.subtitle.getText().length() > 0 ? View.VISIBLE : View.GONE);

            Integer st = states.get(t.id);
            if (st != null && st == Downloads.DONE) {
                h.state.setIcon(Icons.DOWNLOADED);
                h.state.setVisibility(View.VISIBLE);
            } else if (st != null && (st == Downloads.QUEUED || st == Downloads.ACTIVE)) {
                h.state.setIcon(Icons.DOWNLOAD);
                h.state.setVisibility(View.VISIBLE);
            } else {
                h.state.setVisibility(View.GONE);
            }
            h.duration.setText(t.durationMs > 0 ? Format.duration(t.durationMs) : "");
            return convertView;
        }
    }
}
