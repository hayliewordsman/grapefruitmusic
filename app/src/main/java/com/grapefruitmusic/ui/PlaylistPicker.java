package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;

import com.grapefruitmusic.data.PlaylistMeta;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Notice;

import java.util.List;

/** "Add to Playlist": pick one of the source's playlists, or make a new one. */
final class PlaylistPicker {
    private PlaylistPicker() {
    }

    static void show(final BaseFragment page, final MusicSource source, final List<MediaItem> tracks) {
        final Context app = page.getActivity().getApplicationContext();
        Bg.run(new Bg.Work<List<MediaItem>>() {
            @Override
            public List<MediaItem> run() throws Exception {
                return source.playlists();
            }
        }, new Bg.Done<List<MediaItem>>() {
            @Override
            public void done(final List<MediaItem> playlists, Exception error) {
                final Context c = page.getActivity();
                if (c == null) return;
                if (error != null) {
                    Notice.showLong(app, BaseFragment.describe(error, source));
                    return;
                }
                String[] names = new String[playlists.size() + 1];
                names[0] = "New Playlist…";
                for (int i = 0; i < playlists.size(); i++) names[i + 1] = playlists.get(i).title;
                Ui.dialog(c)
                        .setTitle("Add to Playlist")
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) askName(c, source, tracks);
                                else add(app, source, playlists.get(which - 1), tracks);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private static void add(final Context app, final MusicSource source, final MediaItem playlist,
                            final List<MediaItem> tracks) {
        Bg.run(new Bg.Work<Void>() {
            @Override
            public Void run() throws Exception {
                source.addToPlaylist(playlist, tracks);
                return null;
            }
        }, new Bg.Done<Void>() {
            @Override
            public void done(Void result, Exception error) {
                Notice.show(app, error != null ? BaseFragment.describe(error, source)
                        : "Added to “" + playlist.title + "”");
            }
        });
    }

    private static void askName(Context c, final MusicSource source, final List<MediaItem> tracks) {
        final Context app = c.getApplicationContext();
        final EditText field = Ui.field(c, 16, false, "Playlist name");
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        Ui.dialog(c)
                .setTitle("New Playlist")
                .setView(field)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String name = field.getText().toString().trim();
                        if (name.length() == 0) return;
                        Bg.run(new Bg.Work<Void>() {
                            @Override
                            public Void run() throws Exception {
                                source.createPlaylist(name, tracks);
                                return null;
                            }
                        }, new Bg.Done<Void>() {
                            @Override
                            public void done(Void result, Exception error) {
                                if (error == null) PlaylistMeta.changed();
                                Notice.show(app, error != null ? BaseFragment.describe(error, source)
                                        : "Created “" + name + "”");
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
