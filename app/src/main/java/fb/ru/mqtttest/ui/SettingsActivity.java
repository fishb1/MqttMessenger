package fb.ru.mqtttest.ui;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import fb.ru.mqtttest.App;
import fb.ru.mqtttest.R;
import fb.ru.mqtttest.common.Settings;
import fb.ru.mqtttest.common.UserSession;
import fb.ru.mqtttest.common.logger.Log;

/**
 * Форма настроек. Показывает фрагмент настроек.
 *
 * Created by kolyan on 13.03.18.
 */
public class SettingsActivity extends AppCompatActivity {

    Settings mSettings;
    UserSession mSession;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSettings = ((App) getApplication()).getSettings();
        mSession = ((App) getApplication()).getUserSession();
        setContentView(R.layout.activity_settings);
        Fragment fragment = getFragmentManager().findFragmentByTag(Settings.TAG);
        if (fragment == null) {
            fragment = new SettingsFragment();
            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, fragment, Settings.TAG)
                    .commit();
        }
        mSettings.addOnSettingsChangedListener((SettingsFragment) fragment);
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Fragment fragment = getFragmentManager().findFragmentByTag(Settings.TAG);
        if (fragment != null) {
            mSettings.removeOnSettingsChangedListener((SettingsFragment) fragment);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_item_reset) {
            showConfirmResetDialog();
            return true;
        } else if (item.getItemId() == android.R.id.home) { // Если в меню нажали стрелочку, то вернуться назад
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showConfirmResetDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Reset settings");
        dialog.setMessage("Are you sure?");
        dialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(Settings.TAG, "Reset settings");
                mSettings.init(mSession, true);
            }
        });
        dialog.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }
}
