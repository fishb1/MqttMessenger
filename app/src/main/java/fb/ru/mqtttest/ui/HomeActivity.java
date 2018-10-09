package fb.ru.mqtttest.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import fb.ru.mqtttest.App;
import fb.ru.mqtttest.GeoService;
import fb.ru.mqtttest.R;
import fb.ru.mqtttest.common.VendorUtils;
import fb.ru.mqtttest.common.logger.LogFragment;
import fb.ru.mqtttest.common.logger.LogView;
import fb.ru.mqtttest.mqtt.MessagingService;

/**
 * Основное активити.
 */
public class HomeActivity extends AppCompatActivity {

//    private static final String TAG = "HomeActivity";

    private static int CODE_INPUT_MESSAGE = 1;
    private static int CODE_LOCATION_PERMISSION = 2;

    View mContentView;
    LogView mLogView;
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
            if (!mLocationService.isRequesting()) {
                startService(new Intent(HomeActivity.this, GeoService.class)
                        .setAction(GeoService.ACTION_START_UPDATES));
            }
            isGeoServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isGeoServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
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
        findViewById(R.id.sos).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                publishMessage("SOS!!!1");
            }
        });
        mContentView = findViewById(R.id.content);
        LogFragment logFragment = (LogFragment) getSupportFragmentManager().findFragmentById(R.id.content);
        mLogView = logFragment.getLogView();
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
            case R.id.menu_open_auto_start_menu:
                VendorUtils.gotoAutoStartSetting(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void publishMessage(String json) {
        mLogView.appendToLog("You: " + json);
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
            } else {
                checkFineLocationPermission();
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Установка видимости пункта меню автозагузки (на китайских аппаратах)
        MenuItem autoStartItem = menu.findItem(R.id.menu_open_auto_start_menu);
        if (autoStartItem != null) {
            autoStartItem.setVisible(VendorUtils.hasAutoStart());
        }
        return true;
    }

    private void logout() {
        ((App) getApplication()).getUserSession().clear();
        startActivity(new Intent(this, LauncherActivity.class));
        finish();
    }
}

