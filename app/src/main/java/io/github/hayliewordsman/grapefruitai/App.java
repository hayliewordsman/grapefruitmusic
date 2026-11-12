package io.github.hayliewordsman.grapefruitai;

import android.app.Application;

import io.github.hayliewordsman.grapefruitai.net.Tls;
import io.github.hayliewordsman.grapefruitai.secure.Secrets;
import io.github.hayliewordsman.grapefruitai.ui.Fonts;

public final class App extends Application {
    private static App instance;

    public static App get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Tls.install(this);
        Fonts.init(this);
        Secrets.init(this);
    }
}
