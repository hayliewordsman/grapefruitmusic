package com.grapefruitlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;

/** Private app storage holds all four prepared packs; no installed-app artwork is retained in RAM. */
final class IconDiskCache {
    interface Progress {void ready(int count,int total);}
    private static final Object FILE_LOCK=new Object(); // Also protects overlapping activity instances.
    private static final String FORMAT="plates-1";
    private final Context context;
    private final File directory;
    private final SharedPreferences revisions;
    private final String configuration;
    private final Object lock=new Object();
    private final Map<String,Entry> entries=new HashMap<>();
    private long changes;

    IconDiskCache(Context c) {
        context=c.getApplicationContext();directory=new File(context.getFilesDir(),"launcher-icons");
        revisions=context.getSharedPreferences("icon-cache",Context.MODE_PRIVATE);
        configuration=context.getResources().getDisplayMetrics().densityDpi+":"+
                (context.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)+":"+Locale.getDefault().toString();
    }
    String version(AppCatalog.App app) {
        return app.sourceStamp+":"+revisions.getLong("all",0)+":"+revisions.getLong("pkg."+app.component.getPackageName(),0);
    }
    void packageChanged(String pkg) {
        synchronized(lock) {
            String key="pkg."+pkg;revisions.edit().putLong(key,revisions.getLong(key,0)+1).apply();changes++;
            java.util.Iterator<Entry> iterator=entries.values().iterator();
            while(iterator.hasNext())if(iterator.next().pkg.equals(pkg))iterator.remove();
        }
    }
    void invalidate() {
        synchronized(lock){revisions.edit().putLong("all",revisions.getLong("all",0)+1).apply();changes++;entries.clear();}
    }
    /** Called on the catalog worker before publishing the new list to the UI. */
    int prepare(List<AppCatalog.App> apps,int pixels,Progress progress)throws InterruptedException {
        long epoch;synchronized(lock){epoch=changes;}
        Map<String,Entry> prepared=new HashMap<>();Set<String> wanted=new HashSet<>();int failed=0,done=0;
        LauncherTheme[] themes=new LauncherTheme[LauncherTheme.NAMES.length];
        for(int i=0;i<themes.length;i++)themes[i]=new LauncherTheme(context,i);
        for(AppCatalog.App app:apps) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            Entry entry=entry(app,pixels);Drawable original=null;boolean loaded=false,appFailed=false;
            for(int theme=0;theme<themes.length;theme++) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                File file=file(entry,theme);wanted.add(file.getName());
                if(validFile(file,pixels))continue;
                Bitmap bitmap=null;
                try {
                    if(!loaded){original=original(app);loaded=true;}
                    bitmap=render(themes[theme],original,pixels);write(file,bitmap);
                }catch(IOException|RuntimeException e){appFailed=true;}
                finally{if(bitmap!=null)bitmap.recycle();}
            }
            if(appFailed)failed++;prepared.put(app.id(),entry);
            if(progress!=null)progress.ready(++done,apps.size());
        }
        synchronized(lock) {
            if(epoch!=changes)return failed; // A package changed mid-pass; the catalog schedules a fresh pass.
            entries.clear();entries.putAll(prepared);
            synchronized(FILE_LOCK) {
                File[] files=directory.listFiles();
                if(files!=null)for(File file:files) {
                    String name=file.getName();
                    if((name.endsWith(".png") || name.endsWith(".png.bak")) && !wanted.contains(name))file.delete();
                }
            }
        }
        return failed;
    }
    /** Prepared PNGs are small: read before changing the cell, so a cache hit never paints a placeholder. */
    Bitmap read(AppCatalog.App app,int theme,int pixels) {
        Entry entry;synchronized(lock){entry=entries.get(app.id());}
        if(entry==null || entry.pixels!=pixels || !entry.version.equals(version(app)))return null;
        return readFile(file(entry,theme));
    }
    /** Fallback for missing/corrupt files, or an app bound before catalog preparation finishes. */
    Bitmap load(AppCatalog.App app,LauncherTheme theme,int pixels) {
        Entry entry=entry(app,pixels);File file=file(entry,theme.id);Bitmap bitmap=readFile(file);
        if(bitmap==null) {
            bitmap=render(theme,original(app),pixels);
            if(!Thread.currentThread().isInterrupted() && entry.version.equals(version(app)))
                try{write(file,bitmap);}catch(IOException|RuntimeException ignored){}
        }
        synchronized(lock) {
            Entry current=entries.get(app.id());
            if(entry.version.equals(version(app)) && (current==null || current.version.equals(entry.version)))entries.put(app.id(),entry);
        }
        return bitmap;
    }
    private Entry entry(AppCatalog.App app,int pixels) {
        String version=version(app);
        String digest=hash(FORMAT+"\n"+configuration+"\n"+pixels+"\n"+app.id()+"\n"+version);
        return new Entry(app.component.getPackageName(),version,pixels,digest);
    }
    private File file(Entry entry,int theme){return new File(directory,entry.digest+"-"+theme+".png");}
    private Drawable original(AppCatalog.App app) {
        try{return context.getPackageManager().getActivityIcon(app.component);}
        catch(Exception e){return context.getPackageManager().getDefaultActivityIcon();}
        catch(OutOfMemoryError e){return null;}
    }
    private Bitmap render(LauncherTheme theme,Drawable original,int pixels) {
        try{return IconPack.render(theme,original,pixels);}
        catch(OutOfMemoryError|RuntimeException e){return IconPack.render(theme,null,pixels);}
    }
    private static boolean validFile(File file,int pixels) {
        synchronized(FILE_LOCK) {
            if(!file.isFile())return false;
            try(FileInputStream in=new AtomicFile(file).openRead()) {
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                BitmapFactory.decodeStream(in,null,options);return options.outWidth==pixels && options.outHeight==pixels;
            }catch(IOException|RuntimeException e){return false;}
        }
    }
    private static Bitmap readFile(File file) {
        synchronized(FILE_LOCK) {
            if(!file.isFile())return null;
            try(FileInputStream in=new AtomicFile(file).openRead()) {
                BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                Bitmap bitmap=BitmapFactory.decodeStream(in,null,options);
                if(bitmap==null)file.delete();return bitmap;
            }catch(IOException|RuntimeException|OutOfMemoryError e){return null;}
        }
    }
    private static void write(File file,Bitmap bitmap)throws IOException {
        synchronized(FILE_LOCK) {
            File parent=file.getParentFile();
            if(parent==null || (!parent.isDirectory() && !parent.mkdirs()))throw new IOException("Icon directory unavailable");
            AtomicFile atomic=new AtomicFile(file);FileOutputStream out=atomic.startWrite();boolean finished=false;
            try {
                if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Icon encoding failed");
                atomic.finishWrite(out);finished=true;
            }finally{if(!finished)atomic.failWrite(out);}
        }
    }
    private static String hash(String value) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
            StringBuilder hex=new StringBuilder();for(byte b:bytes)hex.append(Character.forDigit((b>>4)&15,16)).append(Character.forDigit(b&15,16));
            return hex.toString();
        }catch(java.security.NoSuchAlgorithmException|java.io.UnsupportedEncodingException e){throw new IllegalStateException(e);}
    }
    private static final class Entry {
        final String pkg,version,digest;final int pixels;
        Entry(String pkg,String version,int pixels,String digest){this.pkg=pkg;this.version=version;this.pixels=pixels;this.digest=digest;}
    }
}
