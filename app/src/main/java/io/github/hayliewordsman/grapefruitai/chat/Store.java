package io.github.hayliewordsman.grapefruitai.chat;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.github.hayliewordsman.grapefruitai.net.Http;

/**
 * Conversation history in the app's private files directory: one JSON file per conversation,
 * attachments and generated images beside it. Nothing is synced or backed up.
 */
public final class Store {
    private final File dir;
    private final File files;

    public Store(Context c) {
        dir = new File(c.getFilesDir(), "conversations");
        files = new File(c.getFilesDir(), "media");
        dir.mkdirs();
        files.mkdirs();
    }

    public synchronized void save(Conversation c) {
        try {
            c.updated = System.currentTimeMillis();
            File tmp = new File(dir, c.id + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                out.write(c.toJson().getBytes("UTF-8"));
                out.getFD().sync();
            } finally {
                out.close();
            }
            if (!tmp.renameTo(new File(dir, c.id + ".json"))) throw new IOException("rename failed");
        } catch (Exception e) {
            android.util.Log.w("GrapefruitAI", "Could not save conversation: " + e.getClass().getSimpleName());
        }
    }

    public synchronized List<Conversation> list(String mode) {
        List<Conversation> out = new ArrayList<Conversation>();
        File[] all = dir.listFiles();
        if (all == null) return out;
        for (File f : all) {
            if (!f.getName().endsWith(".json")) continue;
            try {
                Conversation c = Conversation.fromJson(read(f));
                if (mode == null || mode.equals(c.mode)) out.add(c);
            } catch (Exception ignored) {
                // Damaged file: skipped.
            }
        }
        Collections.sort(out, new Comparator<Conversation>() {
            @Override
            public int compare(Conversation a, Conversation b) {
                return a.updated < b.updated ? 1 : a.updated > b.updated ? -1 : 0;
            }
        });
        return out;
    }

    public synchronized void delete(Conversation c) {
        for (Message m : c.messages) {
            for (Attachment a : m.attachments) deleteMedia(a);
            for (Attachment a : m.images) deleteMedia(a);
        }
        new File(dir, c.id + ".json").delete();
    }

    private void deleteMedia(Attachment a) {
        File f = new File(a.path);
        try {
            if (f.getCanonicalPath().startsWith(files.getCanonicalPath())) f.delete();
        } catch (IOException ignored) {
            // Left in place.
        }
    }

    /** A new private file for media, named by its extension. */
    public File newMedia(String extension) {
        return new File(files, java.util.UUID.randomUUID().toString() + "." + extension);
    }

    static String read(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            return new String(Http.readAll(in, 64 * 1024 * 1024), "UTF-8");
        } finally {
            in.close();
        }
    }
}
