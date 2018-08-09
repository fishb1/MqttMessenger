package fb.ru.mqtttest.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

import fb.ru.mqtttest.App;
import fb.ru.mqtttest.GeoService2;
import fb.ru.mqtttest.MessagingService;
import fb.ru.mqtttest.R;
import fb.ru.mqtttest.common.Utils;
import fb.ru.mqtttest.common.Settings;
import fb.ru.mqtttest.common.UserSession;
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

    EditText mStressTestDialogMsgCountView;
    Button mStressTestButtonView;
    Handler mStressTestHandler = new Handler();
    boolean mStressTestStarted;
    long mStressTestStartTime;
    long mStressTestMsgCount;
    AlertDialog mStressTestDialog;

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
    boolean isGapiServiceBound;
    GeoService2 mLocationService;
    ServiceConnection mGapiServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mLocationService = ((GeoService2.LocalBinder) binder).getService();
            isGapiServiceBound = true;
            invalidateOptionsMenu();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isGapiServiceBound = false;
            invalidateOptionsMenu();
        }
    };
    Handler mHandler = new Handler(Looper.getMainLooper());

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
        bindService(new Intent(this, GeoService2.class),
                mGapiServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mStressTestDialog != null) {
            mStressTestDialog.cancel();
        }
        if (isMessagingServiceBound) {
            unbindService(mMessagingServiceConnection);
            isMessagingServiceBound = false;
        }
        if (isGapiServiceBound) {
            unbindService(mGapiServiceConnection);
            isGapiServiceBound = false;
        }
        // Установить обратно стандартный логгер
        Log.setLogger(new AndroidLogWrapper());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mStressTestStarted) {
            stopStressTest();
            toggleStressTestButtonText();
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
            case R.id.menu_item_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.menu_item_control_service:
                toggleService();
                return true;
            case R.id.menu_item_test:
                showStressTestDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void publishMessage(String json) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
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
        if (isGapiServiceBound) {
            if (mLocationService.isRequesting()) {
                Log.d(TAG, "Остановка службы обновления геолокации");
                mLocationService.removeLocationUpdates();
                mHandler.removeCallbacksAndMessages(null);
                Utils.setGeoServiceAutoBoot(this, false);
            } else {
                Log.d(TAG, "Запуск службы обновления геолокации");
                if (checkFineLocationPermission()) {
                    mLocationService.requestLocationUpdates(mMessenger);
                    Utils.setGeoServiceAutoBoot(this, true);
                }
            }
            invalidateOptionsMenu();
            Snackbar.make(mContentView, getString(R.string.service_message,
                    mLocationService.isRequesting() ? "started" : "stopped"),
                    Snackbar.LENGTH_LONG).show();
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
        MenuItem toggleServiceItem = menu.findItem(R.id.menu_item_control_service);
        if (toggleServiceItem != null) {
            toggleServiceItem.setEnabled(isGapiServiceBound);
            boolean started = isGapiServiceBound && mLocationService.isRequesting();
            toggleServiceItem.setChecked(started);
            toggleServiceItem.setTitle(started ? R.string.stop_service : R.string.start_service);
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
        FilterTagLogger filter = new FilterTagLogger(TAG, Settings.TAG, GeoService2.TAG,
                MessagingService.TAG);
        wrapper.setNext(filter);
        LogFragment logFragment = (LogFragment) getSupportFragmentManager().findFragmentById(R.id.content);
        filter.setNext(mLogView = logFragment.getLogView());
    }

    private void showStressTestDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setView(R.layout.stress_test_dialog);
        dialog.setTitle("Stress test");
        dialog.setNegativeButton(android.R.string.cancel, null);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (mStressTestStarted) {
                    stopStressTest();
                }
            }
        });
        dialog.setCancelable(false);
        mStressTestDialog = dialog.show();
        mStressTestButtonView = mStressTestDialog.findViewById(R.id.btn_control);
        mStressTestButtonView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mStressTestStarted) {
                    startStressTest();
                } else {
                    stopStressTest();
                }
                toggleStressTestButtonText();
            }
        });
        mStressTestDialogMsgCountView = mStressTestDialog.findViewById(R.id.text_msg_count);
        mStressTestDialogMsgCountView.setText(String.valueOf(20));
    }

    private void toggleStressTestButtonText() {
        if (mStressTestButtonView != null) {
            mStressTestButtonView.setText(mStressTestStarted ? "Stop" : "Start");
        }
    }

    private void startStressTest() {
        Log.d(TAG, "Starting stress test");
        if (!mStressTestStarted) {
            String strValue = mStressTestDialogMsgCountView.getText().toString();
            if (TextUtils.isEmpty(strValue)) {
                strValue = "0";
            }
            int count = Integer.valueOf(strValue);
            if (count <= 0) {
                Snackbar.make(mContentView, "Message count less zero!", Snackbar.LENGTH_LONG)
                        .show();
                return;
            }
            long delay = 1000 / count;
            new StressTestRunnable(mStressTestHandler, delay).run();
            mStressTestStarted = true;
            Log.d(TAG, "Stress test started: speed=" + strValue + " msg in second");
            mLogView.setMuted(true);
        } else {
            Log.w(TAG, "Stress test already running!");
        }
    }

    private void stopStressTest() {
        Log.d(TAG, "Stopping stress test");
        if (mStressTestStarted) {
            mStressTestHandler.removeCallbacksAndMessages(null);
            mLogView.setMuted(false);
            mStressTestStarted = false;
            Log.d(TAG, "Stress test stopped");
            Log.d(TAG, String.format("Running time: %s sec", (System.currentTimeMillis() - mStressTestStartTime) / 1000));
            Log.d(TAG, String.format("Message count: %s", mStressTestMsgCount));

        } else {
            Log.d(TAG, "Stress test not started!");
        }
    }

    private class StressTestRunnable implements Runnable {

        final Handler mHandler;
        final long mDelay;

        StressTestRunnable(Handler h, long d) {
            mStressTestMsgCount = 0;
            mStressTestStartTime = System.currentTimeMillis();
            mHandler = h;
            mDelay = d;
        }

        @Override
        public void run() {
            mStressTestMsgCount++;
            mLocationService.sendLastLocation(mMessenger);
            mHandler.postDelayed(this, mDelay);
        }
    }
}