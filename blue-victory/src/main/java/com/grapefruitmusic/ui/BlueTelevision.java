package com.grapefruitmusic.ui;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.R;
import com.grapefruitmusic.util.Notice;
import java.util.*;

/** Blue-only integration: one TV tab, one active media source and rows in the main Settings page. */
final class BlueTelevision extends TvFeature {
    final TvRepository repo;
    final TvSession session;
    final TvStyle style;
    private String section="Channels";
    private TvMini mini;
    private boolean destroyed;
    private final Runnable updated=this::changed;
    BlueTelevision(MainActivity host){
        super(host);style=new TvStyle(host,0);repo=TvRepository.get(host);session=TvSession.get(host);
        repo.listen(updated);session.listeners.add(updated);TvDownloads.listeners.add(updated);
    }
    List<String> sections(){return repo.sections();}
    String selected(){return sections().contains(section)?section:"Channels";}
    void selected(String value){section=sections().contains(value)?value:"Channels";}
    BaseFragment page(String name){selected(name);return TvBrowsePage.create(section);}
    BaseFragment watchPage(){return new TvWatchPage();}
    View mini(){if(mini==null)mini=new TvMini(this);mini.update();return mini;}
    boolean active(){return session.active();}
    boolean watchVisible(){return session.watchVisible;}
    void foreground(boolean visible){session.foreground(visible);}
    void destroy(){destroyed=true;repo.unlisten(updated);session.listeners.remove(updated);TvDownloads.listeners.remove(updated);session.attach(null);}
    private void changed(){
        if(destroyed||host.isFinishing())return;
        host.onPlayerChanged();
        android.app.Fragment showing=host.getFragmentManager().findFragmentById(R.id.content);
        if(showing instanceof SettingsFragment&&showing.isResumed())((SettingsFragment)showing).refreshTvSettings();
    }
    void play(Channel channel){if(session.play(channel))host.showTvNowPlaying();}
    void download(Channel channel){try{TvDownloads.enqueue(host,channel);Notice.show(host,"Added to TV Downloads");repo.changed();}catch(Exception e){Notice.showLong(host,e.getMessage());}}
    interface Choice { void picked(int value); }
    void choice(String title,String[] options,int selected,Choice picked){
        Ui.dialog(host).setTitle(title).setSingleChoiceItems(options,selected,(d,n)->{d.dismiss();picked.picked(n);}).setNegativeButton("Cancel",null).show();
    }
    interface FormAction{void save(String[] values) throws Exception;}
    void form(String title,String[] labels,String[] values,boolean[] secret,FormAction save){
        LinearLayout body=style.column();body.setPadding(style.dp(8),0,style.dp(8),0);
        EditText[] fields=new EditText[labels.length];
        for(int n=0;n<labels.length;n++){
            TextView label=style.label(labels[n],13,style.secondary);label.setPadding(style.dp(8),style.dp(10),0,style.dp(4));body.addView(label);
            EditText input=new BlueEditText(host);input.setTextSize(16);input.setTypeface(Fonts.regular());input.setSingleLine(true);input.setTextColor(style.text);input.setText(values[n]);
            if(secret[n])input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
            fields[n]=input;body.addView(input,new LinearLayout.LayoutParams(-1,style.dp(48)));
        }
        ScrollView scroll=new ScrollView(host);scroll.addView(body);
        GrapefruitDialog dialog=Ui.dialog(host).setTitle(title).setView(scroll).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
            String[] got=new String[fields.length];for(int n=0;n<fields.length;n++)got[n]=fields[n].getText().toString();
            try{save.save(got);dialog.dismiss();}catch(Exception e){Notice.showLong(host,e.getMessage()==null?"Check these values and try again.":e.getMessage());}
        });
    }
    private void addProvider(String kind,UserSources.Source old){
        boolean login=UserSources.LOGIN.equals(kind);
        String[] labels=login?new String[]{"Name","Server address","Username","Password"}:new String[]{"Name","M3U playlist address","XMLTV guide address (optional)"};
        String[] values=login?new String[]{old==null?"My TV":old.name,old==null?"https://":old.address,old==null?"":old.username,old==null?"":old.password}:new String[]{old==null?"My TV":old.name,old==null?"https://":old.address,old==null?"":old.guide};
        form(old==null?(login?"Add Xtream Provider":"Add M3U Playlist"):"Edit TV Connection",labels,values,login?new boolean[]{false,false,false,true}:new boolean[]{false,false,false},v->{
            if(v[0].trim().isEmpty()||v[1].trim().isEmpty()||(login&&(v[2].trim().isEmpty()||v[3].isEmpty())))throw new IllegalArgumentException("Complete the connection fields.");
            if(!v[1].startsWith("http://")&&!v[1].startsWith("https://"))throw new IllegalArgumentException("Use an http:// or https:// address.");
            UserSources.Source saved;
            if(old==null)saved=repo.sources.add(kind,v[0].trim(),v[1].trim(),login?v[2].trim():"",login?v[3]:"");
            else {TvDownloads.pauseSource(host,old.id);saved=new UserSources.Source(old.id,kind,v[0].trim(),v[1].trim(),login?v[2].trim():"",login?v[3]:"");saved.streams=old.streams;saved.streamOverride=old.streamOverride;saved.guide=old.guide;repo.sources.update(saved);}
            if(!login){saved.guide=v[2].trim();repo.sources.update(saved);}repo.refresh(saved);repo.changed();
        });
    }
    private void provider(UserSources.Source source){
        String[] labels={"Refresh","Edit Connection","Guide Address","Stream Limit","Remove Provider"};
        Ui.dialog(host).setTitle(source.name).setItems(labels,(d,n)->{
            if(n==0)repo.refresh(source);
            if(n==1)addProvider(source.kind,source);
            if(n==2)form("XMLTV Guide",new String[]{"Guide address (optional)"},new String[]{source.guide},new boolean[]{false},v->{source.guide=v[0].trim();repo.sources.update(source);repo.guide.refresh();repo.changed();});
            if(n==3)form("Provider Stream Limit",new String[]{"Maximum simultaneous streams"},new String[]{String.valueOf(source.streams)},new boolean[]{false},v->{int max=Integer.parseInt(v[0].trim());if(max<1||max>20)throw new IllegalArgumentException("Choose a limit between 1 and 20.");source.streams=max;source.streamOverride=true;repo.sources.update(source);repo.changed();});
            if(n==4)Ui.dialog(host).setTitle("Remove "+source.name+"?").setMessage("The connection will be removed. Finished downloads stay on this device.").setPositiveButton("Remove",(dd,w)->{if(session.current!=null&&Guide.sourceId(session.current).equals(source.id))session.pause(true);repo.remove(source.id);}).setNegativeButton("Cancel",null).show();
        }).show();
    }
    void addSettings(Grouped g){
        g.section("TV Providers");
        for(UserSources.Source source:repo.sources.list())g.value(source.name,UserSources.LOGIN.equals(source.kind)?"Xtream":"M3U",v->provider(source));
        g.value("Add M3U Playlist","",v->addProvider(UserSources.M3U,null));
        g.value("Add Xtream Provider","",v->addProvider(UserSources.LOGIN,null));
        g.footer("Your connected services supply their channels, programme guide, films and series. Connections are stored privately in Blue Grapefruit.");
        g.section("TV Playback");
        String[] qualities={"Auto","Data Saver","Compatibility"};int quality=Math.max(0,Math.min(2,repo.prefs.getInt("quality",0)));
        g.value("TV Playback Quality",qualities[quality],v->choice("TV Playback Quality",qualities,quality,n->{repo.prefs.edit().putInt("quality",n).apply();session.settingsChanged();}));
        g.toggle("TV Captions",repo.prefs.getBoolean("captions",false),(t,on)->{repo.prefs.edit().putBoolean("captions",on).apply();session.settingsChanged();});
        g.value("TV Caption Language",repo.prefs.getString("captionLanguage","en"),v->form("TV Caption Language",new String[]{"Language code"},new String[]{repo.prefs.getString("captionLanguage","en")},new boolean[]{false},values->{repo.prefs.edit().putString("captionLanguage",values[0].trim()).apply();session.settingsChanged();}));
        g.toggle("Background TV Audio",repo.prefs.getBoolean("backgroundAudio",true),(t,on)->{repo.prefs.edit().putBoolean("backgroundAudio",on).apply();session.settingsChanged();});
        g.footer("When Blue Grapefruit is in the background, TV keeps playing as audio only. Switching to music pauses TV, and switching to TV pauses music.");
        g.section("TV Guide");
        g.value("Refresh TV Guide","",v->{repo.guide.refresh();repo.refresh(null);repo.changed();Notice.show(host,"TV guide refresh requested");});
        g.footer("Programme information comes from your provider. When none is supplied, the guide shows unavailable-information blocks.");
        if(repo.sections().contains("Downloads")){
            g.section("TV Downloads");g.value("Manage TV Downloads",String.valueOf(TvDownloads.all(host).size()),v->host.showTvSection("Downloads"));
            g.footer("The Wi-Fi Only Downloads setting above applies to both music and TV. Films and episodes can be downloaded from On Demand.");
        }
        g.section("Victory Edition");
        g.value("Victory Roll Call","",v->host.push(new AcknowledgementsFragment()));
    }
}
