package fb.ru.mqtttest.ui;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.view.MenuItem;

import fb.ru.mqtttest.R;

/**
 * Фрагмент настроек.
 *
 * Created by kolyan on 13.03.18.
 */
public class SettingsFragment extends PreferenceFragment {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        setHasOptionsMenu(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { // Если в меню нажали стрелочку, то вернуться назад
            getActivity().onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }
}
