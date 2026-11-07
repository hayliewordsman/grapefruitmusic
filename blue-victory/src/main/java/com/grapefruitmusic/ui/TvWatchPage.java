package com.grapefruitmusic.ui;

import android.app.Dialog;
import android.graphics.Color;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** The TV watch layout and transport controls, sharing one player with the picture mini-player. */
public final class TvWatchPage extends BaseFragment {
    private BlueTelevision tv;
    private TvStyle b;
    private TvVideoView video,fullVideo;
    private TextView title,state,quality,now,pause,captions,elapsed;
    private TextView record;
    private List<TvRecordings.Item> planned=Collections.emptyList();
    private final Runnable recordingChanged=()->{if(getActivity()!=null)planned=TvRecordings.all(getActivity());update();};
    private SeekBar seek;
    private Dialog fullscreen;
    private boolean seeking;
    private final Handler timer=new Handler(Looper.getMainLooper());
    private final Runnable changed=this::update;
    private final Runnable tick=new Runnable(){public void run(){if(isResumed()){update();timer.postDelayed(this,1000);}}};
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved){
        tv=(BlueTelevision)main().television();b=tv.style;TvSession s=tv.session;
        ListView scroll=new ListView(getActivity());scroll.setDivider(null);scroll.setClipToPadding(false);
        LinearLayout page=b.column();page.setPadding(b.dp(14),b.dp(12),b.dp(14),b.dp(20));scroll.addHeaderView(page,null,false);
        LinearLayout header=b.row();header.addView(b.round("‹",goBack()),new LinearLayout.LayoutParams(b.dp(44),b.dp(44)));
        title=b.text(s.current==null?"TV":s.current.name,18,true);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(b.dp(12),0,b.dp(8),0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));page.addView(header);
        video=new TvVideoView(getActivity(),b);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);vp.topMargin=b.dp(12);page.addView(video,vp);
        LinearLayout info=b.row();info.setPadding(b.dp(4),b.dp(12),b.dp(4),b.dp(10));
        state=b.text("",12,true);info.addView(state);quality=b.label("",12,b.secondary);quality.setSingleLine();quality.setEllipsize(TextUtils.TruncateAt.END);quality.setPadding(b.dp(10),0,0,0);info.addView(quality,new LinearLayout.LayoutParams(0,-2,1));page.addView(info);
        now=b.label("",13,b.text);now.setMaxLines(3);now.setPadding(b.dp(4),0,b.dp(4),b.dp(8));page.addView(now);
        LinearLayout bar=b.row();seek=new SeekBar(getActivity());seek.setMax(1000);seek.getProgressDrawable().setColorFilter(b.accent,android.graphics.PorterDuff.Mode.SRC_IN);if(Build.VERSION.SDK_INT>=16&&seek.getThumb()!=null)seek.getThumb().setColorFilter(b.accent,android.graphics.PorterDuff.Mode.SRC_IN);
        elapsed=b.label("0:00",12,b.secondary);bar.addView(seek,new LinearLayout.LayoutParams(0,b.dp(40),1));bar.addView(elapsed);bar.setVisibility(s.current!=null&&!s.current.live()?View.VISIBLE:View.GONE);page.addView(bar);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){seeking=true;}public void onProgressChanged(SeekBar bar,int value,boolean user){if(user)elapsed.setText(clock(s.duration()*value/1000)+" / "+clock(s.duration()));}public void onStopTrackingTouch(SeekBar bar){s.seekTo(s.duration()*bar.getProgress()/1000);seeking=false;}});
        TvButtonRow actions=new TvButtonRow(getActivity());actions.setPadding(0,b.dp(8),0,b.dp(8));page.addView(actions,new LinearLayout.LayoutParams(-1,-2));
        pause=action(actions,s.playing()?"Pause":"Play",()->s.toggle());
        if(s.current!=null&&s.current.live()){action(actions,"Previous",()->changeChannel(-1));action(actions,"Next",()->changeChannel(1));}
        else {action(actions,"−10 s",()->s.seekBy(-10000));action(actions,"+30 s",()->s.seekBy(30000));}
        action(actions,"Retry",()->{if(s.current!=null)s.play(s.current);});
        action(actions,"Quality",()->{String[] labels={"Auto","Data Saver","Compatibility"};tv.choice("TV Playback Quality",labels,tv.repo.prefs.getInt("quality",0),n->{tv.repo.prefs.edit().putInt("quality",n).apply();s.settingsChanged();});});
        captions=action(actions,"Captions",()->{tv.repo.prefs.edit().putBoolean("captions",!tv.repo.prefs.getBoolean("captions",false)).apply();s.settingsChanged();});
        action(actions,"Full screen",this::fullScreen);
        if(s.current!=null){action(actions,tv.repo.favourite(s.current)?"Remove Favourite":"Favourite",()->{tv.repo.toggleFavourite(s.current);rebuild();});if(s.current.live())record=action(actions,"Record",()->{long time=System.currentTimeMillis();TvRecordings.Item item=TvRecordings.scheduledAt(planned,s.current.id,time,time+1);if(item!=null&&item.active())TvRecorderService.stop(item.id);else tv.recordNow(s.current);});}
        if(s.current!=null&&s.current.onDemand()&&!s.current.local())action(actions,"Download",()->tv.download(s.current));
        if(s.current!=null){TextView description=b.label(s.current.meta==null?s.current.description:s.current.meta,13,b.secondary);description.setPadding(b.dp(4),b.dp(14),b.dp(4),b.dp(14));page.addView(description);}
        if(s.current!=null&&s.current.live()){
            TextView lineup=b.text("Channels",18,true);lineup.setPadding(b.dp(4),b.dp(12),0,b.dp(8));page.addView(lineup);
        }
        final java.util.List<Channel> channels=s.current!=null&&s.current.live()?tv.repo.channels:java.util.Collections.<Channel>emptyList();
        scroll.setAdapter(new BaseAdapter(){public int getCount(){return channels.size();}public Channel getItem(int i){return channels.get(i);}public long getItemId(int i){return i;}public View getView(int i,View old,ViewGroup parent){Channel c=getItem(i);FrameLayout item=new FrameLayout(getActivity());item.setPadding(b.dp(14),b.dp(4),b.dp(14),b.dp(4));TextView row=b.button(c.name,tv.session.current!=null&&tv.session.current.id.equals(c.id),v->{tv.session.play(c);rebuild();});row.setGravity(Gravity.CENTER_VERTICAL);row.setMaxLines(2);row.setEllipsize(TextUtils.TruncateAt.END);item.addView(row,new FrameLayout.LayoutParams(-1,-2));return item;}});
        update();return scroll;
    }
    private TextView action(TvButtonRow row,String label,Runnable callback){TextView button=b.button(label,false,v->callback.run());row.addView(button);return button;}
    private void changeChannel(int delta){tv.session.next(delta);rebuild();}
    private void rebuild(){if(isAdded())getFragmentManager().beginTransaction().detach(this).attach(this).commit();}
    private void update(){
        if(tv==null||video==null)return;TvSession s=tv.session;
        title.setText(s.current==null?"TV":s.current.name);state.setText(s.playing()&&s.current!=null&&s.current.live()?"● LIVE":s.status);quality.setText(s.format);
        pause.setText(s.playing()?"Pause":"Play");captions.setVisibility(s.captionsAvailable?View.VISIBLE:View.GONE);captions.setSelected(tv.repo.prefs.getBoolean("captions",false));
        if(record!=null&&s.current!=null){long time=System.currentTimeMillis();TvRecordings.Item item=TvRecordings.scheduledAt(planned,s.current.id,time,time+1);record.setText(item!=null&&item.active()?"Stop Recording":"Record");}
        video.update(s);if(fullVideo!=null)fullVideo.update(s);
        if(s.watchVisible&&isResumed())s.attach(fullVideo==null?video.texture:fullVideo.texture);
        if(s.current!=null&&s.current.live()){
            Guide.Program p=tv.repo.guide.now(s.current,System.currentTimeMillis());Guide.Program next=tv.repo.guide.now(s.current,p.end+1);
            now.setText("Now: "+p.title+"\nNext: "+next.title);
        }else now.setText("");
        if(!seeking){long duration=s.duration();seek.setEnabled(duration>0);seek.setProgress(duration>0?(int)(s.position()*1000/duration):0);elapsed.setText(clock(s.position())+" / "+clock(duration));}
        if(getActivity()!=null){if(s.playing())getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    }
    private void fullScreen(){
        if(fullscreen!=null)return;
        fullscreen=new Dialog(getActivity(),android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout stage=new FrameLayout(getActivity());stage.setBackgroundColor(Color.BLACK);
        fullVideo=new TvVideoView(getActivity(),b);stage.addView(fullVideo,new FrameLayout.LayoutParams(-1,-1));
        TvButtonRow controls=new TvButtonRow(getActivity());controls.setPadding(b.dp(8),b.dp(8),b.dp(8),b.dp(8));controls.setBackground(b.shape(0xd9102440,22,true));
        action(controls,"Play / Pause",()->tv.session.toggle());action(controls,"Exit full screen",()->fullscreen.dismiss());
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);cp.leftMargin=cp.rightMargin=b.dp(16);cp.bottomMargin=b.dp(20);stage.addView(controls,cp);
        fullVideo.setOnClickListener(v->controls.setVisibility(controls.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        fullscreen.setContentView(stage);fullscreen.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fullscreen.setOnDismissListener(d->{fullVideo=null;fullscreen=null;update();});fullscreen.show();fullscreen.getWindow().setLayout(-1,-1);update();
    }
    private static String clock(long ms){long seconds=Math.max(0,ms)/1000;return seconds>=3600?String.format(Locale.US,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60):String.format(Locale.US,"%d:%02d",seconds/60,seconds%60);}
    @Override public void onResume(){super.onResume();tv.session.watchVisible=true;tv.session.listeners.add(changed);tv.repo.listen(changed);TvRecordings.listeners.add(recordingChanged);recordingChanged.run();tv.session.changed();timer.post(tick);}
    @Override public void onPause(){timer.removeCallbacks(tick);tv.session.listeners.remove(changed);tv.repo.unlisten(changed);TvRecordings.listeners.remove(recordingChanged);tv.session.watchVisible=false;if(fullscreen!=null)fullscreen.dismiss();if(getActivity()!=null)getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);tv.session.changed();super.onPause();}
    @Override public void onDestroyView(){video=null;fullVideo=null;record=null;super.onDestroyView();}
    @Override boolean onBackPressed(){if(fullscreen!=null){fullscreen.dismiss();return true;}return false;}
}
