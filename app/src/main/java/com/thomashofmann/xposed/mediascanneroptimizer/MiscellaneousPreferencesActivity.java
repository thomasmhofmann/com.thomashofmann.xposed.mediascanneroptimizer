package com.thomashofmann.xposed.mediascanneroptimizer;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;

import com.thomashofmann.xposed.lib.Paypal;

public class MiscellaneousPreferencesActivity extends PreferencesActivity{

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_misc);

        Preference triggerDonate = (Preference) findPreference("pref_donate");
        triggerDonate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference arg0) {
                triggerDonate();
                return true;
            }
        });
    }

    private void triggerDonate() {
        Intent intent = Paypal.createDonationIntent(this, "email@thomashofmann.com", "XMSO", "EUR");
        startActivity(intent);
    }

}
