package com.grapefruitmusic.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.grapefruitmusic.R;
import com.grapefruitmusic.source.Folders;
import com.grapefruitmusic.source.Sources;
import com.grapefruitmusic.ui.widget.Icons;
import com.grapefruitmusic.util.Bg;
import com.grapefruitmusic.util.Prefs;

import java.util.Map;
import java.util.Set;

/** Settings › Excluded Folders: hide folders from On This Phone. */
public final class ExcludedFoldersFragment extends BaseFragment {
    private ScrollView page;
    private Map<String, Integer> folders;
    private String error;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        page = Grouped.page(getActivity());
        build();
        load();
        return page;
    }

    private void load() {
        final Context app = getActivity().getApplicationContext();
        bg(new Bg.Work<Map<String, Integer>>() {
            @Override
            public Map<String, Integer> run() throws Exception {
                return Sources.get(app).local().musicFolders();
            }
        }, new Bg.Done<Map<String, Integer>>() {
            @Override
            public void done(Map<String, Integer> result, Exception e) {
                folders = result;
                error = e != null ? e.getMessage() : null;
                build();
            }
        });
    }

    private static String storageRoot() {
        return Folders.normalize(Environment.getExternalStorageDirectory().getPath());
    }

    private static boolean hidden(String folder, Set<String> excluded) {
        for (String e : excluded) {
            if (folder.equals(e) || Folders.isInside(folder, e)) return true;
        }
        return false;
    }

    private void build() {
        Context c = getActivity();
        Grouped g = Grouped.on(page);
        g.add(Ui.header(c, "Settings", "Excluded Folders", goBack()));
        Set<String> excluded = Prefs.excludedFolders();
        String root = storageRoot();

        g.section("Excluded");
        for (final String folder : excluded) {
            Integer count = folders != null ? folders.get(folder) : null;
            folderRow(g, folder, count, true, root);
        }
        g.action("Add Folder…", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addByPath();
            }
        });
        g.footer(excluded.isEmpty()
                ? "Songs in excluded folders, and in folders inside them, don't appear under On This Phone."
                : "Tap a folder to show its songs again.");

        g.section("Folders with Music");
        int shown = 0;
        if (folders != null) {
            for (Map.Entry<String, Integer> f : folders.entrySet()) {
                if (hidden(f.getKey(), excluded)) continue;
                folderRow(g, f.getKey(), f.getValue(), false, root);
                shown++;
            }
        }
        if (folders == null && error == null) g.footer("Looking for music folders…");
        else if (error != null) g.footer(error);
        else if (shown == 0) g.footer("No other folders with music on this phone.");
        else g.footer("Tap a folder to hide it.");
    }

    private void folderRow(Grouped g, final String folder, Integer count, final boolean excluded, String root) {
        Context c = g.c;
        LinearLayout row = g.row(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Set<String> set = Prefs.excludedFolders();
                if (excluded) set.remove(folder);
                else set.add(folder);
                save(set);
            }
        });
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(c, 17, R.attr.gfTextPrimary, false);
        name.setText(Folders.name(folder));
        TextView path = Ui.text(c, 13, R.attr.gfTextSecondary, false);
        String detail = Folders.relative(folder, root);
        if (count != null) detail += "  ·  " + count + (count == 1 ? " song" : " songs");
        path.setText(detail);
        texts.addView(name);
        texts.addView(path);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (excluded) {
            TextView show = Ui.text(c, 16, R.attr.gfAccent, false);
            show.setText("Show");
            show.setPadding(Ui.dp(c, 8), 0, 0, 0);
            row.addView(show);
        } else {
            row.addView(Ui.icon(c, Icons.CHECK, Ui.accent(c), 20));
        }
        g.addRow(row, true);
    }

    private void save(Set<String> set) {
        Prefs.setExcludedFolders(set);
        Sources.get(getActivity()).notifyChanged();
        build();
    }

    private void addByPath() {
        final Context c = getActivity();
        final EditText field = new EditText(c);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        String root = storageRoot();
        field.setText(root != null ? root + "/" : "/");
        field.setSelection(field.getText().length());
        LinearLayout box = new LinearLayout(c);
        int pad = Ui.dp(c, 16);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        Ui.dialog(c)
                .setTitle("Exclude a Folder")
                .setMessage("Enter the folder's full path. Folders inside it are excluded too.")
                .setView(box)
                .setPositiveButton("Exclude", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String folder = Folders.normalize(field.getText().toString());
                        if (folder == null) {
                            Toast.makeText(c, "Enter a full path that starts with /", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Set<String> set = Prefs.excludedFolders();
                        set.add(folder);
                        save(set);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
