package com.grapefruitfast;
import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import java.util.Locale;

/**
 * Where the viewer is, to choose which channels' providers can serve them. From the phone itself only:
 * the mobile network's country, then the SIM's, then the system region setting. No permission, no
 * location, and nothing is sent anywhere. Settings › Region overrides it. Providers still decide: a
 * stream refused for region falls through to the channel's next source.
 */
public final class Region {
    /** Settings › Region: stored codes and their names ("auto" first). */
    public static final String[] CODES={"auto","CA","US","GB","EU","AU","XX"};
    public static final String[] NAMES={"Automatic","Canada","United States","United Kingdom","Europe","Australia","Elsewhere"};
    private static final String[] EUROPE={"AT","BE","BG","HR","CY","CZ","DK","EE","FI","FR","DE","GR","HU","IE","IT","LV","LT","LU","MT","NL","PL","PT","RO","SK","SI","ES","SE","NO","IS","CH","LI"};
    private Region() {}

    /** The country (ISO code, upper case) the phone says it's in, or "" if it can't tell. */
    public static String detected(Context c) {
        try {
            TelephonyManager t=(TelephonyManager)c.getSystemService(Context.TELEPHONY_SERVICE);
            if(t!=null) {
                String network=t.getPhoneType()!=TelephonyManager.PHONE_TYPE_CDMA?t.getNetworkCountryIso():null;
                if(network!=null&&network.length()==2) return network.toUpperCase(Locale.US);
                String sim=t.getSimCountryIso();
                if(sim!=null&&sim.length()==2) return sim.toUpperCase(Locale.US);
            }
        } catch(RuntimeException e) { /* no telephony: a TV, a tablet */ }
        String locale=Locale.getDefault().getCountry();
        return locale==null?"":locale.toUpperCase(Locale.US);
    }

    /** The region in use: the Settings choice, or what the phone says (Europe for European countries). */
    public static String current(Context c,SharedPreferences prefs) {
        String chosen=prefs.getString("region","auto");
        if(!"auto".equals(chosen)) return chosen;
        return group(detected(c));
    }

    /** A country code as one of CODES' regions. */
    public static String group(String country) {
        if(country==null||country.isEmpty()) return "";
        for(String e:EUROPE) if(e.equals(country)) return "EU";
        switch(country){case "CA":case "US":case "GB":case "AU":return country;default:return "XX";}
    }

    public static String name(String code) {
        for(int i=0;i<CODES.length;i++) if(CODES[i].equals(code)) return NAMES[i];
        return "Unknown";
    }

    /** Whether a channel has a source offered in this region ("" = unknown: show everything). */
    public static boolean offered(Channel channel,String region) {
        if(region.isEmpty()||"US".equals(region)) return true;
        // Xumo is a US service: a channel only Xumo carries isn't offered elsewhere.
        return !channel.availability.startsWith("United States");
    }
}
