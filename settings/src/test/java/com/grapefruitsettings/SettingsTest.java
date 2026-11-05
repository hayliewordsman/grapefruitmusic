package com.grapefruitsettings;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import com.grapefruitmusic.ui.DialogEditing;
import com.grapefruitmusic.ui.UniversalTheme;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=21,qualifiers="w320dp-h568dp-mdpi")
public class SettingsTest {
    private Context context;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("settings",0).edit().clear().commit();
        ThemeProvider.prefs(context).edit().clear().commit();
        Robolectric.buildContentProvider(ThemeProvider.class).create(UniversalTheme.URI.getAuthority()).get();
    }
    @Test public void blueRetainsMediaSelectionAndRevision(){
        ThemeState initial=new ThemeState(-1,-1,null,null);
        ThemeState blue=initial.select(3,"blue");assertEquals(-1,blue.mediaTheme);assertNull(blue.mediaRevision);
        ThemeState light=blue.select(1,"light");assertEquals(1,light.mediaTheme);
        ThemeState next=light.select(3,"next");assertEquals(3,next.theme);assertEquals(1,next.mediaTheme);assertEquals("light",next.mediaRevision);
    }
    @Test public void mediaMappingAndManualOverrideSurviveBlue(){
        SharedPreferences audio=context.getSharedPreferences("audio-test",0);audio.edit().clear().putInt("theme",2).commit();
        int[] mapping={4,3,2,-1};
        assertFalse(UniversalTheme.sync(context,audio,true,mapping));assertEquals(2,audio.getInt("theme",-1));
        ThemeProvider.select(context,1);assertTrue(UniversalTheme.sync(context,audio,true,mapping));assertEquals(3,audio.getInt("theme",-1));
        audio.edit().putInt("theme",2).commit();ThemeProvider.select(context,3);
        assertFalse(UniversalTheme.sync(context,audio,true,mapping));assertEquals(2,audio.getInt("theme",-1));
        ThemeProvider.select(context,0);assertTrue(UniversalTheme.sync(context,audio,true,mapping));assertEquals(4,audio.getInt("theme",-1));
    }
    @Test public void launcherAcceptsAllFourThemes(){
        SharedPreferences launcher=context.getSharedPreferences("launcher-test",0);launcher.edit().clear().commit();
        for(int i=0;i<4;i++){ThemeProvider.select(context,i);assertTrue(UniversalTheme.sync(context,launcher,false,new int[]{0,1,2,3}));assertEquals(i,launcher.getInt("theme",-1));}
    }
    @Test public void readOnlyProviderRejectsAllExternalWrites(){
        ThemeProvider provider=Robolectric.buildContentProvider(ThemeProvider.class).create().get();
        try{provider.insert(UniversalTheme.URI,new ContentValues());fail();}catch(SecurityException expected){}
        try{provider.update(UniversalTheme.URI,new ContentValues(),null,null);fail();}catch(SecurityException expected){}
        try{provider.delete(UniversalTheme.URI,null,null);fail();}catch(SecurityException expected){}
        try(Cursor cursor=provider.query(UniversalTheme.URI,null,null,null,null)){assertTrue(cursor.moveToFirst());assertEquals(4,cursor.getColumnCount());}
    }
    @Test public void explicitEditingPreservesSelectionAndPastePosition(){
        EditText f=new EditText(context);DialogEditing.prepare(f);assertFalse(f.isLongClickable());
        assertNotNull(f.getCustomSelectionActionModeCallback());
        assertFalse(f.getCustomSelectionActionModeCallback().onCreateActionMode(null,null));
        f.setText("hello world");f.setSelection(6,11);DialogEditing.copy(f,true);assertEquals("hello ",f.getText().toString());
        f.setSelection(0,5);DialogEditing.paste(f);assertEquals("world ",f.getText().toString());assertEquals(5,f.getSelectionStart());
        DialogEditing.selectAll(f);assertEquals(0,f.getSelectionStart());assertEquals(6,f.getSelectionEnd());
    }
    @Test @Config(sdk=28) public void modernFieldsKeepNativeTextSelection(){
        EditText f=new EditText(context);boolean longClick=f.isLongClickable();DialogEditing.prepare(f);
        assertEquals(longClick,f.isLongClickable());assertNull(f.getCustomSelectionActionModeCallback());
    }
    @Test public void sideBySideIdentityAndStockRoutes(){
        assertEquals("io.github.hayliewordsman.grapefruitsettings",context.getPackageName());
        assertNull(context.getApplicationInfo().sharedLibraryFiles);
        assertEquals("com.android.settings",StockSettings.intent("android.settings.WIFI_SETTINGS").getPackage());
    }
    @Test public void originalDialogGetsSafeEditingToolbar(){
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();
        EditText field=new EditText(activity);field.setText("Original JP5S dialog");
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(activity).setTitle("Edit").setView(field).create();
        dialog.show();LegacyTheme.prepareDialog(dialog);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertFalse(field.isLongClickable());assertNotNull(find(dialog.getWindow().getDecorView(),"Paste"));
        assertNotNull(find(dialog.getWindow().getDecorView(),"Select all"));dialog.dismiss();activity.finish();
    }
    @Test public void replacementSwitchRetainsNativeCheckedCallbacks(){
        LegacySwitch control=new LegacySwitch(context);int[] calls={0};
        control.setOnCheckedChangeListener((button,on)->calls[0]++);
        control.setChecked(true);assertTrue(control.isChecked());assertEquals(1,calls[0]);
        control.performClick();assertFalse(control.isChecked());assertEquals(2,calls[0]);
    }
    @Test public void networkCredentialsAreValidatedAndEscaped(){
        assertFalse(MainActivity.validPassword("short"));assertTrue(MainActivity.validPassword("test passphrase"));
        assertTrue(MainActivity.validPassword(String.join("",Collections.nCopies(64,"a"))));
        assertEquals("\"a\\\"b\\\\c\"",MainActivity.quote("a\"b\\c"));
    }
    @Test public void defaultAndEveryThemeBuildAtNarrowWidth(){
        for(int i=0;i<4;i++){
            context.getSharedPreferences("settings",0).edit().putInt("theme",i).commit();
            try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
                MainActivity a=controller.get();View root=a.findViewById(android.R.id.content);
                root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(568,View.MeasureSpec.EXACTLY));root.layout(0,0,320,568);
                assertNotNull(find(root,"App appearance"));click(find(root,"App appearance"));
                assertNotNull(find(a.findViewById(android.R.id.content),"Victory Blue"));
            }
        }
    }
    @Test @Config(sdk=28,qualifiers="w360dp-h800dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderThemePreviews() throws Exception {
        java.io.File dir=new java.io.File("build/theme-previews");dir.mkdirs();
        for(int i=0;i<4;i++){
            context.getSharedPreferences("settings",0).edit().putInt("theme",i).commit();
            try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
                MainActivity a=controller.get();click(find(a.findViewById(android.R.id.content),"App appearance"));
                View root=a.findViewById(android.R.id.content);root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,360,800);
                android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(360,800,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,"theme-"+i+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
            }
        }
    }
    private static void click(View v){while(v!=null&&!v.hasOnClickListeners()){android.view.ViewParent p=v.getParent();v=p instanceof View?(View)p:null;}assertNotNull(v);v.performClick();}
    private static View find(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return v;if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
}
