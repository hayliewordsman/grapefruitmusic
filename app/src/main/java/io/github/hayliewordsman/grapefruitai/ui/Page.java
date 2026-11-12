package io.github.hayliewordsman.grapefruitai.ui;

import android.content.Intent;
import android.view.View;

/** One of the four tabs. Its view is built once and kept across rotations. */
public abstract class Page {
    protected final MainActivity a;
    private View view;

    protected Page(MainActivity a) {
        this.a = a;
    }

    public final View view() {
        if (view == null) view = build();
        return view;
    }

    protected abstract View build();

    /** Shown (again). */
    public void onShow() {
    }

    public void onHide() {
    }

    /** The screen turned or resized. */
    public void onConfigurationChanged() {
    }

    /** Back pressed while showing: true if handled. */
    public boolean onBack() {
        return false;
    }

    public void onResult(int request, int result, Intent data) {
    }

    public void onDestroy() {
    }
}
