package ifgi.wayto_navigation;

import android.app.Activity;
import android.os.Bundle;

import ifgi.wayto_navigation.fragments.SettingsFragment;

/**
 * Created by Daniel on 08.04.2016.
 */
public class SettingsActivity extends Activity {
    public SettingsActivity() {
        super();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


}