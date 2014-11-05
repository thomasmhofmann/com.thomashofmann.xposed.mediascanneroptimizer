package com.thomashofmann.xposed.mediascanneroptimizer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.util.Log;

import com.thomashofmann.xposed.lib.Logger;
import com.thomashofmann.xposed.lib.XposedPreferenceFragment;
import com.thomashofmann.xposed.mediascanneroptimizer.R;

public class PreferencesFragment extends XposedPreferenceFragment {
    static final String ACTION_SCAN_EXTERNAL = "com.thomashofmann.xposed.mediascanneroptimizer.SCAN_EXTERNAL";
    static final String ACTION_DELETE_MEDIA_STORE_CONTENTS = "com.thomashofmann.xposed.mediascanneroptimizer.DELETE_MEDIA_STORE_CONTENTS";

    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.addPreferencesFromResource(R.xml.preferences);

        Preference triggerRescan = (Preference) findPreference("pref_trigger_media_scan");
        triggerRescan.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference arg0) {
                triggerMediaScanner();
                return true;
            }
        });

        Preference deleteMediaStoreContent = (Preference) findPreference("pref_delete_media_store");
        deleteMediaStoreContent.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference arg0) {
                deleteMediaStoreContent();
                return true;
            }
        });
    }

    @Override
    protected String getPreferencesChangedAction() {
        return "pref-xmso";
    }

    private void triggerMediaScanner() {
        Intent intent = new Intent(ACTION_SCAN_EXTERNAL);
        getActivity().sendBroadcast(intent);
    }

    private void deleteMediaStoreContent() {
        Intent intent = new Intent(ACTION_DELETE_MEDIA_STORE_CONTENTS);
        getActivity().sendBroadcast(intent);
    }

}
