package com.grapefruitsettings;
import android.app.*;
import android.content.*;
import android.os.Bundle;
/** Routes framework and fragment navigation to this APK's owned device components. */
public class DeviceActivity extends android.app.Activity {
    @Override protected void onCreate(Bundle state){DeviceSettings.install(getApplication());DeviceSettings.incoming(this);super.onCreate(state);}
    @Override public void startActivityForResult(Intent i,int request){super.startActivityForResult(DeviceSettings.route(this,i),request);}
    @Override public void startActivityForResult(Intent i,int request,Bundle options){super.startActivityForResult(DeviceSettings.route(this,i),request,options);}
    @Override public void startActivityFromFragment(Fragment f,Intent i,int request){super.startActivityFromFragment(f,DeviceSettings.route(this,i),request);}
    @Override public void startActivityFromFragment(Fragment f,Intent i,int request,Bundle options){super.startActivityFromFragment(f,DeviceSettings.route(this,i),request,options);}
    @Override public void startActivityFromChild(Activity child,Intent i,int request){super.startActivityFromChild(child,DeviceSettings.route(this,i),request);}
    @Override public void startActivityFromChild(Activity child,Intent i,int request,Bundle options){super.startActivityFromChild(child,DeviceSettings.route(this,i),request,options);}
    @Override public void startActivities(Intent[] intents){super.startActivities(DeviceSettings.routes(this,intents));}
    @Override public void startActivities(Intent[] intents,Bundle options){super.startActivities(DeviceSettings.routes(this,intents),options);}
    @Override public boolean startActivityIfNeeded(Intent i,int request){return super.startActivityIfNeeded(DeviceSettings.route(this,i),request);}
    @Override public boolean startActivityIfNeeded(Intent i,int request,Bundle options){return super.startActivityIfNeeded(DeviceSettings.route(this,i),request,options);}
    @Override public boolean startNextMatchingActivity(Intent i){return super.startNextMatchingActivity(DeviceSettings.route(this,i));}
    @Override public boolean startNextMatchingActivity(Intent i,Bundle options){return super.startNextMatchingActivity(DeviceSettings.route(this,i),options);}
    @Override public boolean navigateUpTo(Intent i){return super.navigateUpTo(DeviceSettings.route(this,i));}
}
