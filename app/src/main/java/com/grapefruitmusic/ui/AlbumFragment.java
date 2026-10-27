package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.data.PlaylistMeta;
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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An album or playlist: artwork, Play / Shuffle, Download, and the track list. Playlists have an
 * Edit mode for the name, description and cover photo, for removing songs and for adding them
 * (Add Songs); Done saves it all.
 */
public final class AlbumFragment extends BaseFragment implements Downloads.Listener, Player.Listener {
    private static final int RED = 0xffff3b30;

    private ListView list;
    private TrackAdapter adapter;
    private TextView footer;
    private View downloadRow;
    private IconView downloadIcon;
    private TextView downloadLabel;
    private View backButton;
    private TextView cancelButton;
    private TextView editButton;
    private ArtworkView art;
    private TextView coverLabel;
    private TextView titleView;
    private EditText nameField;
    private TextView descriptionView;
    private EditText descriptionField;
    private TextView editNote;
    private View addSongsButton;
    private View playButtons;
    /** Loaded tracks, kept while a page on top of this one (an artist) is open. */
    private List<MediaItem> tracks = new ArrayList<MediaItem>();
    private boolean loaded;
    private boolean loading;
    private Map<String, Integer> states = new HashMap<String, Integer>();

    // Edit mode. Nothing is saved until Done.
    private boolean editing;
    private boolean saving;
    /** The songs left; removed ones go to removedTracks. */
    private List<MediaItem> editTracks = new ArrayList<MediaItem>();
    private final List<MediaItem> removedTracks = new ArrayList<MediaItem>();
    /** Songs added with Add Songs (also in editTracks, at the end), saved after the removals. */
    private final List<MediaItem> addedTracks = new ArrayList<MediaItem>();
    /** Text typed so far, kept if the page's views are rebuilt. */
    private String editName;
    private String editDescription;
    /** A new cover file, "" to remove the cover photo, or null for no change. */
    private String pendingCover;

    private static final String ARG_EDIT = "edit";

    static AlbumFragment newInstance(String sourceId, MediaItem container, String back) {
        AlbumFragment f = new AlbumFragment();
        f.setArguments(args(sourceId, container, back));
        return f;
    }

    /** A playlist that opens in edit mode (a new one, ready for Add Songs). */
    static AlbumFragment editing(String sourceId, MediaItem playlist, String back) {
        AlbumFragment f = newInstance(sourceId, playlist, back);
        f.getArguments().putBoolean(ARG_EDIT, true);
        return f;
    }

    private boolean isPlaylist() {
        return item().kind == MediaItem.PLAYLIST;
    }

    @Override
    String backTitle() {
        return item().title;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        final Context c = getActivity();
        final MediaItem album = item();
        list = new ListView(c);
        RowAdapter.style(list);

        LinearLayout header = Ui.header(c, backLabel(), null, goBack());
        header.addView(topBar(c, header), 0);

        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
        info.addView(cover(c), new LinearLayout.LayoutParams(Ui.dp(c, 136), Ui.dp(c, 136)));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(c, 14), Ui.dp(c, 2), 0, 0);
        titleView = Ui.text(c, 20, R.attr.gfTextPrimary, true);
        titleView.setSingleLine(false);
        titleView.setMaxLines(3);
        titleView.setText(album.title);
        texts.addView(titleView);
        nameField = Ui.field(c, 17, true, "Playlist Name");
        nameField.setSingleLine(true);
        nameField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameField.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        nameField.setVisibility(View.GONE);
        texts.addView(nameField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
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

        descriptionView = Ui.text(c, 15, R.attr.gfTextSecondary, false);
        descriptionView.setSingleLine(false);
        descriptionView.setMaxLines(6);
        descriptionView.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 2));
        header.addView(descriptionView);
        descriptionField = Ui.field(c, 15, false, "Description");
        descriptionField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        descriptionField.setSingleLine(false);
        descriptionField.setMinLines(2);
        descriptionField.setMaxLines(6);
        descriptionField.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        fl.topMargin = Ui.dp(c, 10);
        header.addView(descriptionField, fl);
        editNote = Ui.text(c, 13, R.attr.gfTextSecondary, false);
        editNote.setSingleLine(false);
        editNote.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 4));
        header.addView(editNote);
        LinearLayout addSongs = Ui.pill(c, Icons.PLUS, "Add Songs");
        addSongs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (editing && !saving) AddSongsSheet.show(getActivity(), source(), addTarget);
            }
        });
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        al.topMargin = Ui.dp(c, 12);
        al.bottomMargin = Ui.dp(c, 4);
        header.addView(addSongs, al);
        addSongsButton = addSongs;

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
        playButtons = buttons;

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
                if (!editing && i >= 0 && i < tracks.size()) {
                    Player p = Player.get(getActivity());
                    p.play(tracks, i, p.isShuffle());
                }
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                int i = position - list.getHeaderViewsCount();
                if (!editing && i >= 0 && i < tracks.size()) {
                    TrackActions.show(AlbumFragment.this, tracks.get(i), source());
                }
                return true;
            }
        });
        showHeader();
        if (loaded) {
            showTracks();
            restoreScroll(list);
            if (!source().isRemote() && !editing) load();
        } else {
            load();
        }
        return list;
    }

    @Override
    public void onDestroyView() {
        rememberScroll(list);
        if (editing) {
            editName = nameField.getText().toString();
            editDescription = descriptionField.getText().toString();
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        // Left without Done or Cancel (e.g. another tab was picked): drop the unsaved cover.
        if (editing) discardPendingCover();
        super.onDestroy();
    }

    /** "‹ Back" (or "Cancel" while editing) on the left, "Edit" / "Done" on the right. */
    private View topBar(Context c, LinearLayout header) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setMinimumHeight(Ui.dp(c, 40));
        if (header.getChildCount() > 0) {
            backButton = header.getChildAt(0);
            ViewGroup.LayoutParams lp = backButton.getLayoutParams();
            header.removeView(backButton);
            bar.addView(backButton, lp);
        }
        cancelButton = Ui.textButton(c, "Cancel", false);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.leftMargin = -Ui.dp(c, 10);
        bar.addView(cancelButton, cl);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelEditing();
            }
        });
        bar.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
        editButton = Ui.textButton(c, "Edit", false);
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        el.rightMargin = -Ui.dp(c, 10);
        bar.addView(editButton, el);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (editing) finishEditing();
                else startEditing();
            }
        });
        return bar;
    }

    /** Artwork, with an "Edit" band across the bottom in edit mode (tap to change the cover). */
    private View cover(Context c) {
        FrameLayout frame = new FrameLayout(c);
        art = Ui.artwork(c, 0);
        frame.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        coverLabel = new TextView(c);
        coverLabel.setText("Edit");
        coverLabel.setTextColor(0xffffffff);
        coverLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        coverLabel.setGravity(Gravity.CENTER);
        coverLabel.setPadding(0, Ui.dp(c, 5), 0, Ui.dp(c, 6));
        GradientDrawable band = new GradientDrawable();
        band.setColor(0x99000000);
        float r = Ui.dp(c, 4); // ArtworkView's corner radius
        band.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        coverLabel.setBackground(band);
        frame.addView(coverLabel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        frame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (editing && !saving) {
                    CoverPicker.show(AlbumFragment.this, editTracks, hasCoverWhenSaved(), coverListener);
                }
            }
        });
        frame.setClickable(false);
        return frame;
    }

    private boolean canEdit() {
        return isPlaylist() && source().canEditPlaylists();
    }

    /** Shows the header for the current mode. */
    private void showHeader() {
        Context c = getActivity();
        MediaItem album = item();
        String description = album.description != null ? album.description.trim() : "";
        boolean e = editing;
        backButton.setVisibility(e ? View.GONE : View.VISIBLE);
        cancelButton.setVisibility(e ? View.VISIBLE : View.GONE);
        editButton.setVisibility(canEdit() ? View.VISIBLE : View.GONE);
        editButton.setText(saving ? "Saving…" : (e ? "Done" : "Edit"));
        editButton.setTypeface(e ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        editButton.setEnabled(!saving);
        cancelButton.setEnabled(!saving);
        editButton.setAlpha(saving ? 0.5f : 1f);
        cancelButton.setAlpha(saving ? 0.5f : 1f);
        titleView.setText(album.title);
        titleView.setVisibility(e ? View.GONE : View.VISIBLE);
        nameField.setVisibility(e ? View.VISIBLE : View.GONE);
        descriptionView.setText(description);
        descriptionView.setVisibility(!e && description.length() > 0 ? View.VISIBLE : View.GONE);
        descriptionField.setVisibility(e ? View.VISIBLE : View.GONE);
        editNote.setText(source().isRemote() ? "The cover photo is kept on this phone, not on your server." : "");
        editNote.setVisibility(e && editNote.length() > 0 ? View.VISIBLE : View.GONE);
        coverLabel.setVisibility(e ? View.VISIBLE : View.GONE);
        ((View) art.getParent()).setClickable(e);
        playButtons.setVisibility(e ? View.GONE : View.VISIBLE);
        addSongsButton.setVisibility(e ? View.VISIBLE : View.GONE);
        addSongsButton.setEnabled(!saving);
        addSongsButton.setAlpha(saving ? 0.5f : 1f);
        if (e) {
            downloadRow.setVisibility(View.GONE);
            if (editName != null) nameField.setText(editName);
            if (editDescription != null) descriptionField.setText(editDescription);
            editName = null;
            editDescription = null;
        }
        nameField.setEnabled(!saving);
        descriptionField.setEnabled(!saving);
        showCover(c);
    }

    private void showCover(Context c) {
        MediaItem album = item();
        MediaItem shown = album;
        if (editing && pendingCover != null) {
            shown = new MediaItem(album.kind, album.sourceId, album.id, album.title);
            shown.artKey = pendingCover.length() > 0 ? pendingCover : PlaylistMeta.get(c).sourceArtKey(album);
        }
        ArtworkLoader.get(c).into(art, shown, Ui.dp(c, 280));
    }

    /** Will the playlist have a cover photo after Done? */
    private boolean hasCoverWhenSaved() {
        if (pendingCover != null) return pendingCover.length() > 0;
        return PlaylistMeta.get(getActivity()).isCover(item().artKey);
    }

    private final CoverPicker.Listener coverListener = new CoverPicker.Listener() {
        @Override
        public void onCoverChosen(File file) {
            if (!editing || saving) {
                file.delete();
                return;
            }
            discardPendingCover();
            pendingCover = file.getPath();
            if (getActivity() != null && art != null) showCover(getActivity());
        }

        @Override
        public void onCoverRemoved() {
            discardPendingCover();
            pendingCover = PlaylistMeta.get(getActivity()).isCover(item().artKey) ? "" : null;
            showCover(getActivity());
        }
    };

    private void discardPendingCover() {
        if (pendingCover != null && pendingCover.length() > 0) new File(pendingCover).delete();
        pendingCover = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (editing && getActivity() != null) {
            CoverPicker.onActivityResult(getActivity(), requestCode, resultCode, data, coverListener);
        }
    }

    @Override
    boolean onBackPressed() {
        if (!editing) return false;
        if (!saving) cancelEditing();
        return true;
    }

    private void startEditing() {
        if (editing || loading || !loaded) return;
        MediaItem album = item();
        editing = true;
        editTracks = new ArrayList<MediaItem>(tracks);
        removedTracks.clear();
        addedTracks.clear();
        pendingCover = null;
        nameField.setText(album.title);
        descriptionField.setText(album.description != null ? album.description : "");
        showHeader();
        showTracks();
    }

    private void cancelEditing() {
        if (!editing || saving) return;
        hideKeyboard();
        discardPendingCover();
        editing = false;
        removedTracks.clear();
        addedTracks.clear();
        showHeader();
        showTracks();
    }

    private void remove(MediaItem track) {
        if (!editing || saving) return;
        if (addedTracks.remove(track)) {
            // Added while editing: not on the source yet, so nothing to remove there.
            editTracks.remove(track);
            showTracks();
        } else if (editTracks.remove(track)) {
            removedTracks.add(track);
            showTracks();
        }
    }

    /** Done: saves the changes to the playlist's source and the phone, then leaves edit mode. */
    private void finishEditing() {
        if (!editing || saving) return;
        hideKeyboard();
        final MediaItem album = item();
        final MusicSource s = source();
        String name = nameField.getText().toString().trim();
        String description = descriptionField.getText().toString().trim();
        String oldDescription = album.description != null ? album.description.trim() : "";
        final String newName = name.length() > 0 && !name.equals(album.title) ? name : null;
        final String newDescription = description.equals(oldDescription) ? null : description;
        final List<MediaItem> removed = new ArrayList<MediaItem>(removedTracks);
        final List<MediaItem> added = new ArrayList<MediaItem>(addedTracks);
        final String cover = pendingCover;
        if (newName == null && newDescription == null && removed.isEmpty() && added.isEmpty() && cover == null) {
            editing = false;
            showHeader();
            showTracks();
            return;
        }
        // Saved on a copy, so the page keeps showing the old values until it's done.
        final MediaItem saved = new MediaItem(album.kind, album.sourceId, album.id, album.title);
        saved.artKey = album.artKey;
        saved.description = album.description;
        final Context app = getActivity().getApplicationContext();
        saving = true;
        showHeader();
        bg(new Bg.Work<Void>() {
            @Override
            public Void run() throws Exception {
                boolean sourceKeptDescription = false;
                if (newName != null || newDescription != null || !removed.isEmpty()) {
                    sourceKeptDescription = s.editPlaylist(saved, newName, newDescription, removed);
                }
                // After the removals: Subsonic removes by position, counted before anything's added.
                if (!added.isEmpty()) s.addToPlaylist(saved, added);
                PlaylistMeta.get(app).save(saved, sourceKeptDescription ? null : newDescription, cover);
                if (newDescription != null) saved.description = newDescription;
                return null;
            }
        }, new Bg.Done<Void>() {
            @Override
            public void done(Void result, Exception error) {
                saving = false;
                if (error != null) {
                    showHeader();
                    Ui.dialog(getActivity())
                            .setTitle("Couldn't Save Changes")
                            .setMessage(describe(error, s))
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
                if (newName != null) album.title = newName;
                album.description = saved.description;
                album.artKey = saved.artKey;
                pendingCover = null; // Now the playlist's cover.
                editing = false;
                tracks = new ArrayList<MediaItem>(editTracks);
                removedTracks.clear();
                addedTracks.clear();
                showHeader();
                showTracks();
                // Fresh entry ids (Subsonic counts them by position), and the added songs as the source has them.
                if (!removed.isEmpty() || !added.isEmpty()) load();
            }
        });
    }

    /** Add Songs' view of the playlist being edited. */
    private final AddSongsSheet.Target addTarget = new AddSongsSheet.Target() {
        @Override
        public boolean has(MediaItem track) {
            return indexOf(editTracks, track) >= 0;
        }

        @Override
        public boolean added(MediaItem track) {
            return indexOf(addedTracks, track) >= 0;
        }

        @Override
        public void add(List<MediaItem> songs) {
            if (!editing || saving) return;
            editTracks.addAll(songs);
            addedTracks.addAll(songs);
            showTracks();
        }

        @Override
        public void takeBack(MediaItem track) {
            int i = indexOf(addedTracks, track);
            if (i < 0) return;
            remove(addedTracks.get(i));
        }

        @Override
        public int addedCount() {
            return addedTracks.size();
        }
    };

    /** The last song in list that's the same song as track, or -1. */
    private static int indexOf(List<MediaItem> list, MediaItem track) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).sameAs(track)) return i;
        }
        return -1;
    }

    private void hideKeyboard() {
        View focus = getActivity() != null ? getActivity().getCurrentFocus() : null;
        if (focus == null) return;
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        focus.clearFocus();
    }

    /** The songs on screen: the edited list while editing. */
    private List<MediaItem> shown() {
        return editing ? editTracks : tracks;
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
        if (!loaded) footer.setText("Loading\u2026");
        loading = true;
        bg(new Bg.Work<List<MediaItem>>() {
            @Override
            public List<MediaItem> run() throws Exception {
                return s.tracks(album);
            }
        }, new Bg.Done<List<MediaItem>>() {
            @Override
            public void done(List<MediaItem> result, Exception error) {
                loading = false;
                if (error != null) {
                    if (!loaded) footer.setText(describe(error, s));
                    return;
                }
                tracks = result;
                loaded = true;
                if (!editing) showTracks();
                // A new playlist: straight into edit mode, for Add Songs.
                if (getArguments().getBoolean(ARG_EDIT)) {
                    getArguments().putBoolean(ARG_EDIT, false);
                    startEditing();
                }
            }
        });
    }

    private void showTracks() {
        List<MediaItem> songs = shown();
        long total = 0;
        for (MediaItem t : songs) total += t.durationMs;
        footer.setText(songs.isEmpty() ? "No songs." : Format.songsSummary(songs.size(), total));
        adapter.notifyDataSetChanged();
        refreshStates();
    }

    private void refreshStates() {
        if (editing) return;
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
                    .setDestructive(true)
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
                    .setDestructive(true)
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
            return shown().size();
        }

        @Override
        public Object getItem(int position) {
            return shown().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        private final class Holder {
            IconView remove;
            TextView number;
            IconView playing;
            ArtworkView art;
            TextView title;
            TextView subtitle;
            IconView state;
            TextView duration;
            IconView more;
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
                h.remove = new IconView(context);
                h.remove.setIcon(Icons.REMOVE);
                h.remove.setColor(RED);
                int pad = Ui.dp(context, 9);
                h.remove.setPadding(pad, pad, pad, pad);
                h.remove.setContentDescription("Remove");
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(Ui.dp(context, 42), Ui.dp(context, 42));
                rl.leftMargin = -Ui.dp(context, 8);
                rl.rightMargin = Ui.dp(context, 4);
                row.addView(h.remove, rl);
                h.remove.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        remove((MediaItem) v.getTag());
                    }
                });
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
                h.more = Ui.moreButton(context);
                ((LinearLayout.LayoutParams) h.more.getLayoutParams()).rightMargin = -Ui.dp(context, 8);
                h.more.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        TrackActions.show(AlbumFragment.this, (MediaItem) v.getTag(), source());
                    }
                });
                row.addView(h.more);
                row.setTag(h);
                convertView = row;
            } else {
                h = (Holder) convertView.getTag();
            }
            MediaItem t = shown().get(position);
            MediaItem current = Player.get(context).current();
            boolean isCurrent = current != null && current.key().equals(t.key());
            boolean playlist = isPlaylist();
            h.remove.setVisibility(editing ? View.VISIBLE : View.GONE);
            h.remove.setTag(t);

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
            h.more.setTag(t);
            if (editing) {
                h.number.setVisibility(View.GONE);
                h.playing.setVisibility(View.GONE);
                h.state.setVisibility(View.GONE);
                h.duration.setVisibility(View.GONE);
                h.more.setVisibility(View.GONE);
            } else {
                h.duration.setVisibility(View.VISIBLE);
                h.more.setVisibility(View.VISIBLE);
            }
            return convertView;
        }
    }
}
