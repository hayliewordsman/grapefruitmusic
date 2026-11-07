// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitmusic.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.R;

/** The Maps minigame, reached through the same five taps on Developer / Haylie. */
public final class VictoryBreakFragment extends BaseFragment {
    private VictoryBreakView scene;
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle state){
        ScrollView page=Grouped.page(getActivity());Grouped body=Grouped.on(page);
        body.add(Ui.header(getActivity(),"About","Victory Break",goBack()));
        body.footer("Tap the head. Make your mark.");
        scene=new VictoryBreakView(getActivity(),Ui.color(getActivity(),R.attr.gfAccent));
        scene.setLayoutParams(new LinearLayout.LayoutParams(-1,Ui.dp(getActivity(),148+Math.min(280,getResources().getConfiguration().screenWidthDp*.65f))));body.add(scene);
        TextView counter=Ui.text(getActivity(),14,R.attr.gfTextSecondary,false);counter.setText("0 drops");counter.setGravity(Gravity.CENTER);body.add(counter);
        scene.setOnClickListener(v->{scene.drop();counter.setText(scene.count+(scene.count==1?" drop":" drops"));});
        body.footer("Drops clear when you go back.");return page;
    }
    @Override public void onDestroyView(){if(scene!=null)scene.clear();scene=null;super.onDestroyView();}
}
