package com.ayamsz.cosydvr;

import android.content.Intent;
import android.os.Bundle;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.Map;

public class CosyDVRPreferenceActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        findViewById(R.id.btnBackSettings).setOnClickListener(v -> returnToMainMenu());

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::returnToMainMenu
            );
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new MyPreferenceFragment())
                .commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(requireContext());

            ListPreference LP = findPreference("sd_card_path");
            if (LP != null) {
                StorageUtils stutils = new StorageUtils();
                java.util.List<StorageUtils.StorageInfo> storageList = stutils.getStorageList(requireContext());

                CharSequence[] entries = new CharSequence[storageList.size()];
                CharSequence[] entryValues = new CharSequence[storageList.size()];
                for (int i = 0; i < storageList.size(); i++) {
                    entries[i] = storageList.get(i).getDisplayName();
                    entryValues[i] = storageList.get(i).path;
                }
                LP.setEntries(entries);
                LP.setEntryValues(entryValues);

                String currentPath = sharedPref.getString("sd_card_path", "");
                if (!currentPath.isEmpty()) {
                    LP.setSummary(currentPath);
                } else {
                    LP.setSummary("Not set - using internal app storage");
                }
            }

            // Update all summaries
            Map<String, ?> keys = sharedPref.getAll();
            for (Map.Entry<String, ?> entry : keys.entrySet()) {
                updatePreferenceSummary(sharedPref, entry.getKey());
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updatePreferenceSummary(sharedPreferences, key);
        }

        private void updatePreferenceSummary(SharedPreferences sharedPreferences, String key) {
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
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);

        finish();
        overridePendingTransition(0, 0);

    }

    @Override
    public void onBackPressed() {
        returnToMainMenu();
    }
}