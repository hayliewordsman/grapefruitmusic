package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.tags.SongTags;
import com.grapefruitmusic.tags.Tags;
import com.grapefruitmusic.ui.widget.ArtworkView;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Notice;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;

/** Edit Tags: a song's title, artist, album and so on, and its artwork. */
public final class TagEditorFragment extends BaseFragment {
    private ScrollView page;
    private SongTags.Target target;
    /** The tags as loaded (null while loading); the fields start from these. */
    private Tags loaded;
    private String error;
    private byte[] picture;
    private boolean pictureChanged;
    private boolean saving;

    private EditText title;
    private EditText artist;
    private EditText album;
    private EditText albumArtist;
    private EditText genre;
    private EditText year;
    private EditText track;
    private EditText trackTotal;
    private EditText disc;
    private EditText discTotal;
    private ArtworkView art;
    private TextView saveButton;

    static TagEditorFragment newInstance(MediaItem song, String back) {
        TagEditorFragment f = new TagEditorFragment();
        f.setArguments(args(song.sourceId, song, back));
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        page = Grouped.page(getActivity());
        if (target == null) target = SongTags.target(getActivity(), item());
        build();
        if (loaded == null && error == null) load();
        return page;
    }

    private void load() {
        final SongTags.Target t = target;
        final MediaItem song = item();
        if (t == null) {
            error = "Grapefruit can't change this song's tags.";
            build();
            return;
        }
        bg(new Bg.Work<Tags>() {
            @Override
            public Tags run() throws Exception {
                return SongTags.read(t, song);
            }
        }, new Bg.Done<Tags>() {
            @Override
            public void done(Tags result, Exception e) {
                if (e != null) {
                    error = e.getMessage() != null ? e.getMessage() : "Couldn't read this song's tags.";
                } else {
                    loaded = result;
                    picture = result.picture;
                }
                build();
            }
        });
    }

    private void build() {
        final Context c = getActivity();
        // Keep what's been typed when the page is rebuilt.
        Tags typed = title != null && loaded != null ? fields() : loaded;
        Grouped g = Grouped.on(page);

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6), 0);
        TextView cancel = Ui.textButton(c, "Cancel", false);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        });
        bar.addView(cancel);
        bar.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
        saveButton = Ui.textButton(c, saving ? "Saving…" : "Save", true);
        saveButton.setEnabled(loaded != null && !saving);
        saveButton.setAlpha(loaded != null && !saving ? 1f : 0.4f);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        bar.addView(saveButton);
        g.add(bar);
        LinearLayout header = Ui.header(c, null, "Edit Tags", null);
        header.setPadding(header.getPaddingLeft(), Ui.dp(c, 2), header.getPaddingRight(), header.getPaddingBottom());
        g.add(header);

        if (loaded == null) {
            TextView status = Ui.status(c);
            status.setText(error != null ? error : "Loading…");
            g.add(status);
            return;
        }

        // The artwork, and a button to change it.
        LinearLayout artBox = new LinearLayout(c);
        artBox.setOrientation(LinearLayout.VERTICAL);
        artBox.setGravity(Gravity.CENTER_HORIZONTAL);
        artBox.setPadding(0, Ui.dp(c, 8), 0, 0);
        art = Ui.artwork(c, 150);
        art.setCornerRadiusDp(6);
        art.setPlaceholderColors(Ui.color(c, R.attr.gfFill), Ui.color(c, R.attr.gfTextSecondary));
        showPicture();
        View.OnClickListener pick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickArtwork();
            }
        };
        art.setOnClickListener(pick);
        art.setContentDescription("Artwork");
        artBox.addView(art);
        TextView change = Ui.textButton(c, picture != null ? "Change Artwork" : "Add Artwork", false);
        change.setOnClickListener(pick);
        artBox.addView(change);
        g.add(artBox);

        g.section("Song");
        title = field(g, "Title", typed.title, false);
        artist = field(g, "Artist", typed.artist, false);
        album = field(g, "Album", typed.album, false);
        albumArtist = field(g, "Album Artist", typed.albumArtist, false);
        genre = field(g, "Genre", typed.genre, false);
        year = field(g, "Year", typed.year > 0 ? String.valueOf(typed.year) : "", true);
        year.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        EditText[] t = numbers(g, "Track", typed.track, typed.trackTotal);
        track = t[0];
        trackTotal = t[1];
        EditText[] d = numbers(g, "Disc", typed.disc, typed.discTotal);
        disc = d[0];
        discTotal = d[1];
        if (target.note != null) g.footer(target.note);
    }

    /** A labelled row with a text field; returns the field. */
    private EditText field(Grouped g, String label, String value, boolean number) {
        Context c = g.c;
        LinearLayout row = g.row(null);
        row.addView(label(c, label));
        EditText e = edit(c, value, number);
        e.setHint(label);
        e.setContentDescription(label);
        row.addView(e, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        g.addRow(row, false);
        return e;
    }

    /** "Track [3] of [12]". */
    private EditText[] numbers(Grouped g, String label, int n, int total) {
        Context c = g.c;
        LinearLayout row = g.row(null);
        row.addView(label(c, label));
        EditText a = edit(c, n > 0 ? String.valueOf(n) : "", true);
        a.setContentDescription(label);
        a.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        row.addView(a, new LinearLayout.LayoutParams(Ui.dp(c, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView of = Ui.text(c, 16, R.attr.gfTextSecondary, false);
        of.setText("of");
        of.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        row.addView(of);
        EditText b = edit(c, total > 0 ? String.valueOf(total) : "", true);
        b.setContentDescription(label + " count");
        b.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        row.addView(b, new LinearLayout.LayoutParams(Ui.dp(c, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
        g.addRow(row, false);
        return new EditText[]{a, b};
    }

    private static TextView label(Context c, String text) {
        TextView l = Ui.text(c, 16, R.attr.gfTextPrimary, false);
        l.setText(text);
        l.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 112), ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    private static EditText edit(Context c, String value, boolean number) {
        EditText e = new EditText(c);
        e.setBackgroundColor(0);
        e.setSingleLine(true);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setTextColor(Ui.color(c, R.attr.gfTextPrimary));
        e.setHintTextColor(Ui.color(c, R.attr.gfTextSecondary));
        e.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        e.setText(value);
        return e;
    }

    private void showPicture() {
        if (art == null) return;
        Bitmap b = null;
        if (picture != null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 1;
            b = BitmapFactory.decodeByteArray(picture, 0, picture.length, o);
        }
        art.setBitmap(b);
    }

    private void pickArtwork() {
        CoverPicker.show(this, Collections.singletonList(item()), picture != null, "Artwork", "Remove Artwork",
                coverListener());
    }

    private CoverPicker.Listener coverListener() {
        return new CoverPicker.Listener() {
            @Override
            public void onCoverChosen(File file) {
                try {
                    picture = readAll(file);
                    pictureChanged = true;
                } catch (IOException e) {
                    Notice.show(getActivity(), "Couldn't use that picture");
                } finally {
                    file.delete();
                }
                if (getActivity() != null) build();
            }

            @Override
            public void onCoverRemoved() {
                picture = null;
                pictureChanged = true;
                if (getActivity() != null) build();
            }
        };
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!CoverPicker.onActivityResult(getActivity(), requestCode, resultCode, data, coverListener())) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private static byte[] readAll(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] b = new byte[(int) f.length()];
            int off = 0;
            int n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            return b;
        } finally {
            in.close();
        }
    }

    /** What the fields say now. */
    private Tags fields() {
        Tags t = new Tags();
        t.title = title.getText().toString().trim();
        t.artist = artist.getText().toString().trim();
        t.album = album.getText().toString().trim();
        t.albumArtist = albumArtist.getText().toString().trim();
        t.genre = genre.getText().toString().trim();
        t.year = number(year);
        t.track = number(track);
        t.trackTotal = number(trackTotal);
        t.disc = number(disc);
        t.discTotal = number(discTotal);
        t.picture = picture;
        return t;
    }

    private static int number(EditText e) {
        String s = e.getText().toString().trim();
        try {
            return s.length() == 0 ? 0 : Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void save() {
        if (loaded == null || saving) return;
        final Tags t = fields();
        if (t.title.length() == 0) {
            Notice.show(getActivity(), "A song needs a title");
            return;
        }
        if (t.year != 0 && (t.year < 1000 || t.year > 9999)) {
            Notice.show(getActivity(), "The year needs four digits, like 1999");
            return;
        }
        final Context app = getActivity().getApplicationContext();
        final SongTags.Target where = target;
        final MediaItem song = item();
        final boolean withPicture = pictureChanged;
        saving = true;
        build();
        hideKeyboard();
        bg(new Bg.Work<Void>() {
            @Override
            public Void run() throws Exception {
                SongTags.write(app, where, song, t, withPicture);
                return null;
            }
        }, new Bg.Done<Void>() {
            @Override
            public void done(Void result, Exception e) {
                saving = false;
                if (e != null) {
                    Notice.showLong(app, describeSaveError(e));
                    build();
                    return;
                }
                SongTags.saved(app, song, t, withPicture);
                Notice.show(app, "Saved");
                close();
            }
        });
    }

    private String describeSaveError(Exception e) {
        if (e instanceof Http.StatusException && target.server != null) {
            int code = ((Http.StatusException) e).code;
            if (code == 401 || code == 403) {
                return target.server.name() + " didn't let Grapefruit change this song: on Jellyfin, only "
                        + "administrators can edit songs.";
            }
        }
        if (target.server != null && !(e instanceof Http.StatusException) && e.getMessage() == null) {
            return "Can't reach " + target.server.name() + ".";
        }
        return e.getMessage() != null ? e.getMessage() : "Couldn't save the changes.";
    }

    private void hideKeyboard() {
        if (getActivity() == null || page == null) return;
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(page.getWindowToken(), 0);
    }

    private void close() {
        hideKeyboard();
        if (getFragmentManager() != null) getFragmentManager().popBackStack();
    }
}
