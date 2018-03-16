package fb.ru.mqtttest;

import android.app.IntentService;
import android.app.LauncherActivity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import fb.ru.mqtttest.common.Settings;
import fb.ru.mqtttest.common.logger.Log;

public class GapiService extends IntentService {

    public static final String TAG = "GapiService";

    private final static int NOTIFICATION_ID = 777;
    private static final long MAX_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(30);

    Settings mSettings;
    Settings.OnSettingsChangedListener mSettingListener = new Settings.OnSettingsChangedListener() {
        @Override
        public void onSettingsChanged(String settingName) {
            if (Settings.PREF_TIMEOUT.equals(settingName)) { // Если изменили таймаут, то переподключиться
                mGoogleApiListener.reconnect();
            }
        }
    };
    GoogleApiListener mGoogleApiListener; // Слушатель обновлений местоположения от Google API
    boolean mStarted;
    Messenger mMessenger;
    boolean mMessengerServiceBound;
    ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mMessenger = new Messenger(service);
            mMessengerServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mMessengerServiceBound = false;
        }
    };


    public GapiService() {
        super("GapiService");
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Service created");
        super.onCreate();
        mSettings = ((App) getApplication()).getSettings();
        bindService(new Intent(this, MessagingService.class), mConnection,
                BIND_AUTO_CREATE);
        mGoogleApiListener = new GoogleApiListener();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        super.onDestroy();
        finishWatching();
        if (mMessengerServiceBound) {
            unbindService(mConnection);
            mMessengerServiceBound = false;
        }
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        //TODO: handle start foreground command on 26+ devices
        Log.d(TAG, "onHandleIntent: intent=" + intent);
    }

    public void startWatching() {
        Log.d(TAG, "Start tracking location");
        startForeground(NOTIFICATION_ID, getNotification());
        mGoogleApiListener.connect();
        mStarted = true;
        mSettings.addOnSettingsChangedListener(mSettingListener);
    }

    public void finishWatching() {
        Log.d(TAG, "Stop tracking location");
        mSettings.removeOnSettingsChangedListener(mSettingListener);
        mGoogleApiListener.disconnect();
        mStarted = false;
        stopForeground(true);
    }

    public boolean isStarted() {
        return mStarted;
    }
    /**
     * Отправить сообщение с локацией по MQTT.
     *
     * @param location локация
     */
    private void sendLocation(Location location) {
        if (mMessenger == null) { // Еще не подключился?
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("sp", 0);
        payload.put("lo", location.getLongitude());
        payload.put("la", location.getLatitude());
        payload.put("ac", location.getAccuracy());
        payload.put("h", location.getAltitude());
        payload.put("a", 0);
        payload.put("aa", 0);
        payload.put("s", 0);
        Message msg = Message.obtain();
        msg.obj = payload;
        try {
            mMessenger.send(msg);
        } catch (RemoteException e) {
            Log.e(TAG, "Error", e);
        }
    }
    /**
     * Отправить послудние координаты. Нужно для стресс теста.
     */
    public void sendLastLocation() {
        Location location = mGoogleApiListener.mBestLocation;
        if (location != null) {
            sendLocation(location);
        } else {
            sendLocation(new Location("Mock location"));
        }
    }
    /**
     * Биндер к этой службе для остальных компонент приложения. Для управления подключением
     * к Google API.
     *
     */
    public class MyBinder extends Binder {

        public GapiService getService() {
            return GapiService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Client connected");
        return new MyBinder();
    }

    private Notification getNotification() {
        Intent intent = new Intent(this, LauncherActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                NOTIFICATION_ID, intent, 0);
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setContentText("Don't click me!")
                .setWhen(0)
                .setContentTitle(getString(R.string.app_name));
        return builder.build();
    }
    /**
     * Класс отвечает за обновление местоположения через Google API.
     */
    private class GoogleApiListener implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            com.google.android.gms.location.LocationListener {

        private Location mBestLocation;
        private GoogleApiClient mGoogleApiClient;

        void connect() {
            mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
            // Начать принимать обновление местоположения
            mGoogleApiClient.connect();
        }

        private void disconnect() {
            if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                removeLocationUpdates();
                mGoogleApiClient.disconnect();
            }
        }

        private void reconnect() {
            disconnect();
            connect();
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            if (mGoogleApiClient.isConnected()) {
                requestLocationUpdates(); // Стартануть обновления
            } else  {
                Log.w(TAG, "Not connected to Google Api?");
            }
        }


        @Override
        public void onConnectionSuspended(int i) {
            Log.w(TAG, "onConnectionSuspended: i=" + i);
            reconnect();
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult result) {
            Log.w(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
            reconnect();
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG, "onLocationChanged: " + location.getLatitude() + " " + location.getLongitude() + ", accuracy=" + location.getAccuracy());
            if (isBetterLocation(location, mBestLocation)) {
                mBestLocation = location;
                sendLocation(mBestLocation);
            }
        }

        private void requestLocationUpdates() {
            Log.d(TAG, "requestLocationUpdates: request location updates, timeout="
                    + mSettings.getTimeout());
            LocationRequest request = LocationRequest.create()
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    .setInterval(MAX_UPDATE_INTERVAL)
                    .setFastestInterval(mSettings.getTimeout());
            try {
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                        request, this);
                Log.d(TAG, "requestLocationUpdates: Connected to FusedLocationApi");
            } catch (Exception e) {
                Log.w(TAG, "requestLocationUpdates: Unable to request location updates: ", e);
                reconnect();
            }
        }

        private void removeLocationUpdates() {
            try {
                LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            } catch (Exception e) {
                Log.w(TAG, "removeLocationUpdates: Нет подключения к Google API!", e);
            }
        }
        /**
         * Локация лучше чем имеющаеся.
         *
         * @param location проверяемая локация (только что полученная)
         * @param currentBestLocation лучшая локация на момент проверки
         * @return да/нет
         */
        private boolean isBetterLocation(Location location, Location currentBestLocation) {
            if (currentBestLocation == null) {
                // A new location is always better than no location
                Log.d(TAG, "Best location is null");
                return true;
            }

            // Check whether the new location fix is newer or older
            long timeDelta = location.getTime() - currentBestLocation.getTime();
            boolean isSignificantlyNewer = timeDelta > MAX_UPDATE_INTERVAL * 10;
            boolean isSignificantlyOlder = timeDelta < -MAX_UPDATE_INTERVAL * 10;
            boolean isNewer = timeDelta > 0;

            // If it's been more than two minutes since the current location, use the new location
            // because the user has likely moved
            if (isSignificantlyNewer) {
                Log.d(TAG, "New location is significantly newer");
                return true;
                // If the new location is more than two minutes older, it must be worse
            } else if (isSignificantlyOlder) {
                return false;
            }

            // Check whether the new location fix is more or less accurate
            int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
            boolean isLessAccurate = accuracyDelta > 0;
            boolean isMoreAccurate = accuracyDelta < 0;
            boolean isSignificantlyLessAccurate = accuracyDelta > currentBestLocation.getAccuracy() * 1.5;

            // Check if the old and new location are from the same provider
            boolean isFromSameProvider = isSameProvider(location.getProvider(),
                    currentBestLocation.getProvider());

            // Determine location quality using a combination of timeliness and accuracy
            if (isMoreAccurate) {
                Log.d(TAG, "New location is more accurate");
                return true;
            } else if (isNewer && !isLessAccurate) {
                Log.d(TAG, "New location is newer and not less accurate");
                return true;
            } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
                Log.d(TAG, "New location is newer and not significantly less accurate");
                return true;
            }
            return false;
        }
        /**
         * Checks whether two providers are the same
         */
        private boolean isSameProvider(String provider1, String provider2) {
            if (provider1 == null) {
                return provider2 == null;
            }
            return provider1.equals(provider2);
        }
    }
}