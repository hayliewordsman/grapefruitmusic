package com.grapefruitmusic.ui;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;

import com.grapefruitmusic.data.PlaylistMeta;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.ui.widget.ArtworkView;
import com.grapefruitmusic.util.ArtworkLoader;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Notice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chooses a playlist cover photo: a photo on this phone, album art from the playlist's songs, or
 * a picture from another app. The picture is cropped square and saved as a JPEG for PlaylistMeta.
 */
final class CoverPicker {
    interface Listener {
        /** A new cover file, not yet saved to the playlist. */
        void onCoverChosen(File file);

        void onCoverRemoved();
    }

    static final int REQUEST_PICK = 4101;
    /** Saved covers are at most this many pixels square. */
    private static final int SIZE = 600;
    private static final int MAX_PHOTOS = 300;
    private static final String PHOTO_SOURCE = "photo";

    private CoverPicker() {
    }

    static void show(Fragment fragment, List<MediaItem> tracks, boolean hasCover, Listener listener) {
        show(fragment, tracks, hasCover, "Cover Photo", "Remove Cover Photo", listener);
    }

    /** With other words: a song's "Artwork" and "Remove Artwork", say. */
    static void show(final Fragment fragment, List<MediaItem> tracks, boolean hasCover, String title,
                     String removeLabel, final Listener listener) {
        final Context c = fragment.getActivity();
        final List<MediaItem> art = distinctArt(tracks);
        final Intent pick = pickIntent();
        List<String> labels = new ArrayList<String>();
        final List<Runnable> actions = new ArrayList<Runnable>();
        labels.add("Choose from Photos");
        actions.add(new Runnable() {
            @Override
            public void run() {
                photos(c, listener);
            }
        });
        if (!art.isEmpty()) {
            labels.add("Choose from Album Art");
            actions.add(new Runnable() {
                @Override
                public void run() {
                    albumArt(c, art, listener);
                }
            });
        }
        if (pick.resolveActivity(c.getPackageManager()) != null) {
            labels.add("Choose with Another App…");
            actions.add(new Runnable() {
                @Override
                public void run() {
                    fragment.startActivityForResult(pick, REQUEST_PICK);
                }
            });
        }
        if (hasCover) {
            labels.add(removeLabel);
            actions.add(new Runnable() {
                @Override
                public void run() {
                    listener.onCoverRemoved();
                }
            });
        }
        Ui.dialog(c)
                .setTitle(title)
                .setItems(labels.toArray(new String[labels.size()]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        actions.get(which).run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static Intent pickIntent() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        return i;
    }

    /** Call from the fragment's onActivityResult; true if it was the picker's result. */
    static boolean onActivityResult(Context c, int requestCode, int resultCode, Intent data, Listener listener) {
        if (requestCode != REQUEST_PICK) return false;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        final Context app = c.getApplicationContext();
        final Uri uri = data.getData();
        save(app, new Bg.Work<Bitmap>() {
            @Override
            public Bitmap run() throws IOException {
                return rotate(decodeUri(app, uri), orientation(app, uri));
            }
        }, listener);
        return true;
    }

    /** One song per album artwork, in playlist order. */
    private static List<MediaItem> distinctArt(List<MediaItem> tracks) {
        List<MediaItem> out = new ArrayList<MediaItem>();
        Set<String> seen = new HashSet<String>();
        for (MediaItem t : tracks) {
            if (t.artKey != null && seen.add(t.sourceId + "|" + t.artKey)) out.add(t);
        }
        return out;
    }

    private static void albumArt(final Context c, List<MediaItem> art, final Listener listener) {
        final Context app = c.getApplicationContext();
        grid(c, "Album Art", art, new Pick() {
            @Override
            public void onPick(MediaItem item) {
                ArtworkLoader.get(app).load(item, SIZE, new ArtworkLoader.Callback() {
                    @Override
                    public void onArtwork(final Bitmap bitmap) {
                        if (bitmap == null) {
                            Notice.show(app, "Couldn't load that artwork.");
                            return;
                        }
                        save(app, new Bg.Work<Bitmap>() {
                            @Override
                            public Bitmap run() {
                                return bitmap;
                            }
                        }, listener);
                    }
                });
            }
        });
    }

    private static void photos(final Context c, final Listener listener) {
        final Context app = c.getApplicationContext();
        Bg.run(new Bg.Work<List<MediaItem>>() {
            @Override
            public List<MediaItem> run() {
                return findPhotos(app);
            }
        }, new Bg.Done<List<MediaItem>>() {
            @Override
            public void done(List<MediaItem> photos, Exception error) {
                if (photos == null || photos.isEmpty()) {
                    Notice.show(app, "No photos found.");
                    return;
                }
                grid(c, "Photos", photos, new Pick() {
                    @Override
                    public void onPick(final MediaItem photo) {
                        save(app, new Bg.Work<Bitmap>() {
                            @Override
                            public Bitmap run() throws IOException {
                                return rotate(decodeFile(photo.path), photo.trackNumber);
                            }
                        }, listener);
                    }
                });
            }
        });
    }

    /** Pictures on the phone, newest first. trackNumber holds the photo's rotation. */
    private static List<MediaItem> findPhotos(Context c) {
        List<MediaItem> out = new ArrayList<MediaItem>();
        Cursor cur;
        try {
            cur = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media.DATA, MediaStore.Images.Media.ORIENTATION},
                    null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");
        } catch (RuntimeException e) {
            return out;
        }
        if (cur == null) return out;
        try {
            while (cur.moveToNext() && out.size() < MAX_PHOTOS) {
                String path = cur.getString(0);
                if (path == null || !new File(path).exists()) continue;
                MediaItem m = new MediaItem(0, PHOTO_SOURCE, path, null);
                m.path = path;
                m.artKey = path;
                m.trackNumber = cur.getInt(1);
                out.add(m);
            }
        } finally {
            cur.close();
        }
        return out;
    }

    private interface Pick {
        void onPick(MediaItem item);
    }

    /** A dialog with a grid of square pictures. */
    private static void grid(Context c, String title, final List<MediaItem> items, final Pick pick) {
        final Context ctx = c;
        int width = c.getResources().getDisplayMetrics().widthPixels;
        int columns = Math.max(3, Math.min(5, width / Ui.dp(c, 110)));
        final int cellPx = width / columns;
        int gap = Ui.dp(c, 4);
        GridView g = new GridView(c);
        g.setNumColumns(columns);
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        g.setHorizontalSpacing(gap);
        g.setVerticalSpacing(gap);
        g.setPadding(gap * 3, gap * 3, gap * 3, gap * 3);
        g.setClipToPadding(false);
        g.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return items.size();
            }

            @Override
            public Object getItem(int position) {
                return items.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ArtworkView v = (ArtworkView) convertView;
                if (v == null) {
                    v = Ui.artwork(ctx, 0);
                    v.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                ArtworkLoader.get(ctx).into(v, items.get(position), cellPx);
                return v;
            }
        });
        final GrapefruitDialog dialog = Ui.dialog(c).setTitle(title).setView(g).setNegativeButton("Cancel", null).create();
        g.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                dialog.dismiss();
                pick.onPick(items.get(position));
            }
        });
        dialog.show();
    }

    /** Crops and saves the picture off the main thread, then hands the file to the listener. */
    private static void save(final Context app, final Bg.Work<Bitmap> picture, final Listener listener) {
        Bg.run(new Bg.Work<File>() {
            @Override
            public File run() throws Exception {
                Bitmap b = picture.run();
                if (b == null) throw new IOException("Not a picture");
                File out = PlaylistMeta.get(app).newCoverFile();
                writeSquare(b, out);
                return out;
            }
        }, new Bg.Done<File>() {
            @Override
            public void done(File file, Exception error) {
                if (file == null) {
                    Notice.show(app, "Couldn't use that picture.");
                    return;
                }
                listener.onCoverChosen(file);
            }
        });
    }

    /** The middle square of the picture, at most SIZE pixels, as a JPEG. */
    static void writeSquare(Bitmap src, File out) throws IOException {
        int side = Math.min(src.getWidth(), src.getHeight());
        float scale = Math.min(1f, SIZE / (float) side);
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        Bitmap square = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2,
                side, side, m, true);
        FileOutputStream os = new FileOutputStream(out);
        try {
            if (!square.compress(Bitmap.CompressFormat.JPEG, 90, os)) throw new IOException("Couldn't save");
        } finally {
            os.close();
        }
    }

    private static Bitmap rotate(Bitmap b, int degrees) {
        if (b == null || degrees % 360 == 0) return b;
        Matrix m = new Matrix();
        m.setRotate(degrees);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    /** Smallest power-of-two downsampling that keeps the short side at least SIZE. */
    private static int sampleSize(int w, int h) {
        int sample = 1;
        while (Math.min(w, h) / (sample * 2) >= SIZE) sample *= 2;
        return sample;
    }

    private static Bitmap decodeFile(String path) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sampleSize(o.outWidth, o.outHeight);
        return BitmapFactory.decodeFile(path, o);
    }

    private static Bitmap decodeUri(Context c, Uri uri) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) return null;
        try {
            BitmapFactory.decodeStream(in, null, o);
        } finally {
            in.close();
        }
        if (o.outWidth <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sampleSize(o.outWidth, o.outHeight);
        in = c.getContentResolver().openInputStream(uri);
        if (in == null) return null;
        try {
            return BitmapFactory.decodeStream(in, null, o);
        } finally {
            in.close();
        }
    }

    /** Rotation stored with a picture from the gallery, or 0. */
    private static int orientation(Context c, Uri uri) {
        try {
            Cursor cur = c.getContentResolver().query(uri, new String[]{MediaStore.Images.Media.ORIENTATION},
                    null, null, null);
            if (cur == null) return 0;
            try {
                return cur.moveToFirst() ? cur.getInt(0) : 0;
            } finally {
                cur.close();
            }
        } catch (RuntimeException e) {
            return 0; // Not a MediaStore picture.
        }
    }
}
