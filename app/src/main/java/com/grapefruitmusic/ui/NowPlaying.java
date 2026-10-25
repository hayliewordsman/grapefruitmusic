package com.grapefruitmusic.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.grapefruitmusic.R;
import com.grapefruitmusic.lyrics.Lyrics;
import com.grapefruitmusic.lyrics.LyricsRepository;
import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.playback.Player;
import com.grapefruitmusic.ui.widget.ArtworkView;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.ui.widget.NowPlayingCard;
import com.grapefruitmusic.ui.widget.Scrubber;
import com.grapefruitmusic.util.ArtworkLoader;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Blur;
import com.grapefruitmusic.util.Format;
import com.grapefruitmusic.util.Lists;

import java.util.ArrayList;
import java.util.List;

/** The Now Playing card: artwork, controls, time-synced lyrics and Up Next. */
final class NowPlaying {
    private static final int MODE_ART = 0;
    private static final int MODE_LYRICS = 1;
    private static final int MODE_QUEUE = 2;
    private static final float DIM = 0.45f;
    private static final long FOLLOW_PAUSE_MS = 3000;

    private final Activity activity;
    private final Player player;
    private final AudioManager audio;
    private final View root;
    private final View dim;
    private final NowPlayingCard card;
    private final ArtworkView art;
    private final ListView lyricsList;
    private final ListView queueList;
    private final TextView message;
    private final TextView title;
    private final TextView artist;
    private final TextView elapsed;
    private final TextView remaining;
    private final Scrubber scrubber;
    private final Scrubber volume;
    private final IconView play;
    private final IconView btnLyrics;
    private final IconView btnShuffle;
    private final IconView btnRepeat;
    private final IconView btnQueue;
    private final View condensed;
    private final ArtworkView smallArt;
    private final TextView smallTitle;
    private final TextView smallArtist;
    private final FrameLayout listArea;
    private final int accent;
    private final int secondary;

    private final LyricsAdapter lyricsAdapter;
    private final QueueAdapter queueAdapter;

    private boolean open;
    private boolean started;
    private int mode = MODE_ART;
    private String trackKey;
    private Lyrics lyrics;
    private boolean lyricsLoading;
    private int lyricIndex = -2;
    private long lastUserScroll;
    private Boolean artPlaying;

    // Dragging a song in Up Next by its handle.
    private boolean dragging;
    private int dragFrom;
    private int dragAt;
    private MediaItem dragCurrent;
    private ImageView dragGhost;
    private float dragGrabOffset;
    private float dragLastRawY;
    private int dragScroll;

    /** Keeps the list scrolling while a dragged song is held near its top or bottom edge. */
    private final Runnable dragAutoScroll = new Runnable() {
        @Override
        public void run() {
            if (!dragging) return;
            if (dragScroll != 0 && queueList.getChildCount() > 0) {
                View first = queueList.getChildAt(0);
                queueList.setSelectionFromTop(queueList.getFirstVisiblePosition(),
                        first.getTop() - dragScroll * Ui.dp(activity, 6));
                moveDrag(dragLastRawY);
            }
            Bg.mainDelayed(this, 30);
        }
    };

    private final View.OnTouchListener handleTouch = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View handle, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Neither the list nor the card (pull-to-close) may take this gesture.
                    handle.getParent().requestDisallowInterceptTouchEvent(true);
                    startDrag((View) handle.getParent(), (Integer) handle.getTag(), e.getRawY());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    moveDrag(e.getRawY());
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    endDrag();
                    return true;
                default:
                    return true;
            }
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            if (open && started) Bg.mainDelayed(this, 250);
        }
    };

    NowPlaying(Activity a, View root) {
        activity = a;
        this.root = root;
        player = Player.get(a);
        audio = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
        accent = Ui.accent(a);
        secondary = Ui.color(a, R.attr.gfTextSecondary);
        int primary = Ui.color(a, R.attr.gfTextPrimary);
        int separator = Ui.color(a, R.attr.gfSeparator);

        dim = root.findViewById(R.id.np_dim);
        card = (NowPlayingCard) root.findViewById(R.id.np_card);
        art = (ArtworkView) root.findViewById(R.id.np_art);
        lyricsList = (ListView) root.findViewById(R.id.np_lyrics);
        queueList = (ListView) root.findViewById(R.id.np_queue);
        message = (TextView) root.findViewById(R.id.np_message);
        title = (TextView) root.findViewById(R.id.np_title);
        artist = (TextView) root.findViewById(R.id.np_artist);
        elapsed = (TextView) root.findViewById(R.id.np_elapsed);
        remaining = (TextView) root.findViewById(R.id.np_remaining);
        scrubber = (Scrubber) root.findViewById(R.id.np_scrubber);
        volume = (Scrubber) root.findViewById(R.id.np_volume);
        play = (IconView) root.findViewById(R.id.np_play);
        IconView prev = (IconView) root.findViewById(R.id.np_prev);
        IconView next = (IconView) root.findViewById(R.id.np_next);
        btnLyrics = (IconView) root.findViewById(R.id.np_btn_lyrics);
        btnShuffle = (IconView) root.findViewById(R.id.np_btn_shuffle);
        btnRepeat = (IconView) root.findViewById(R.id.np_btn_repeat);
        btnQueue = (IconView) root.findViewById(R.id.np_btn_queue);
        condensed = root.findViewById(R.id.np_condensed);
        smallArt = (ArtworkView) root.findViewById(R.id.np_small_art);
        smallTitle = (TextView) root.findViewById(R.id.np_small_title);
        smallArtist = (TextView) root.findViewById(R.id.np_small_artist);
        listArea = (FrameLayout) root.findViewById(R.id.np_list_area);
        IconView volLow = (IconView) root.findViewById(R.id.np_vol_low);
        IconView volHigh = (IconView) root.findViewById(R.id.np_vol_high);

        card.setColors(Ui.color(a, R.attr.gfBackground), Ui.color(a, R.attr.gfScrim));
        art.setPlaceholderColors(Ui.color(a, R.attr.gfFill), secondary);
        art.setCornerRadiusDp(6);
        art.setShadowDp(14);
        smallArt.setPlaceholderColors(Ui.color(a, R.attr.gfFill), secondary);
        root.findViewById(R.id.np_mini_header).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(MODE_ART);
            }
        });
        scrubber.setColors(separator, secondary, secondary);
        scrubber.setThumb(3.5f, 9, false);
        boolean dark = Ui.isDark(a);
        volume.setColors(separator, secondary, dark ? 0xffd1d1d6 : 0xffffffff);
        volume.setThumb(8, 10, true);
        prev.setIcon(Icons.PREVIOUS);
        next.setIcon(Icons.NEXT);
        prev.setColor(primary);
        next.setColor(primary);
        play.setColor(primary);
        volLow.setIcon(Icons.SPEAKER_LOW);
        volHigh.setIcon(Icons.SPEAKER_HIGH);
        volLow.setColor(secondary);
        volHigh.setColor(secondary);
        btnLyrics.setIcon(Icons.LYRICS);
        btnShuffle.setIcon(Icons.SHUFFLE);
        btnRepeat.setIcon(Icons.REPEAT);
        btnQueue.setIcon(Icons.QUEUE);
        prev.setContentDescription("Previous");
        next.setContentDescription("Next");
        play.setContentDescription("Play or pause");
        btnLyrics.setContentDescription("Lyrics");
        btnShuffle.setContentDescription("Shuffle");
        btnRepeat.setContentDescription("Repeat");
        btnQueue.setContentDescription("Up Next");

        dim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        });
        root.findViewById(R.id.np_grabber).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        });
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.togglePlay();
            }
        });
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.previous();
            }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.next();
            }
        });
        btnShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.setShuffle(!player.isShuffle());
            }
        });
        btnRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.cycleRepeat();
            }
        });
        btnLyrics.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(mode == MODE_LYRICS ? MODE_ART : MODE_LYRICS);
            }
        });
        btnQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(mode == MODE_QUEUE ? MODE_ART : MODE_QUEUE);
            }
        });

        scrubber.setListener(new Scrubber.Listener() {
            @Override
            public void onScrubStart() {
            }

            @Override
            public void onScrub(long value) {
                showTimes(value, player.durationMs());
            }

            @Override
            public void onScrubEnd(long value) {
                player.seekTo(value);
                lyricIndex = -2;
            }
        });
        volume.setListener(new Scrubber.Listener() {
            @Override
            public void onScrubStart() {
            }

            @Override
            public void onScrub(long value) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (int) value, 0);
            }

            @Override
            public void onScrubEnd(long value) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (int) value, 0);
            }
        });

        card.setDragLimit(scrubber);
        card.setDragListener(new NowPlayingCard.DragListener() {
            @Override
            public void onDrag(float offset) {
                card.setTranslationY(offset);
                dim.setAlpha(DIM * Math.max(0f, 1f - offset / Math.max(1, root.getHeight())));
            }

            @Override
            public void onRelease(boolean dismiss) {
                if (dismiss) {
                    close();
                } else {
                    card.animate().translationY(0).setDuration(200).setListener(null).start();
                    dim.animate().alpha(DIM).setDuration(200).setListener(null).start();
                }
            }
        });

        lyricsAdapter = new LyricsAdapter(a, primary, secondary);
        TextView credit = Ui.text(a, 13, R.attr.gfTextSecondary, false);
        credit.setPadding(Ui.dp(a, 24), Ui.dp(a, 24), Ui.dp(a, 24), Ui.dp(a, 8));
        lyricsAdapter.credit = credit;
        lyricsList.addFooterView(credit, null, false);
        lyricsList.setAdapter(lyricsAdapter);
        lyricsList.setClipToPadding(false);
        lyricsList.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int h = b - t;
                if (h != ob - ot) lyricsList.setPadding(0, h / 3, 0, h / 2);
            }
        });
        lyricsList.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                lastUserScroll = System.currentTimeMillis();
                return false;
            }
        });
        lyricsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (lyrics != null && lyrics.synced && position < lyrics.lines.size()) {
                    player.seekTo(lyrics.lines.get(position).timeMs);
                    lastUserScroll = 0;
                    lyricIndex = -2;
                }
            }
        });

        queueAdapter = new QueueAdapter(a);
        TextView upNext = Ui.text(a, 20, R.attr.gfTextPrimary, true);
        upNext.setText("Up Next");
        upNext.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), Ui.dp(a, 8));
        queueList.addHeaderView(upNext, null, false);
        queueList.setAdapter(queueAdapter);
        queueList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                player.skipToUpNext(position - queueList.getHeaderViewsCount());
            }
        });
    }

    boolean isOpen() {
        return open;
    }

    void onStart() {
        started = true;
        if (open) {
            Bg.cancelMain(ticker);
            Bg.main(ticker);
        }
    }

    void onStop() {
        started = false;
        Bg.cancelMain(ticker);
    }

    void open() {
        if (open || player.current() == null) return;
        open = true;
        update();
        float h = root.getHeight();
        card.setVisibility(View.VISIBLE);
        dim.setVisibility(View.VISIBLE);
        card.setTranslationY(h);
        dim.setAlpha(0f);
        card.animate().translationY(0).setDuration(320).setInterpolator(new DecelerateInterpolator(2f))
                .setListener(null).start();
        dim.animate().alpha(DIM).setDuration(320).setListener(null).start();
        Bg.cancelMain(ticker);
        Bg.main(ticker);
    }

    void openImmediately() {
        if (player.current() == null) return;
        open = true;
        card.setVisibility(View.VISIBLE);
        dim.setVisibility(View.VISIBLE);
        card.setTranslationY(0);
        dim.setAlpha(DIM);
        update();
        Bg.cancelMain(ticker);
        Bg.main(ticker);
    }

    void close() {
        if (!open) return;
        open = false;
        Bg.cancelMain(ticker);
        card.animate().translationY(root.getHeight()).setDuration(260).setInterpolator(new DecelerateInterpolator())
                .setListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        if (!open) card.setVisibility(View.GONE);
                    }
                }).start();
        dim.animate().alpha(0f).setDuration(260).setListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!open) dim.setVisibility(View.GONE);
            }
        }).start();
    }

    /** Called whenever the player changes. */
    void update() {
        MediaItem item = player.current();
        if (item == null) {
            trackKey = null;
            if (open) close();
            return;
        }
        title.setText(item.title);
        String line = item.artist != null ? item.artist : "";
        if (item.album != null) line = line.length() == 0 ? item.album : line + " — " + item.album;
        artist.setText(line);
        smallTitle.setText(item.title);
        smallArtist.setText(line);
        boolean playing = player.wantsToPlay();
        play.setIcon(playing ? Icons.PAUSE : Icons.PLAY);
        btnShuffle.setColor(player.isShuffle() ? accent : secondary);
        btnRepeat.setIcon(player.repeatMode() == Player.REPEAT_ONE ? Icons.REPEAT_ONE : Icons.REPEAT);
        btnRepeat.setColor(player.repeatMode() == Player.REPEAT_OFF ? secondary : accent);

        // iOS 10: the artwork shrinks back when paused.
        if (artPlaying == null || artPlaying != playing) {
            float scale = playing ? 1f : 0.84f;
            if (artPlaying == null || !open) {
                art.setScaleX(scale);
                art.setScaleY(scale);
            } else {
                art.animate().scaleX(scale).scaleY(scale).setDuration(380)
                        .setInterpolator(playing ? new OvershootInterpolator(1.4f) : new DecelerateInterpolator())
                        .setListener(null).start();
            }
            artPlaying = playing;
        }

        if (!item.key().equals(trackKey)) {
            trackKey = item.key();
            loadArtwork(item);
            loadLyrics(item);
        }
        if (mode == MODE_QUEUE && !dragging) queueAdapter.setItems(player.upNext());
        tick();
    }

    private void loadArtwork(final MediaItem item) {
        final String key = item.key();
        final int size = Math.max(Ui.dp(activity, 300), 512);
        Bitmap cached = ArtworkLoader.get(activity).cached(item, size);
        art.setBitmap(cached);
        smallArt.setBitmap(cached);
        card.setBlurredArtwork(cached != null ? Blur.of(cached) : null);
        if (cached != null) return;
        ArtworkLoader.get(activity).load(item, size, new ArtworkLoader.Callback() {
            @Override
            public void onArtwork(Bitmap bitmap) {
                if (!key.equals(trackKey)) return;
                art.setBitmap(bitmap);
                smallArt.setBitmap(bitmap);
                card.setBlurredArtwork(bitmap != null ? Blur.of(bitmap) : null);
            }
        });
    }

    private void loadLyrics(final MediaItem item) {
        final String key = item.key();
        lyrics = null;
        lyricsLoading = true;
        lyricIndex = -2;
        lyricsAdapter.setLyrics(null);
        showMode();
        final Context app = activity.getApplicationContext();
        Bg.run(new Bg.Work<Lyrics>() {
            @Override
            public Lyrics run() {
                return LyricsRepository.load(app, item);
            }
        }, new Bg.Done<Lyrics>() {
            @Override
            public void done(Lyrics result, Exception error) {
                if (!key.equals(trackKey)) return;
                lyrics = result;
                lyricsLoading = false;
                lyricsAdapter.setLyrics(result);
                lyricsList.setSelection(0);
                showMode();
                tick();
            }
        });
    }

    private void setMode(int m) {
        mode = m;
        lyricIndex = -2;
        lastUserScroll = 0;
        if (m == MODE_QUEUE) queueAdapter.setItems(player.upNext());
        showMode();
        tick();
    }

    private void showMode() {
        // Lyrics and Up Next: small artwork and title on top, the list below (iOS-style).
        boolean condensedMode = mode != MODE_ART;
        art.setVisibility(condensedMode ? View.INVISIBLE : View.VISIBLE);
        if (condensedMode && condensed.getVisibility() != View.VISIBLE) {
            condensed.setAlpha(0f);
            condensed.animate().alpha(1f).setDuration(180).setListener(null).start();
        }
        condensed.setVisibility(condensedMode ? View.VISIBLE : View.GONE);
        title.setVisibility(condensedMode ? View.GONE : View.VISIBLE);
        artist.setVisibility(condensedMode ? View.GONE : View.VISIBLE);
        boolean showLyrics = mode == MODE_LYRICS && lyrics != null;
        lyricsList.setVisibility(showLyrics ? View.VISIBLE : View.GONE);
        queueList.setVisibility(mode == MODE_QUEUE ? View.VISIBLE : View.GONE);
        if (mode == MODE_LYRICS && lyrics == null) {
            message.setVisibility(View.VISIBLE);
            message.setText(lyricsLoading ? "Looking for lyrics…" : "No lyrics for this song");
        } else {
            message.setVisibility(View.GONE);
        }
        card.setScrollingChild(mode == MODE_LYRICS ? lyricsList : mode == MODE_QUEUE ? queueList : null);
        btnLyrics.setColor(mode == MODE_LYRICS ? accent : secondary);
        btnQueue.setColor(mode == MODE_QUEUE ? accent : secondary);
    }

    private void showTimes(long pos, long dur) {
        elapsed.setText(Format.duration(pos));
        remaining.setText(Format.remaining(pos, dur));
    }

    private void tick() {
        if (!open || player.current() == null) return;
        long pos = player.positionMs();
        long dur = player.durationMs();
        scrubber.setMax(dur);
        if (!scrubber.isDragging()) {
            scrubber.setValue(pos);
            showTimes(pos, dur);
        }
        if (!volume.isDragging()) {
            volume.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            volume.setValue(audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        }
        if (mode == MODE_LYRICS && lyrics != null && lyrics.synced) {
            // Highlight a moment early, as the singer starts the line.
            int idx = lyrics.indexAt(pos + 250);
            if (idx != lyricIndex) {
                lyricIndex = idx;
                lyricsAdapter.setCurrent(idx);
                if (System.currentTimeMillis() - lastUserScroll > FOLLOW_PAUSE_MS) {
                    lyricsList.smoothScrollToPositionFromTop(Math.max(idx, 0), lyricsList.getHeight() / 3
                            - lyricsList.getPaddingTop(), 400);
                }
            }
        }
    }

    private void startDrag(View row, int index, float rawY) {
        if (dragging || row.getWidth() == 0 || row.getHeight() == 0) return;
        Bitmap shot = Bitmap.createBitmap(row.getWidth(), row.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(shot);
        canvas.drawColor(Ui.color(activity, R.attr.gfSurface));
        row.draw(canvas);
        dragGhost = new ImageView(activity);
        dragGhost.setImageBitmap(shot);
        dragGhost.setAlpha(0.95f);
        listArea.addView(dragGhost, new FrameLayout.LayoutParams(row.getWidth(), row.getHeight(),
                Gravity.TOP | Gravity.LEFT));
        int[] rowLoc = new int[2];
        int[] areaLoc = new int[2];
        row.getLocationOnScreen(rowLoc);
        listArea.getLocationOnScreen(areaLoc);
        dragGhost.setTranslationY(rowLoc[1] - areaLoc[1]);
        dragGrabOffset = rawY - rowLoc[1];
        dragFrom = index;
        dragAt = index;
        dragCurrent = player.current();
        dragLastRawY = rawY;
        dragScroll = 0;
        dragging = true;
        queueAdapter.setHidden(index);
        Bg.cancelMain(dragAutoScroll);
        Bg.main(dragAutoScroll);
    }

    private void moveDrag(float rawY) {
        if (!dragging) return;
        dragLastRawY = rawY;
        int[] areaLoc = new int[2];
        int[] listLoc = new int[2];
        listArea.getLocationOnScreen(areaLoc);
        queueList.getLocationOnScreen(listLoc);
        float top = rawY - dragGrabOffset - areaLoc[1];
        top = Math.max(0, Math.min(top, listArea.getHeight() - dragGhost.getHeight()));
        dragGhost.setTranslationY(top);

        // The row under the middle of the dragged song is where it goes.
        float center = areaLoc[1] + top + dragGhost.getHeight() / 2f - listLoc[1];
        int count = queueAdapter.getCount();
        int pos = queueList.pointToPosition(queueList.getWidth() / 2, (int) center);
        int target;
        if (pos == AdapterView.INVALID_POSITION) {
            View first = queueList.getChildAt(0);
            target = first != null && center < first.getTop() ? 0 : count - 1;
        } else {
            target = pos - queueList.getHeaderViewsCount();
        }
        target = Math.max(0, Math.min(count - 1, target));
        if (target != dragAt) {
            queueAdapter.move(dragAt, target);
            dragAt = target;
        }
        float edge = Ui.dp(activity, 40);
        dragScroll = center < edge ? -1 : (center > queueList.getHeight() - edge ? 1 : 0);
    }

    private void endDrag() {
        if (!dragging) return;
        dragging = false;
        Bg.cancelMain(dragAutoScroll);
        listArea.removeView(dragGhost);
        dragGhost = null;
        queueAdapter.setHidden(-1);
        // If the song changed meanwhile, Up Next shifted under the finger: drop the move.
        if (player.current() == dragCurrent && dragFrom != dragAt) player.moveUpNext(dragFrom, dragAt);
        queueAdapter.setItems(player.upNext());
    }

    /** Lyrics lines: the current one bold and bright, the rest faded. */
    private static final class LyricsAdapter extends BaseAdapter {
        private final Context context;
        private final int primary;
        private final int secondary;
        private Lyrics lyrics;
        private int current = -1;
        TextView credit;

        LyricsAdapter(Context context, int primary, int secondary) {
            this.context = context;
            this.primary = primary;
            this.secondary = secondary;
        }

        void setLyrics(Lyrics l) {
            lyrics = l;
            current = -1;
            credit.setText(l != null && l.credit != null ? "Lyrics: " + l.credit : "");
            notifyDataSetChanged();
        }

        void setCurrent(int index) {
            current = index;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return lyrics == null ? 0 : lyrics.lines.size();
        }

        @Override
        public Object getItem(int position) {
            return lyrics.lines.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView t = (TextView) convertView;
            if (t == null) {
                t = new TextView(context);
                t.setPadding(Ui.dp(context, 24), Ui.dp(context, 9), Ui.dp(context, 24), Ui.dp(context, 9));
                t.setLineSpacing(0, 1.05f);
            }
            String text = lyrics.lines.get(position).text;
            boolean synced = lyrics.synced;
            t.setText(text.trim().length() == 0 ? (synced ? "♪" : " ") : text);
            if (synced) {
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                boolean now = position == current;
                t.setTextColor(now ? primary : secondary);
                t.setAlpha(now ? 1f : (position < current ? 0.45f : 0.6f));
            } else {
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                t.setTypeface(Typeface.DEFAULT);
                t.setTextColor(primary);
                t.setAlpha(1f);
            }
            return t;
        }
    }

    private static final class QueueHolder {
        ArtworkView art;
        TextView title;
        TextView subtitle;
        IconView handle;
    }

    /** Up Next rows, each with a handle on the right for dragging it to a new place. */
    private final class QueueAdapter extends BaseAdapter {
        private final Context context;
        private List<MediaItem> items = new ArrayList<MediaItem>();
        /** Row being dragged (drawn as a floating copy instead), or -1. */
        private int hidden = -1;

        QueueAdapter(Context context) {
            this.context = context;
        }

        void setItems(List<MediaItem> list) {
            items = list;
            notifyDataSetChanged();
        }

        void setHidden(int index) {
            hidden = index;
            notifyDataSetChanged();
        }

        void move(int from, int to) {
            Lists.move(items, from, to);
            hidden = to;
            notifyDataSetChanged();
        }

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
            QueueHolder h;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(context, 20), Ui.dp(context, 4), Ui.dp(context, 6), Ui.dp(context, 4));
                row.setBackgroundResource(Ui.resource(context, R.attr.gfRowBackground));
                h = new QueueHolder();
                h.art = Ui.artwork(context, 40);
                row.addView(h.art);
                LinearLayout texts = new LinearLayout(context);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 4), 0);
                h.title = Ui.text(context, 15, R.attr.gfTextPrimary, false);
                h.subtitle = Ui.text(context, 13, R.attr.gfTextSecondary, false);
                texts.addView(h.title);
                texts.addView(h.subtitle);
                row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                h.handle = new IconView(context);
                h.handle.setIcon(Icons.HANDLE);
                h.handle.setColor(secondary);
                int pad = Ui.dp(context, 11);
                h.handle.setPadding(pad, pad, pad, pad);
                h.handle.setContentDescription("Drag to reorder");
                h.handle.setOnTouchListener(handleTouch);
                row.addView(h.handle, new LinearLayout.LayoutParams(Ui.dp(context, 46), Ui.dp(context, 46)));
                row.setTag(h);
                convertView = row;
            } else {
                h = (QueueHolder) convertView.getTag();
            }
            MediaItem m = items.get(position);
            h.title.setText(m.title);
            h.subtitle.setText(m.artist != null ? m.artist : "");
            h.handle.setTag(position);
            ArtworkLoader.get(context).into(h.art, m, Ui.dp(context, 40));
            convertView.setVisibility(position == hidden ? View.INVISIBLE : View.VISIBLE);
            return convertView;
        }
    }
}
