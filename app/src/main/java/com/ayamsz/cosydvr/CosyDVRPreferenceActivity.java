package com.ayamsz.cosydvr;

import android.content.Intent;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.Preference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Map;

public class CosyDVRPreferenceActivity extends PreferenceActivity
{
    @Override
    protected void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> returnToMainMenu()
            );
        }

        getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragment
        implements OnSharedPreferenceChangeListener
    {
        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());

            ListPreference LP = (ListPreference) findPreference("sd_card_path");
            Context context = getActivity();

            StorageUtils stutils = new StorageUtils();
            ArrayList storageList = (ArrayList) stutils.getStorageList(context);

            CharSequence[] entries = new CharSequence[stutils.getStorageList(context).size()];
            CharSequence[] entryValues = new CharSequence[stutils.getStorageList(context).size()];
            for (int i = 0; i < stutils.getStorageList(context).size(); i++) {
            	entries[i] = stutils.getStorageList(context).get(i).getDisplayName();
            	entryValues[i] = stutils.getStorageList(context).get(i).path;
            }
            LP.setEntries(entries);
            LP.setEntryValues(entryValues);

            String currentPath = sharedPref.getString("sd_card_path", "");
            if (!currentPath.isEmpty()) {
                LP.setSummary(currentPath);
            } else {
                LP.setSummary("Not set - using internal app storage");
            }

            sharedPref.registerOnSharedPreferenceChangeListener(this);

            Map<String,?> keys = sharedPref.getAll();
            for(Map.Entry<String,?> entry : keys.entrySet()){
                onSharedPreferenceChanged(sharedPref,entry.getKey());
            }
        }

        @Override
        public void onViewCreated(android.view.View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            // Obliczamy odpowiedni margines (np. 60dp, co wystarczy na zegar i baterię)
            int paddingTop = (int) (60 * getResources().getDisplayMetrics().density);

            // Ustawiamy padding (lewo: 0, góra: margines, prawo: 0, dół: 0)
            view.setPadding(0, paddingTop, 0, 0);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Preference pref = findPreference(key);
            if (pref == null) return;
            if (pref instanceof EditTextPreference) {
                String value = sharedPreferences.getString(key, "");
                if (key.equals("video_duration")) {
                    pref.setSummary(value + " minutes");
                } else {
                    pref.setSummary(value);
                }
            } else if (pref instanceof ListPreference) {
                ListPreference listPref = (ListPreference) pref;
                pref.setSummary(listPref.getEntry());
            }
        }
    }



    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            returnToMainMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void returnToMainMenu() {
        Intent intent = new Intent(this, CosyDVR.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        finish();
    }

    // old android sdk
    @Override
    public void onBackPressed() {
        returnToMainMenu();
    }
}

