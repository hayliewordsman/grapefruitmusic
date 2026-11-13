package io.github.hayliewordsman.grapefruitai.ui;

import android.app.Activity;
import android.content.Intent;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
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
import io.github.hayliewordsman.grapefruitai.net.HttpException;
import io.github.hayliewordsman.grapefruitai.or.Catalog;
import io.github.hayliewordsman.grapefruitai.or.Friendly;
import io.github.hayliewordsman.grapefruitai.or.Models;
import io.github.hayliewordsman.grapefruitai.or.OpenRouter;
import io.github.hayliewordsman.grapefruitai.or.OrModel;
import io.github.hayliewordsman.grapefruitai.or.Routing;
import io.github.hayliewordsman.grapefruitai.secure.Secrets;
import io.github.hayliewordsman.grapefruitai.util.Bg;
import io.github.hayliewordsman.grapefruitai.util.Format;
import io.github.hayliewordsman.grapefruitai.util.Prefs;

/**
 * Chat / Research: direct OpenRouter conversations with the model the user picks, streamed,
 * with attachments the model accepts and (optionally) OpenRouter's web search plugin.
 */
public final class ChatPage extends Page implements MessageView.Actions {
    static final String DEFAULT_MODEL = "openrouter/auto";
    private static final int PICK = 1;
    static final String NO_WEB_SYSTEM = "You are Grapefruit AI, a helpful assistant. Live web search is OFF in "
            + "this conversation: you cannot browse or look anything up. If asked about recent events or live "
            + "information, say that you can't check and that the user can turn on Web search.";
    static final String WEB_SYSTEM = "You are Grapefruit AI, a helpful assistant. Web search is ON: OpenRouter's "
            + "web search plugin adds results to the conversation. Base claims about current events on those "
            + "results and cite the sources.";

    private final Store store;
    private Conversation conversation;
    private LinearLayout list;
    private ScrollView scroll;
    private EditText input;
    private IconView send;
    private TextView modelButton;
    private TextView webChip;
    private LinearLayout status;
    private LinearLayout pendingRow;
    private final List<Attachment> pending = new ArrayList<Attachment>();
    private final List<MessageView> views = new ArrayList<MessageView>();
    private Http[] active;
    private Message streaming;
    private boolean updateQueued;

    public ChatPage(MainActivity a) {
        super(a);
        store = new Store(a);
    }

    @Override
    protected View build() {
        Palette p = Ui.p();
        LinearLayout root = Ui.vertical(a);
        root.setBackgroundColor(p.background);

        LinearLayout header = Ui.horizontal(a);
        header.setPadding(Ui.dp(a, 16), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 4));
        TextView title = Ui.title(a, "Chat");
        header.addView(title, Ui.weight(1));
        IconView history = Ui.circle(a, IconView.HISTORY, 40, false, "Conversation history");
        history.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHistory();
            }
        });
        header.addView(history);
        IconView fresh = Ui.circle(a, IconView.PLUS, 40, false, "New conversation");
        ((LinearLayout.LayoutParams) fresh.getLayoutParams()).leftMargin = Ui.dp(a, 8);
        fresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newConversation();
            }
        });
        header.addView(fresh);
        root.addView(header, Ui.matchWrap());

        android.widget.HorizontalScrollView tools = Ui.chipRow(a);
        modelButton = Ui.chip(a, "Model", false);
        modelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickModel();
            }
        });
        Ui.addChip(tools, modelButton);
        webChip = Ui.chip(a, "Web search", false);
        webChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleWeb();
            }
        });
        Ui.addChip(tools, webChip);
        status = Ui.statusPill(a);
        status.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                explainStatus();
            }
        });
        Ui.addChip(tools, status);
        root.addView(tools, Ui.matchWrap());

        scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        list = Ui.vertical(a);
        list.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 12));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        pendingRow = Ui.horizontal(a);
        pendingRow.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), 0);
        root.addView(pendingRow, Ui.matchWrap());

        LinearLayout composer = Ui.horizontal(a);
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6));
        IconView attach = Ui.circle(a, IconView.ATTACH, 44, false, "Attach a file");
        attach.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.startForResult(ChatPage.this, Attachments.pickIntent("*/*"), PICK);
            }
        });
        composer.addView(attach);
        input = Ui.field(a, "Message", true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(6);
        LinearLayout.LayoutParams ilp = Ui.weight(1);
        ilp.leftMargin = Ui.dp(a, 8);
        ilp.rightMargin = Ui.dp(a, 8);
        composer.addView(input, ilp);
        send = Ui.circle(a, IconView.SEND, 44, true, "Send");
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (streaming != null) stop();
                else sendFromInput();
            }
        });
        composer.addView(send);
        root.addView(composer, Ui.matchWrap());

        List<Conversation> recent = store.list(Conversation.CHAT);
        conversation = recent.isEmpty() ? new Conversation(Conversation.CHAT) : recent.get(0);
        showConversation();
        Models.load(a, false, null);
        return root;
    }

    @Override
    public void onShow() {
        refreshChrome();
    }

    private String model() {
        if (conversation != null && conversation.model != null) return conversation.model;
        return Prefs.str(Prefs.CHAT_MODEL, DEFAULT_MODEL);
    }

    private void refreshChrome() {
        if (modelButton == null) return;
        OrModel m = Models.find(model());
        modelButton.setText((m != null ? m.name : model()) + "  ▼");
        boolean web = Prefs.bool(Prefs.WEB_SEARCH, false);
        Ui.setChipChosen(webChip, web);
        webChip.setText(web ? "Web search on" : "No live web");
        if (Secrets.isLocked()) Ui.setStatus(status, Ui.STATUS_BUSY, "OpenRouter: locked");
        else if (!Secrets.has(Secrets.OPENROUTER_KEY)) Ui.setStatus(status, Ui.STATUS_ERROR, "OpenRouter: no key");
        else if (lastStatus != Ui.STATUS_UNKNOWN) Ui.setStatus(status, lastStatus, lastStatusText);
        else Ui.setStatus(status, Ui.STATUS_UNKNOWN, "OpenRouter: key set");
    }

    private int lastStatus = Ui.STATUS_UNKNOWN;
    private String lastStatusText = "";

    private void setProviderStatus(int s, String text) {
        lastStatus = s;
        lastStatusText = text;
        refreshChrome();
    }

    private void explainStatus() {
        StringBuilder b = new StringBuilder();
        b.append("Provider: OpenRouter (openrouter.ai), called directly from this device with your own key.\n\n");
        b.append(Secrets.isLocked() ? "Your saved key is locked: unlock it in Settings.\n"
                : Secrets.has(Secrets.OPENROUTER_KEY) ? "API key: set (" + Secrets.describeMode(Secrets.mode()) + ").\n"
                : "API key: not set. Add it in Settings.\n");
        if (lastStatusText.length() > 0) b.append("Last request: ").append(lastStatusText).append('\n');
        b.append("Model list: ").append(Models.all().size()).append(" models");
        if (Models.fetchedAt() > 0) {
            b.append(", updated ").append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(Models.fetchedAt())));
        }
        if (Models.lastError() != null) b.append("\nModel list error: ").append(Models.lastError());
        b.append("\n\nWeb search: ").append(Prefs.bool(Prefs.WEB_SEARCH, false)
                ? "on (OpenRouter web plugin; extra charge per search, billed by OpenRouter)."
                : "off: the model has no live internet access.");
        new Sheet(a, "Status").message(b.toString()).button("Settings", false, new Runnable() {
            @Override
            public void run() {
                a.showTab(MainActivity.TAB_SETTINGS);
            }
        }).button("OK", true, null).show();
    }

    private void toggleWeb() {
        boolean on = !Prefs.bool(Prefs.WEB_SEARCH, false);
        if (on) {
            OrModel m = Models.find(model());
            String price = m != null && m.priceWebSearch != null
                    ? "This model lists " + Format.perUnit(m.priceWebSearch, "search") + " (USD) for native search. " : "";
            new Sheet(a, "Turn on web search?")
                    .message("Grapefruit AI uses OpenRouter's documented web search plugin (\"web\"). Up to 5 results "
                            + "are added to each request. " + price + "OpenRouter charges web search separately: with "
                            + "its default Exa engine, about $0.007 (USD) per request; models with native search pass "
                            + "through the provider's price. See openrouter.ai/docs for current prices.")
                    .button("Cancel", false, null)
                    .button("Turn on", true, new Runnable() {
                        @Override
                        public void run() {
                            Prefs.put(Prefs.WEB_SEARCH, true);
                            refreshChrome();
                        }
                    }).show();
        } else {
            Prefs.put(Prefs.WEB_SEARCH, false);
            refreshChrome();
        }
    }

    private void pickModel() {
        ModelPicker.show(a, Catalog.CHAT, model(), new ModelPicker.Listener() {
            @Override
            public void onPicked(OrModel m) {
                Prefs.put(Prefs.CHAT_MODEL, m.id);
                conversation.model = m.id;
                if (!conversation.messages.isEmpty()) store.save(conversation);
                refreshChrome();
            }
        });
    }

    private void newConversation() {
        if (streaming != null) stop();
        conversation = new Conversation(Conversation.CHAT);
        conversation.model = Prefs.str(Prefs.CHAT_MODEL, DEFAULT_MODEL);
        pending.clear();
        showConversation();
        input.setText("");
    }

    private void showConversation() {
        list.removeAllViews();
        views.clear();
        if (conversation.messages.isEmpty()) {
            TextView hint = Ui.status(a);
            hint.setText(Secrets.has(Secrets.OPENROUTER_KEY) || Secrets.isLocked()
                    ? "Ask anything. Pick a model above; prices are shown in US dollars in the model list."
                    : "Add your OpenRouter API key in Settings to start chatting.");
            list.addView(hint, Ui.matchWrap());
        }
        for (Message m : conversation.messages) addView(m);
        refreshPending();
        refreshChrome();
        scrollToEnd();
    }

    private void addView(Message m) {
        if (views.isEmpty()) list.removeAllViews();
        MessageView v = new MessageView(a, m, this);
        views.add(v);
        list.addView(v, Ui.matchWrap());
    }

    private void scrollToEnd() {
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void showHistory() {
        final List<Conversation> all = store.list(Conversation.CHAT);
        Sheet s = new Sheet(a, "Conversations");
        if (all.isEmpty()) s.message("No saved conversations yet.");
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        for (final Conversation c : all) {
            s.row(c.title, df.format(new Date(c.updated)) + " · " + c.messages.size() + " messages",
                    conversation != null && c.id.equals(conversation.id), new Runnable() {
                        @Override
                        public void run() {
                            if (streaming != null) stop();
                            conversation = c;
                            showConversation();
                        }
                    });
        }
        s.button("Delete current", false, new Runnable() {
            @Override
            public void run() {
                Sheet.confirm(a, "Delete this conversation?", "It is removed from this device.", "Delete", true,
                        new Runnable() {
                            @Override
                            public void run() {
                                if (streaming != null) stop();
                                store.delete(conversation);
                                newConversation();
                            }
                        });
            }
        });
        s.button("Close", true, null).show();
    }

    // --- Attachments ---

    @Override
    public void onResult(int request, int result, final Intent data) {
        if (request != PICK || result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                try {
                    final Attachment x = Attachments.importUri(a, store, data.getData());
                    Bg.ui(new Runnable() {
                        @Override
                        public void run() {
                            pending.add(x);
                            refreshPending();
                        }
                    });
                } catch (final Exception e) {
                    Bg.ui(new Runnable() {
                        @Override
                        public void run() {
                            Ui.toast(a, "Couldn't attach: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void refreshPending() {
        pendingRow.removeAllViews();
        for (final Attachment x : pending) {
            TextView chip = Ui.chip(a, x.name + "  ×", false);
            chip.setContentDescription("Remove " + x.name);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pending.remove(x);
                    new File(x.path).delete();
                    refreshPending();
                }
            });
            LinearLayout.LayoutParams lp = Ui.wrap();
            lp.rightMargin = Ui.dp(a, 6);
            pendingRow.addView(chip, lp);
        }
        pendingRow.setVisibility(pending.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Null if the model can take these attachments, else why not. */
    static String attachmentProblem(OrModel m, List<Attachment> files) {
        if (m == null) return null;
        for (Attachment x : files) {
            if (x.isImage() && !m.acceptsImages()) return m.name + " doesn't accept images. Choose a vision model.";
            if (!x.isImage() && !x.isText() && !m.acceptsFiles()) {
                return m.name + " doesn't accept files like " + x.name + ". Choose a model with file input.";
            }
        }
        return null;
    }

    // --- Sending ---

    private void sendFromInput() {
        send(input.getText().toString().trim());
    }

    /** Sends text (and the pending attachments) with the chosen model, if it's a chat model. */
    void send(String text) {
        if (text.length() == 0 && pending.isEmpty()) return;
        OrModel chosen = Models.find(model());
        String block = Routing.chatBlock(chosen);
        if (block != null) {
            offerImagesTab(chosen, text, new ArrayList<Attachment>(pending), true);
            return;
        }
        if (!ensureKey()) return;
        String problem = attachmentProblem(chosen, pending);
        if (problem != null) {
            Sheet.alert(a, "Attachment not supported", problem);
            return;
        }
        Message user = new Message(Message.USER);
        user.text = text;
        user.attachments.addAll(pending);
        pending.clear();
        refreshPending();
        input.setText("");
        conversation.messages.add(user);
        if (conversation.model == null) conversation.model = model();
        conversation.autoTitle();
        addView(user);
        run();
    }

    private boolean ensureKey() {
        if (Secrets.isLocked()) {
            SettingsPage.unlockDialog(a, null);
            return false;
        }
        if (!Secrets.has(Secrets.OPENROUTER_KEY)) {
            new Sheet(a, "OpenRouter API key needed")
                    .message("Grapefruit AI uses your own OpenRouter key. Add it in Settings; by default it's kept "
                            + "for this session only.")
                    .button("Cancel", false, null)
                    .button("Open Settings", true, new Runnable() {
                        @Override
                        public void run() {
                            a.showTab(MainActivity.TAB_SETTINGS);
                        }
                    }).show();
            return false;
        }
        return true;
    }

    /** The last friendly sheet shown (for tests). */
    Sheet lastSheet;

    /**
     * The model is for image generation/editing: say so, and offer to take the prompt and any
     * images to the Images tab with that model.
     */
    private void offerImagesTab(final OrModel m, final String text, final List<Attachment> files, final boolean clear) {
        final List<Attachment> images = new ArrayList<Attachment>();
        for (Attachment x : files) if (x.isImage()) images.add(x);
        boolean image = m != null && m.isImage();
        Sheet s = new Sheet(a, image ? "Use the Images tab" : "Not a chat model")
                .message(Routing.chatBlock(m) != null ? Routing.chatBlock(m) : Routing.USE_IMAGES_TAB);
        if (image) {
            s.message(images.isEmpty() ? "Your prompt will be carried over."
                    : "Your prompt and " + images.size() + " image" + (images.size() == 1 ? "" : "s")
                    + " will be carried over to edit there.");
            s.button("Cancel", false, null);
            s.button("Open Images tab", true, new Runnable() {
                @Override
                public void run() {
                    ((ImagePage) a.page(MainActivity.TAB_IMAGES)).prefill(m.id, text, images);
                    if (clear) {
                        pending.clear();
                        refreshPending();
                        input.setText("");
                    }
                    a.showTab(MainActivity.TAB_IMAGES);
                }
            });
        } else {
            s.button("Choose a chat model", true, new Runnable() {
                @Override
                public void run() {
                    pickModel();
                }
            });
        }
        lastSheet = s.show();
    }

    @Override
    public void useImagesTab(Message failed) {
        int i = conversation.messages.indexOf(failed);
        Message user = i > 0 && conversation.messages.get(i - 1).isUser() ? conversation.messages.get(i - 1) : null;
        offerImagesTab(Models.find(failed.model != null ? failed.model : model()),
                user != null ? user.text : "", user != null ? user.attachments : new ArrayList<Attachment>(), false);
    }

    @Override
    public void retry(Message failed) {
        if (streaming != null || !ensureKey()) return;
        int i = conversation.messages.indexOf(failed);
        if (i >= 0) {
            conversation.messages.remove(i);
            for (MessageView v : views) {
                if (v.message() == failed) {
                    list.removeView(v);
                    views.remove(v);
                    break;
                }
            }
        }
        run();
    }

    /** Streams the assistant's reply to the conversation as it stands. */
    private void run() {
        final String model = model();
        final boolean web = Prefs.bool(Prefs.WEB_SEARCH, false);
        final List<Message> history = new ArrayList<Message>(conversation.messages);
        final Message reply = new Message(Message.ASSISTANT);
        reply.model = model;
        reply.streaming = true;
        reply.webSearch = web;
        conversation.messages.add(reply);
        addView(reply);
        streaming = reply;
        send.setIcon(IconView.STOP);
        send.setContentDescription("Stop generating");
        setProviderStatus(Ui.STATUS_BUSY, "OpenRouter: generating…");
        scrollToEnd();
        final Http[] holder = new Http[1];
        active = holder;
        final String key = Secrets.get(Secrets.OPENROUTER_KEY);
        final Conversation conv = conversation;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                Friendly.Error error = null;
                int attempt = 0;
                while (true) {
                    try {
                        new OpenRouter(key).chat(model, history, web, web ? WEB_SYSTEM : NO_WEB_SYSTEM,
                                new Listener(reply), holder);
                        error = null;
                    } catch (IOException e) {
                        error = holder[0] != null && holder[0].isCancelled() ? null : Friendly.of(e);
                        boolean transient_ = !(e instanceof Friendly.ModelRouteException)
                                && (!(e instanceof HttpException) || ((HttpException) e).code >= 500
                                || ((HttpException) e).code == 408);
                        boolean empty;
                        synchronized (reply) {
                            empty = reply.text.length() == 0 && reply.images.isEmpty();
                        }
                        if (error != null && transient_ && attempt == 0 && empty) {
                            attempt++;
                            Bg.ui(new Runnable() {
                                @Override
                                public void run() {
                                    setProviderStatus(Ui.STATUS_BUSY, "OpenRouter: reconnecting…");
                                }
                            });
                            try {
                                Thread.sleep(1500);
                            } catch (InterruptedException ignored) {
                                // Go on.
                            }
                            if (holder[0] == null || !holder[0].isCancelled()) continue;
                            error = null;
                        }
                    }
                    break;
                }
                final Friendly.Error err = error;
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        finish(conv, reply, holder, err);
                    }
                });
            }
        });
    }

    static String describe(IOException e) {
        String m = e.getMessage();
        if (e instanceof java.net.UnknownHostException) return "No connection to openrouter.ai. Check the network and retry.";
        if (e instanceof java.net.SocketTimeoutException) return "The connection timed out. Retry to reconnect.";
        if (e instanceof javax.net.ssl.SSLException) return "Secure connection failed: " + m;
        return m != null ? m : e.getClass().getSimpleName();
    }

    private void finish(Conversation conv, Message reply, Http[] holder, Friendly.Error error) {
        synchronized (reply) {
            reply.streaming = false;
            if (holder[0] != null && holder[0].isCancelled()) reply.stopped = true;
            if (error != null) {
                reply.error = error.message;
                reply.errorDetail = error.detail;
            }
        }
        store.save(conv);
        if (streaming == reply) {
            streaming = null;
            active = null;
            send.setIcon(IconView.SEND);
            send.setContentDescription("Send");
        }
        if (error != null) setProviderStatus(Ui.STATUS_ERROR, "OpenRouter: error");
        else if (reply.stopped) setProviderStatus(Ui.STATUS_OK, "OpenRouter: stopped");
        else setProviderStatus(Ui.STATUS_OK, "OpenRouter: connected");
        refresh(reply);
    }

    private void stop() {
        Http[] h = active;
        if (h != null && h[0] != null) h[0].cancel();
    }

    private void refresh(Message m) {
        for (MessageView v : views) {
            if (v.message() == m) {
                synchronized (m) {
                    v.update();
                }
            }
        }
    }

    /** Updates the streaming message at most every 150 ms (old tablets render slowly). */
    private void queueUpdate(final Message m) {
        if (updateQueued) return;
        updateQueued = true;
        Bg.later(new Runnable() {
            @Override
            public void run() {
                updateQueued = false;
                refresh(m);
                if (isNearEnd()) scroll.fullScroll(View.FOCUS_DOWN);
            }
        }, 150);
    }

    private boolean isNearEnd() {
        View child = scroll.getChildAt(0);
        return child == null || child.getBottom() - (scroll.getScrollY() + scroll.getHeight()) < Ui.dp(a, 320);
    }

    private final class Listener implements OpenRouter.ChatListener {
        private final Message m;

        Listener(Message m) {
            this.m = m;
        }

        @Override
        public void onText(String delta) {
            synchronized (m) {
                m.text += delta;
            }
            post();
        }

        @Override
        public void onReasoning(String delta) {
            synchronized (m) {
                m.reasoning += delta;
            }
            post();
        }

        @Override
        public void onImage(byte[] data, String mime) {
            try {
                String ext = mime.contains("jpeg") ? "jpg" : mime.contains("webp") ? "webp" : "png";
                File f = store.newMedia(ext);
                java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                try {
                    out.write(data);
                } finally {
                    out.close();
                }
                synchronized (m) {
                    m.images.add(new Attachment("image." + ext, mime, f.getAbsolutePath(), data.length));
                }
                post();
            } catch (IOException ignored) {
                // Not shown.
            }
        }

        @Override
        public void onCitation(String title, String url) {
            synchronized (m) {
                for (String[] c : m.citations) if (c[1].equals(url)) return;
                m.citations.add(new String[]{title, url});
            }
            post();
        }

        @Override
        public void onUsage(long promptTokens, long completionTokens, double cost) {
            synchronized (m) {
                m.promptTokens = promptTokens;
                m.completionTokens = completionTokens;
                m.cost = cost;
            }
        }

        private void post() {
            Bg.ui(new Runnable() {
                @Override
                public void run() {
                    queueUpdate(m);
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        stop();
    }
}
