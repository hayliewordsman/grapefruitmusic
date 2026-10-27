package com.grapefruitmusic.ui;

import android.app.Fragment;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ScrollView;

import com.grapefruitmusic.model.MediaItem;
import com.grapefruitmusic.net.Http;
import com.grapefruitmusic.source.MusicSource;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.util.Bg;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** Shared helpers for pages. */
public abstract class BaseFragment extends Fragment {
    static final String ARG_SOURCE = "source";
    static final String ARG_ITEM = "item";
    static final String ARG_MODE = "mode";
    static final String ARG_BACK = "back";
    static final String ARG_TITLE = "title";

    /*
     * Pages pushed on top (an album, an artist) destroy this page's views but keep the page,
     * so loaded content lives in fields and the list's scroll position is remembered here.
     */
    private int scrollPosition;
    private int scrollOffset;
    /** How far a scrolling page (Settings and the like) was scrolled; kept the same way, for Back. */
    private int pageScrollY;
    /**
     * The first item showing, to keep the place when the screen turns and a page swaps between
     * its portrait and landscape layouts (-1: the top).
     */
    int firstShown = -1;
    /** Orientation the view was last built for (0: not built yet). */
    private int builtFor;

    /** Pages laid out differently sideways rebuild their view when the screen turns. */
    boolean rebuildsOnRotate() {
        return false;
    }

    /** Did the screen turn since the view was last built (so a saved scroll position won't fit)? */
    boolean turnedSinceLastView() {
        return builtFor != 0 && builtFor != getResources().getConfiguration().orientation;
    }

    @Override
    public void onViewCreated(View view, Bundle saved) {
        super.onViewCreated(view, saved);
        builtFor = getResources().getConfiguration().orientation;
        if (view instanceof ScrollView && pageScrollY > 0) restorePageScroll((ScrollView) view, pageScrollY);
    }

    @Override
    public void onDestroyView() {
        View view = getView();
        if (view instanceof ScrollView) pageScrollY = view.getScrollY();
        super.onDestroyView();
    }

    /** Back at a scrolling page: once its content is laid out (it can't scroll before), back to where it was. */
    private static void restorePageScroll(final ScrollView page, final int y) {
        page.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (page.getChildCount() == 0 || page.getChildAt(0).getHeight() == 0) return;
                page.removeOnLayoutChangeListener(this);
                page.scrollTo(0, y);
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (rebuildsOnRotate() && getView() != null && newConfig.orientation != builtFor && isAdded()
                && getFragmentManager() != null) {
            // Detach and attach: the page keeps its loaded content, only its view is rebuilt.
            getFragmentManager().beginTransaction().detach(this).attach(this).commitAllowingStateLoss();
        }
    }

    void rememberScroll(ListView list) {
        if (list == null) return;
        scrollPosition = list.getFirstVisiblePosition();
        View first = list.getChildAt(0);
        scrollOffset = first != null ? first.getTop() : 0;
    }

    void restoreScroll(ListView list) {
        list.setSelectionFromTop(scrollPosition, scrollOffset);
    }

    void resetScroll() {
        scrollPosition = 0;
        scrollOffset = 0;
    }

    /** A playlist was deleted (or made): pages listing playlists reload. */
    void onPlaylistsChanged() {
    }

    /** Handles the Back key itself (returns true), e.g. to leave edit mode. */
    boolean onBackPressed() {
        return false;
    }

    /** What a page opened from this one shows on its back button. */
    String backTitle() {
        return "Back";
    }

    MainActivity main() {
        return (MainActivity) getActivity();
    }

    /** Runs work off the main thread; the callback is skipped if the page has gone away. */
    <T> void bg(Bg.Work<T> work, final Bg.Done<T> done) {
        Bg.run(work, new Bg.Done<T>() {
            @Override
            public void done(T result, Exception error) {
                if (isAdded() && getActivity() != null && !getActivity().isFinishing()) done.done(result, error);
            }
        });
    }

    MusicSource source() {
        String id = getArguments() != null ? getArguments().getString(ARG_SOURCE) : null;
        Sources sources = Sources.get(getActivity());
        MusicSource s = id != null ? sources.byId(id) : null;
        return s != null ? s : sources.current();
    }

    MediaItem item() {
        return getArguments() != null ? (MediaItem) getArguments().getSerializable(ARG_ITEM) : null;
    }

    String backLabel() {
        return getArguments() != null ? getArguments().getString(ARG_BACK) : null;
    }

    View.OnClickListener goBack() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getFragmentManager() != null) getFragmentManager().popBackStack();
            }
        };
    }

    static Bundle args(String sourceId, MediaItem item, String back) {
        Bundle b = new Bundle();
        b.putString(ARG_SOURCE, sourceId);
        if (item != null) b.putSerializable(ARG_ITEM, item);
        b.putString(ARG_BACK, back);
        return b;
    }

    /** Human wording for load errors. */
    static String describe(Exception e, MusicSource source) {
        if (e instanceof Http.StatusException) {
            int code = ((Http.StatusException) e).code;
            if (code == 401 || code == 403) {
                return source.name() + " didn't accept the sign-in. Remove the server in Settings and add it again.";
            }
            return source.name() + " had a problem (" + e.getMessage() + ").";
        }
        if (e instanceof UnknownHostException || e instanceof ConnectException
                || e instanceof SocketTimeoutException || e instanceof FileNotFoundException) {
            return "Can't reach " + source.name() + ". Your downloaded music still plays offline.";
        }
        return e.getMessage() != null ? e.getMessage() : "Something went wrong.";
    }
}
