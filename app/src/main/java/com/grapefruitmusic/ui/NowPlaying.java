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
import com.grapefruitmusic.radio.MyStations;
import com.grapefruitmusic.radio.Radio;
import com.grapefruitmusic.radio.RadioArt;
import com.grapefruitmusic.radio.StationText;
import com.grapefruitmusic.ui.widget.ArtworkView;
import com.grapefruitmusic.ui.widget.DragFrame;
import com.grapefruitmusic.ui.widget.IconView;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.ui.widget.NowPlayingCard;
import com.grapefruitmusic.ui.widget.Scrubber;
import com.grapefruitmusic.ui.widget.TuningDial;
import com.grapefruitmusic.util.ArtworkLoader;
import com.grapefruitmusic.util.Bg;
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
    /** An audiobook's chapters, in Up Next's place. */
    private static final int MODE_CHAPTERS = 3;
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
    /** Where the music is from, between the round buttons at the top (the album, the show). */
    private final TextView contextTitle;
    private final boolean darkTheme;
    /** A podcast episode: back and on are 15 seconds, not the previous and next episodes. */
    private boolean skipMode;
    private final TextView elapsed;
    private final TextView remaining;
    private final Scrubber scrubber;
    private final Scrubber volume;
    private final IconView play;
    private final IconView btnLyrics;
    private final IconView btnShuffle;
    private final IconView btnRepeat;
    private final IconView btnKaraoke;
    private final IconView btnLive;
    private final IconView btnQueue;
    /** A live station: + keeps it in My Stations (✓ when it's there; tap again to let it go). */
    private final IconView btnStation;
    /** Audiobooks: their chapters (a list where Up Next goes), and the one being heard. */
    private final com.grapefruitmusic.ui.widget.ActionIconView btnChapters;
    private final ListView chaptersList;
    private final ChaptersAdapter chaptersAdapter;
    private int chapterIndex = -2;
    /** Synced lyrics' timing: a pill in the corner of Lyrics, with a menu to nudge them. */
    private final TextView lyricsTiming;
    /** Playback speed: a menu of speeds from the button (music, podcasts and audiobooks each their own). */
    private final com.grapefruitmusic.ui.widget.ActionIconView btnSpeed;
    private final View condensed;
    private final ArtworkView smallArt;
    private final TextView smallTitle;
    private final TextView smallArtist;
    /** The lines that scroll when too long for their space. */
    private final TextView[] marquees;
    private final DragFrame listArea;
    private final IconView prev;
    private final IconView next;
    private final View times;
    /** In the song position's place while a live station plays. */
    private final TextView liveLabel;
    private final View tunerRow;
    private final TuningDial dial;
    private final View musicButtons;
    private final View radioButtons;
    private final Segmented output;
    private final TextView outputNote;
    private final IconView btnPreset;
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
    /** Laid out in two columns (a wide or square screen): see configureContentLayout. */
    private boolean twoColumns;
    /** About how wide Lyrics and Up Next are in two columns, in dp (0: the whole width). */
    private int lyricsColumnDp;
    private String trackKey;
    private Lyrics lyrics;
    private boolean lyricsLoading;
    private int lyricIndex = -2;
    private long lastUserScroll;
    private Boolean artPlaying;
    private String queueAnchorKey;
    /** Showing the radio (not the music queue). */
    private boolean radioMode;
    /** A live internet station is playing (no position; stop, not pause). */
    private boolean liveMode;
    /** ArtworkLoader.generation() when the artwork was loaded. */
    private int artGeneration;
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
        // Shuffle and repeat go either side of the transport, as small round buttons.
        btnShuffle = Ui.circleButton(a, Icons.SHUFFLE, 42, "Shuffle");
        btnRepeat = Ui.circleButton(a, Icons.REPEAT, 42, "Repeat");
        LinearLayout transport = (LinearLayout) root.findViewById(R.id.np_transport);
        transport.addView(btnShuffle, 0);
        transport.addView(btnRepeat);
        btnKaraoke = (IconView) root.findViewById(R.id.np_btn_karaoke);
        btnLive = (IconView) root.findViewById(R.id.np_btn_live);
        btnQueue = (IconView) root.findViewById(R.id.np_btn_queue);
        btnStation = new com.grapefruitmusic.ui.widget.ActionIconView(a);
        for (IconView button : new IconView[]{btnLyrics, btnQueue, btnKaraoke, btnLive, btnStation}) {
            ((com.grapefruitmusic.ui.widget.ActionIconView)button).setLabelVisible(true);
        }

        btnStation.setVisibility(View.GONE);
        ((LinearLayout) root.findViewById(R.id.np_buttons)).addView(btnStation,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        btnChapters = new com.grapefruitmusic.ui.widget.ActionIconView(a);
        btnChapters.setIcon(Icons.CHAPTERS);
        btnChapters.setContentDescription("Chapters");
        btnChapters.setLabelVisible(true);
        btnChapters.setVisibility(View.GONE);
        btnChapters.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(mode == MODE_CHAPTERS ? MODE_ART : MODE_CHAPTERS);
            }
        });
        ((LinearLayout) root.findViewById(R.id.np_buttons)).addView(btnChapters,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        btnSpeed = new com.grapefruitmusic.ui.widget.ActionIconView(a);
        btnSpeed.setIcon(Icons.SPEED);
        btnSpeed.setLabelVisible(true);
        btnSpeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSpeeds();
            }
        });
        ((LinearLayout) root.findViewById(R.id.np_buttons)).addView(btnSpeed,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        condensed = root.findViewById(R.id.np_condensed);
        smallArt = (ArtworkView) root.findViewById(R.id.np_small_art);
        smallTitle = (TextView) root.findViewById(R.id.np_small_title);
        smallArtist = (TextView) root.findViewById(R.id.np_small_artist);
        // Text too long for its line scrolls by (marquee, in the layout) rather than end in "…":
        // three times for each song, and only while Now Playing is showing (see scrollTitles).
        marquees = new TextView[] {title, artist, smallTitle, smallArtist,
                (TextView) root.findViewById(R.id.np_album)};
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
        chaptersList = new ListView(a);
        RowAdapter.style(chaptersList);
        chaptersList.setVerticalFadingEdgeEnabled(true);
        chaptersList.setFadingEdgeLength(Ui.dp(a, 32));
        chaptersList.setVisibility(View.GONE);
        chaptersAdapter = new ChaptersAdapter(a);
        chaptersList.setAdapter(chaptersAdapter);
        chaptersList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                goToChapter(position);
            }
        });
        listArea.addView(chaptersList, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        lyricsTiming = Ui.text(a, 13, R.attr.gfTextPrimary, false);
        lyricsTiming.setTypeface(Fonts.medium());
        lyricsTiming.setBackground(Ui.pressable(a, Ui.color(a, R.attr.gfControl), Ui.PILL, true));
        lyricsTiming.setPadding(Ui.dp(a, 14), Ui.dp(a, 7), Ui.dp(a, 14), Ui.dp(a, 7));
        lyricsTiming.setVisibility(View.GONE);
        lyricsTiming.setOnClickListener(v -> showLyricsTiming());
        FrameLayout.LayoutParams timingLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
        timingLp.setMargins(0, 0, Ui.dp(a, 14), Ui.dp(a, 12));
        listArea.addView(lyricsTiming, timingLp);
        times = root.findViewById(R.id.np_times);
        tunerRow = root.findViewById(R.id.np_tuner);
        dial = (TuningDial) root.findViewById(R.id.np_dial);
        musicButtons = root.findViewById(R.id.np_buttons);
        radioButtons = root.findViewById(R.id.np_radio_buttons);
        btnPreset = (IconView) root.findViewById(R.id.np_btn_preset);
        output = new Segmented(a, new String[]{"Headphones", "Speaker"}, 0);
        FrameLayout outputArea = (FrameLayout) root.findViewById(R.id.np_output);
        outputArea.addView(output);
        // Where the radio can't be sent to the speaker, a note in the switch's place.
        outputNote = Ui.text(a, 14, R.attr.gfTextSecondary, false);
        outputNote.setSingleLine(false);
        outputNote.setText("On this tablet the radio plays through the headphones.");
        outputNote.setGravity(Gravity.CENTER_VERTICAL);
        outputArea.addView(outputNote, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        buildRadioControls(root);
        IconView volLow = (IconView) root.findViewById(R.id.np_vol_low);
        IconView volHigh = (IconView) root.findViewById(R.id.np_vol_high);

        int background = Ui.color(a, R.attr.gfBackground);
        darkTheme = ((background >> 16 & 0xff) * 299 + (background >> 8 & 0xff) * 587 + (background & 0xff) * 114) / 1000 < 128;
        card.setColors(background, Ui.color(a, R.attr.gfScrim));
        contextTitle = (TextView) root.findViewById(R.id.np_context);
        contextTitle.setTypeface(Fonts.semiBold());
        art.setPlaceholderColors(Ui.color(a, R.attr.gfFill), secondary);
        art.setCornerRadiusDp(18);
        art.setShadowDp(18);
        smallArt.setCornerRadiusDp(12);
        smallArt.setPlaceholderColors(Ui.color(a, R.attr.gfFill), secondary);
        root.findViewById(R.id.np_mini_header).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(MODE_ART);
            }
        });
        // A thicker track filled in the accent, and a white thumb ringed with it.
        scrubber.setColors((primary & 0x00ffffff) | 0x33000000, accent, 0xffffffff);
        scrubber.setTrackDp(4);
        scrubber.setThumb(7, 11, true);
        scrubber.setThumbRing(accent, 2.5f);
        liveLabel = Ui.text(a, 12, R.attr.gfAccent, true);
        liveLabel.setText("\u25cf  LIVE");
        liveLabel.setGravity(Gravity.CENTER);
        liveLabel.setVisibility(View.GONE);
        ViewGroup scrubberRow = (ViewGroup) scrubber.getParent();
        scrubberRow.addView(liveLabel, scrubberRow.indexOfChild(scrubber) + 1,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 30)));
        volume.setColors((primary & 0x00ffffff) | 0x33000000, secondary, Ui.color(a, R.attr.gfKnob));
        volume.setTrackDp(4);
        volume.setThumb(8, 10, true);
        prev.setIcon(Icons.PREVIOUS);
        next.setIcon(Icons.NEXT);
        Ui.styleCircle(prev, false);
        Ui.styleCircle(next, false);
        IconView closeIcon = (IconView)root.findViewById(R.id.np_close);
        closeIcon.setIcon(Icons.CHEVRON_DOWN);
        Ui.styleCircle(closeIcon, false);
        closeIcon.setContentDescription("Close Now Playing");
        closeIcon.setOnClickListener(v -> close());
        IconView moreIcon = (IconView)root.findViewById(R.id.np_more);
        moreIcon.setIcon(Icons.MORE);
        Ui.styleCircle(moreIcon, false);
        moreIcon.setContentDescription("More actions");
        moreIcon.setOnClickListener(v -> {
            if (radio.inSession()) { PresetLabel.add(activity, radio.frequency()); return; }
            MediaItem current = player.current();
            android.app.Fragment fragment = activity.getFragmentManager().findFragmentById(R.id.content);
            if (current != null && fragment instanceof BaseFragment) {
                com.grapefruitmusic.source.MusicSource source = com.grapefruitmusic.source.Sources.get(activity).byId(current.sourceId);
                if (com.grapefruitmusic.podcast.PodcastSource.ID.equals(current.sourceId))
                    PodcastsFragment.episodeActions((BaseFragment)fragment, current, null);
                else if (source != null) TrackActions.show((BaseFragment)fragment, current, source);
            }
        });
        // The play button: the accent gradient, in a soft glow of it.
        android.graphics.drawable.LayerDrawable glow = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{
                        Ui.shape(a, (accent & 0x00ffffff) | 0x30000000, 0, false, true),
                        Ui.accentPressable(a, 0, true)});
        int glowInset = Ui.dp(a, 5);
        glow.setLayerInset(1, glowInset, glowInset, glowInset, glowInset);
        play.setBackground(glow);
        play.setColor(Ui.onAccent(a));
        volLow.setIcon(Icons.SPEAKER_LOW);
        volHigh.setIcon(Icons.SPEAKER_HIGH);
        volLow.setColor(secondary);
        volHigh.setColor(secondary);
        btnLyrics.setIcon(Icons.LYRICS);
        btnShuffle.setIcon(Icons.SHUFFLE);
        btnRepeat.setIcon(Icons.REPEAT);
        btnKaraoke.setIcon(Icons.MIC);
        btnLive.setIcon(Icons.CHEERING);
        btnLive.setContentDescription("Live Mode");
        btnLive.setOnClickListener(v -> {
            boolean on = !Prefs.liveMode();
            Prefs.setLiveMode(on);
            player.audioEffectsChanged();
            showKaraoke();
            if (on) showEffectStrength(true);
            Notice.show(activity, on ? "Live Mode on" : "Live Mode off");
        });
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
                if (on) showEffectStrength(false);
                Notice.show(activity, on ? "Karaoke Mode on" : "Karaoke Mode off");
            }
        });
        btnLive.setOnLongClickListener(v -> { showEffectStrength(true); return true; });
        btnKaraoke.setOnLongClickListener(v -> { showEffectStrength(false); return true; });
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
                else if (skipMode) player.skipBy(-15000);
                else player.previous();
            }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (radioMode) radio.seek(true);
                else if (skipMode) player.skipBy(15000);
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
        btnStation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaItem station = player.current();
                if (station == null || !station.live) return;
                MyStations mine = MyStations.get(activity);
                if (mine.contains(station)) {
                    mine.remove(station);
                    Notice.show(activity, "Removed " + station.title + " from My Stations");
                } else {
                    mine.add(station);
                    Notice.show(activity, "Added " + station.title + " to My Stations");
                }
                showStationButton(station);
            }
        });

        scrubber.setListener(new Scrubber.Listener() {
            @Override
            public void onScrubStart() {
            }

            @Override
            public void onScrub(long value) {
                showTimes(value, scrubber.getMax());
            }

            @Override
            public void onScrubCancel() {
                tick();
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
                setVolume((int) value);
            }

            @Override
            public void onScrubEnd(long value) {
                setVolume((int) value);
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
        lyricsAdapter.setColumnWidth(lyricsColumnDp);
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
                    player.seekTo(Math.max(0, lyrics.lines.get(position).timeMs + lyrics.offsetMs));
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
                Radio.Preset saved = radio.preset(f);
                if (saved != null) {
                    radio.removePreset(f);
                    Notice.show(activity, "Removed " + saved.label() + " from Presets");
                } else {
                    PresetLabel.add(activity, f);
                }
            }
        });
    }

    /** A full-width control remains available on cramped displays or from Lyrics/Up Next. */
    private void showEffectStrength(final boolean liveEffect) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Ui.dp(activity, 16);
        content.setPadding(padding, padding, padding, padding);
        final TextView valueLabel = Ui.text(activity, 16, R.attr.gfTextPrimary, true);
        final Scrubber strength = new Scrubber(activity);
        strength.setMax(100);
        long initial = Math.round((liveEffect ? Prefs.liveAmount() : Prefs.karaokeAmount()) * 100);
        strength.setValue(initial);
        valueLabel.setText(initial + "%");
        strength.setContentDescription(liveEffect ? "Live Mode strength" : "Vocal removal");
        strength.setColors((secondary & 0x00ffffff) | 0x40000000, accent, accent);
        strength.setListener(new Scrubber.Listener() {
            @Override public void onScrubStart() { }
            @Override public void onScrub(long value) { valueLabel.setText(value + "%"); }
            @Override public void onScrubEnd(long value) {
                if (liveEffect) Prefs.setLiveAmount(value / 100f);
                else Prefs.setKaraokeAmount(value / 100f);
                player.audioEffectsChanged();
            }
        });
        content.addView(valueLabel);
        content.addView(strength, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 56)));
        Ui.dialog(activity).setTitle(liveEffect ? "Live Mode strength" : "Karaoke strength")
                .setView(content).setPositiveButton("Done", null).show();
    }

    /** The volume the slider (and the volume keys) control: the radio may have its own. */
    int volumeStream() {
        return radioMode ? radio.volumeStream() : AudioManager.STREAM_MUSIC;
    }

    /** The radio's own volume (the chip's, on some tablets), or else the stream's. */
    private void setVolume(int value) {
        if (radioMode && radio.ownVolumeMax() > 0) radio.setOwnVolume(value);
        else audio.setStreamVolume(volumeStream(), value, 0);
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
        side = false;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.setMargins(0, 0, 0, 0);
        card.setLayoutParams(lp);
        configureContentLayout(config);
        // Lyrics or Up Next open while the screen turns: laid out again for the new columns.
        if (mode != MODE_ART) showMode();
        card.setSide(side);
        // The grab bar goes across the top of the card, or down the left edge of the panel.
        grabber.setVisibility(side ? View.GONE : View.VISIBLE);
        sideGrabber.setVisibility(side ? View.VISIBLE : View.GONE);
        content.setPadding(side ? Ui.dp(activity, 22) : 0, side ? Ui.dp(activity, 12) : 0, 0, 0);
    }

    /** Full-width shared player; only the inner arrangement changes with available room. */
    private void configureContentLayout(Configuration config) {
        // Two columns on a wide or square screen (a phone on its side, a tablet, a Unihertz Titan):
        // the artwork at the left; the title, the controls and the buttons at the right.
        boolean landscape = Ui.wide(config);
        twoColumns = landscape;
        lyricsColumnDp = landscape ? Math.round(config.screenWidthDp * 0.44f) : 0;
        if (lyricsAdapter != null) lyricsAdapter.setColumnWidth(lyricsColumnDp);
        if (queueAdapter != null) queueAdapter.notifyDataSetChanged();
        boolean compact = config.screenHeightDp < 500;
        LinearLayout content = (LinearLayout) root.findViewById(R.id.np_content);
        LinearLayout body = (LinearLayout)root.findViewById(R.id.np_main);
        LinearLayout controls = (LinearLayout)root.findViewById(R.id.np_controls);
        View stage = root.findViewById(R.id.np_stage);
        FrameLayout artwork = (FrameLayout)root.findViewById(R.id.np_art_group);
        View metadata = root.findViewById(R.id.np_metadata);
        View buttons = root.findViewById(R.id.np_buttons);
        View radioRow = root.findViewById(R.id.np_radio_buttons);
        // The progress track starts 24dp in: its 14dp margin plus 10dp thumb inset.
        // PlayerMetadata already supplies a 12dp outer gap; landscape labels live in controls.
        int metadataPadding = Ui.dp(activity, landscape ? 24 : 12);
        metadata.setPadding(metadataPadding, metadata.getPaddingTop(),
                metadataPadding, metadata.getPaddingBottom());
        ViewGroup destination = landscape ? controls : artwork;
        if (metadata.getParent() != destination) {
            ((ViewGroup)metadata.getParent()).removeView(metadata);
            if (landscape) controls.addView(metadata, 0, new LinearLayout.LayoutParams(-1, -2));
            else artwork.addView(metadata, new FrameLayout.LayoutParams(-1, -2));
        }
        // The buttons (Lyrics, Up Next...) and the radio's row: under the controls in two columns,
        // across the bottom otherwise.
        ViewGroup buttonsHome = landscape ? controls : content;
        for (View v : new View[]{radioRow, buttons}) {
            if (v.getParent() == buttonsHome) continue;
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            ((ViewGroup) v.getParent()).removeView(v);
            buttonsHome.addView(v, lp);
        }
        body.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        // The artwork takes a little less than half; the controls the rest.
        stage.setLayoutParams(landscape ? new LinearLayout.LayoutParams(0, -1, 0.44f)
                : new LinearLayout.LayoutParams(-1, 0, 1));
        controls.setLayoutParams(landscape ? new LinearLayout.LayoutParams(0, -1, 0.56f)
                : new LinearLayout.LayoutParams(-1, -2));
        int padding = Ui.dp(activity, landscape ? 8 : 0);
        controls.setPadding(padding, 0, padding, Ui.dp(activity, landscape ? 8 : 0));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 20 : 24);
        // Shuffle, back, play, on, repeat: the biggest set that fits across the row (the whole
        // screen, or the controls' column), down to a square phone's.
        int across = landscape ? Math.round(config.screenWidthDp * 0.56f) - 2 * 8 - 16 : config.screenWidthDp - 16;
        int[][] sets = {{42, 54, 80, 14}, {38, 48, 68, 10}, {34, 42, 58, 8}, {30, 38, 50, 5}, {28, 34, 46, 4}};
        int[] set = sets[sets.length - 1];
        for (int[] s : sets) {
            if (2 * s[0] + 2 * s[1] + s[2] + 4 * s[3] <= across && (!compact || s[2] <= 62)) {
                set = s;
                break;
            }
        }
        int small = set[0], middle = set[1], big = set[2], gap = set[3];
        resizeHeight(root.findViewById(R.id.np_transport), big + 8);
        resizeHeight(root.findViewById(R.id.np_volume_row), compact ? 28 : 36);
        resizeHeight(buttons, compact ? 54 : 60);
        resizeHeight(root.findViewById(R.id.np_grabber), compact ? 48 : 60);
        resizeHeight(scrubber, compact ? 26 : 32);
        IconView[] row = {btnShuffle, prev, play, next, btnRepeat};
        for (int i = 0; i < row.length; i++) {
            IconView button = row[i];
            int size = button == play ? big : button == prev || button == next ? middle : small;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) button.getLayoutParams();
            if (lp == null) lp = new LinearLayout.LayoutParams(0, 0);
            lp.width = lp.height = Ui.dp(activity, size);
            lp.leftMargin = i == 0 ? 0 : Ui.dp(activity, gap);
            lp.rightMargin = 0;
            lp.gravity = Gravity.CENTER_VERTICAL;
            button.setLayoutParams(lp);
            int pad = Math.round(Ui.dp(activity, size) * (button == play ? 0.33f : 0.27f));
            button.setPadding(pad, pad, pad, pad);
        }
    }

    private void resizeHeight(View view, int dp) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        lp.height = Ui.dp(activity, dp);
        view.setLayoutParams(lp);
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

    /**
     * Long titles scroll only where they can be seen: here while Now Playing is open, in the mini
     * player while it's closed. A scrolling line redraws (with everything over and under it) every
     * frame, which on an older tablet takes time from Karaoke Mode and Live Mode.
     */
    private void scrollTitles(boolean here) {
        for (TextView t : marquees) t.setSelected(here);
        if (activity instanceof MainActivity) ((MainActivity) activity).setMiniTitleScrolls(!here);
    }

    void open() {
        if (open || (player.current() == null && !radio.inSession())) return;
        open = true;
        scrollTitles(true);
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
        scrollTitles(true);
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
        scrollTitles(false);
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

    /** Back and on: 15 seconds in a podcast episode, else the previous and next songs. */
    private void setSkipMode(boolean on) {
        skipMode = on;
        prev.setIcon(on ? Icons.BACK_15 : Icons.PREVIOUS);
        next.setIcon(on ? Icons.FORWARD_15 : Icons.NEXT);
        if (!radioMode) {
            prev.setContentDescription(on ? "Back 15 seconds" : liveMode ? "Previous station" : "Previous");
            next.setContentDescription(on ? "Forward 15 seconds" : liveMode ? "Next station" : "Next");
        }
    }

    /**
     * Only the buttons that do something for what's playing: a podcast has no lyrics, and Karaoke
     * Mode and Live Mode are for music. The rest share the row.
     */
    /** The Timing pill: "Timing" when the lyrics are as timed, else how far they're nudged. */
    private void showTimingLabel() {
        long off = lyrics != null ? lyrics.offsetMs : 0;
        String amount = String.valueOf(Math.abs(off) / 1000f).replaceAll("\\.0$", "");
        lyricsTiming.setText(off == 0 ? "Timing" : (off > 0 ? "+" : "\u2212") + amount + " s");
        lyricsTiming.setContentDescription(off == 0 ? "Lyrics timing, as timed"
                : "Lyrics timing, " + amount + " seconds " + (off > 0 ? "later" : "earlier"));
    }

    /** Earlier, later or as timed: half a second at a time, kept for this song. */
    private void showLyricsTiming() {
        if (lyrics == null) return;
        TabMenu.Builder b = new TabMenu.Builder();
        b.add("Earlier by 0.5 s", TabMenu.NO_ICON, false, () -> nudgeLyrics(lyrics.offsetMs - 500));
        b.add("Later by 0.5 s", TabMenu.NO_ICON, false, () -> nudgeLyrics(lyrics.offsetMs + 500));
        b.rule();
        b.add("As Timed", TabMenu.NO_ICON, lyrics.offsetMs == 0, () -> nudgeLyrics(0));
        TabMenu.show(activity, lyricsTiming, false, b.items);
    }

    private void nudgeLyrics(long offset) {
        final MediaItem item = player.current();
        if (lyrics == null || item == null) return;
        final long off = Math.max(-30000, Math.min(30000, offset));
        lyrics.offsetMs = off;
        final Context app = activity.getApplicationContext();
        Bg.execute(() -> LyricsRepository.setOffset(app, item, off));
        lyricIndex = -2;
        showTimingLabel();
        tick();
    }

    /** 1.5 as "1.5×", 2 as "2×". */
    static String speedLabel(float speed) {
        String n = speed == Math.round(speed) ? String.valueOf(Math.round(speed))
                : String.valueOf(Math.round(speed * 100) / 100f).replaceAll("0+$", "");
        return n + "\u00d7";
    }

    /** The Speed button: "Speed" at 1×, the speed (in the accent) otherwise. */
    private void showSpeed() {
        float s = player.speed();
        btnSpeed.setContentDescription(s == 1f ? "Speed" : "Speed, " + speedLabel(s));
        btnSpeed.setColor(s == 1f ? secondary : accent);
        btnSpeed.invalidate();
    }

    /** The speeds, rising from the button, the one playing ticked. */
    private void showSpeeds() {
        TabMenu.Builder b = new TabMenu.Builder();
        float now = player.speed();
        for (final float s : Player.SPEEDS) {
            b.add(speedLabel(s), TabMenu.NO_ICON, Math.abs(s - now) < 0.01f, new Runnable() {
                @Override
                public void run() {
                    player.setSpeed(s);
                    showSpeed();
                    tick();
                }
            });
        }
        TabMenu.show(activity, btnSpeed, false, b.items);
    }

    // ---- Audiobooks ----

    private com.grapefruitmusic.audiobooks.Audiobook book(MediaItem item) {
        return Player.isAudiobook(item)
                ? com.grapefruitmusic.audiobooks.AudiobookStore.get(activity).book(item.albumId) : null;
    }

    /** The chapter list, the one being heard marked (and, when opened, scrolled to). */
    private void showChapters(boolean scroll) {
        MediaItem item = player.current();
        com.grapefruitmusic.audiobooks.Audiobook b = book(item);
        if (b == null) return;
        int at = b.chapterAt(item.trackNumber - 1, player.positionMs());
        chaptersAdapter.set(b, at);
        if (scroll && at >= 0) chaptersList.setSelectionFromTop(at, Ui.dp(activity, 48));
    }

    private void goToChapter(int index) {
        MediaItem item = player.current();
        com.grapefruitmusic.audiobooks.Audiobook b = book(item);
        if (b == null || index < 0 || index >= b.chapters.size()) return;
        com.grapefruitmusic.audiobooks.Audiobook.Chapter c = b.chapters.get(index);
        if (c.part == item.trackNumber - 1) player.seekTo(c.startMs);
        else player.jumpTo(c.part, c.startMs);
        chapterIndex = -2;
        tick();
    }

    /** An audiobook's lines: the chapter as the title, "author · Chapter 3 of 12" under it. */
    private void showAudiobookTitles(MediaItem item, int chapter) {
        com.grapefruitmusic.audiobooks.Audiobook b = book(item);
        if (b == null) return;
        boolean chapters = b.chapters.size() > 1;
        String heading = chapters && chapter >= 0 ? b.chapters.get(chapter).title : b.title;
        String by = b.author != null ? b.author : "";
        if (chapters && chapter >= 0) {
            by = (by.length() == 0 ? "" : by + "  \u00b7  ") + "Chapter " + (chapter + 1) + " of " + b.chapters.size();
        }
        setText(title, heading);
        setText(artist, by);
        setText(smallTitle, heading);
        setText(smallArtist, b.title + (b.author != null ? " \u2014 " + b.author : ""));
    }

    /** One row a chapter: its title and where it starts; the one playing in the accent. */
    private final class ChaptersAdapter extends BaseAdapter {
        private final Context context;
        private com.grapefruitmusic.audiobooks.Audiobook book;
        private int current = -1;

        ChaptersAdapter(Context context) {
            this.context = context;
        }

        void set(com.grapefruitmusic.audiobooks.Audiobook b, int at) {
            book = b;
            current = at;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return book == null ? 0 : book.chapters.size();
        }

        @Override
        public Object getItem(int position) {
            return book.chapters.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = (LinearLayout) convertView;
            if (row == null) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Ui.dp(context, 52));
                row.setPadding(Ui.dp(context, 20), Ui.dp(context, 6), Ui.dp(context, 20), Ui.dp(context, 6));
                row.setBackground(Ui.rowBackground(context));
                TextView name = Ui.text(context, 16, R.attr.gfTextPrimary, false);
                name.setSingleLine(false);
                name.setMaxLines(2);
                row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                TextView at = Ui.text(context, 13, R.attr.gfTextSecondary, false);
                at.setPadding(Ui.dp(context, 12), 0, 0, 0);
                row.addView(at);
            }
            com.grapefruitmusic.audiobooks.Audiobook.Chapter c = book.chapters.get(position);
            TextView name = (TextView) row.getChildAt(0);
            TextView at = (TextView) row.getChildAt(1);
            name.setText(c.title);
            boolean now = position == current;
            name.setTextColor(now ? accent : primary);
            name.setTypeface(now ? Fonts.semiBold() : Fonts.regular());
            // Where it starts in the book.
            long start = c.startMs;
            for (int i = 0; i < c.part && i < book.parts.size(); i++) start += Math.max(0, book.parts.get(i).durationMs);
            at.setText(Format.duration(start));
            row.setContentDescription(c.title + ", at " + Format.duration(start) + (now ? ", playing" : ""));
            return row;
        }
    }

    private void showButtonsFor(MediaItem item) {
        btnSpeed.setVisibility(Player.speedKind(item) >= 0 ? View.VISIBLE : View.GONE);
        showSpeed();
        boolean audiobook = Player.isAudiobook(item);
        com.grapefruitmusic.audiobooks.Audiobook heardBook = book(item);
        btnChapters.setVisibility(heardBook != null && heardBook.chapters.size() > 1 ? View.VISIBLE : View.GONE);
        btnQueue.setVisibility(audiobook ? View.GONE : View.VISIBLE);
        if (audiobook && (mode == MODE_LYRICS || mode == MODE_QUEUE)) setMode(MODE_ART);
        if (btnChapters.getVisibility() != View.VISIBLE && mode == MODE_CHAPTERS) setMode(MODE_ART);
        // A book's parts play once, in order: no shuffle or repeat.
        if (!radioMode && !liveMode) {
            btnShuffle.setVisibility(audiobook ? View.GONE : View.VISIBLE);
            btnRepeat.setVisibility(audiobook ? View.GONE : View.VISIBLE);
        }
        boolean podcast = Player.isPodcast(item) || audiobook;
        btnLyrics.setVisibility(item.live || podcast ? View.GONE : View.VISIBLE);
        btnKaraoke.setVisibility(podcast ? View.GONE : View.VISIBLE);
        btnLive.setVisibility(podcast ? View.GONE : View.VISIBLE);
        if (podcast && mode == MODE_LYRICS) setMode(MODE_ART);
        LinearLayout row = (LinearLayout) musicButtons;
        int shown = 0;
        for (int i = 0; i < row.getChildCount(); i++) if (row.getChildAt(i).getVisibility() == View.VISIBLE) shown++;
        // Three or fewer: a set width each, centred, rather than stretched across the row.
        for (int i = 0; i < row.getChildCount(); i++) {
            View b = row.getChildAt(i);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.getLayoutParams();
            int width = shown <= 3 ? Ui.dp(activity, 96) : 0;
            float weight = shown <= 3 ? 0 : 1;
            if (lp.width != width || lp.weight != weight) {
                lp.width = width;
                lp.weight = weight;
                b.setLayoutParams(lp);
            }
        }
    }

    private void showKaraoke() {
        btnKaraoke.setColor(Prefs.karaoke() ? accent : secondary);
        btnLive.setColor(Prefs.liveMode() ? accent : secondary);
        btnLive.setSelected(Prefs.liveMode());
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
        trackKey = null;
        radioArtKey = null;
        // Live mode is worked out afresh for the next song or station.
        liveMode = false;
        liveLabel.setVisibility(View.GONE);
        btnLyrics.setVisibility(View.VISIBLE);
        // Shuffle and repeat are by the transport now, which the radio shares: not for the radio.
        btnShuffle.setVisibility(on ? View.GONE : View.VISIBLE);
        btnRepeat.setVisibility(on ? View.GONE : View.VISIBLE);
        btnStation.setVisibility(View.GONE);
        if (on) setSkipMode(false);
    }

    /** + or ✓: whether the station playing is in My Stations. */
    private void showStationButton(MediaItem station) {
        boolean kept = MyStations.get(activity).contains(station);
        btnStation.setIcon(kept ? Icons.CHECK : Icons.PLUS);
        btnStation.setColor(kept ? accent : secondary);
        btnStation.setContentDescription(kept ? "Remove from My Stations" : "Add to My Stations");
    }

    /** Switches the controls between songs and a live internet station. */
    private void setLiveMode(boolean on) {
        liveMode = on;
        if (on && mode == MODE_LYRICS) setMode(MODE_ART);
        scrubber.setVisibility(on ? View.GONE : View.VISIBLE);
        times.setVisibility(on ? View.GONE : View.VISIBLE);
        liveLabel.setVisibility(on ? View.VISIBLE : View.GONE);
        // No lyrics, shuffle or repeat for a station.
        btnLyrics.setVisibility(on ? View.GONE : View.VISIBLE);
        btnShuffle.setVisibility(on ? View.GONE : View.VISIBLE);
        btnRepeat.setVisibility(on ? View.GONE : View.VISIBLE);
        btnStation.setVisibility(on ? View.VISIBLE : View.GONE);
        card.setDragLimit(on ? liveLabel : scrubber);
        prev.setContentDescription(on ? "Previous station" : "Previous");
        next.setContentDescription(on ? "Next station" : "Next");
        play.setContentDescription(on ? "Play or stop the station" : "Play or pause");
    }

    private void updateRadio() {
        int freq = radio.frequency();
        setText(contextTitle, "FM Radio");
        setText(title, radio.title());
        setText((TextView) root.findViewById(R.id.np_album), "");
        int state = radio.state();
        setText(artist, state == Radio.STARTING ? "Tuning in…" : state == Radio.SEEKING ? "Looking for a station…"
                : radio.subtitle());
        boolean playing = radio.wantsToPlay();
        play.setIcon(playing ? Icons.STOP : Icons.PLAY);
        dial.setFrequency(freq);
        boolean switchable = radio.canUseSpeaker();
        output.setVisibility(switchable ? View.VISIBLE : View.GONE);
        outputNote.setVisibility(switchable ? View.GONE : View.VISIBLE);
        output.select(radio.speaker() ? 1 : 0);
        boolean saved = radio.preset(freq) != null;
        btnPreset.setIcon(saved ? Icons.CHECK : Icons.PLUS);
        btnPreset.setColor(saved ? accent : primary);
        btnPreset.setContentDescription(saved ? "Remove from Presets" : "Add to Presets");
        scaleArt(playing);
        String key = freq + "|" + radio.name();
        // Not while the dial turns: a new picture (and its blur) for every step would be too slow.
        if (!key.equals(radioArtKey) && !dial.isTurning()) {
            radioArtKey = key;
            Bitmap b = RadioArt.of(freq, radio.name(), Math.max(Ui.dp(activity, 300), 512));
            art.setBitmap(b);
            smallArt.setBitmap(b);
            tintFrom(b);
        }
        tick();
    }

    /** Leaves the text alone when it's the same (so the radio text keeps scrolling). */
    private static void setText(TextView t, String text) {
        if (text == null) text = "";
        if (!text.equals(t.getText().toString())) t.setText(text);
    }

    /** The artwork keeps its size, playing or paused. */
    private void scaleArt(boolean playing) {
        artPlaying = playing;
    }

    /** The background's gradient and the artwork's glow, in the artwork's colour. */
    private void tintFrom(Bitmap b) {
        int colour = NowPlayingCard.colourOf(b);
        card.setTint(colour);
        art.setShadowColor(colour == 0 ? 0x55000000
                : (colour & 0x00ffffff) | (darkTheme ? 0x99000000 : 0x66000000));
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
        if (item.live != liveMode) setLiveMode(item.live);
        String heading;
        String line;
        if (item.live) {
            String[] lines = StationText.nowPlaying(item.title, item.artist, player.liveTitle());
            heading = lines[0];
            line = player.isBuffering() ? "Tuning in\u2026" : lines[1];
        } else {
            heading = item.title;
            line = item.artist != null ? item.artist : "";
            if (item.album != null) line = line.length() == 0 ? item.album : line + " — " + item.album;
        }
        // Unchanged text is left alone, so a long station line keeps scrolling.
        setText(title, heading);
        String from = item.live ? "Internet Radio" : item.album;
        setText(contextTitle, from == null || from.length() == 0 ? "Now Playing" : from);
        String by = item.artist != null ? item.artist : "";
        int number = player.queueNumber(), length = player.queueLength();
        if (!item.live && length > 1 && number > 0) {
            by = (by.length() == 0 ? "" : by + "  \u00b7  ") + number + "/" + length;
        }
        setText(artist, item.live ? line : by);
        setText((TextView) root.findViewById(R.id.np_album), item.live ? "" : item.album);
        setText(smallTitle, heading);
        setText(smallArtist, line);
        boolean playing = player.wantsToPlay();
        play.setIcon(playing ? (item.live ? Icons.STOP : Icons.PAUSE) : Icons.PLAY);
        if (item.live) showStationButton(item);
        setSkipMode((Player.isPodcast(item) || Player.isAudiobook(item)) && !item.live);
        if (Player.isAudiobook(item)) {
            chapterIndex = -2;
            showAudiobookTitles(item, -1);
        }
        showButtonsFor(item);
        btnShuffle.setColor(player.isShuffle() ? accent : secondary);
        btnRepeat.setIcon(player.repeatMode() == Player.REPEAT_ONE ? Icons.REPEAT_ONE : Icons.REPEAT);
        btnRepeat.setColor(player.repeatMode() == Player.REPEAT_OFF ? secondary : accent);
        showKaraoke();
        scaleArt(playing);

        if (!item.key().equals(trackKey)) {
            trackKey = item.key();
            artGeneration = ArtworkLoader.generation();
            loadArtwork(item);
            loadLyrics(item);
        } else if (artGeneration != ArtworkLoader.generation()) {
            // Its picture was changed (Edit Tags).
            artGeneration = ArtworkLoader.generation();
            loadArtwork(item);
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
        tintFrom(cached);
        if (cached != null) return;
        ArtworkLoader.get(activity).load(item, size, new ArtworkLoader.Callback() {
            @Override
            public void onArtwork(Bitmap bitmap) {
                if (!key.equals(trackKey)) return;
                art.setBitmap(bitmap);
                smallArt.setBitmap(bitmap);
                tintFrom(bitmap);
            }
        });
    }

    private void loadLyrics(final MediaItem item) {
        final String key = item.key();
        if (item.live) {
            lyrics = null;
            lyricsLoading = false;
            lyricsAdapter.setLyrics(null);
            showMode();
            return;
        }
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
        if (m == MODE_CHAPTERS) showChapters(true);
        showMode();
        tick();
    }

    private void showMode() {
        // Lyrics and Up Next: small artwork and title on top, the list below (iOS-style). In two
        // columns the list takes the whole left side, where the artwork was, and the title and
        // artist stay at the right, above the progress bar.
        boolean condensedMode = mode != MODE_ART;
        boolean keepTitle = condensedMode && twoColumns;
        root.findViewById(R.id.np_art_row).setVisibility(condensedMode ? View.INVISIBLE : View.VISIBLE);
        root.findViewById(R.id.np_metadata).setVisibility(condensedMode && !keepTitle ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.np_mini_header).setVisibility(twoColumns ? View.GONE : View.VISIBLE);
        art.setVisibility(condensedMode ? View.INVISIBLE : View.VISIBLE);
        if (condensedMode && condensed.getVisibility() != View.VISIBLE) {
            condensed.setAlpha(0f);
            condensed.animate().alpha(1f).setDuration(180).setListener(null).start();
        }
        condensed.setVisibility(condensedMode ? View.VISIBLE : View.GONE);
        title.setVisibility(condensedMode && !keepTitle ? View.GONE : View.VISIBLE);
        artist.setVisibility(condensedMode && !keepTitle ? View.GONE : View.VISIBLE);
        boolean showLyrics = mode == MODE_LYRICS && lyrics != null;
        lyricsList.setVisibility(showLyrics ? View.VISIBLE : View.GONE);
        lyricsTiming.setVisibility(showLyrics && lyrics.synced ? View.VISIBLE : View.GONE);
        showTimingLabel();
        queueList.setVisibility(mode == MODE_QUEUE ? View.VISIBLE : View.GONE);
        chaptersList.setVisibility(mode == MODE_CHAPTERS ? View.VISIBLE : View.GONE);
        btnChapters.setColor(mode == MODE_CHAPTERS ? accent : secondary);
        if (mode == MODE_LYRICS && lyrics == null) {
            message.setVisibility(View.VISIBLE);
            message.setText(lyricsLoading ? "Looking for lyrics…" : "No lyrics for this song");
        } else {
            message.setVisibility(View.GONE);
        }
        card.setScrollingChild(mode == MODE_LYRICS ? lyricsList : mode == MODE_QUEUE ? queueList
                : mode == MODE_CHAPTERS ? chaptersList : null);
        btnLyrics.setColor(mode == MODE_LYRICS ? accent : secondary);
        btnQueue.setColor(mode == MODE_QUEUE ? accent : secondary);
    }

    private void showTimes(long pos, long dur) {
        elapsed.setText(Format.duration(pos));
        // Faster or slower: how long is left to listen, at that speed; otherwise the length.
        float speed = player.speed();
        if (speed != 1f && dur > 0) {
            remaining.setText("\u2212" + Format.duration(Math.round(Math.max(0, dur - pos) / speed)));
        } else {
            remaining.setText(Format.duration(dur));
        }
    }

    private void tick() {
        if (!open) return;
        if (!volume.isDragging()) {
            if (radioMode && radio.ownVolumeMax() > 0) {
                volume.setMax(radio.ownVolumeMax());
                volume.setValue(radio.ownVolume());
            } else {
                int stream = volumeStream();
                volume.setMax(audio.getStreamMaxVolume(stream));
                volume.setValue(audio.getStreamVolume(stream));
            }
        }
        if (radioMode || liveMode || player.current() == null) return;
        long pos = player.positionMs();
        long dur = player.durationMs();
        scrubber.setEnabled(dur > 0);
        scrubber.setMax(dur);
        if (!scrubber.isDragging()) {
            scrubber.setValue(pos);
            showTimes(pos, dur);
        }
        MediaItem heard = player.current();
        if (Player.isAudiobook(heard)) {
            com.grapefruitmusic.audiobooks.Audiobook book = book(heard);
            int at = book != null ? book.chapterAt(heard.trackNumber - 1, pos) : -1;
            if (at != chapterIndex) {
                chapterIndex = at;
                showAudiobookTitles(heard, at);
                if (mode == MODE_CHAPTERS) showChapters(false);
            }
        }
        if (mode == MODE_LYRICS && lyrics != null && lyrics.synced) {
            // Highlight a moment early, as the singer starts the line.
            int idx = lyrics.indexAt(pos + 250 - lyrics.offsetMs);
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
        /** Synced lines' size, and the space at each side: less in a narrow column. */
        private float syncedSp = 23f;
        private int sideDp = 24;

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

        /**
         * The lyrics' width in dp (0: the whole screen). In a narrow column (the left half of a
         * square phone, like a Clicks Communicator) the lines get smaller, down to 17sp, and the
         * sides closer, so a line still holds a phrase rather than two words.
         */
        void setColumnWidth(int dp) {
            float size = dp <= 0 ? 23f : Math.max(17f, Math.min(23f, dp / 10f));
            int side = dp > 0 && dp < 260 ? 16 : 24;
            if (size == syncedSp && side == sideDp) return;
            syncedSp = size;
            sideDp = side;
            if (credit != null) {
                credit.setPadding(Ui.dp(context, side), Ui.dp(context, 24), Ui.dp(context, side), Ui.dp(context, 8));
            }
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
                t.setLineSpacing(0, 1.05f);
            }
            t.setPadding(Ui.dp(context, sideDp), Ui.dp(context, 9), Ui.dp(context, sideDp), Ui.dp(context, 9));
            String text = lyrics.lines.get(position).text;
            boolean synced = lyrics.synced;
            t.setText(text.trim().length() == 0 ? (synced ? "♪" : " ") : text);
            if (synced) {
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, syncedSp);
                t.setTypeface(Fonts.semiBold());
                boolean now = position == current;
                t.setTextColor(now ? primary : secondary);
                t.setAlpha(now ? 1f : (position < current ? 0.45f : 0.6f));
            } else {
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.round(syncedSp * 18f / 23f));
                t.setTypeface(Fonts.regular());
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
        /** Laid out for a narrow column (1) or not (0); -1 not yet. */
        int narrow = -1;
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

        /**
         * In a narrow column (the left half of a square phone, like a Clicks Communicator) less
         * space around each song and a smaller picture, so its title has room.
         */
        private void fitColumn(View row, QueueHolder h) {
            int narrow = lyricsColumnDp > 0 && lyricsColumnDp < 260 ? 1 : 0;
            if (h.narrow == narrow) return;
            h.narrow = narrow;
            boolean n = narrow == 1;
            row.setPadding(Ui.dp(context, n ? 12 : 20), Ui.dp(context, 4), Ui.dp(context, n ? 2 : 6), Ui.dp(context, 4));
            ViewGroup.LayoutParams art = h.art.getLayoutParams();
            art.width = art.height = Ui.dp(context, n ? 34 : 40);
            h.art.setLayoutParams(art);
            h.texts.setPadding(Ui.dp(context, n ? 10 : 12), 0, Ui.dp(context, n ? 2 : 4), 0);
            int box = Ui.dp(context, n ? 38 : 46);
            for (IconView v : new IconView[]{h.speaker, h.handle}) {
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                lp.width = lp.height = box;
                v.setLayoutParams(lp);
            }
            int pad = Ui.dp(context, n ? 10 : 13);
            h.speaker.setPadding(pad, pad, pad, pad);
            pad = Ui.dp(context, n ? 8 : 11);
            h.handle.setPadding(pad, pad, pad, pad);
        }

        private View song(QueueEntry e, View convertView) {
            QueueHolder h;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Ui.dp(context, 54));
                row.setPadding(Ui.dp(context, 20), Ui.dp(context, 4), Ui.dp(context, 6), Ui.dp(context, 4));
                row.setBackground(Ui.rowBackground(context));
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
            fitColumn(convertView, h);
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
