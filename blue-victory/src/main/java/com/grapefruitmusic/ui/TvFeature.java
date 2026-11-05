package com.grapefruitmusic.ui;

import android.view.View;
import java.util.List;

/** The audio shell's optional in-process TV extension. Implementation is compiled only into Blue. */
abstract class TvFeature {
    final MainActivity host;
    TvFeature(MainActivity host){this.host=host;}
    static TvFeature create(MainActivity host){
        if(!host.getResources().getBoolean(com.grapefruitmusic.R.bool.blue_victory))return null;
        try{return (TvFeature)Class.forName("com.grapefruitmusic.ui.BlueTelevision").getDeclaredConstructor(MainActivity.class).newInstance(host);}
        catch(ClassNotFoundException e){return null;}
        catch(Exception e){throw new IllegalStateException("Couldn't open built-in TV",e);}
    }
    abstract List<String> sections();
    abstract BaseFragment page(String section);
    abstract String selected();
    abstract void selected(String name);
    abstract BaseFragment watchPage();
    abstract View mini();
    abstract boolean active();
    abstract boolean watchVisible();
    abstract void foreground(boolean visible);
    abstract void destroy();
    abstract void addSettings(Grouped group);
}
