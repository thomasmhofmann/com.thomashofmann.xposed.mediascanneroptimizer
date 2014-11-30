package com.thomashofmann.xposed.mediascanneroptimizer;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcel;
import android.preference.Preference;
import android.util.Log;

import com.thomashofmann.xposed.lib.Logger;
import com.thomashofmann.xposed.lib.Paypal;
import com.thomashofmann.xposed.lib.XposedPreferenceFragment;
import com.thomashofmann.xposed.mediascanneroptimizer.R;

import java.util.List;

public class PreferencesFragment extends XposedPreferenceFragment {
    static final String PREF_CHANGE_ACTION = "pref-xmso";
    static final String ACTION_SCAN_EXTERNAL = "com.thomashofmann.xposed.mediascanneroptimizer.SCAN_EXTERNAL";
    static final String ACTION_DELETE_MEDIA_STORE_CONTENTS = "com.thomashofmann.xposed.mediascanneroptimizer.DELETE_MEDIA_STORE_CONTENTS";

    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.addPreferencesFromResource(R.xml.preferences);

        Preference triggerDonate = (Preference) findPreference("pref_donate");
        triggerDonate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference arg0) {
                triggerDonate();
                return true;
            }
        });

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
        return PREF_CHANGE_ACTION;
    }

    private void triggerDonate() {
        Intent intent = Paypal.createDonationIntent(getActivity(), "email@thomashofmann.com", "XMSO", "EUR");
        getActivity().startActivity(intent);
    }

    private void triggerMediaScanner() {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(new Uri.Builder().scheme("file").authority(ACTION_SCAN_EXTERNAL).build());
        intent.putExtra(ACTION_SCAN_EXTERNAL, true);
        getActivity().sendBroadcast(intent);
    }

    private void deleteMediaStoreContent() {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(new Uri.Builder().scheme("file").authority(ACTION_DELETE_MEDIA_STORE_CONTENTS).build());
        intent.putExtra(ACTION_DELETE_MEDIA_STORE_CONTENTS, true);
        getActivity().sendBroadcast(intent);
    }

}
