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
        TvRecordings.listener=updated;com.grapefruitmusic.util.Bg.execute(()->TvRecordings.wake(host.getApplicationContext()));
    }
    List<String> sections(){return repo.sections();}
    String selected(){return sections().contains(section)?section:"Channels";}
    void selected(String value){section=sections().contains(value)?value:"Channels";}
    BaseFragment page(String name){selected(name);return "Recordings".equals(section)?new TvRecordingsPage():"TV Apps".equals(section)?new TvAppsPage():TvBrowsePage.create(section);}
    BaseFragment watchPage(){return new TvWatchPage();}
    View mini(){if(mini==null)mini=new TvMini(this);mini.update();return mini;}
    boolean active(){return session.active();}
    boolean watchVisible(){return session.watchVisible;}
    void foreground(boolean visible){session.foreground(visible);}
    void destroy(){destroyed=true;repo.unlisten(updated);session.listeners.remove(updated);TvDownloads.listeners.remove(updated);if(TvRecordings.listener==updated)TvRecordings.listener=null;session.attach(null);}
    private void changed(){
        if(destroyed||host.isFinishing())return;
        host.onPlayerChanged();
        android.app.Fragment showing=host.getFragmentManager().findFragmentById(R.id.content);
        if(showing instanceof SettingsFragment&&showing.isResumed())((SettingsFragment)showing).refreshTvSettings();
    }
    void play(Channel channel){if(session.play(channel))host.showTvNowPlaying();}
    void download(Channel channel){try{TvDownloads.enqueue(host,channel);Notice.show(host,"Added to TV Downloads");repo.changed();}catch(Exception e){Notice.showLong(host,e.getMessage());}}
    void recordNow(Channel channel){
        if(channel==null||!channel.live())return;
        long now=System.currentTimeMillis();Guide.Program program=repo.guide.now(channel,now);
        List<String> labels=new ArrayList<String>();List<Long> ends=new ArrayList<Long>();
        if(!program.placeholder&&program.end>now){labels.add("Record Current Programme");ends.add(program.end);}
        for(int minutes:new int[]{15,30,60,120}){labels.add("Record for "+minutes+" Minutes");ends.add(now+minutes*Guide.MINUTE);}
        Ui.dialog(host).setTitle("Record "+channel.name).setItems(labels.toArray(new String[0]),(d,n)->record(channel,!program.placeholder?program.title:channel.name,now,ends.get(n))).setNegativeButton("Cancel",null).show();
    }
    void record(Channel channel,String title,long start,long end){
        try{
            if(channel.added()){
                String id=Guide.sourceId(channel);int overlapping=0;
                for(TvRecordings.Item item:TvRecordings.all(host))if(!item.download()&&id.equals(item.sourceId)&&(item.waiting()||item.active())&&item.from<end+TvRecordings.late(host)*Guide.MINUTE&&item.to>start-TvRecordings.early(host)*Guide.MINUTE)overlapping++;
                if(overlapping>=Connections.limit(host,id))throw new IllegalArgumentException("This provider’s streams are already reserved for recordings at that time. Cancel one or adjust Streams at a Time in Settings.");
            }
            if(TvRecordings.free(host)<64L*1024*1024)throw new IllegalArgumentException("There is not enough free storage for a recording.");
            if(Build.VERSION.SDK_INT>=33&&host.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)host.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},803);
            TvRecordings.schedule(host,channel,title,start,end);repo.changed();Notice.showLong(host,"Recording scheduled · "+TvRecordings.when(host,start,end));
            if(!TvRecordings.exactAlarms(host))Ui.dialog(host).setTitle("Recording start times").setMessage("Allow alarms and reminders in Android’s settings so scheduled recordings can start on time.").setPositiveButton("Open Android Settings",(d,n)->{try{host.startActivity(new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM",android.net.Uri.parse("package:"+host.getPackageName())));}catch(ActivityNotFoundException ignored){}}).setNegativeButton("Later",null).show();
        }catch(Exception error){Notice.showLong(host,error.getMessage()==null?"The recording could not be scheduled.":error.getMessage());}
    }
    void program(Channel channel,Guide.Program program){
        long now=System.currentTimeMillis();TvRecordings.Item planned=TvRecordings.scheduledAt(TvRecordings.all(host),channel.id,program.start,program.end);
        List<String> labels=new ArrayList<String>();List<Runnable> actions=new ArrayList<Runnable>();labels.add("Watch Channel");actions.add(()->play(channel));
        if(program.end>now){if(planned==null){labels.add(program.start<=now?"Record the Rest":"Record");actions.add(()->record(channel,program.placeholder?channel.name:program.title,Math.max(now,program.start),program.end));}
            else{labels.add(planned.active()?"Stop Recording":"Cancel Recording");actions.add(()->{if(planned.active())TvRecorderService.stop(planned.id);else TvRecordings.remove(host,planned.id);repo.changed();});}}
        Ui.dialog(host).setTitle(program.title).setMessage(channel.name+" · "+TvRecordings.when(host,program.start,program.end)+(program.description.isEmpty()?"":"\n\n"+program.description)+(planned==null?"":planned.active()?"\nRecording now.":"\nScheduled for recording.")).setItems(labels.toArray(new String[0]),(d,n)->actions.get(n).run()).setNegativeButton("Close",null).show();
    }
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
        g.value("Clear TV Favourites","",v->Ui.dialog(host).setTitle("Clear TV favourites?").setPositiveButton("Clear",(d,n)->{repo.prefs.edit().remove("favourites").apply();repo.changed();}).setNegativeButton("Cancel",null).show());
        String[] qualities={"Auto","Data Saver","Compatibility"};int quality=Math.max(0,Math.min(2,repo.prefs.getInt("quality",0)));
        g.value("TV Playback Quality",qualities[quality],v->choice("TV Playback Quality",qualities,quality,n->{repo.prefs.edit().putInt("quality",n).apply();session.settingsChanged();}));
        g.toggle("TV Captions",repo.prefs.getBoolean("captions",false),(t,on)->{repo.prefs.edit().putBoolean("captions",on).apply();session.settingsChanged();});
        g.value("TV Caption Language",repo.prefs.getString("captionLanguage","en"),v->form("TV Caption Language",new String[]{"Language code"},new String[]{repo.prefs.getString("captionLanguage","en")},new boolean[]{false},values->{repo.prefs.edit().putString("captionLanguage",values[0].trim()).apply();session.settingsChanged();}));
        g.toggle("Background TV Audio",repo.prefs.getBoolean("backgroundAudio",true),(t,on)->{repo.prefs.edit().putBoolean("backgroundAudio",on).apply();session.settingsChanged();});
        g.footer("When Blue Grapefruit is in the background, TV keeps playing as audio only. Switching to music pauses TV, and switching to TV pauses music.");
        g.section("TV Guide");
        g.value("Refresh TV Guide","",v->{repo.guide.refresh();repo.refresh(null);repo.changed();Notice.show(host,"TV guide refresh requested");});
        g.footer("Programme information comes from your provider. When none is supplied, the guide shows unavailable-information blocks.");
        g.section("TV Recordings");
        g.value("Manage TV Recordings",TvRecordings.size(TvRecordings.free(host))+" free",v->host.showTvSection("Recordings"));
        int[] minutes={0,1,2,5,10};String[] padding={"None","1 minute","2 minutes","5 minutes","10 minutes"};
        g.value("Start Recordings Early",TvRecordings.early(host)+" min",v->choice("Start Recordings Early",padding,index(minutes,TvRecordings.early(host)),n->repo.prefs.edit().putInt("recordEarly",minutes[n]).apply()));
        g.value("End Recordings Late",TvRecordings.late(host)+" min",v->choice("End Recordings Late",padding,index(minutes,TvRecordings.late(host)),n->repo.prefs.edit().putInt("recordLate",minutes[n]).apply()));
        g.footer("Record live TV now or schedule a programme from Guide. Android restores scheduled alarms after a restart; force-stopping Blue prevents them until you open it again. Recordings share your provider’s stream limit with playback and downloads.");
        if(repo.sections().contains("Downloads")){
            g.section("TV Downloads");g.value("Manage TV Downloads",String.valueOf(TvDownloads.all(host).size()),v->host.showTvSection("Downloads"));
            g.footer("The Wi-Fi Only Downloads setting above applies to both music and TV. Films and episodes can be downloaded from On Demand.");
        }
        g.section("Victory Edition");
        g.value("Victory Roll Call","",v->host.push(new AcknowledgementsFragment()));
    }
    private static int index(int[] values,int value){for(int n=0;n<values.length;n++)if(values[n]==value)return n;return 0;}
}
