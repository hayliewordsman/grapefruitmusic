package com.grapefruitfast;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w1024dp-h600dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class InterfaceTest {
    @Test public void browseFavouritesSearchAndSettings() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a=controller.get();
            View root=a.getWindow().getDecorView();measure(root,1024,600);
            assertNotNull(text(root,"Live TV"));assertNotNull(text(root,"CityNews 24/7 · Toronto"));
            capture(root,"landscape-dark.png");
            text(root,"Favourites").performClick();measure(a.getWindow().getDecorView(),1024,600);
            assertNotNull(text(a.getWindow().getDecorView(),"No channels match. Try another filter."));
            text(a.getWindow().getDecorView(),"Search").performClick();root=a.getWindow().getDecorView();
            EditText search=edit(root);assertNotNull(search);search.setText("Winnipeg");measure(root,1024,600);
            assertNotNull(text(root,"Global News · Winnipeg"));
            text(root,"Settings").performClick();measure(a.getWindow().getDecorView(),1024,600);
            assertNotNull(text(a.getWindow().getDecorView(),"Make yourself comfortable."));
        }
    }
    @Test @Config(qualifiers="w360dp-h640dp-mdpi") public void portraitUsesBottomNavigation() throws Exception {
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();measure(root,360,640);
            assertNotNull(text(root,"Live TV"));capture(root,"portrait-dark.png");
        }
    }
    @Test public void themesSurviveRecreation() throws Exception {
        android.content.Context c=org.robolectric.RuntimeEnvironment.getApplication();
        c.getSharedPreferences("fast",0).edit().putInt("theme",1).commit();
        try(org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();measure(root,1024,600);
            assertEquals(Color.parseColor("#4A1622"),heading(root).getCurrentTextColor());capture(root,"landscape-light.png");
        }
    }
    private static TextView heading(View v){if(v instanceof TextView&&((TextView)v).getText().toString().equals("Live TV")&&((TextView)v).getTextSize()>=29)return(TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView t=heading(g.getChildAt(i));if(t!=null)return t;}}return null;}
    private static void measure(View v,int w,int h){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);}
    private static TextView text(View v,String s){if(v instanceof TextView&&((TextView)v).getText().toString().equals(s))return(TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView t=text(g.getChildAt(i),s);if(t!=null)return t;}}return null;}
    private static EditText edit(View v){if(v instanceof EditText)return(EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText t=edit(g.getChildAt(i));if(t!=null)return t;}}return null;}
    private static void capture(View v,String name)throws Exception{if(!"true".equals(System.getProperty("fast.capture")))return;java.io.File dir=new java.io.File("../work/ui-preview");dir.mkdirs();Bitmap image=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(image));try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}
