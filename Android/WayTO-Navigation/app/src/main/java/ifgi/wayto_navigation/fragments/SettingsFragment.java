package ifgi.wayto_navigation.fragments;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import ifgi.wayto_navigation.R;

/**
 * Created by Daniel on 08.04.2016.
 */
public class SettingsFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }
}
