// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.grapefruit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.View;
import android.widget.TextView;

/** Appearance only. Launcher3 retains ownership of icons, drag, accessibility and Quickstep. */
public final class GrapefruitLauncherStyle {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path plate = new Path();
    private final int surface, control, outline, text;
    private final float density;

    public GrapefruitLauncherStyle(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        surface = color(context, android.R.color.system_surface_container_dark,
                android.R.color.system_surface_container_light);
        control = color(context, android.R.color.system_surface_container_high_dark,
                android.R.color.system_surface_container_high_light);
        outline = color(context, android.R.color.system_outline_variant_dark,
                android.R.color.system_outline_variant_light);
        text = color(context, android.R.color.system_on_surface_dark, android.R.color.system_on_surface_light);
    }
    private static int color(Context context, int dark, int light) {
        boolean night = (context.getResources().getConfiguration().uiMode & 0x30) == 0x20;
        return context.getColor(night ? dark : light);
    }
    public static void apply(Activity activity, View workspaceRoot, View dock) {
        GrapefruitLauncherStyle theme = new GrapefruitLauncherStyle(activity);
        workspaceRoot.setBackgroundColor(color(activity, android.R.color.system_background_dark,
                android.R.color.system_background_light));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(theme.surface);
        pill.setCornerRadius(100000f);
        pill.setStroke(Math.max(1, Math.round(theme.density)), theme.outline);
        int inset = Math.round(8 * theme.density);
        dock.setBackground(new InsetDrawable(pill, inset, 0, inset, 0));
        dock.setElevation(6 * theme.density);
    }
    public void draw(Canvas canvas, TextView label, Rect iconBounds, boolean decorateLabel) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(control);
        // A teardrop backing surrounds every icon. Keep the original drawable and its badges live.
        rect.set(iconBounds);
        float pad = 2 * density;
        rect.inset(-pad, -pad);
        plate.reset();
        float cx = rect.centerX(), cy = rect.centerY(), rx = rect.width() / 2, ry = rect.height() / 2;
        plate.moveTo(cx, rect.top);
        plate.cubicTo(cx + rx * .5523f, rect.top, rect.right, cy - ry * .5523f, rect.right, cy);
        plate.lineTo(rect.right, rect.bottom - pad);
        plate.quadTo(rect.right, rect.bottom, rect.right - pad, rect.bottom);
        plate.lineTo(cx, rect.bottom);
        plate.cubicTo(cx - rx * .5523f, rect.bottom, rect.left, cy + ry * .5523f, rect.left, cy);
        plate.cubicTo(rect.left, cy - ry * .5523f, cx - rx * .5523f, rect.top, cx, rect.top);
        plate.close();
        canvas.drawPath(plate, paint);
        paint.setColor(outline);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, density));
        canvas.drawPath(plate, paint);
        if (!decorateLabel || label.getLayout() == null || (label.getCurrentTextColor() >>> 24) == 0) return;
        float width = 0;
        for (int i = 0; i < label.getLayout().getLineCount(); i++)
            width = Math.max(width, label.getLayout().getLineWidth(i));
        width = Math.min(label.getWidth() - 2 * density, width + 12 * density);
        float top = label.getExtendedPaddingTop();
        rect.set((label.getWidth() - width) / 2, top - density,
                (label.getWidth() + width) / 2,
                Math.min(label.getHeight(), top + label.getLayout().getHeight() + density));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(control);
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(outline);
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint);
    }
    public void applyText(TextView view) {
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(text);
    }
}
