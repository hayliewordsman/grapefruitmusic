package com.grapefruitmusic.ui;
import java.util.Locale;
final class TvTimes {
    static String length(int seconds){return seconds<3600?String.format(Locale.US,"%d:%02d",seconds/60,seconds%60):String.format(Locale.US,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60);}
}
