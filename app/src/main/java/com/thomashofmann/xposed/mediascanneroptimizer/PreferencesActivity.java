package com.thomashofmann.xposed.mediascanneroptimizer;

import android.content.Context;
import android.os.Bundle;

import com.thomashofmann.xposed.lib.Settings;
import com.thomashofmann.xposed.lib.XposedPreferenceActivity;

public class PreferencesActivity extends XposedPreferenceActivity  {
    final static String PREF_CHANGE_ACTION = "pref-xmso";
    final static String ACTION_PREFS_SCAN_BEHAVIOR = "scanBehavior";
    final static String ACTION_PREFS_USER_INTERACTION = "userInteraction";
    final static String ACTION_PREFS_LOGGING = "logging";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getPreferenceManager().setSharedPreferencesName(Settings.SETTINGS_FILE_NAME);
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_WORLD_READABLE);

        String action = getIntent().getAction();
        if (ACTION_PREFS_SCAN_BEHAVIOR.equals(action)) {
            addPreferencesFromResource(R.xml.preferences_scan_behavior);
        } else if (ACTION_PREFS_USER_INTERACTION.equals(action)) {
            addPreferencesFromResource(R.xml.preferences_user_interaction);
        } else if (ACTION_PREFS_LOGGING.equals(action)) {
            addPreferencesFromResource(R.xml.preferences_logging);
        }
    }

    @Override
    protected String getPreferencesChangedAction() {
        return PREF_CHANGE_ACTION;
    }
}
