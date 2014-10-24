package com.thomashofmann.android.xposed.mediascanner.optimizer.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import com.thomashofmann.android.xposed.mediascanner.optimizer.R;
import com.thomashofmann.android.xposed.mediascanner.optimizer.XposedMod;

@TargetApi(11)
@SuppressWarnings("all")
public class PreferencesFragment extends PreferenceFragment {

	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final PreferenceManager preferenceManager = this.getPreferenceManager();
		preferenceManager.setSharedPreferencesName(XposedMod.SETTINGS_FILE_NAME);
		preferenceManager.setSharedPreferencesMode(Context.MODE_WORLD_READABLE);
		this.addPreferencesFromResource(R.xml.preferences);

		Preference triggerRescan = (Preference) findPreference("pref_trigger_media_scan");
		triggerRescan.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference arg0) {
				triggerMediaScanner(false);
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

	private void triggerMediaScanner(boolean deleteMediaStore) {
		Intent intent = new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://"
				+ Environment.getExternalStorageDirectory()));
		if (deleteMediaStore) {
			intent.putExtra("deleteMediaStore", true);
		}
		intent.putExtra("userInitiatedScan", true);
		getActivity().sendBroadcast(intent);
	}

	private void deleteMediaStoreContent() {
		triggerMediaScanner(true);
	}

}
