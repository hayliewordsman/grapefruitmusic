package com.grapefruitfast;
import android.content.Context;
import android.os.Build;
import java.io.*;
import java.util.regex.Pattern;

/**
 * As Music's CrashReport: when Grapefruit TV closes because of a fault in it, a short report kept on the
 * device (the app and Android versions, the device model, where in the code it stopped). Nothing is sent
 * anywhere: the next time the app starts it offers to share the report, and the person decides. Web
 * addresses lose their query strings, and IPTV logins' usernames and passwords are blanked.
 */
public final class CrashReport {
    private static final String FILE="last-crash.txt";
    private static final int MAX_CHARS=24*1024;
    private CrashReport() {}

    /** Keeps a report of any crash from now on (once, as the app starts). */
    public static void install(Context context) {
        final Context app=context.getApplicationContext();
        final Thread.UncaughtExceptionHandler android=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,e)->{
            try{write(app,report(app,thread,e));}catch(Throwable ignored){/* Android's handler still closes the app */}
            if(android!=null)android.uncaughtException(thread,e);
        });
    }
    static String report(Context c,Thread thread,Throwable e) {
        StringWriter trace=new StringWriter();e.printStackTrace(new PrintWriter(trace));
        String version="?";
        try{version=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception ignored){}
        String text="Grapefruit TV "+version+"\nAndroid "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\n"
            +Build.MANUFACTURER+" "+Build.MODEL+"\nThread: "+thread.getName()+"\n\n"+redact(trace.toString());
        return text.length()>MAX_CHARS?text.substring(0,MAX_CHARS):text;
    }
    private static final Pattern QUERY=Pattern.compile("(https?://[^\\s?#]+)\\?[^\\s#]*");
    private static final Pattern LOGIN=Pattern.compile("(/(?:live|movie|series)/)[^/\\s]+/[^/\\s]+/");
    private static final Pattern USERINFO=Pattern.compile("(https?://)[^/@\\s]+@");
    /** No sign-in details in a report: query strings, user:password@, and /live/<user>/<password>/ go. */
    static String redact(String s) {
        s=QUERY.matcher(s).replaceAll("$1?…");
        s=LOGIN.matcher(s).replaceAll("$1…/…/");
        return USERINFO.matcher(s).replaceAll("$1…@");
    }
    private static void write(Context c,String text) throws IOException {
        FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),FILE));
        try{out.write(text.getBytes("UTF-8"));}finally{out.close();}
    }
    /** The last crash's report, if one hasn't been shared or dismissed yet; else null. */
    public static String pending(Context c) {
        File f=new File(c.getFilesDir(),FILE);
        if(!f.exists())return null;
        try{return new String(UserSources.read(new FileInputStream(f),MAX_CHARS*2),"UTF-8");}catch(IOException e){return null;}
    }
    public static void clear(Context c) { new File(c.getFilesDir(),FILE).delete(); }
}
