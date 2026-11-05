package com.grapefruitmusic.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import com.grapefruitmusic.audiobooks.Audiobook;
import com.grapefruitmusic.audiobooks.AudiobookStore;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.ui.widget.ArtworkView;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.ArtworkLoader;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Notice;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Audiobooks tab: the books on this device (AudiobookStore), the ones being listened to first,
 * each with how far in it is. Tap one to carry on where it was left; Add copies books in (M4B, MP3,
 * any DRM-free audio, or a Libro.fm zip); Libro.fm, where to buy them.
 */
public final class AudiobooksFragment extends BaseFragment {
    static final String LIBRO_FM = "https://libro.fm";
    private static final int REQUEST_ADD = 81;

    private ListView list;
    private BooksAdapter adapter;
    private TextView status;
    private boolean scanned;

    @Override
    String backTitle() {
        return "Audiobooks";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        Context c = getActivity();
        list = new ListView(c);
        RowAdapter.style(list);
        LinearLayout header = Ui.header(c, null, "Audiobooks", null);
        LinearLayout actions = Ui.actionRow(c);
        actions.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
        LinearLayout add = Ui.pill(c, Icons.PLUS, "Add");
        add.setContentDescription("Add audiobooks");
        add.setOnClickListener(v -> add(this));
        actions.addView(add, new LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1));
        LinearLayout libro = Ui.pill(c, Icons.BOOK, "Libro.fm");
        libro.setContentDescription("Buy audiobooks at Libro.fm");
        libro.setOnClickListener(v -> openLibroFm(getActivity()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1);
        lp.leftMargin = Ui.dp(c, 8);
        actions.addView(libro, lp);
        header.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView note = Ui.text(c, 13, R.attr.gfTextSecondary, false);
        note.setSingleLine(false);
        note.setText("Buy your audiobooks from Libro.fm: they're DRM-free M4B and MP3 files that play here, and every"
                + " purchase supports a local bookstore.");
        note.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
        header.addView(note);
        list.addHeaderView(header, null, false);
        status = Ui.status(c);
        status.setGravity(Gravity.CENTER);
        list.addFooterView(status, null, false);
        adapter = new BooksAdapter(c);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object row = adapter.getItem(position - list.getHeaderViewsCount());
                if (row instanceof Audiobook) play((Audiobook) row, false);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Object row = adapter.getItem(position - list.getHeaderViewsCount());
                if (row instanceof Audiobook) actions((Audiobook) row);
                return true;
            }
        });
        show(AudiobookStore.get(c).books());
        restoreScroll(list);
        return list;
    }

    @Override
    public void onDestroyView() {
        rememberScroll(list);
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        scan();
    }

    /** Looks for new, changed and removed books (a moment for each new one). */
    void scan() {
        final Context app = getActivity().getApplicationContext();
        if (!scanned && AudiobookStore.get(app).books().isEmpty()) {
            status.setText("Looking for audiobooks…");
            status.setVisibility(View.VISIBLE);
        }
        bg(new Bg.Work<List<Audiobook>>() {
            @Override
            public List<Audiobook> run() {
                return AudiobookStore.get(app).scan();
            }
        }, new Bg.Done<List<Audiobook>>() {
            @Override
            public void done(List<Audiobook> books, Exception error) {
                scanned = true;
                show(books != null ? books : AudiobookStore.get(app).books());
            }
        });
    }

    private void show(List<Audiobook> books) {
        adapter.setBooks(books);
        if (books.isEmpty() && scanned) {
            status.setText("No audiobooks yet. Tap Add to choose M4B or MP3 files, or a zip from Libro.fm. Or put each"
                    + " book in its own folder in the Audiobooks folder on this device, and it shows up here.");
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(books.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    static void openLibroFm(Activity a) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(LIBRO_FM)));
        } catch (android.content.ActivityNotFoundException e) {
            Notice.show(a, "No app here opens web pages. Libro.fm is at libro.fm.");
        }
    }

    /** Carries on where the book was left (from the start, if it was finished, or asked). */
    private void play(Audiobook b, boolean fromStart) {
        AudiobookStore store = AudiobookStore.get(getActivity());
        if (fromStart || b.finished) store.startOver(b.id);
        Audiobook now = store.book(b.id);
        if (now == null || now.parts.isEmpty()) return;
        Player.get(getActivity()).play(now.items(), Math.min(now.part, now.parts.size() - 1), false);
        main().openNowPlaying();
    }

    private void actions(final Audiobook b) {
        final AudiobookStore store = AudiobookStore.get(getActivity());
        List<String> items = new ArrayList<String>();
        items.add("Play from the Start");
        items.add(b.finished ? "Mark as Not Finished" : "Mark as Finished");
        if (b.imported) items.add(com.grapefruitmusic.util.Brand.text("Remove from Grapefruit"));
        Ui.dialog(getActivity()).setTitle(b.title)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        play(b, true);
                    } else if (which == 1) {
                        store.setFinished(b.id, !b.finished);
                        show(store.books());
                    } else {
                        Ui.dialog(getActivity()).setTitle("Remove “" + b.title + "”?")
                                .setMessage(com.grapefruitmusic.util.Brand.text("Grapefruit's copy of this audiobook is deleted from this device."))
                                .setDestructive(true)
                                .setPositiveButton("Remove", (d2, w2) -> {
                                    store.delete(b.id);
                                    show(store.books());
                                })
                                .setNegativeButton("Cancel", null).show();
                    }
                })
                .show();
    }

    // ---- Adding ----

    /** Add: the system's file picker (Android 4.4+), or the files in Download (4.2 and 4.3). */
    static void add(final BaseFragment page) {
        final Activity a = page.getActivity();
        if (Build.VERSION.SDK_INT >= 19) {
            Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try {
                page.startActivityForResult(open, REQUEST_ADD);
                return;
            } catch (android.content.ActivityNotFoundException e) {
                // As on Android 4.2, then.
            }
        }
        final List<File> files = new ArrayList<File>();
        File[] found = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).listFiles();
        if (found != null) {
            for (File f : found) {
                String n = f.getName().toLowerCase(Locale.US);
                if (f.isFile() && (n.endsWith(".zip") || n.endsWith(".m4b") || n.endsWith(".m4a") || n.endsWith(".mp3")
                        || n.endsWith(".ogg") || n.endsWith(".opus") || n.endsWith(".flac"))) {
                    files.add(f);
                }
            }
        }
        final Intent other = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        final boolean canAsk = other.resolveActivity(a.getPackageManager()) != null;
        if (files.isEmpty() && !canAsk) {
            Ui.dialog(a).setTitle("Add Audiobooks")
                    .setMessage("Put the audiobook (an M4B or MP3 file, or a Libro.fm zip) in the Download folder, then try"
                            + " again. Or copy each book's folder into the Audiobooks folder on this device.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String[] items = new String[files.size() + (canAsk ? 1 : 0)];
        for (int i = 0; i < files.size(); i++) items[i] = files.get(i).getName();
        if (canAsk) items[items.length - 1] = "Choose with another app…";
        Ui.dialog(a).setTitle(files.isEmpty() ? "Add Audiobooks" : "Add from Download")
                .setItems(items, (d, which) -> {
                    if (which < files.size()) {
                        List<Uri> one = new ArrayList<Uri>();
                        one.add(Uri.fromFile(files.get(which)));
                        importBooks(page, one);
                    } else {
                        page.startActivityForResult(other, REQUEST_ADD);
                    }
                })
                .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_ADD || resultCode != Activity.RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<Uri>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (!uris.isEmpty()) importBooks(this, uris);
    }

    /** Copies them in, with a progress dialog; then the page shows them. */
    private static void importBooks(final BaseFragment page, final List<Uri> uris) {
        final Activity a = page.getActivity();
        final Context app = a.getApplicationContext();
        final TextView progress = Ui.text(a, 15, R.attr.gfTextSecondary, false);
        progress.setText("Copying…");
        final GrapefruitDialog dialog = Ui.dialog(a).setTitle("Adding Audiobooks").setView(progress).show();
        dialog.setCancelable(false);
        Bg.run(new Bg.Work<Integer>() {
            @Override
            public Integer run() throws Exception {
                return AudiobookStore.get(app).add(uris, (done, total) -> Bg.main(() -> {
                    if (total > 0) progress.setText("Copying… " + Math.min(100, done * 100 / total) + "%");
                    else progress.setText("Copying… " + (done / (1024 * 1024)) + " MB");
                }));
            }
        }, new Bg.Done<Integer>() {
            @Override
            public void done(Integer added, Exception error) {
                try {
                    dialog.dismiss();
                } catch (RuntimeException ignored) {
                    // The screen went meanwhile.
                }
                if (error != null) {
                    Notice.showLong(app, error.getMessage() != null ? error.getMessage() : "Couldn't add that.");
                } else {
                    Notice.show(app, added == 1 ? "Added 1 file." : "Added " + added + " files.");
                }
                if (page instanceof AudiobooksFragment && page.getActivity() != null) {
                    ((AudiobooksFragment) page).show(AudiobookStore.get(app).books());
                }
            }
        });
    }

    // ---- The list ----

    /** "8 h 20 min", "45 min". */
    static String length(long ms) {
        long min = Math.round(ms / 60000.0);
        if (min < 60) return min + " min";
        return min / 60 + " h" + (min % 60 > 0 ? " " + min % 60 + " min" : "");
    }

    private final class BooksAdapter extends BaseAdapter {
        private final Context context;
        /** Section titles (String) and books (Audiobook). */
        private final List<Object> rows = new ArrayList<Object>();

        BooksAdapter(Context context) {
            this.context = context;
        }

        void setBooks(List<Audiobook> books) {
            rows.clear();
            List<Audiobook> going = new ArrayList<Audiobook>();
            List<Audiobook> rest = new ArrayList<Audiobook>();
            for (Audiobook b : books) {
                if (b.started() && !b.finished) going.add(b);
                else rest.add(b);
            }
            if (!going.isEmpty()) {
                rows.add("Continue Listening");
                rows.addAll(going);
            }
            if (!rest.isEmpty()) {
                if (!going.isEmpty()) rows.add("All Audiobooks");
                rows.addAll(rest);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return position >= 0 && position < rows.size() ? rows.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof String ? 0 : 1;
        }

        @Override
        public boolean isEnabled(int position) {
            return rows.get(position) instanceof Audiobook;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object row = rows.get(position);
            if (row instanceof String) {
                TextView t = convertView instanceof TextView ? (TextView) convertView : null;
                if (t == null) {
                    t = Ui.text(context, 20, R.attr.gfTextPrimary, true);
                    t.setPadding(Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 6));
                }
                t.setText((String) row);
                return t;
            }
            Audiobook b = (Audiobook) row;
            LinearLayout v = convertView instanceof LinearLayout ? (LinearLayout) convertView : null;
            if (v == null) {
                v = new LinearLayout(context);
                v.setOrientation(LinearLayout.HORIZONTAL);
                v.setGravity(Gravity.CENTER_VERTICAL);
                v.setMinimumHeight(Ui.dp(context, 80));
                v.setPadding(Ui.dp(context, 16), Ui.dp(context, 8), Ui.dp(context, 16), Ui.dp(context, 8));
                v.setBackground(Ui.rowBackground(context));
                ArtworkView art = Ui.artwork(context, 64);
                v.addView(art);
                LinearLayout texts = new LinearLayout(context);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(Ui.dp(context, 14), 0, 0, 0);
                TextView title = Ui.text(context, 16, R.attr.gfTextPrimary, true);
                title.setSingleLine(false);
                title.setMaxLines(2);
                TextView author = Ui.text(context, 14, R.attr.gfTextSecondary, false);
                TextView progress = Ui.text(context, 13, R.attr.gfTextSecondary, false);
                texts.addView(title);
                texts.addView(author);
                texts.addView(progress);
                v.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            }
            ArtworkView art = (ArtworkView) v.getChildAt(0);
            LinearLayout texts = (LinearLayout) v.getChildAt(1);
            ((TextView) texts.getChildAt(0)).setText(b.title);
            TextView author = (TextView) texts.getChildAt(1);
            author.setText(b.author != null ? b.author : "");
            author.setVisibility(b.author != null ? View.VISIBLE : View.GONE);
            TextView progress = (TextView) texts.getChildAt(2);
            long total = b.durationMs();
            String line;
            if (b.finished) {
                line = "Finished";
                progress.setTextColor(Ui.accent(context));
            } else if (b.started() && total > 0) {
                long left = Math.max(0, total - b.elapsedMs());
                line = Math.min(99, b.elapsedMs() * 100 / total) + "%  ·  " + length(left) + " left";
                progress.setTextColor(Ui.accent(context));
            } else {
                line = total > 0 ? length(total) : "";
                if (b.chapters.size() > 1) line += (line.length() > 0 ? "  ·  " : "") + b.chapters.size() + " chapters";
                progress.setTextColor(Ui.color(context, R.attr.gfTextSecondary));
            }
            progress.setText(line);
            ArtworkLoader.get(context).into(art, b.card(), Ui.dp(context, 64));
            v.setContentDescription(b.title + (b.author != null ? ", " + b.author : "") + ", " + line);
            return v;
        }
    }
}
