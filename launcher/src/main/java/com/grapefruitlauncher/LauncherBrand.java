// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitlauncher;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.widget.TextView;

final class LauncherBrand {
    static final String SOURCE="https://github.com/hayliewordsman/grapefruitmusic/tree/codex/grapefruit-launcher-preview/launcher";
    static final String UPSTREAM="https://android.googlesource.com/platform/packages/apps/Launcher2/+/002c25eede2a33a5378c16c5bb9165179783e729";
    private static Typeface pixel;
    static synchronized Typeface pixel(Context context) {
        if(pixel==null)pixel=Typeface.createFromAsset(context.getAssets(),"fonts/GeistPixel-Triangle-Launcher.ttf");
        return pixel;
    }
    static TextView wordmark(Context context,LauncherTheme theme) {
        TextView name=new TextView(context);name.setTypeface(pixel(context));name.setTextSize(32);
        name.setIncludeFontPadding(false);name.setGravity(Gravity.CENTER);name.setTextColor(theme.text);
        name.setLineSpacing(theme.dp(5),1);name.setContentDescription("Grapefruit Launcher wordmark");
        SpannableString text=new SpannableString("Grapefruit\nLauncher");
        text.setSpan(new ForegroundColorSpan(theme.accent),0,10,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);name.setText(text);
        return name;
    }
}
