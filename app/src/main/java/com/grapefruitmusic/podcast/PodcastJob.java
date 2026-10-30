package com.grapefruitmusic.podcast;

import android.app.job.*;

/** Loaded only by the scheduler on API 21+. */
@android.annotation.TargetApi(21)
public final class PodcastJob extends JobService {
    private Thread worker;

    @Override
    public boolean onStartJob(final JobParameters p) {
        worker =
                new Thread(
                        () -> {
                            PodcastRefresh.refresh(this);
                            if (!Thread.currentThread().isInterrupted()) jobFinished(p, false);
                        },
                        "gf-podcast-refresh");
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters p) {
        if (worker != null) worker.interrupt();
        return true;
    }
}
