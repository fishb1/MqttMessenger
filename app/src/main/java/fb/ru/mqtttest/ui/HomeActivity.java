package fb.ru.mqtttest.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

import fb.ru.mqtttest.App;
import fb.ru.mqtttest.GeoService;
import fb.ru.mqtttest.MessagingService;
import fb.ru.mqtttest.R;
import fb.ru.mqtttest.common.Settings;
import fb.ru.mqtttest.common.UserSession;
import fb.ru.mqtttest.common.Utils;
import fb.ru.mqtttest.common.VendorUtils;
import fb.ru.mqtttest.common.logger.AndroidLogWrapper;
import fb.ru.mqtttest.common.logger.FilterTagLogger;
import fb.ru.mqtttest.common.logger.Log;
import fb.ru.mqtttest.common.logger.LogFragment;
import fb.ru.mqtttest.common.logger.LogView;

/**
 * Основное активити.
 */
public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    private static int CODE_INPUT_MESSAGE = 1;
    private static int CODE_LOCATION_PERMISSION = 2;

    View mContentView;
    LogView mLogView;
    UserSession mSession;
    Settings mSettings;
    // Поля для взимодействия с службой сообщений (чтобы вручную отправлять сообщения)
    Messenger mMessenger; // Мессенджер для отправки сообщений в службу сообщений
    boolean isMessagingServiceBound;
    ServiceConnection mMessagingServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mMessenger = new Messenger(service);
            isMessagingServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isMessagingServiceBound = false;
        }
    };
    // Поля для взаимодействия со службой геолокации
    boolean isGeoServiceBound;
    GeoService mLocationService;
    ServiceConnection mGapiServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mLocationService = ((GeoService.LocalBinder) binder).getService();
            isGeoServiceBound = true;
            invalidateOptionsMenu();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isGeoServiceBound = false;
            invalidateOptionsMenu();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        // TODO: implement DI here
        mSession = ((App) getApplication()).getUserSession();
        mSettings = ((App) getApplication()).getSettings();
        bindService(new Intent(this, MessagingService.class),
                mMessagingServiceConnection, BIND_AUTO_CREATE);
        if (checkFineLocationPermission()) {
            bindLocationUpdateService();
        }
        findViewById(R.id.fab_input_message).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(HomeActivity.this,
                        MessageActivity.class), CODE_INPUT_MESSAGE);
            }
        });
        mContentView = findViewById(R.id.content);
        initLogger(); // Initialize the logger (don't forget return original logger back)
    }

    private void bindLocationUpdateService() {
        bindService(new Intent(this, GeoService.class),
                mGapiServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isMessagingServiceBound) {
            unbindService(mMessagingServiceConnection);
            isMessagingServiceBound = false;
        }
        if (isGeoServiceBound) {
            unbindService(mGapiServiceConnection);
            isGeoServiceBound = false;
        }
        // Установить обратно стандартный логгер
        Log.setLogger(new AndroidLogWrapper());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CODE_INPUT_MESSAGE && resultCode == RESULT_OK) {
            publishMessage(data.getStringExtra(MessageActivity.EXTRA_MESSAGE));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.home_menu, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_logout:
                logout();
                return true;
            case R.id.menu_item_control_service:
                toggleService();
                return true;
            case R.id.menu_open_auto_start_menu:
                VendorUtils.gotoAutoStartSetting(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void publishMessage(String json) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Object>>() {
        }.getType();
        Message msg = Message.obtain();
        try {
            Log.d(TAG, "Sending message: " + json);
            msg.obj = gson.fromJson(json, type);
            mMessenger.send(msg);
        } catch (Throwable e) {
            Log.e(TAG, "Msg parsing error", e);
            Snackbar.make(mContentView, "Message format error: " + e.getMessage(),
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void toggleService() {
        if (isGeoServiceBound) {
            boolean started = false;
            if (mLocationService.isRequesting()) {
                Log.d(TAG, "Остановка службы обновления геолокации");
                mLocationService.removeLocationUpdates();
                Utils.setGeoServiceAutoBoot(this, false);
            } else {
                Log.d(TAG, "Запуск службы обновления геолокации");
                if (checkFineLocationPermission()) {
                    mLocationService.requestLocationUpdates();
                    Utils.setGeoServiceAutoBoot(this, true);
                }
                started = true;
            }
            invalidateOptionsMenu();
            Snackbar.make(mContentView, getString(R.string.service_message, started ?
                    "started" : "stopped"), Snackbar.LENGTH_LONG).show();
        }
    }

    boolean checkFineLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    CODE_LOCATION_PERMISSION);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CODE_LOCATION_PERMISSION) {
            String permission = permissions[0];
            if (permission != null && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                bindLocationUpdateService();
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Установка доступности пункта меню запуска обновления локации
        MenuItem toggleServiceItem = menu.findItem(R.id.menu_item_control_service);
        if (toggleServiceItem != null) {
            toggleServiceItem.setEnabled(isGeoServiceBound);
            boolean started = isGeoServiceBound && mLocationService.isRequesting();
            toggleServiceItem.setChecked(started);
            toggleServiceItem.setTitle(started ? R.string.stop_service : R.string.start_service);
        }
        // Установка видимости пункта меню автозагузки (на китайских аппаратах)
        MenuItem autoStartItem = menu.findItem(R.id.menu_open_auto_start_menu);
        if (autoStartItem != null) {
            autoStartItem.setVisible(VendorUtils.hasAutoStart());
        }
        return true;
    }

    private void logout() {
        mSession.clear();
        startActivity(new Intent(this, LauncherActivity.class));
        finish();
    }

    private void initLogger() {
        AndroidLogWrapper wrapper = new AndroidLogWrapper();
        Log.setLogger(wrapper);
        FilterTagLogger filter = new FilterTagLogger(TAG, Settings.TAG, GeoService.TAG,
                MessagingService.TAG);
        wrapper.setNext(filter);
        LogFragment logFragment = (LogFragment) getSupportFragmentManager().findFragmentById(R.id.content);
        filter.setNext(mLogView = logFragment.getLogView());
    }
}

