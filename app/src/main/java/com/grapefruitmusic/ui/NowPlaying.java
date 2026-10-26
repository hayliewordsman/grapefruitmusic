package com.grapefruitmusic.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
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
import com.grapefruitmusic.radio.Radio;
import com.grapefruitmusic.radio.RadioArt;
import com.grapefruitmusic.ui.widget.ArtworkView;
import com.grapefruitmusic.ui.widget.DragFrame;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.ui.widget.NowPlayingCard;
import com.grapefruitmusic.ui.widget.Scrubber;
import com.grapefruitmusic.ui.widget.TuningDial;
import com.grapefruitmusic.ui.widget.VerticalSlider;
import com.grapefruitmusic.util.ArtworkLoader;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Blur;
import com.grapefruitmusic.util.Format;
import com.grapefruitmusic.util.Notice;
import com.grapefruitmusic.util.Prefs;

import java.util.ArrayList;
import java.util.List;

/**
 * The Now Playing card: artwork, controls, time-synced lyrics and Up Next. It slides up from the
 * bottom, or in landscape pops in from the right as a panel beside the library. While the radio
 * is on it shows the station instead, with a dial, seek buttons and where the sound goes.
 */
final class NowPlaying {
    private static final int MODE_ART = 0;
    private static final int MODE_LYRICS = 1;
    private static final int MODE_QUEUE = 2;
    private static final float DIM = 0.45f;
    private static final long FOLLOW_PAUSE_MS = 3000;

    private final Activity activity;
    private final Player player;
    private final Radio radio;
    private final AudioManager audio;
    private final View root;
    private final View dim;
    private final NowPlayingCard card;
    private final View content;
    private final View grabber;
    private final View sideGrabber;
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
    private final IconView btnKaraoke;
    private final IconView btnQueue;
    private final View condensed;
    private final ArtworkView smallArt;
    private final TextView smallTitle;
    private final TextView smallArtist;
    private final DragFrame listArea;
    private final IconView prev;
    private final IconView next;
    private final View times;
    private final View tunerRow;
    private final TuningDial dial;
    private final View musicButtons;
    private final View radioButtons;
    private final Segmented output;
    private final IconView btnPreset;
    private final VerticalSlider karaokeSlider;
    private final int accent;
    private final int secondary;
    private final int primary;

    private final LyricsAdapter lyricsAdapter;
    private final QueueAdapter queueAdapter;

    private boolean open;
    /** Landscape: the card is a panel at the right. */
    private boolean side;
    private boolean started;
    private int mode = MODE_ART;
    private String trackKey;
    private Lyrics lyrics;
    private boolean lyricsLoading;
    private int lyricIndex = -2;
    private long lastUserScroll;
    private Boolean artPlaying;
    private String queueAnchorKey;
    /** Showing the radio (not the music queue). */
    private boolean radioMode;
    private String radioArtKey;

    // Dragging a song in Up Next by its handle. The list's data stays put while dragging: the
    // rows in between slide aside (translationY), and the move is made on release.
    private boolean dragging;
    /** The lifted copy is gliding into its new place after release. */
    private boolean dragSettling;
    private int dragFrom;
    private int dragAt;
    private MediaItem dragCurrent;
    private ImageView dragGhost;
    /** Extra pixels around the lifted copy for its shadow. */
    private int dragShadow;
    private float dragGrabOffset;
    private float dragLastRawY;
    /** Auto-scroll speed in pixels per frame: negative up, positive down. */
    private float dragScroll;
    /** Height of an Up Next row. */
    private int dragRowHeight;

    /** Keeps the list scrolling while a dragged song is held near its top or bottom edge. */
    private final Runnable dragAutoScroll = new Runnable() {
        @Override
        public void run() {
            if (!dragging) return;
            if (dragScroll != 0 && queueList.getChildCount() > 0) {
                View first = queueList.getChildAt(0);
                queueList.setSelectionFromTop(queueList.getFirstVisiblePosition(),
                        first.getTop() - Math.round(dragScroll));
                moveDrag(dragLastRawY);
            }
            Bg.mainDelayed(this, 16);
        }
    };

    private final View.OnTouchListener handleTouch = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View handle, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startDrag((View) handle.getParent(), (Integer) handle.getTag(), e.getRawY());
                    // From here the DragFrame around the list owns the gesture; keep the card's
                    // pull-to-close (an ancestor of the frame) out of it.
                    if (dragging) listArea.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    moveDrag(e.getRawY());
                    return true;
                case MotionEvent.ACTION_UP:
                    endDrag();
                    return true;
                default:
                    // ACTION_CANCEL: the list redrew this row, or the DragFrame took over. The
                    // drag carries on through the DragFrame.
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
        radio = Radio.get(a);
        audio = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
        accent = Ui.accent(a);
        secondary = Ui.color(a, R.attr.gfTextSecondary);
        primary = Ui.color(a, R.attr.gfTextPrimary);
        int separator = Ui.color(a, R.attr.gfSeparator);

        dim = root.findViewById(R.id.np_dim);
        card = (NowPlayingCard) root.findViewById(R.id.np_card);
        content = root.findViewById(R.id.np_content);
        grabber = root.findViewById(R.id.np_grabber);
        sideGrabber = root.findViewById(R.id.np_side_grabber);
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
        prev = (IconView) root.findViewById(R.id.np_prev);
        next = (IconView) root.findViewById(R.id.np_next);
        btnLyrics = (IconView) root.findViewById(R.id.np_btn_lyrics);
        btnShuffle = (IconView) root.findViewById(R.id.np_btn_shuffle);
        btnRepeat = (IconView) root.findViewById(R.id.np_btn_repeat);
        btnKaraoke = (IconView) root.findViewById(R.id.np_btn_karaoke);
        btnQueue = (IconView) root.findViewById(R.id.np_btn_queue);
        condensed = root.findViewById(R.id.np_condensed);
        smallArt = (ArtworkView) root.findViewById(R.id.np_small_art);
        smallTitle = (TextView) root.findViewById(R.id.np_small_title);
        smallArtist = (TextView) root.findViewById(R.id.np_small_artist);
        listArea = (DragFrame) root.findViewById(R.id.np_list_area);
        listArea.setCallback(new DragFrame.Callback() {
            @Override
            public boolean isDragging() {
                return dragging;
            }

            @Override
            public void onDragMove(float rawY) {
                moveDrag(rawY);
            }

            @Override
            public void onDragEnd() {
                endDrag();
            }
        });
        times = root.findViewById(R.id.np_times);
        tunerRow = root.findViewById(R.id.np_tuner);
        dial = (TuningDial) root.findViewById(R.id.np_dial);
        musicButtons = root.findViewById(R.id.np_buttons);
        radioButtons = root.findViewById(R.id.np_radio_buttons);
        btnPreset = (IconView) root.findViewById(R.id.np_btn_preset);
        karaokeSlider = (VerticalSlider) root.findViewById(R.id.np_karaoke);
        output = new Segmented(a, new String[]{"Headphones", "Speaker"}, 0);
        ((FrameLayout) root.findViewById(R.id.np_output)).addView(output);
        buildRadioControls(root);
        buildKaraokeSlider(root);
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
        volume.setColors(separator, secondary, Ui.color(a, R.attr.gfKnob));
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
        btnKaraoke.setIcon(Icons.MIC);
        btnQueue.setIcon(Icons.QUEUE);
        prev.setContentDescription("Previous");
        next.setContentDescription("Next");
        play.setContentDescription("Play or pause");
        btnLyrics.setContentDescription("Lyrics");
        btnShuffle.setContentDescription("Shuffle");
        btnRepeat.setContentDescription("Repeat");
        btnKaraoke.setContentDescription("Karaoke Mode");
        btnKaraoke.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !Prefs.karaoke();
                Prefs.setKaraoke(on);
                if (on) Player.prepareKaraoke(activity);
                player.audioEffectsChanged();
                showKaraoke();
                Notice.show(activity, on ? "Karaoke Mode on" : "Karaoke Mode off");
            }
        });
        btnQueue.setContentDescription("Up Next");

        dim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        });
        View.OnClickListener closeClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        };
        grabber.setOnClickListener(closeClick);
        sideGrabber.setOnClickListener(closeClick);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (radioMode) radio.togglePlay();
                else player.togglePlay();
            }
        });
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (radioMode) radio.seek(false);
                else player.previous();
            }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (radioMode) radio.seek(true);
                else player.next();
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
                audio.setStreamVolume(volumeStream(), (int) value, 0);
            }

            @Override
            public void onScrubEnd(long value) {
                audio.setStreamVolume(volumeStream(), (int) value, 0);
            }
        });

        card.setDragLimit(scrubber);
        card.setDragHandle(sideGrabber);
        card.setDragListener(new NowPlayingCard.DragListener() {
            @Override
            public void onDrag(float offset) {
                setOffset(offset);
                dim.setAlpha(DIM * Math.max(0f, 1f - offset / Math.max(1f, hiddenOffset())));
            }

            @Override
            public void onRelease(boolean dismiss) {
                if (dismiss) {
                    close();
                } else {
                    slide(0).setDuration(200).setListener(null).start();
                    dim.animate().alpha(DIM).setDuration(200).setListener(null).start();
                }
            }
        });
        applyLayout();

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
        // With little queued, the list would be too short to scroll Now Playing to the top, and
        // Recently Played would always show. Blank room below Up Next keeps history tucked away
        // above. Measured with the rows, so it's right on the first layout too.
        View queueSpacer = new View(a) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = queueList.getHeight();
                int now = queueAdapter.nowSectionPosition();
                int room = height > 0 && now > 0 ? Math.max(0, height - queueAdapter.heightFrom(now, height, queueList)) : 0;
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), room);
            }
        };
        queueList.addFooterView(queueSpacer, null, false);
        queueList.setAdapter(queueAdapter);
        queueList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                queueAdapter.click(position);
            }
        });
    }

    private void buildRadioControls(View root) {
        dial.setColors(secondary, accent);
        dial.setRange(Radio.LOWEST, Radio.HIGHEST, Radio.STEP);
        dial.setListener(new TuningDial.Listener() {
            @Override
            public void onTuning(int freq) {
                radio.tune(freq);
            }

            @Override
            public void onTuned(int freq) {
                radio.tune(freq);
                // The artwork waits for the dial to stop.
                update();
            }
        });
        IconView down = (IconView) root.findViewById(R.id.np_tune_down);
        IconView up = (IconView) root.findViewById(R.id.np_tune_up);
        down.setIcon(Icons.CHEVRON_LEFT);
        up.setIcon(Icons.CHEVRON_RIGHT);
        down.setColor(secondary);
        up.setColor(secondary);
        down.setContentDescription("Tune down 0.1");
        up.setContentDescription("Tune up 0.1");
        down.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                radio.step(false);
            }
        });
        up.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                radio.step(true);
            }
        });
        output.setContentDescription("Play the radio through");
        output.setListener(new Segmented.Listener() {
            @Override
            public void onSelected(int index) {
                radio.setSpeaker(index == 1);
            }
        });
        btnPreset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int f = radio.frequency();
                if (radio.preset(f) != null) {
                    radio.removePreset(f);
                    Notice.show(activity, "Removed " + Radio.format(f) + " FM from Presets");
                } else {
                    radio.addPreset(f);
                    Notice.show(activity, "Added " + Radio.format(f) + " FM to Presets");
                }
            }
        });
    }

    private void buildKaraokeSlider(View root) {
        karaokeSlider.setIcon(Icons.MIC);
        karaokeSlider.setColors((secondary & 0x00ffffff) | 0x40000000, accent, secondary);
        karaokeSlider.setContentDescription("Vocal removal");
        karaokeSlider.setListener(new VerticalSlider.Listener() {
            @Override
            public void onSlide(float value) {
                Prefs.setKaraokeAmount(value);
                player.audioEffectsChanged();
            }

            @Override
            public void onSlideEnd(float value) {
                onSlide(value);
            }
        });
        // As tall as there's room for, up to 240dp.
        root.findViewById(R.id.np_stage).addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int h = Math.min(Ui.dp(activity, 240), (b - t) - Ui.dp(activity, 24));
                ViewGroup.LayoutParams lp = karaokeSlider.getLayoutParams();
                if (h > 0 && lp.height != h) {
                    lp.height = h;
                    karaokeSlider.setLayoutParams(lp);
                }
            }
        });
    }

    /** The volume the slider (and the volume keys) control: the radio may have its own. */
    int volumeStream() {
        return radioMode ? radio.volumeStream() : AudioManager.STREAM_MUSIC;
    }

    boolean isOpen() {
        return open;
    }

    /** The screen turned: from the bottom in portrait, a panel at the right in landscape. */
    void configurationChanged() {
        card.animate().cancel();
        dim.animate().cancel();
        applyLayout();
        setOffset(0);
        if (open) {
            dim.setAlpha(DIM);
        } else {
            card.setVisibility(View.GONE);
            dim.setVisibility(View.GONE);
        }
    }

    private void applyLayout() {
        Configuration config = activity.getResources().getConfiguration();
        side = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
        if (side) {
            int screen = Ui.dp(activity, config.screenWidthDp);
            int width = Math.max(Ui.dp(activity, 320), Math.min(Math.round(screen * 0.42f), Ui.dp(activity, 440)));
            int margin = Ui.dp(activity, 8);
            lp.width = Math.min(width, screen - Ui.dp(activity, 48));
            lp.gravity = Gravity.RIGHT;
            lp.setMargins(0, margin, margin, margin);
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.setMargins(0, Ui.dp(activity, 16), 0, 0);
        }
        card.setLayoutParams(lp);
        card.setSide(side);
        // The grab bar goes across the top of the card, or down the left edge of the panel.
        grabber.setVisibility(side ? View.GONE : View.VISIBLE);
        sideGrabber.setVisibility(side ? View.VISIBLE : View.GONE);
        content.setPadding(side ? Ui.dp(activity, 22) : 0, side ? Ui.dp(activity, 12) : 0, 0, 0);
    }

    /** How far the card moves to be out of sight: down, or right at the side. */
    private float hiddenOffset() {
        if (!side) return root.getHeight();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
        return lp.width + lp.rightMargin;
    }

    private void setOffset(float offset) {
        card.setTranslationX(side ? offset : 0);
        card.setTranslationY(side ? 0 : offset);
    }

    private ViewPropertyAnimator slide(float offset) {
        return side ? card.animate().translationX(offset) : card.animate().translationY(offset);
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
        if (open || (player.current() == null && !radio.inSession())) return;
        open = true;
        update();
        card.setVisibility(View.VISIBLE);
        dim.setVisibility(View.VISIBLE);
        setOffset(hiddenOffset());
        dim.setAlpha(0f);
        slide(0).setDuration(320).setInterpolator(new DecelerateInterpolator(2f)).setListener(null).start();
        dim.animate().alpha(DIM).setDuration(320).setListener(null).start();
        Bg.cancelMain(ticker);
        Bg.main(ticker);
    }

    void openImmediately() {
        if (player.current() == null && !radio.inSession()) return;
        open = true;
        card.setVisibility(View.VISIBLE);
        dim.setVisibility(View.VISIBLE);
        setOffset(0);
        dim.setAlpha(DIM);
        update();
        Bg.cancelMain(ticker);
        Bg.main(ticker);
    }

    void close() {
        if (!open) return;
        open = false;
        Bg.cancelMain(ticker);
        slide(hiddenOffset()).setDuration(260).setInterpolator(new DecelerateInterpolator())
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

    private void showKaraoke() {
        btnKaraoke.setColor(Prefs.karaoke() ? accent : secondary);
        showKaraokeSlider();
    }

    /** The vocal slider beside the artwork while Karaoke Mode is on (the artwork makes room). */
    private void showKaraokeSlider() {
        boolean show = !radioMode && Prefs.karaoke() && mode == MODE_ART;
        if (!karaokeSlider.isSliding()) karaokeSlider.setValue(Prefs.karaokeAmount());
        if (show == (karaokeSlider.getVisibility() == View.VISIBLE)) return;
        karaokeSlider.setVisibility(show ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) art.getLayoutParams();
        int side = Ui.dp(activity, show ? 58 : 12);
        lp.leftMargin = side;
        lp.rightMargin = side;
        art.setLayoutParams(lp);
    }

    /** Switches the card between the music queue and the radio. */
    private void setRadioMode(boolean on) {
        radioMode = on;
        if (on && mode != MODE_ART) setMode(MODE_ART);
        scrubber.setVisibility(on ? View.GONE : View.VISIBLE);
        times.setVisibility(on ? View.GONE : View.VISIBLE);
        tunerRow.setVisibility(on ? View.VISIBLE : View.GONE);
        musicButtons.setVisibility(on ? View.GONE : View.VISIBLE);
        radioButtons.setVisibility(on ? View.VISIBLE : View.GONE);
        card.setDragLimit(on ? tunerRow : scrubber);
        prev.setContentDescription(on ? "Previous station" : "Previous");
        next.setContentDescription(on ? "Next station" : "Next");
        play.setContentDescription(on ? "Play or stop the radio" : "Play or pause");
        // The radio's text can be long: let it scroll by.
        artist.setEllipsize(on ? android.text.TextUtils.TruncateAt.MARQUEE : android.text.TextUtils.TruncateAt.END);
        artist.setMarqueeRepeatLimit(-1);
        artist.setSelected(on);
        trackKey = null;
        radioArtKey = null;
        showKaraokeSlider();
    }

    private void updateRadio() {
        int freq = radio.frequency();
        setText(title, radio.title());
        int state = radio.state();
        setText(artist, state == Radio.STARTING ? "Tuning in…" : state == Radio.SEEKING ? "Looking for a station…"
                : radio.subtitle());
        boolean playing = radio.wantsToPlay();
        play.setIcon(playing ? Icons.STOP : Icons.PLAY);
        dial.setFrequency(freq);
        output.select(radio.speaker() ? 1 : 0);
        boolean saved = radio.preset(freq) != null;
        btnPreset.setIcon(saved ? Icons.CHECK : Icons.PLUS);
        btnPreset.setColor(saved ? accent : primary);
        btnPreset.setContentDescription(saved ? "Remove from Presets" : "Add to Presets");
        scaleArt(playing);
        String key = freq + "|" + radio.programName();
        // Not while the dial turns: a new picture (and its blur) for every step would be too slow.
        if (!key.equals(radioArtKey) && !dial.isTurning()) {
            radioArtKey = key;
            Bitmap b = RadioArt.of(freq, radio.programName(), Math.max(Ui.dp(activity, 300), 512));
            art.setBitmap(b);
            smallArt.setBitmap(b);
            card.setBlurredArtwork(Blur.of(b));
        }
        tick();
    }

    /** Leaves the text alone when it's the same (so the radio text keeps scrolling). */
    private static void setText(TextView t, String text) {
        if (!text.equals(t.getText().toString())) t.setText(text);
    }

    /** iOS 10: the artwork shrinks back when paused. */
    private void scaleArt(boolean playing) {
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
    }

    /** Called whenever the player or the radio changes. */
    void update() {
        boolean r = radio.inSession();
        if (r != radioMode) setRadioMode(r);
        if (r) {
            updateRadio();
            return;
        }
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
        showKaraoke();
        scaleArt(playing);

        if (!item.key().equals(trackKey)) {
            trackKey = item.key();
            loadArtwork(item);
            loadLyrics(item);
        }
        if (mode == MODE_QUEUE) refreshQueue(false);
        tick();
    }

    /**
     * Rebuilds the queue list. Opening Up Next, or a new song starting, scrolls so "Now Playing"
     * is at the top; Recently Played sits above it, revealed by scrolling up.
     */
    private void refreshQueue(boolean anchor) {
        if (dragging) return;
        MediaItem current = player.current();
        queueAdapter.setData(player.history(), current, player.upNext());
        String key = current != null ? current.key() : null;
        if (anchor || (key != null && !key.equals(queueAnchorKey))) {
            queueAnchorKey = key;
            queueList.setSelectionFromTop(queueAdapter.nowSectionPosition(), 0);
        }
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
        if (m == MODE_QUEUE) refreshQueue(true);
        showMode();
        showKaraokeSlider();
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
        if (!open) return;
        if (!volume.isDragging()) {
            int stream = volumeStream();
            volume.setMax(audio.getStreamMaxVolume(stream));
            volume.setValue(audio.getStreamVolume(stream));
        }
        if (radioMode || player.current() == null) return;
        long pos = player.positionMs();
        long dur = player.durationMs();
        scrubber.setMax(dur);
        if (!scrubber.isDragging()) {
            scrubber.setValue(pos);
            showTimes(pos, dur);
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
        if (dragging || dragSettling || row.getWidth() == 0 || row.getHeight() == 0) return;
        // A lifted copy of the row, with a soft shadow, follows the finger.
        dragShadow = Ui.dp(activity, 10);
        int w = row.getWidth();
        int h = row.getHeight();
        Bitmap shot = Bitmap.createBitmap(w, h + 2 * dragShadow, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(shot);
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(Ui.color(activity, R.attr.gfSurface));
        shadow.setShadowLayer(dragShadow * 0.8f, 0, dragShadow * 0.25f, 0x40000000);
        canvas.drawRect(0, dragShadow, w, dragShadow + h, shadow);
        canvas.save();
        canvas.translate(0, dragShadow);
        row.draw(canvas);
        canvas.restore();
        dragGhost = new ImageView(activity);
        dragGhost.setImageBitmap(shot);
        listArea.addView(dragGhost, new FrameLayout.LayoutParams(w, h + 2 * dragShadow, Gravity.TOP | Gravity.LEFT));
        int[] rowLoc = new int[2];
        int[] areaLoc = new int[2];
        row.getLocationOnScreen(rowLoc);
        listArea.getLocationOnScreen(areaLoc);
        dragGhost.setTranslationY(rowLoc[1] - areaLoc[1] - dragShadow);
        dragGhost.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start();
        dragGrabOffset = rawY - rowLoc[1];
        dragFrom = index;
        dragAt = index;
        dragRowHeight = h;
        dragCurrent = player.current();
        dragLastRawY = rawY;
        dragScroll = 0;
        dragging = true;
        // Hide the row itself (no re-binding of the list), and remember it for rows bound later.
        queueAdapter.hidden = index;
        row.setVisibility(View.INVISIBLE);
        Bg.cancelMain(dragAutoScroll);
        Bg.main(dragAutoScroll);
    }

    /** How far the Up Next row at index is pushed aside to make room for the dragged song. */
    private int dragShift(int index) {
        if (!dragging) return 0;
        if (index == dragFrom) return 0;
        if (dragFrom < dragAt && index > dragFrom && index <= dragAt) return -dragRowHeight;
        if (dragFrom > dragAt && index >= dragAt && index < dragFrom) return dragRowHeight;
        return 0;
    }

    /** Slides the visible rows to where they belong for the current drop spot. */
    private void applyShifts(boolean animate) {
        for (int i = 0; i < queueList.getChildCount(); i++) {
            View child = queueList.getChildAt(i);
            Object tag = child.getTag();
            if (!(tag instanceof QueueHolder)) continue;
            QueueHolder h = (QueueHolder) tag;
            if (h.role != ROLE_NEXT) continue;
            float to = dragShift(h.index);
            if (child.getTranslationY() == to && h.shiftTarget == to) continue;
            h.shiftTarget = to;
            child.animate().cancel();
            if (animate) {
                child.animate().translationY(to).setDuration(150).setInterpolator(new DecelerateInterpolator()).start();
            } else {
                child.setTranslationY(to);
            }
        }
    }

    private void moveDrag(float rawY) {
        if (!dragging) return;
        dragLastRawY = rawY;
        int[] areaLoc = new int[2];
        int[] listLoc = new int[2];
        listArea.getLocationOnScreen(areaLoc);
        queueList.getLocationOnScreen(listLoc);
        float top = rawY - dragGrabOffset - areaLoc[1];
        top = Math.max(0, Math.min(top, listArea.getHeight() - dragRowHeight));
        dragGhost.setTranslationY(top - dragShadow);

        // The drop spot: where the middle of the dragged song is, counted in Up Next rows from a
        // visible Up Next row's own (unshifted) place.
        float center = areaLoc[1] + top + dragRowHeight / 2f - listLoc[1];
        int count = queueAdapter.nextCount();
        int target = dragAt;
        for (int i = 0; i < queueList.getChildCount(); i++) {
            View child = queueList.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof QueueHolder && ((QueueHolder) tag).role == ROLE_NEXT) {
                int index = ((QueueHolder) tag).index;
                target = index + (int) Math.floor((center - child.getTop()) / dragRowHeight);
                break;
            }
        }
        target = Math.max(0, Math.min(count - 1, target));
        if (target != dragAt) {
            dragAt = target;
            applyShifts(true);
        }

        // Near the top or bottom edge, scroll; faster the closer to the edge.
        float edge = Ui.dp(activity, 48);
        float max = Ui.dp(activity, 10);
        if (center < edge) dragScroll = -max * Math.min(1f, (edge - center) / edge);
        else if (center > queueList.getHeight() - edge) {
            dragScroll = max * Math.min(1f, (center - (queueList.getHeight() - edge)) / edge);
        } else dragScroll = 0;
    }

    private void endDrag() {
        if (!dragging) return;
        Bg.cancelMain(dragAutoScroll);
        // If the song changed meanwhile, Up Next shifted under the finger: drop the move.
        boolean moved = player.current() == dragCurrent && dragFrom != dragAt;
        if (moved) player.moveUpNext(dragFrom, dragAt);
        final int landing = moved ? dragAt : dragFrom;
        // Where the song lands: a visible Up Next row's own place, stepped to the landing spot.
        float slot = Float.NaN;
        int[] areaLoc = new int[2];
        listArea.getLocationOnScreen(areaLoc);
        for (int i = 0; i < queueList.getChildCount(); i++) {
            View child = queueList.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof QueueHolder && ((QueueHolder) tag).role == ROLE_NEXT) {
                int[] loc = new int[2];
                child.getLocationOnScreen(loc);
                float home = loc[1] - child.getTranslationY() - areaLoc[1];
                slot = home + (landing - ((QueueHolder) tag).index) * (float) dragRowHeight;
                break;
            }
        }
        dragging = false;
        dragSettling = true;
        // Show the new order now: the rows are already where they end up, so nothing moves.
        // The landing spot stays empty until the lifted copy gets there.
        queueAdapter.hidden = landing;
        for (int i = 0; i < queueList.getChildCount(); i++) {
            View child = queueList.getChildAt(i);
            child.animate().cancel();
            child.setTranslationY(0);
        }
        refreshQueue(false);

        final ImageView ghost = dragGhost;
        Runnable finish = new Runnable() {
            @Override
            public void run() {
                if (!dragSettling || dragGhost != ghost) return;
                dragSettling = false;
                dragGhost = null;
                listArea.removeView(ghost);
                queueAdapter.hidden = -1;
                for (int i = 0; i < queueList.getChildCount(); i++) {
                    View child = queueList.getChildAt(i);
                    Object tag = child.getTag();
                    if (tag instanceof QueueHolder && ((QueueHolder) tag).role == ROLE_NEXT) child.setVisibility(View.VISIBLE);
                }
            }
        };
        if (ghost == null || Float.isNaN(slot) || slot < -dragRowHeight || slot > listArea.getHeight()) {
            finish.run();
            return;
        }
        ghost.animate().translationY(slot - dragShadow).scaleX(1f).scaleY(1f).setDuration(140)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(finish).start();
        // In case the animation never ends (the card closed meanwhile).
        Bg.mainDelayed(finish, 400);
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

    private static final int ROW_SECTION = 0;
    private static final int ROW_SONG = 1;
    private static final int ROW_EMPTY = 2;
    private static final int ROLE_HISTORY = 0;
    private static final int ROLE_NOW = 1;
    private static final int ROLE_NEXT = 2;

    /** One line of the queue list: a section title, a song, or an empty-state note. */
    private static final class QueueEntry {
        final int type;
        final String text;
        final MediaItem item;
        final int role;
        /** Index within its section (Up Next index for ROLE_NEXT). */
        final int index;

        QueueEntry(int type, String text, MediaItem item, int role, int index) {
            this.type = type;
            this.text = text;
            this.item = item;
            this.role = role;
            this.index = index;
        }
    }

    private static final class QueueHolder {
        int role;
        int index;
        /** Where the row is sliding to (translationY) while a song is dragged. */
        float shiftTarget;
        View texts;
        ArtworkView art;
        TextView title;
        TextView subtitle;
        IconView speaker;
        IconView handle;
    }

    private static final class SectionHolder {
        TextView title;
        TextView action;
    }

    /**
     * Recently Played (oldest first, faded), Now Playing, then Up Next with drag handles.
     */
    private final class QueueAdapter extends BaseAdapter {
        private final Context context;
        private final List<QueueEntry> entries = new ArrayList<QueueEntry>();
        private List<MediaItem> history = new ArrayList<MediaItem>();
        private MediaItem current;
        private List<MediaItem> next = new ArrayList<MediaItem>();
        /** Up Next index being dragged (drawn as a floating copy instead), or -1. */
        private int hidden = -1;
        private int nowSection;
        private int firstNext;
        /** Measured height of each row type, filled in as needed. */
        private final int[] rowHeights = new int[3];

        QueueAdapter(Context context) {
            this.context = context;
        }

        void setData(List<MediaItem> history, MediaItem current, List<MediaItem> next) {
            this.history = history;
            this.current = current;
            this.next = next;
            rebuild();
        }

        private void rebuild() {
            entries.clear();
            if (!history.isEmpty()) {
                entries.add(new QueueEntry(ROW_SECTION, "Recently Played", null, -1, -1));
                for (int i = 0; i < history.size(); i++) {
                    entries.add(new QueueEntry(ROW_SONG, null, history.get(i), ROLE_HISTORY, i));
                }
            }
            nowSection = entries.size();
            if (current != null) {
                entries.add(new QueueEntry(ROW_SECTION, "Now Playing", null, -1, -1));
                entries.add(new QueueEntry(ROW_SONG, null, current, ROLE_NOW, 0));
            }
            entries.add(new QueueEntry(ROW_SECTION, "Up Next", null, -1, -1));
            firstNext = entries.size();
            if (next.isEmpty()) {
                entries.add(new QueueEntry(ROW_EMPTY, "Nothing else is queued.", null, -1, -1));
            } else {
                for (int i = 0; i < next.size(); i++) {
                    entries.add(new QueueEntry(ROW_SONG, null, next.get(i), ROLE_NEXT, i));
                }
            }
            notifyDataSetChanged();
        }

        /** Height of the rows from position on, counting only up to limit. */
        int heightFrom(int position, int limit, ViewGroup parent) {
            int width = View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY);
            int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            int total = 0;
            for (int i = position; i < entries.size() && total < limit; i++) {
                int type = entries.get(i).type;
                if (rowHeights[type] == 0) {
                    View v = getView(i, null, parent);
                    v.measure(width, unspecified);
                    rowHeights[type] = v.getMeasuredHeight();
                }
                total += rowHeights[type];
            }
            return total;
        }

        int nowSectionPosition() {
            return nowSection;
        }

        int firstNextPosition() {
            return firstNext;
        }

        int nextCount() {
            return next.size();
        }


        void click(int position) {
            if (position < 0 || position >= entries.size()) return;
            QueueEntry e = entries.get(position);
            if (e.type != ROW_SONG) return;
            if (e.role == ROLE_HISTORY) player.playNow(e.item);
            else if (e.role == ROLE_NEXT) player.skipToUpNext(e.index);
        }

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public Object getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public int getItemViewType(int position) {
            return entries.get(position).type;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            QueueEntry e = entries.get(position);
            return e.type == ROW_SONG && e.role != ROLE_NOW;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            QueueEntry e = entries.get(position);
            if (e.type == ROW_SECTION) return section(e, convertView);
            if (e.type == ROW_EMPTY) {
                TextView t = (TextView) convertView;
                if (t == null) {
                    t = Ui.text(context, 15, R.attr.gfTextSecondary, false);
                    t.setPadding(Ui.dp(context, 20), Ui.dp(context, 10), Ui.dp(context, 20), Ui.dp(context, 24));
                }
                t.setText(e.text);
                return t;
            }
            return song(e, convertView);
        }

        private View section(QueueEntry e, View convertView) {
            SectionHolder h;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.BOTTOM);
                row.setPadding(Ui.dp(context, 20), Ui.dp(context, 14), Ui.dp(context, 20), Ui.dp(context, 6));
                h = new SectionHolder();
                h.title = Ui.text(context, 20, R.attr.gfTextPrimary, true);
                row.addView(h.title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                h.action = Ui.text(context, 16, R.attr.gfAccent, false);
                h.action.setText("Clear");
                h.action.setPadding(Ui.dp(context, 12), Ui.dp(context, 4), 0, Ui.dp(context, 2));
                h.action.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        player.clearHistory();
                    }
                });
                row.addView(h.action);
                row.setTag(h);
                convertView = row;
            } else {
                h = (SectionHolder) convertView.getTag();
            }
            h.title.setText(e.text);
            h.action.setVisibility("Recently Played".equals(e.text) ? View.VISIBLE : View.GONE);
            return convertView;
        }

        private View song(QueueEntry e, View convertView) {
            QueueHolder h;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Ui.dp(context, 54));
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
                h.texts = texts;
                row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                h.speaker = new IconView(context);
                h.speaker.setIcon(Icons.SPEAKER_HIGH);
                h.speaker.setColor(accent);
                int pad = Ui.dp(context, 13);
                h.speaker.setPadding(pad, pad, pad, pad);
                row.addView(h.speaker, new LinearLayout.LayoutParams(Ui.dp(context, 46), Ui.dp(context, 46)));
                h.handle = new IconView(context);
                h.handle.setIcon(Icons.HANDLE);
                h.handle.setColor(secondary);
                pad = Ui.dp(context, 11);
                h.handle.setPadding(pad, pad, pad, pad);
                h.handle.setContentDescription("Drag to reorder");
                h.handle.setOnTouchListener(handleTouch);
                row.addView(h.handle, new LinearLayout.LayoutParams(Ui.dp(context, 46), Ui.dp(context, 46)));
                row.setTag(h);
                convertView = row;
            } else {
                h = (QueueHolder) convertView.getTag();
            }
            MediaItem m = e.item;
            h.title.setText(m.title);
            h.title.setTextColor(e.role == ROLE_NOW ? accent : Ui.color(context, R.attr.gfTextPrimary));
            h.subtitle.setText(m.artist != null ? m.artist : "");
            ArtworkLoader.get(context).into(h.art, m, Ui.dp(context, 40));
            // Songs already played are faded, so the past reads differently from what's next.
            float alpha = e.role == ROLE_HISTORY ? 0.55f : 1f;
            h.art.setAlpha(alpha);
            h.texts.setAlpha(alpha);
            h.speaker.setVisibility(e.role == ROLE_NOW ? View.VISIBLE : View.GONE);
            h.handle.setVisibility(e.role == ROLE_NEXT ? View.VISIBLE : View.GONE);
            h.handle.setTag(e.index);
            boolean beingDragged = e.role == ROLE_NEXT && e.index == hidden;
            convertView.setVisibility(beingDragged ? View.INVISIBLE : View.VISIBLE);
            h.role = e.role;
            h.index = e.index;
            // Rows scrolled into view mid-drag sit where the drag has pushed them.
            int shift = e.role == ROLE_NEXT ? dragShift(e.index) : 0;
            convertView.animate().cancel();
            convertView.setTranslationY(shift);
            h.shiftTarget = shift;
            return convertView;
        }
    }
}
