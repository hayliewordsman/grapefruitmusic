package com.grapefruitfast;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.util.concurrent.*;

/** Film and series posters: fetched in the background, scaled down for the list, kept in memory and on disk. */
final class Posters {
    private static final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(8*1024*1024){
        @Override protected int sizeOf(String k,Bitmap b){return b.getRowBytes()*b.getHeight();}
    };
    private static final ExecutorService work=Executors.newFixedThreadPool(3);
    private static final Handler main=new Handler(Looper.getMainLooper());
    private Posters() {}
    /** Shows url's picture in view (at about width pixels), or leaves it blank if there's none. */
    static void show(final Context c,final ImageView view,final String url,final int width) {
        view.setTag(url);
        if(url==null||url.isEmpty()){view.setImageDrawable(null);return;}
        Bitmap hit=memory.get(url);
        if(hit!=null){view.setImageBitmap(hit);return;}
        view.setImageDrawable(null);
        final File dir=new File(c.getCacheDir(),"posters");
        work.execute(()->{
            Bitmap b=null;
            try{
                dir.mkdirs();
                File f=new File(dir,Integer.toHexString(url.hashCode())+"-"+url.length());
                if(!f.exists()){
                    byte[] data=UserSources.fetch(url,LivePlayer.USER_AGENT);
                    if(data.length>4*1024*1024)return;
                    FileOutputStream out=new FileOutputStream(f);out.write(data);out.close();
                }
                BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
                int sample=1;while(o.outWidth/(sample*2)>=width)sample*=2;
                o=new BitmapFactory.Options();o.inSampleSize=sample;b=BitmapFactory.decodeFile(f.getPath(),o);
            }catch(Exception e){/* no picture: the row keeps its initials */}
            if(b==null)return;
            final Bitmap got=b;memory.put(url,got);
            main.post(()->{if(url.equals(view.getTag()))view.setImageBitmap(got);});
        });
    }
}
