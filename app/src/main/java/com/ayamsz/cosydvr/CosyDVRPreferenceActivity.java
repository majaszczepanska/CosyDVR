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

import java.util.ArrayList;
import java.util.Map;

public class CosyDVRPreferenceActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> returnToMainMenu()
            );
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new MyPreferenceFragment())
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

        @Override
        public void onViewCreated(android.view.View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            // Dynamic padding for the top HUD area
            int paddingTop = (int) (65 * getResources().getDisplayMetrics().density);
            view.setPadding(0, paddingTop, 0, 0);
            view.setBackgroundColor(android.graphics.Color.parseColor("#121212"));
            
            // Removing divider lines between rows for a cleaner look
            if (view instanceof android.view.ViewGroup) {
                android.view.ViewGroup vg = (android.view.ViewGroup) view;
                // Looking for the list (RecyclerView) inside the fragment
                for (int i = 0; i < vg.getChildCount(); i++) {
                    android.view.View child = vg.getChildAt(i);
                    if (child instanceof androidx.recyclerview.widget.RecyclerView) {
                        ((androidx.recyclerview.widget.RecyclerView) child).addItemDecoration(
                            new androidx.recyclerview.widget.DividerItemDecoration(requireContext(), 
                            androidx.recyclerview.widget.DividerItemDecoration.VERTICAL) {
                                @Override
                                public void onDraw(android.graphics.Canvas c, androidx.recyclerview.widget.RecyclerView parent, androidx.recyclerview.widget.RecyclerView.State state) {
                                    // We draw nothing - empty method removes default lines
                                }
                            }
                        );
                    }
                }
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

    @Override
    public void onBackPressed() {
        returnToMainMenu();
    }
}
