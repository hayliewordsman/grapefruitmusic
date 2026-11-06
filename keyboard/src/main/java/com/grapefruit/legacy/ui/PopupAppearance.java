package com.grapefruit.legacy.ui;

import android.graphics.Color;
import android.view.View;
import com.grapefruit.legacy.GrapefruitTheme;

/** A padded Grapefruit panel replaces the Holo popup nine-patch. */
public final class PopupAppearance {
    private PopupAppearance() {}
    public static void apply(View panel) {
        GrapefruitTheme t=new GrapefruitTheme(panel.getContext());
        android.graphics.drawable.GradientDrawable background=SettingsUi.shape(t.surface,t.dp(16));
        background.setStroke(Math.max(1,t.dp(1)),t.outline);panel.setBackground(background);
        int gap=t.dp(8);panel.setPadding(gap,gap,gap,gap);
        panel.setSoundEffectsEnabled(false);
    }
}
