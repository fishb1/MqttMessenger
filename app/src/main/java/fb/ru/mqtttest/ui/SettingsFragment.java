package fb.ru.mqtttest.ui;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;

import fb.ru.mqtttest.R;
import fb.ru.mqtttest.common.Settings;

/**
 * Фрагмент настроек.
 *
 * Created by kolyan on 13.03.18.
 */
public class SettingsFragment extends PreferenceFragment
        implements Settings.OnSettingsChangedListener {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
    }

    @Override
    public void onSettingsChanged(String key) { // При сбросе настрок, данные инпутов не пересчитываются, пока форма открыта, приходится вручну выставлять новые значения
        Preference pref = findPreference(key);
        if (pref != null) {
            EditTextPreference textPref = (EditTextPreference) pref;
            textPref.setText(getPreferenceManager().getSharedPreferences().getString(key,
                    textPref.getText()));
        }
    }
}
