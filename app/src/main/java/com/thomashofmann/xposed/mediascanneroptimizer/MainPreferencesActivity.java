package com.thomashofmann.xposed.mediascanneroptimizer;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class MainPreferencesActivity extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_headers_legacy);
	}
}
