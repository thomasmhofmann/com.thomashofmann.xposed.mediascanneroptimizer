package com.thomashofmann.android.xposed.mediascanner.optimizer.ui;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class PreferencesActivity extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getFragmentManager().findFragmentById(android.R.id.content) == null) {
			getFragmentManager().beginTransaction().add(android.R.id.content, new PreferencesFragment()).commit();
		}
	}
}
