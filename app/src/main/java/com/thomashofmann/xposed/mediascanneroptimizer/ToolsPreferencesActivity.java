package com.thomashofmann.xposed.mediascanneroptimizer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;

public class ToolsPreferencesActivity extends PreferencesActivity{
    final static String ACTION_SCAN_EXTERNAL = "com.thomashofmann.xposed.mediascanneroptimizer.SCAN_EXTERNAL";
    final static String ACTION_DELETE_MEDIA_STORE_CONTENTS = "com.thomashofmann.xposed.mediascanneroptimizer.DELETE_MEDIA_STORE_CONTENTS";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_tools);

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

    private void triggerMediaScanner() {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(new Uri.Builder().scheme("file").authority(ACTION_SCAN_EXTERNAL).build());
        intent.putExtra(ACTION_SCAN_EXTERNAL, true);
        sendBroadcast(intent);
    }

    private void deleteMediaStoreContent() {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(new Uri.Builder().scheme("file").authority(ACTION_DELETE_MEDIA_STORE_CONTENTS).build());
        intent.putExtra(ACTION_DELETE_MEDIA_STORE_CONTENTS, true);
        sendBroadcast(intent);
    }

}
