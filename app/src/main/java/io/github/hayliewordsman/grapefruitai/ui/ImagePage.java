package io.github.hayliewordsman.grapefruitai.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.github.hayliewordsman.grapefruitai.chat.Attachment;
import io.github.hayliewordsman.grapefruitai.chat.Conversation;
import io.github.hayliewordsman.grapefruitai.chat.Message;
import io.github.hayliewordsman.grapefruitai.chat.Store;
import io.github.hayliewordsman.grapefruitai.net.Http;
import io.github.hayliewordsman.grapefruitai.or.Catalog;
import io.github.hayliewordsman.grapefruitai.or.Friendly;
import io.github.hayliewordsman.grapefruitai.or.ImageParams;
import io.github.hayliewordsman.grapefruitai.or.ImagePricing;
import io.github.hayliewordsman.grapefruitai.or.Models;
import io.github.hayliewordsman.grapefruitai.or.OpenRouter;
import io.github.hayliewordsman.grapefruitai.or.OrModel;
import io.github.hayliewordsman.grapefruitai.or.Routing;
import io.github.hayliewordsman.grapefruitai.secure.Secrets;
import io.github.hayliewordsman.grapefruitai.util.Bg;
import io.github.hayliewordsman.grapefruitai.util.Format;
import io.github.hayliewordsman.grapefruitai.util.Prefs;

/**
 * Image generation through OpenRouter's Image API (POST /api/v1/images), or chat completions
 * with image output for models served only there. Aspect ratio, resolution and reference images
 * appear only when the chosen model lists them; nothing is assumed about other models.
 */
public final class ImagePage extends Page implements MessageView.Actions {
    private static final int PICK_REFERENCE = 2;

    private final Store store;
    private Conversation conversation;
    private LinearLayout results;
    private ScrollView scroll;
    private EditText prompt;
    private TextView modelButton;
    private TextView costLine;
    private LinearLayout optionsBox;
    private LinearLayout referencesRow;
    private TextView apiLine;
    private TextView editNote;
    private TextView generate;
    private final List<Attachment> references = new ArrayList<Attachment>();
    private String aspect;
    private String resolution;
    private Http[] active;
    private Message running;

    public ImagePage(MainActivity a) {
        super(a);
        store = new Store(a);
    }

    private String model() {
        return Prefs.str(Prefs.IMAGE_MODEL, "google/gemini-2.5-flash-image");
    }

    @Override
    protected View build() {
        Palette p = Ui.p();
        LinearLayout root = Ui.vertical(a);
        root.setBackgroundColor(p.background);
        LinearLayout header = Ui.horizontal(a);
        header.setPadding(Ui.dp(a, 16), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 4));
        header.addView(Ui.title(a, "Images"), Ui.weight(1));
        IconView history = Ui.circle(a, IconView.HISTORY, 40, false, "Image history");
        history.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHistory();
            }
        });
        header.addView(history);
        IconView fresh = Ui.circle(a, IconView.PLUS, 40, false, "New image session");
        ((LinearLayout.LayoutParams) fresh.getLayoutParams()).leftMargin = Ui.dp(a, 8);
        fresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                conversation = new Conversation(Conversation.IMAGE);
                showResults();
            }
        });
        header.addView(fresh);
        root.addView(header, Ui.matchWrap());

        scroll = new ScrollView(a);
        LinearLayout inner = Ui.vertical(a);
        inner.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 14), Ui.dp(a, 16));
        scroll.addView(inner);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        modelButton = Ui.chip(a, "Model", false);
        modelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModelPicker.show(a, Catalog.IMAGE, model(), new ModelPicker.Listener() {
                    @Override
                    public void onPicked(OrModel m) {
                        Prefs.put(Prefs.IMAGE_MODEL, m.id);
                        aspect = null;
                        resolution = null;
                        refreshOptions();
                    }
                });
            }
        });
        modelButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                OrModel m = Models.find(model());
                if (m != null) ModelPicker.details(a, m);
                return true;
            }
        });
        inner.addView(modelButton, Ui.wrap());
        apiLine = Ui.body(a, 13, p.secondary);
        apiLine.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 8));
        inner.addView(apiLine, Ui.matchWrap());

        prompt = Ui.field(a, "Describe the image", true);
        prompt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        prompt.setMinLines(3);
        prompt.setMaxLines(8);
        inner.addView(prompt, Ui.matchWrap());

        optionsBox = Ui.vertical(a);
        inner.addView(optionsBox, Ui.matchWrap());
        referencesRow = Ui.horizontal(a);
        inner.addView(referencesRow, Ui.matchWrap());
        editNote = Ui.body(a, 13, p.secondary);
        editNote.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), 0);
        inner.addView(editNote, Ui.matchWrap());

        costLine = Ui.body(a, 13, p.secondary);
        costLine.setPadding(Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8));
        inner.addView(costLine, Ui.matchWrap());

        generate = Ui.button(a, "Generate", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running != null) cancel();
                else start();
            }
        });
        inner.addView(generate, Ui.matchWrap());

        results = Ui.vertical(a);
        inner.addView(results, Ui.matchWrap());

        List<Conversation> recent = store.list(Conversation.IMAGE);
        conversation = recent.isEmpty() ? new Conversation(Conversation.IMAGE) : recent.get(0);
        showResults();
        Models.load(a, false, new Models.Listener() {
            @Override
            public void onModels(List<OrModel> models, String error) {
                refreshOptions();
            }
        });
        Models.addPricingListener(pricesArrived);
        refreshOptions();
        return root;
    }

    @Override
    public void onShow() {
        refreshOptions();
    }

    /** Controls for what the chosen model accepts, and its listed prices. */
    private void refreshOptions() {
        if (optionsBox == null) return;
        Palette p = Ui.p();
        OrModel m = Models.find(model());
        modelButton.setText((m != null ? m.name : model()) + "  ▼");
        optionsBox.removeAllViews();
        ImageParams params = Models.imageParams(model());
        boolean imageApi = m == null || m.servesImageApi();
        List<String> ratios = params != null ? params.aspectRatios : new ArrayList<String>();
        List<String> sizes = params != null ? params.resolutions : new ArrayList<String>();
        if (!ratios.isEmpty()) optionsBox.addView(choiceRow("Aspect", ratios, aspect, new Choice() {
            public void chose(String v) {
                aspect = v;
                refreshOptions();
            }
        }));
        if (!sizes.isEmpty()) optionsBox.addView(choiceRow("Resolution", sizes, resolution, new Choice() {
            public void chose(String v) {
                resolution = v;
                refreshOptions();
            }
        }));
        if (params == null && m != null && !imageApi) {
            TextView t = Ui.body(a, 13, p.secondary);
            t.setText("This model makes images through chat; OpenRouter lists no size options for it, so it uses "
                    + "its own defaults.");
            optionsBox.addView(t);
        }
        Routing.ImageRoute route = Routing.imageRoute(m);
        if (m == null) {
            apiLine.setText(Models.all().isEmpty() ? "" : "This model isn't in OpenRouter's list; it will be sent to the Images API.");
        } else if (route == Routing.ImageRoute.IMAGES_API) {
            apiLine.setText("This model uses OpenRouter Images API.");
        } else if (route == Routing.ImageRoute.CHAT_COMPLETIONS) {
            apiLine.setText("This model makes images through OpenRouter's Chat API (image output).");
        } else {
            apiLine.setText(m.name + " doesn't generate images. Choose an image model.");
        }
        Routing.Editing editing = Routing.editing(m, params);
        refreshReferences(editing.supported ? editing.maxImages : 0);
        editNote.setText(editing.note);
        generate.setText(running != null ? "Cancel" : references.isEmpty() ? "Generate" : "Edit image");
        StringBuilder cost = new StringBuilder();
        if (m == null) {
            cost.append(Models.all().isEmpty() ? "Loading OpenRouter's model list…" : "Model not in OpenRouter's list.");
        } else {
            ImagePricing pricing = Models.pricing(m.id);
            cost.append("Image generation: ").append(Format.imageGeneration(m, pricing));
            if (pricing != null) {
                for (String line : pricing.lines()) {
                    if (!line.startsWith("Image generation")) cost.append("\n").append(line);
                }
            }
            if (!m.outputsText() && OrModel.value(m.pricePrompt) != null && OrModel.value(m.pricePrompt).signum() > 0
                    && (pricing == null || !pricing.lines().toString().contains("Prompt text"))) {
                cost.append("\nPrompt text: ").append(Format.perMillion(m.pricePrompt));
            }
            cost.append("\nPrices in US dollars");
            if (pricing != null && ImagePricing.MODEL_PAGE.equals(pricing.source)) {
                cost.append(", from the model's openrouter.ai page (OpenRouter's API doesn't list them)");
            }
            cost.append(". The actual charge OpenRouter reports appears under each image; failed generations "
                    + "are not billed by the Images API.");
        }
        costLine.setText(cost.toString());
    }

    interface Choice {
        void chose(String value);
    }

    private View choiceRow(String label, List<String> values, String chosen, final Choice choice) {
        HorizontalScrollView row = Ui.chipRow(a);
        ((LinearLayout) row.getChildAt(0)).setPadding(0, Ui.dp(a, 6), 0, 0);
        TextView l = Ui.text(a, 13, Ui.p().secondary, false);
        l.setText(label);
        Ui.addChip(row, l);
        TextView def = Ui.chip(a, "Default", chosen == null);
        def.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choice.chose(null);
            }
        });
        Ui.addChip(row, def);
        for (final String v : values) {
            TextView chip = Ui.chip(a, v, v.equals(chosen));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    choice.chose(v);
                }
            });
            Ui.addChip(row, chip);
        }
        return row;
    }

    private void refreshReferences(final int max) {
        referencesRow.removeAllViews();
        while (references.size() > max) references.remove(references.size() - 1);
        if (max <= 0) return;
        referencesRow.setPadding(0, Ui.dp(a, 8), 0, 0);
        for (final Attachment r : references) {
            ImageView iv = new ImageView(a);
            Bitmap b = Attachments.thumbnail(r.path, Ui.dp(a, 64));
            if (b != null) iv.setImageBitmap(b);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setContentDescription("Reference image, tap to remove");
            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    references.remove(r);
                    refreshOptions();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 56), Ui.dp(a, 56));
            lp.rightMargin = Ui.dp(a, 6);
            referencesRow.addView(iv, lp);
        }
        if (references.size() < max) {
            TextView add = Ui.chip(a, references.isEmpty() ? "Add an image to edit" : "Add another image", false);
            add.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.startForResult(ImagePage.this, Attachments.pickIntent("image/*"), PICK_REFERENCE);
                }
            });
            referencesRow.addView(add);
        }
    }

    @Override
    public void onResult(int request, int result, final Intent data) {
        if (request != PICK_REFERENCE || result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                try {
                    final Attachment x = Attachments.importUri(a, store, data.getData());
                    Bg.ui(new Runnable() {
                        @Override
                        public void run() {
                            if (!x.isImage()) {
                                Ui.toast(a, "Choose an image");
                                return;
                            }
                            references.add(x);
                            refreshOptions();
                        }
                    });
                } catch (final Exception e) {
                    Bg.ui(new Runnable() {
                        @Override
                        public void run() {
                            Ui.toast(a, "Couldn't add: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void showResults() {
        results.removeAllViews();
        List<Message> ms = conversation.messages;
        for (int i = ms.size() - 1; i >= 0; i--) {
            Message m = ms.get(i);
            if (m.isUser()) continue;
            Message req = i > 0 ? ms.get(i - 1) : null;
            if (req != null && req.isUser()) {
                TextView t = Ui.body(a, 14, Ui.p().text);
                t.setText(req.text);
                t.setPadding(Ui.dp(a, 4), Ui.dp(a, 16), Ui.dp(a, 4), 0);
                t.setTextIsSelectable(true);
                results.addView(t, Ui.matchWrap());
            }
            MessageView v = new MessageView(a, m, this);
            v.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
            results.addView(v, Ui.matchWrap());
        }
    }

    private void showHistory() {
        Sheet s = new Sheet(a, "Image sessions");
        List<Conversation> all = store.list(Conversation.IMAGE);
        if (all.isEmpty()) s.message("Nothing generated yet.");
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        for (final Conversation c : all) {
            s.row(c.title, df.format(new Date(c.updated)), c.id.equals(conversation.id), new Runnable() {
                @Override
                public void run() {
                    conversation = c;
                    showResults();
                }
            });
        }
        s.button("Delete current", false, new Runnable() {
            @Override
            public void run() {
                Sheet.confirm(a, "Delete this image session?", "Its images are removed from this device "
                        + "(copies you saved stay).", "Delete", true, new Runnable() {
                    @Override
                    public void run() {
                        store.delete(conversation);
                        conversation = new Conversation(Conversation.IMAGE);
                        showResults();
                    }
                });
            }
        });
        s.button("Close", true, null).show();
    }

    @Override
    public void retry(Message failed) {
        int i = conversation.messages.indexOf(failed);
        if (i > 0 && conversation.messages.get(i - 1).isUser()) {
            prompt.setText(conversation.messages.get(i - 1).text);
            conversation.messages.remove(i);
            conversation.messages.remove(i - 1);
            showResults();
            start();
        }
    }

    private void start() {
        final String text = prompt.getText().toString().trim();
        if (text.length() == 0) {
            Ui.toast(a, "Describe the image first");
            return;
        }
        if (Secrets.isLocked()) {
            SettingsPage.unlockDialog(a, null);
            return;
        }
        final String key = Secrets.get(Secrets.OPENROUTER_KEY);
        if (key == null) {
            Sheet.alert(a, "OpenRouter API key needed", "Add your OpenRouter key in Settings to generate images.");
            return;
        }
        final OrModel m = Models.find(model());
        final Routing.ImageRoute route = Routing.imageRoute(m);
        if (route == Routing.ImageRoute.NONE) {
            Sheet.alert(a, "Not an image model", m.name + " doesn't generate images. Choose a model from the "
                    + "Images tab's list.");
            return;
        }
        Routing.Editing editing = Routing.editing(m, Models.imageParams(model()));
        if (!references.isEmpty() && !editing.supported) {
            Sheet.alert(a, "Image editing not supported", editing.note);
            return;
        }
        final boolean viaImageApi = route == Routing.ImageRoute.IMAGES_API;
        final String model = model();
        final String ratio = aspect;
        final String res = resolution;
        final List<Attachment> refs = new ArrayList<Attachment>(references);

        Message req = new Message(Message.USER);
        req.text = text;
        req.attachments.addAll(refs);
        final Message out = new Message(Message.ASSISTANT);
        out.model = model;
        out.streaming = true;
        out.text = (refs.isEmpty() ? "Generating" : "Editing " + refs.size() + " image" + (refs.size() == 1 ? "" : "s"))
                + (ratio != null ? " · " + ratio : "") + (res != null ? " · " + res : "")
                + (viaImageApi ? " · Images API" : " · Chat API") + "…";
        conversation.messages.add(req);
        conversation.messages.add(out);
        if (conversation.title.equals("New conversation")) conversation.autoTitle();
        running = out;
        generate.setText("Cancel");
        showResults();
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.smoothScrollTo(0, results.getTop());
            }
        });
        final Http[] holder = new Http[1];
        final boolean[] cancelled = new boolean[1];
        active = holder;
        final Conversation conv = conversation;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                Friendly.Error error = null;
                OpenRouter.ImageResult r = null;
                try {
                    List<String> dataUrls = new ArrayList<String>();
                    for (Attachment x : refs) dataUrls.add(OpenRouter.DataUrl.of(x.mime, Attachments.read(x.path)));
                    OpenRouter or = new OpenRouter(key);
                    r = viaImageApi ? or.generateImage(model, text, ratio, res, dataUrls, holder)
                            : or.generateImageViaChat(model, text, ratio, dataUrls, holder);
                    for (int i = 0; i < r.images.size(); i++) {
                        String mime = r.mimes.get(i);
                        String ext = mime.contains("jpeg") ? "jpg" : mime.contains("webp") ? "webp" : "png";
                        File f = store.newMedia(ext);
                        FileOutputStream fo = new FileOutputStream(f);
                        try {
                            fo.write(r.images.get(i));
                        } finally {
                            fo.close();
                        }
                        synchronized (out) {
                            out.images.add(new Attachment("image." + ext, mime, f.getAbsolutePath(), r.images.get(i).length));
                        }
                    }
                } catch (IOException e) {
                    error = holder[0] != null && holder[0].isCancelled() ? null : Friendly.of(e);
                    if (error == null) cancelled[0] = true;
                }
                final Friendly.Error err = error;
                final OpenRouter.ImageResult result = r;
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (out) {
                            out.streaming = false;
                            out.text = result != null && result.text.length() > 0 ? result.text : "";
                            if (result != null) {
                                out.cost = result.cost;
                                out.promptTokens = result.promptTokens;
                                out.completionTokens = result.completionTokens;
                            }
                            if (err != null) {
                                out.error = err.message;
                                out.errorDetail = err.detail;
                            } else if (cancelled[0]) {
                                out.error = "Cancelled";
                            }
                        }
                        store.save(conv);
                        if (running == out) {
                            running = null;
                            active = null;
                        }
                        refreshOptions();
                        if (conv == conversation) showResults();
                    }
                });
            }
        });
    }

    /** From the Chat tab: an image model, the prompt, and images to edit. */
    public void prefill(String modelId, String text, List<Attachment> images) {
        view();
        OrModel m = Models.find(modelId);
        if (m == null || m.isImage()) Prefs.put(Prefs.IMAGE_MODEL, modelId);
        aspect = null;
        resolution = null;
        if (text != null && text.length() > 0) prompt.setText(text);
        references.clear();
        if (images != null) for (Attachment x : images) if (x.isImage()) references.add(x);
        refreshOptions();
        if (!references.isEmpty() && !Routing.editing(m, Models.imageParams(modelId)).supported) {
            Ui.toast(a, "This model can't edit images; the images were not added");
        }
    }

    @Override
    public void useImagesTab(Message failed) {
        // Already here.
    }

    /** For tests: the chosen references and the generate button's label. */
    List<Attachment> references() {
        return references;
    }

    String buttonLabel() {
        return generate.getText().toString();
    }

    String apiLabel() {
        return apiLine.getText().toString();
    }

    String editingNote() {
        return editNote.getText().toString();
    }

    private void cancel() {
        Http[] h = active;
        if (h != null && h[0] != null) h[0].cancel();
    }

    @Override
    public void onDestroy() {
        cancel();
        Models.removePricingListener(pricesArrived);
    }

    private final Runnable pricesArrived = new Runnable() {
        @Override
        public void run() {
            refreshOptions();
        }
    };
}
