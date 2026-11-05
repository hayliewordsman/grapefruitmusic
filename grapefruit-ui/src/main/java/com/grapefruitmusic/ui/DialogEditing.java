package com.grapefruitmusic.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

/** Avoid the pre-Marshmallow dialog selection ActionMode crash; keep explicit editing actions. */
public final class DialogEditing {
    private DialogEditing() {}
    public static void prepare(EditText field) {
        if (Build.VERSION.SDK_INT >= 23) return;
        field.setLongClickable(false);
        field.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            public boolean onCreateActionMode(ActionMode m, Menu menu) { return false; }
            public boolean onPrepareActionMode(ActionMode m, Menu menu) { return false; }
            public boolean onActionItemClicked(ActionMode m, MenuItem item) { return false; }
            public void onDestroyActionMode(ActionMode m) {}
        });
    }
    public static void selectAll(EditText f) { f.requestFocus(); f.setSelection(0,f.length()); }
    public static void copy(EditText f, boolean cut) {
        int a=Math.max(0,Math.min(f.getSelectionStart(),f.getSelectionEnd()));
        int b=Math.max(a,Math.max(f.getSelectionStart(),f.getSelectionEnd()));
        if(a==b)return;
        ClipboardManager cm=(ClipboardManager)f.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if(cm==null)return;
        cm.setPrimaryClip(ClipData.newPlainText("Text",f.getText().subSequence(a,b)));
        if(cut)f.getText().delete(a,b);
    }
    public static void paste(EditText f) {
        ClipboardManager cm=(ClipboardManager)f.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if(cm==null||!cm.hasPrimaryClip()||cm.getPrimaryClip().getItemCount()==0)return;
        CharSequence text=cm.getPrimaryClip().getItemAt(0).coerceToText(f.getContext());
        if(text==null)return;
        int a=Math.max(0,Math.min(f.getSelectionStart(),f.getSelectionEnd()));
        int b=Math.max(a,Math.max(f.getSelectionStart(),f.getSelectionEnd()));
        Editable editable=f.getText();
        editable.replace(a,b,text);f.setSelection(Math.min(editable.length(),a+text.length()));
    }
}
