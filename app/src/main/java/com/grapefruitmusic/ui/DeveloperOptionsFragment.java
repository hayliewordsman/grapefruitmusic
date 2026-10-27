package com.grapefruitmusic.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

/**
 * Settings › Developer Options: tools for working out what this tablet's hardware lets Grapefruit
 * do, for sending to the developer (Radio Details).
 */
public final class DeveloperOptionsFragment extends BaseFragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        Context c = getActivity();
        ScrollView page = Grouped.page(c);
        Grouped g = Grouped.on(page);
        g.add(Ui.header(c, "Settings", "Developer Options", goBack()));

        g.section("FM Radio");
        g.value("Radio Details", "", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                main().push(new RadioDetailsFragment());
            }
        });
        g.footer("For radios Grapefruit doesn't support yet: " + AboutFragment.DEVELOPER
                + " may ask you to send what it finds.");
        return page;
    }
}
