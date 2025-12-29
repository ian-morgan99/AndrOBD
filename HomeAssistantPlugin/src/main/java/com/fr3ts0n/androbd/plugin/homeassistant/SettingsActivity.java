package com.fr3ts0n.androbd.plugin.homeassistant;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Settings activity for Home Assistant plugin configuration
 */
public class SettingsActivity extends Activity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Display the fragment as the main content
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }
    
    /**
     * Settings fragment
     */
    public static class SettingsFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        
        private SharedPreferences prefs;
        private MultiSelectListPreference dataItemsPref;
        
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            
            // Load preferences from XML
            addPreferencesFromResource(R.xml.preferences);
            
            prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            
            // Setup data items preference
            dataItemsPref = (MultiSelectListPreference) findPreference(HomeAssistantPlugin.ITEMS_SELECTED);
            updateDataItemsList();
            
            // Set summary updaters for all preferences
            updateAllSummaries();
        }
        
        @Override
        public void onResume() {
            super.onResume();
            prefs.registerOnSharedPreferenceChangeListener(this);
        }
        
        @Override
        public void onPause() {
            super.onPause();
            prefs.unregisterOnSharedPreferenceChangeListener(this);
        }
        
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (HomeAssistantPlugin.ITEMS_KNOWN.equals(key)) {
                // Update data items list when new items are discovered
                updateDataItemsList();
            }
            
            // Update summary for changed preference
            Preference pref = findPreference(key);
            if (pref != null) {
                updateSummary(pref);
            }
        }
        
        /**
         * Update the list of available data items
         */
        private void updateDataItemsList() {
            if (dataItemsPref == null) {
                return;
            }
            
            // Get known items from preferences (defensively copy to avoid modifications)
            Set<String> knownItemsSet = prefs.getStringSet(HomeAssistantPlugin.ITEMS_KNOWN, new HashSet<String>());
            Set<String> knownItems = new HashSet<>(knownItemsSet);
            
            if (knownItems.isEmpty()) {
                // No items discovered yet
                dataItemsPref.setEnabled(false);
                dataItemsPref.setSummary("No OBD data items discovered yet. Connect to vehicle to discover items.");
                return;
            }
            
            // Sort items alphabetically
            ArrayList<String> sortedItems = new ArrayList<>(knownItems);
            Collections.sort(sortedItems);
            
            // Convert to arrays
            CharSequence[] entries = sortedItems.toArray(new CharSequence[0]);
            CharSequence[] entryValues = sortedItems.toArray(new CharSequence[0]);
            
            // Update preference
            dataItemsPref.setEntries(entries);
            dataItemsPref.setEntryValues(entryValues);
            dataItemsPref.setEnabled(true);
            
            // Update summary
            updateSummary(dataItemsPref);
        }
        
        /**
         * Update summaries for all preferences
         */
        private void updateAllSummaries() {
            updateSummary(findPreference(HomeAssistantPlugin.PREF_HA_URL));
            updateSummary(findPreference(HomeAssistantPlugin.PREF_HA_TOKEN));
            updateSummary(findPreference(HomeAssistantPlugin.PREF_HA_TRANSMISSION_MODE));
            updateSummary(findPreference(HomeAssistantPlugin.PREF_HA_SSID));
            updateSummary(findPreference(HomeAssistantPlugin.PREF_HA_UPDATE_INTERVAL));
            updateSummary(dataItemsPref);
        }
        
        /**
         * Update summary for a preference to show current value
         */
        private void updateSummary(Preference pref) {
            if (pref == null) {
                return;
            }
            
            if (pref instanceof EditTextPreference) {
                EditTextPreference editPref = (EditTextPreference) pref;
                String value = editPref.getText();
                
                // Don't show token value for security
                if (HomeAssistantPlugin.PREF_HA_TOKEN.equals(pref.getKey())) {
                    if (value != null && !value.isEmpty()) {
                        pref.setSummary("••••••••");
                    } else {
                        pref.setSummary(getString(R.string.ha_token_description));
                    }
                } else {
                    if (value != null && !value.isEmpty()) {
                        pref.setSummary(value);
                    }
                }
            } else if (pref instanceof ListPreference) {
                ListPreference listPref = (ListPreference) pref;
                pref.setSummary(listPref.getEntry());
            } else if (pref instanceof MultiSelectListPreference) {
                MultiSelectListPreference multiPref = (MultiSelectListPreference) pref;
                Set<String> values = multiPref.getValues();
                
                if (values == null || values.isEmpty()) {
                    pref.setSummary("All items (none selected = publish all)");
                } else {
                    pref.setSummary(values.size() + " items selected");
                }
            }
        }
    }
}
