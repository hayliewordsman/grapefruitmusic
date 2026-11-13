package io.github.hayliewordsman.grapefruitai.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

import io.github.hayliewordsman.grapefruitai.chat.Attachment;
import io.github.hayliewordsman.grapefruitai.chat.Message;
import io.github.hayliewordsman.grapefruitai.md.MdRender;
import io.github.hayliewordsman.grapefruitai.util.Format;

/** One chat message: a bubble for the user, markdown for the assistant, then images and details. */
public final class MessageView extends LinearLayout {
    public interface Actions {
        void retry(Message failed);

        /** The model belongs in the Images tab: take the request there. */
        void useImagesTab(Message failed);
    }

    private final Activity a;
    private final Message m;
    private final Actions actions;
    private final LinearLayout body;
    private final LinearLayout extras;
    private final TextView meta;
    private String renderedText;
    private int renderedImages = -1;
    private boolean showReasoning;

    public MessageView(Activity a, Message m, Actions actions) {
        super(a);
        this.a = a;
        this.m = m;
        this.actions = actions;
        setOrientation(VERTICAL);
        Palette p = Ui.p();
        setPadding(Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 14), Ui.dp(a, 6));
        body = Ui.vertical(a);
        extras = Ui.vertical(a);
        meta = Ui.text(a, 12, p.secondary, false);
        meta.setSingleLine(false);
        if (m.isUser()) {
            setGravity(Gravity.END);
            body.setBackground(Ui.shape(a, p.control, Ui.dp(a, 20), true, false));
            body.setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8));
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.dp(a, 48);
            addView(body, lp);
            addView(extras, lp2());
        } else {
            addView(body, Ui.matchWrap());
            addView(extras, Ui.matchWrap());
            addView(meta, Ui.matchWrap());
        }
        update();
    }

    private LayoutParams lp2() {
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END;
        return lp;
    }

    public Message message() {
        return m;
    }

    /** Re-renders what changed (cheap enough to call while a reply streams in). */
    public void update() {
        Palette p = Ui.p();
        String text = m.text;
        if (m.isUser()) {
            if (!text.equals(renderedText)) {
                body.removeAllViews();
                TextView t = Ui.body(a, 16, p.text);
                t.setText(text);
                t.setTextIsSelectable(true);
                body.addView(t);
                renderedText = text;
                extras.removeAllViews();
                for (Attachment x : m.attachments) extras.addView(attachmentChip(x), lp2());
            }
            return;
        }
        String shown = text + (m.streaming ? " …" : "");
        if (!shown.equals(renderedText)) {
            MdRender.render(a, body, shown.length() > 0 ? shown : (m.streaming ? "…" : ""), 16);
            renderedText = shown;
        }
        int imageCount = m.images.size() + m.citations.size() * 1000 + (m.reasoning.length() > 0 ? 1 << 20 : 0)
                + (m.error != null ? 1 << 21 : 0) + (showReasoning ? 1 << 22 : 0);
        if (imageCount != renderedImages || (showReasoning && m.streaming)) {
            renderedImages = imageCount;
            extras.removeAllViews();
            if (m.reasoning.length() > 0) extras.addView(reasoning(), 0);
            for (Attachment img : m.images) extras.addView(image(img), Ui.matchWrap());
            if (!m.citations.isEmpty()) extras.addView(citations(), Ui.matchWrap());
            if (m.error != null) extras.addView(error(), Ui.matchWrap());
        }
        meta.setText(metaText());
        meta.setVisibility(m.streaming ? GONE : VISIBLE);
    }

    private String metaText() {
        StringBuilder b = new StringBuilder();
        if (m.model != null) b.append(m.model);
        if (m.webSearch) b.append(" · web search");
        if (m.promptTokens > 0 || m.completionTokens > 0) {
            b.append(" · ").append(Format.count(m.promptTokens)).append(" in / ")
                    .append(Format.count(m.completionTokens)).append(" out tokens");
        }
        if (m.cost >= 0) b.append(" · cost ").append(Format.cost(m.cost)).append(" USD");
        if (m.stopped) b.append(" · stopped");
        if (m.time > 0) b.append(" · ").append(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(m.time)));
        return b.toString();
    }

    private View reasoning() {
        Palette p = Ui.p();
        LinearLayout box = Ui.vertical(a);
        TextView head = Ui.text(a, 13, p.accent, true);
        head.setText(showReasoning ? "Hide reasoning" : "Show reasoning (" + m.reasoning.length() + " characters)");
        head.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        head.setMinHeight(Ui.dp(a, 40));
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showReasoning = !showReasoning;
                renderedImages = -1;
                update();
            }
        });
        box.addView(head);
        if (showReasoning) {
            TextView t = Ui.body(a, 14, p.secondary);
            t.setText(m.reasoning);
            t.setTextIsSelectable(true);
            box.addView(t);
        }
        return box;
    }

    private View image(final Attachment img) {
        Palette p = Ui.p();
        LinearLayout box = Ui.vertical(a);
        box.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        ImageView iv = new ImageView(a);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_START);
        int max = Math.min(a.getResources().getDisplayMetrics().widthPixels, Ui.dp(a, 640));
        Bitmap b = Attachments.thumbnail(img.path, max);
        if (b != null) iv.setImageBitmap(b);
        iv.setContentDescription("Generated image");
        iv.setMaxHeight(Ui.dp(a, 520));
        box.addView(iv, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LinearLayout actionsRow = Ui.horizontal(a);
        actionsRow.setPadding(0, Ui.dp(a, 6), 0, 0);
        actionsRow.addView(Ui.button(a, "Save PNG", false, new OnClickListener() {
            @Override
            public void onClick(View v) {
                save(img, false);
            }
        }));
        TextView jpeg = Ui.button(a, "Save JPEG", false, new OnClickListener() {
            @Override
            public void onClick(View v) {
                save(img, true);
            }
        });
        LayoutParams lp = Ui.wrap();
        lp.leftMargin = Ui.dp(a, 8);
        actionsRow.addView(jpeg, lp);
        box.addView(actionsRow);
        if (b == null) {
            TextView t = Ui.text(a, 13, p.secondary, false);
            t.setText("Image could not be shown");
            box.addView(t);
        }
        return box;
    }

    private void save(Attachment img, boolean jpeg) {
        try {
            Saver.saveImage(a, Attachments.read(img.path), img.mime, jpeg);
        } catch (java.io.IOException e) {
            Ui.toast(a, "Couldn't read the image");
        }
    }

    private View citations() {
        Palette p = Ui.p();
        TextView t = Ui.body(a, 13, p.secondary);
        SpannableStringBuilder b = new SpannableStringBuilder("Sources\n");
        int n = 1;
        for (String[] c : m.citations) {
            int start = b.length();
            b.append(String.valueOf(n++)).append(". ").append(c[0].length() > 0 ? c[0] : c[1]);
            if (c[1].startsWith("https://") || c[1].startsWith("http://")) {
                b.setSpan(new URLSpan(c[1]), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            b.append('\n');
        }
        t.setText(b);
        t.setLinkTextColor(p.accent);
        t.setMovementMethod(LinkMovementMethod.getInstance());
        t.setPadding(0, Ui.dp(a, 6), 0, 0);
        return t;
    }

    private View error() {
        Palette p = Ui.p();
        LinearLayout box = Ui.vertical(a);
        box.setBackground(Ui.shape(a, p.control, Ui.dp(a, 18), true, false));
        box.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10));
        TextView t = Ui.body(a, 14, p.danger);
        t.setText(m.error);
        t.setTextIsSelectable(true);
        box.addView(t);
        if (m.errorDetail != null && !m.errorDetail.equals(m.error)) {
            // The raw server message, only when asked for.
            final TextView toggle = Ui.text(a, 13, p.accent, true);
            final TextView detail = Ui.body(a, 12, p.secondary);
            detail.setText(m.errorDetail);
            detail.setTextIsSelectable(true);
            detail.setVisibility(GONE);
            toggle.setText("Show details");
            toggle.setMinHeight(Ui.dp(a, 36));
            toggle.setGravity(Gravity.CENTER_VERTICAL);
            toggle.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean show = detail.getVisibility() != VISIBLE;
                    detail.setVisibility(show ? VISIBLE : GONE);
                    toggle.setText(show ? "Hide details" : "Show details");
                }
            });
            box.addView(toggle);
            box.addView(detail);
        }
        if (actions != null) {
            final boolean images = io.github.hayliewordsman.grapefruitai.or.Routing.USE_IMAGES_TAB.equals(m.error);
            TextView button = Ui.button(a, images ? "Open Images tab" : "Retry", true, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (images) actions.useImagesTab(m);
                    else actions.retry(m);
                }
            });
            LayoutParams lp = Ui.wrap();
            lp.topMargin = Ui.dp(a, 8);
            box.addView(button, lp);
        }
        LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(a, 6);
        box.setLayoutParams(lp);
        return box;
    }

    private View attachmentChip(Attachment x) {
        Palette p = Ui.p();
        if (x.isImage()) {
            ImageView iv = new ImageView(a);
            Bitmap b = Attachments.thumbnail(x.path, Ui.dp(a, 160));
            if (b != null) iv.setImageBitmap(b);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(Ui.dp(a, 160));
            iv.setPadding(0, Ui.dp(a, 4), 0, 0);
            iv.setContentDescription(x.name);
            return iv;
        }
        TextView t = Ui.text(a, 13, p.secondary, false);
        t.setText("Attached: " + x.name + " (" + Format.bytes(x.size) + ")");
        t.setPadding(0, Ui.dp(a, 4), 0, 0);
        return t;
    }
}
