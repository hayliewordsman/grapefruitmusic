package com.grapefruitmusic.ui;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.R;
import com.grapefruitmusic.util.Notice;
import java.util.*;

/** Blue-only roll call; the closing paragraph loads as the final row with the roster. */
public final class AcknowledgementsFragment extends BaseFragment {
    private AcknowledgementsRoster roster;
    private ListView list;
    private LinearLayout summary;
    private TextView status;
    private EditText search;
    private RollCallRepository repository;
    private String resultStatus="";
    private static final class Pending { final String title,text; Pending(String title,String text){this.title=title;this.text=text;} }
    private static final Object CLOSING_ROW=new Object();
    private String query="";
    private final List<Object> rows=new ArrayList<Object>();
    private BaseAdapter adapter;
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle saved){
        Context c=getActivity();list=new ListView(c);list.setDivider(null);list.setClipToPadding(false);
        LinearLayout header=new LinearLayout(c);header.setOrientation(LinearLayout.VERTICAL);header.addView(Ui.header(c,"Settings","Victory Roll Call",goBack()));
        summary=new LinearLayout(c);summary.setOrientation(LinearLayout.VERTICAL);summary.setPadding(Ui.dp(c,16),0,Ui.dp(c,16),Ui.dp(c,12));header.addView(summary);
        status=Ui.text(c,13,R.attr.gfTextSecondary,false);status.setText("Loading Victory Roll Call…");summary.addView(status);
        search=new BlueEditText(c);search.setSingleLine(true);search.setTextSize(16);search.setTypeface(Fonts.regular());search.setTextColor(Ui.color(c,R.attr.gfTextPrimary));search.setHintTextColor(Ui.color(c,R.attr.gfTextSecondary));search.setHint("Find a name or state");search.setText(query);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,Ui.dp(c,48));sp.setMargins(Ui.dp(c,16),0,Ui.dp(c,16),Ui.dp(c,12));header.addView(search,sp);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int n){}public void onTextChanged(CharSequence s,int a,int b,int n){query=s.toString();refresh();}public void afterTextChanged(Editable e){}});
        list.addHeaderView(header,null,false);
        adapter=new BaseAdapter(){public boolean areAllItemsEnabled(){return false;}public int getCount(){return rows.size();}public Object getItem(int n){return rows.get(n);}public long getItemId(int n){return n;}public int getViewTypeCount(){return 3;}public int getItemViewType(int n){return rows.get(n)==CLOSING_ROW?2:rows.get(n) instanceof AcknowledgementsRoster.Member?1:0;}public boolean isEnabled(int n){return rows.get(n) instanceof AcknowledgementsRoster.Member||rows.get(n) instanceof Pending;}
            public View getView(int n,View old,ViewGroup parent){Object value=rows.get(n);if(value==CLOSING_ROW){TextView closing=Ui.text(getActivity(),16,R.attr.gfTextPrimary,false);closing.setSingleLine(false);closing.setEllipsize(null);closing.setText("\n"+AcknowledgementsRoster.CLOSING_MESSAGE);closing.setPadding(Ui.dp(getActivity(),18),Ui.dp(getActivity(),12),Ui.dp(getActivity(),18),Ui.dp(getActivity(),32));closing.setLineSpacing(Ui.dp(getActivity(),3),1);return closing;}if(value instanceof String||value instanceof Pending){TextView section=Ui.text(getActivity(),16,R.attr.gfAccent,true);section.setSingleLine(false);section.setEllipsize(null);section.setText(value instanceof Pending?((Pending)value).title:(String)value);if(value instanceof Pending){section.setTextSize(13);section.setTypeface(Fonts.regular());section.setTextColor(Ui.color(getActivity(),R.attr.gfTextSecondary));section.setMinHeight(Ui.dp(getActivity(),48));}section.setPadding(Ui.dp(getActivity(),18),Ui.dp(getActivity(),24),Ui.dp(getActivity(),18),Ui.dp(getActivity(),8));return section;}return member((AcknowledgementsRoster.Member)value);}};
        list.setAdapter(adapter);list.setOnItemClickListener((parent,view,position,id)->{Object value=list.getItemAtPosition(position);if(value instanceof AcknowledgementsRoster.Member)sources((AcknowledgementsRoster.Member)value);else if(value instanceof Pending){Pending pending=(Pending)value;Ui.dialog(getActivity()).setTitle(pending.title).setMessage(pending.text).setPositiveButton("Close",null).show();}});
        if(repository==null)repository=new RollCallRepository(c);
        if(roster!=null){loaded();fetchLatest();}
        else bg(()->repository.offline(),(data,error)->{if(error!=null){status.setText("Loading the latest results…");fetchLatest();return;}roster=data.roster;resultStatus=data.source+" · Checking for updates…";loaded();fetchLatest();});
        return list;
    }
    private void fetchLatest(){
        final RollCallRepository source=repository;
        bg(()->source.refresh(),(data,error)->{
            if(list==null)return;rememberScroll(list);
            if(error!=null){resultStatus=roster==null?"Results could not be loaded. Open this page again to retry.":"Couldn’t refresh · Showing available offline results";if(roster==null){status.setText(resultStatus);return;}}
            else{rememberScroll(list);roster=data.roster;resultStatus=data.source;}
            loaded();
        });
    }
    private void loaded(){
        if(list==null||getActivity()==null)return;Context c=getActivity();summary.removeAllViews();
        status=Ui.text(c,13,R.attr.gfTextSecondary,false);status.setSingleLine(false);status.setEllipsize(null);status.setText((roster.complete?"":"Provisional · ")+roster.date+"\n"+roster.note+(resultStatus.isEmpty()?"":"\n"+resultStatus));status.setLineSpacing(Ui.dp(c,2),1);summary.addView(status);
        legend(PrideFlagView.RAINBOW,"Openly gay or lesbian");legend(PrideFlagView.BISEXUAL,"Openly bisexual");legend(PrideFlagView.TRANSGENDER,"Openly transgender");
        TextView note=Ui.text(c,12,R.attr.gfTextSecondary,false);note.setSingleLine(false);note.setEllipsize(null);note.setText("Flags reflect publicly documented self-identification. An absent flag means no verified label in this snapshot. Tap a name for sources.");note.setPadding(0,Ui.dp(c,8),0,Ui.dp(c,8));summary.addView(note);
        TextView jump=Ui.chip(c,"Jump to state",false);jump.setOnClickListener(v->{String[] options=new String[AcknowledgementsRoster.STATE_NAMES.length+2];options[0]="Federal";options[1]="Governors-elect";System.arraycopy(AcknowledgementsRoster.STATE_NAMES,0,options,2,options.length-2);Ui.dialog(c).setTitle("Victory Roll Call").setItems(options,(d,n)->{query="";if(search!=null)search.setText("");refresh();String target=n==0?"U.S. Senate":n==1?"Democratic governors-elect":options[n];int index=rows.indexOf(target);if(index>=0)list.setSelection(index+list.getHeaderViewsCount());}).show();});summary.addView(jump);
        refresh();restoreScroll(list);
    }
    private void legend(String kind,String label){Context c=getActivity();LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,Ui.dp(c,6),0,0);row.addView(new PrideFlagView(c,kind),new LinearLayout.LayoutParams(Ui.dp(c,28),Ui.dp(c,18)));TextView text=Ui.text(c,13,R.attr.gfTextSecondary,false);text.setText(label);text.setPadding(Ui.dp(c,10),0,0,0);row.addView(text);summary.addView(row);}
    private View member(AcknowledgementsRoster.Member m){Context c=getActivity();LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(c,18),Ui.dp(c,10),Ui.dp(c,18),Ui.dp(c,10));row.setMinimumHeight(Ui.dp(c,64));
        LinearLayout words=new LinearLayout(c);words.setOrientation(LinearLayout.VERTICAL);TextView name=Ui.text(c,16,R.attr.gfTextPrimary,true);name.setSingleLine(false);name.setEllipsize(null);name.setText(m.label());words.addView(name);TextView detail=Ui.text(c,12,R.attr.gfTextSecondary,false);detail.setSingleLine(false);detail.setEllipsize(null);detail.setText(m.detail());detail.setPadding(0,Ui.dp(c,3),0,0);words.addView(detail);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        for(String flag:m.flags){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(Ui.dp(c,28),Ui.dp(c,18));p.leftMargin=Ui.dp(c,8);row.addView(new PrideFlagView(c,flag),p);}return row;
    }
    private void refresh(){
        if(adapter==null||roster==null)return;rows.clear();
        for(String chamber:new String[]{"us_senate","us_house","governor"}){String section="us_senate".equals(chamber)?"U.S. Senate":"governor".equals(chamber)?"Democratic governors-elect":"U.S. House of Representatives";boolean added=false;for(AcknowledgementsRoster.Member m:roster.members)if(chamber.equals(m.chamber)&&m.matches(query)){if(!added){rows.add(section);added=true;}rows.add(m);}if(query.isEmpty()){String pending=roster.pendingChamberText(chamber);if(!pending.isEmpty())rows.add(new Pending(("governor".equals(chamber)?"Unresolved gubernatorial contests":"Unresolved federal contests")+" · tap for details",pending));}}
        for(int n=0;n<AcknowledgementsRoster.STATE_CODES.length;n++){String code=AcknowledgementsRoster.STATE_CODES[n],state=AcknowledgementsRoster.STATE_NAMES[n];List<AcknowledgementsRoster.Member> people=new ArrayList<AcknowledgementsRoster.Member>();for(AcknowledgementsRoster.Member m:roster.members)if(m.chamber.startsWith("state_")&&code.equals(m.state)&&m.matches(query))people.add(m);
            if(!query.isEmpty()&&people.isEmpty()&&!state.toLowerCase(Locale.US).contains(query.toLowerCase(Locale.US)))continue;rows.add(state);
            for(String chamber:new String[]{"state_senate","state_house"}){boolean added=false;for(AcknowledgementsRoster.Member m:people)if(chamber.equals(m.chamber)){if(!added){rows.add("state_senate".equals(chamber)?"State Senate":"State House / Assembly");added=true;}rows.add(m);}}
            int pending=roster.pending(code);if(pending>0)rows.add(new Pending(pending+" unresolved or unverified contest"+(pending==1?"":"s")+" · tap for details",roster.pendingText(code)));if(people.isEmpty()&&query.isEmpty())rows.add("No Democratic members verified in this snapshot yet.");
        }
        if(rows.isEmpty())rows.add("No matching members found.");
        rows.add(CLOSING_ROW);
        adapter.notifyDataSetChanged();
    }
    private void sources(AcknowledgementsRoster.Member member){List<String> links=new ArrayList<String>(member.sources);for(List<String> refs:member.identitySources.values())for(String ref:refs)if(!links.contains(ref))links.add(ref);Ui.dialog(getActivity()).setTitle(member.label()).setItems(links.toArray(new String[0]),(d,n)->open(links.get(n))).setNegativeButton("Close",null).show();}
    private void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){Notice.show(getActivity(),"No browser is available to open this source.");}}
    @Override public void onDestroyView(){rememberScroll(list);list=null;summary=null;adapter=null;search=null;rows.clear();super.onDestroyView();}
}
