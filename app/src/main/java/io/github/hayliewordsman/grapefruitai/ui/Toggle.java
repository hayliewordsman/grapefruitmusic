package io.github.hayliewordsman.grapefruitai.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Grapefruit's switch: a pill track (the accent gradient when on) and a round knob. */
public final class Toggle extends View {
    public interface Listener {
        void onChanged(boolean on);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private boolean on;
    private Listener listener;

    public Toggle(Context c) {
        super(c);
        setClickable(true);
        setFocusable(true);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setChecked(!on);
                if (listener != null) listener.onChanged(on);
            }
        });
    }

    public void setChecked(boolean b) {
        on = b;
        setContentDescription((b ? "On" : "Off"));
        invalidate();
    }

    public boolean isChecked() {
        return on;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Ui.dp(getContext(), 52), Ui.dp(getContext(), 32));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Palette p = Ui.p();
        float w = getWidth(), h = getHeight();
        r.set(0, 0, w, h);
        paint.setStyle(Paint.Style.FILL);
        if (on) paint.setShader(new LinearGradient(0, 0, w, h, p.accent, p.accentEnd, Shader.TileMode.CLAMP));
        else paint.setColor(p.toggleOff);
        canvas.drawRoundRect(r, h / 2, h / 2, paint);
        paint.setShader(null);
        float pad = Ui.dp(getContext(), 3);
        float d = h - 2 * pad;
        float x = on ? w - pad - d / 2 : pad + d / 2;
        paint.setColor(p.knob);
        canvas.drawCircle(x, h / 2, d / 2, paint);
    }
}
