package com.grapefruitlauncher;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/** Four private, procedural icon packs. No activity-name allowlist: every icon gets a plate. */
final class IconPack {
    private final Context context;
    private static final int MAX_PENDING=64;
    private final ThreadPoolExecutor worker=MemoryPolicy.worker("launcher-icons");
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Object lock=new Object();
    private final MemoryPolicy memory;
    private final LruCache<String,Bitmap> cache;
    private final Map<String,Request> pending=new HashMap<>();
    private final ArrayDeque<Request> queue=new ArrayDeque<>();
    private boolean draining,closed;
    private int generation,themeId=-1;
    IconPack(Context c) {
        context=c.getApplicationContext();memory=new MemoryPolicy(context);
        cache=new LruCache<String,Bitmap>(memory.cacheBytes) {
            @Override protected int sizeOf(String key,Bitmap b){return b.getByteCount();}
        };
    }
    void bind(ImageView view,AppCatalog.App app,LauncherTheme theme) {
        int pixels=memory.iconPixels(theme);
        String key=theme.id+":"+pixels+":"+app.id();
        view.setTag(key);
        Bitmap hit=cache.get(key);
        if(hit!=null){view.setImageBitmap(hit);return;}
        view.setImageDrawable(new Plate(theme,null,0));
        synchronized(lock) {
            if(closed)return;
            Request existing=pending.get(key);
            if(existing!=null){existing.watch(view);return;}
            if(pending.size()>=MAX_PENDING) {
                // Retry only while this view still wants the icon. Never retain a screen in a job queue.
                WeakReference<ImageView> weak=new WeakReference<>(view);int epoch=generation;
                main.postDelayed(()->{
                    ImageView target=weak.get();
                    if(epoch==generation && target!=null && key.equals(target.getTag()))bind(target,app,theme);
                },32);
                return;
            }
            Request request=new Request(key,app,theme,pixels,generation);request.watch(view);
            pending.put(key,request);queue.addLast(request);
            if(!draining){draining=true;worker.execute(this::drain);}
        }
    }
    private void drain() {
        while(true) {
            Request request;
            synchronized(lock) {
                request=queue.pollFirst();
                if(closed || request==null){draining=false;return;}
                if(!request.hasTargets()) {
                    if(pending.get(request.key)==request)pending.remove(request.key);
                    continue;
                }
            }
            Bitmap bitmap=null;
            try {
                Drawable original;
                try{original=context.getPackageManager().getActivityIcon(request.app.component);}
                catch(Exception e){original=context.getPackageManager().getDefaultActivityIcon();}
                bitmap=render(request.theme,original,request.pixels);
            }catch(OutOfMemoryError e){cache.evictAll();}
            catch(RuntimeException ignored){ /* Keep the plate if a broken package drawable cannot render. */ }
            synchronized(lock) {
                if(pending.get(request.key)==request)pending.remove(request.key);
                if(!closed && request.generation==generation && bitmap!=null) {
                    cache.put(request.key,bitmap);final Bitmap ready=bitmap;
                    main.post(()->{
                        if(closed || request.generation!=generation)return;
                        for(WeakReference<ImageView> weak:request.views) {
                            ImageView target=weak.get();
                            if(target!=null && request.key.equals(target.getTag()))target.setImageBitmap(ready);
                        }
                    });
                }
            }
        }
    }
    void screenChanged(int selectedTheme) {
        synchronized(lock) {
            generation++;queue.clear();pending.clear();main.removeCallbacksAndMessages(null);
            if(themeId!=selectedTheme){cache.evictAll();themeId=selectedTheme;}
        }
    }
    void invalidate(){synchronized(lock){screenChanged(themeId);cache.evictAll();}}
    void trim(){cache.evictAll();}
    int cachedBytes(){return cache.size();}
    void close() {
        synchronized(lock){closed=true;invalidate();worker.shutdownNow();}
    }
    private static final class Request {
        final String key;
        final AppCatalog.App app;
        final LauncherTheme theme;
        final int pixels,generation;
        final List<WeakReference<ImageView>> views=new ArrayList<>();
        Request(String k,AppCatalog.App a,LauncherTheme t,int size,int epoch){key=k;app=a;theme=t;pixels=size;generation=epoch;}
        void watch(ImageView view) {
            for(int i=views.size()-1;i>=0;i--) {
                ImageView old=views.get(i).get();
                if(old==view)return;
                if(old==null)views.remove(i);
            }
            views.add(new WeakReference<>(view));
        }
        boolean hasTargets() {
            for(WeakReference<ImageView> weak:views) {
                ImageView view=weak.get();if(view!=null && key.equals(view.getTag()))return true;
            }
            return false;
        }
    }
    static Bitmap render(LauncherTheme theme,Drawable original,int size) {
        Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Plate plate=new Plate(theme,original,0);plate.setBounds(0,0,size,size);plate.draw(new Canvas(b));return b;
    }

    /** A circle extended to a gently rounded lower-right corner; Canvas paths work on API 17. */
    static Path teardrop(float left,float top,float size) {
        Path p=new Path();
        p.moveTo(left+size*.5f,top);
        p.cubicTo(left+size*.776f,top,left+size,top+size*.224f,left+size,top+size*.5f);
        p.lineTo(left+size,top+size*.84f);
        p.quadTo(left+size,top+size,left+size*.84f,top+size);
        p.lineTo(left+size*.5f,top+size);
        p.cubicTo(left+size*.224f,top+size,left,top+size*.776f,left,top+size*.5f);
        p.cubicTo(left,top+size*.224f,left+size*.224f,top,left+size*.5f,top);
        p.close();return p;
    }
    static final class Plate extends Drawable {
        private final LauncherTheme theme;
        private final Drawable original;
        private final int glyph; // 0: app/fallback, 1: All Apps, 2: add app.
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int alpha=255;
        Plate(LauncherTheme t,Drawable app,int symbol){theme=t;original=app;glyph=symbol;}
        @Override public void draw(Canvas canvas) {
            Rect r=getBounds();float size=Math.min(r.width(),r.height())-2;
            if(size<=0)return;
            float left=r.exactCenterX()-size/2,top=r.exactCenterY()-size/2;
            Path path=teardrop(left,top,size);
            paint.setStyle(Paint.Style.FILL);paint.setAlpha(alpha);
            paint.setShader(new LinearGradient(left,top,left+size,top+size,
                    glyph==1?theme.accent:theme.control,glyph==1?theme.end:theme.surface,Shader.TileMode.CLAMP));
            canvas.drawPath(path,paint);paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,size/56));
            paint.setColor(theme.accent);paint.setAlpha(alpha*100/255);canvas.drawPath(path,paint);
            paint.setStyle(Paint.Style.FILL);paint.setAlpha(alpha);
            if(original!=null) {
                // Fit the entire source (including legacy rectangular artwork) inside the circle's safe area.
                int w=Math.max(1,original.getIntrinsicWidth()),h=Math.max(1,original.getIntrinsicHeight());
                float scale=size*.60f/Math.max(w,h);
                float cx=left+size*.49f,cy=top+size*.49f;
                Rect old=new Rect(original.getBounds());
                original.setBounds(Math.round(cx-w*scale/2),Math.round(cy-h*scale/2),Math.round(cx+w*scale/2),Math.round(cy+h*scale/2));
                int save=canvas.save();canvas.clipPath(path);
                try {original.draw(canvas);} catch(RuntimeException ignored) {drawGlyph(canvas,left,top,size,2);}
                canvas.restoreToCount(save);original.setBounds(old);
            } else drawGlyph(canvas,left,top,size,glyph==0?2:glyph);
        }
        private void drawGlyph(Canvas c,float x,float y,float s,int symbol) {
            paint.setColor(symbol==1?theme.onAccent:theme.accent);paint.setAlpha(alpha);
            if(symbol==1) {
                for(int row=0;row<3;row++)for(int col=0;col<3;col++) {
                    float l=x+s*(.29f+col*.16f),t=y+s*(.29f+row*.16f);
                    c.drawRoundRect(new RectF(l,t,l+s*.10f,t+s*.10f),s*.025f,s*.025f,paint);
                }
            } else {
                c.drawRoundRect(new RectF(x+s*.28f,y+s*.46f,x+s*.70f,y+s*.52f),s*.03f,s*.03f,paint);
                c.drawRoundRect(new RectF(x+s*.46f,y+s*.28f,x+s*.52f,y+s*.70f),s*.03f,s*.03f,paint);
            }
        }
        @Override public void setAlpha(int a){alpha=a;invalidateSelf();}
        @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
