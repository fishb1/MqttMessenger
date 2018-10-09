package fb.ru.mqtttest;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.gson.Gson;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import fb.ru.mqtttest.common.Utils;
import fb.ru.mqtttest.mqtt.MessagingService;
import fb.ru.mqtttest.ui.LauncherActivity;

public class GeoService extends Service {

    public static final String TAG = "GeoService";
    public static final String ACTION_START_UPDATES = BuildConfig.APPLICATION_ID + ".start_updates";
    public static final String ACTION_STOP_UPDATES = BuildConfig.APPLICATION_ID + ".stop_updates";
    private static final long REPORT_INTERVAL = TimeUnit.MINUTES.toMillis(3);
    private static final int TWO_MINUTES = 1000 * 60 * 2;
    private static final int NOTIFICATION_ID = 12345678;
    private static final String CHANNEL_ID = "channel_01";

    private final Gson mGson = new Gson();
    private boolean mChangingConfiguration;
    private boolean mRequesting;
    Handler mServiceHandler;
    LocalBinder mBinder = new LocalBinder();
    Location mBestLocation;
    LocationListener mListener = new LocationListener() {

        @Override
        public void onLocationChanged(Location location) {
            if (isBetterLocation(location, mBestLocation)) {
                mBestLocation = location;
            }
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
            Log.d(TAG, "onStatusChanged(): " + s);
        }

        @Override
        public void onProviderEnabled(String s) {
            Log.d(TAG, "onProviderEnabled(): " + s);
        }

        @Override
        public void onProviderDisabled(String s) {
            Log.d(TAG, "onProviderDisabled(): " + s);
        }
    };

    private boolean mMessagingServiceBound;
    private Messenger mMessenger;
    ServiceConnection mMessagingServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mMessagingServiceBound = true;
            mMessenger = new Messenger(service);
            if (!mRequesting) {
                startPeriodicalReports();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mMessagingServiceBound = false;
        }
    };

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        mServiceHandler = new Handler(thread.getLooper());

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            CharSequence name = getString(R.string.app_name);
            // Create the channel for the notification
            NotificationChannel chan =
                    new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);
            // Set the Notification Channel for the Notification Manager.
            notificationManager.createNotificationChannel(chan);
        }
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_START_UPDATES.equals(intent.getAction())) {
                Log.d(TAG, "onStartCommand() start command received");
                // Если запуск из автостарта, то сразу включать активный режим не дожидась пока приложение свернется
                startForeground(NOTIFICATION_ID, getNotification().build());
                requestLocationUpdates();
            } else if (ACTION_STOP_UPDATES.equals(intent.getAction())) {
                Log.d(TAG, "onStartCommand() stop command received");
                removeLocationUpdates();
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        stopForeground(true);
        mChangingConfiguration = false;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        stopForeground(true);
        mChangingConfiguration = false;
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind()");
        if (!mChangingConfiguration && mRequesting) {
            Log.d(TAG, "startForeground()");
            startForeground(NOTIFICATION_ID, getNotification().build());
        }
        return true;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        removeLocationUpdates();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mChangingConfiguration = true;
    }

    private Notification.Builder getNotification() {
        Intent intent = new Intent(this, LauncherActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                NOTIFICATION_ID, intent, 0);
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_status)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setContentText("Updating location...")
                .setWhen(0)
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentTitle(getString(R.string.notification_title));
        // Set the Channel ID for Android O.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID); // Channel ID
        }
        return builder;
    }

    /**
     * Начать обновление локации.
     */
    public void requestLocationUpdates() {
        String permission = Manifest.permission.ACCESS_FINE_LOCATION;
        if (ContextCompat.checkSelfPermission(this, permission) !=
                PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Has't location permission!");
            return;
        }
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager != null) {
            startService(new Intent(this, GeoService.class));
            String provider = LocationManager.GPS_PROVIDER;
            manager.requestLocationUpdates(provider, 10000, 5, mListener,
                    mServiceHandler.getLooper());
            mBestLocation = manager.getLastKnownLocation(provider);
            startPeriodicalReports();
        } else {
            Log.w(TAG, "LocationManager is null!");
        }
        // Включить автозагрузку
        if (!Utils.isGeoServiceAutobootEnabled(this)) {
            Utils.setGeoServiceAutoBoot(this, true);
        }
    }

    /**
     * Запустить периодическую отправку координат.
     */
    private void startPeriodicalReports() {
        if (mMessenger == null) {
            getMessengerAsync();
            return;
        }
        mServiceHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendLastLocation(mMessenger);
                // Назначить следующий запуск
                mServiceHandler.postDelayed(this, REPORT_INTERVAL);
            }
        }, TimeUnit.SECONDS.toMillis(10));
        mRequesting = true;
    }

    /**
     * Запустить службу сообщений и получить Messenger.
     */
    private void getMessengerAsync() {
        bindService(new Intent(this, MessagingService.class),
                mMessagingServiceConnection, BIND_AUTO_CREATE);
    }

    public void removeLocationUpdates() {
        // Отключить автозагрузку
        if (Utils.isGeoServiceAutobootEnabled(this)) {
            Utils.setGeoServiceAutoBoot(this, false);
        }
        // Убрать запросы к геоапи
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager != null) {
            manager.removeUpdates(mListener);
            mRequesting = false;
            stopSelf();
            if (mMessagingServiceBound) {
                unbindService(mMessagingServiceConnection);
                mMessagingServiceBound = false;
                mMessenger = null;
            }
            mServiceHandler.removeCallbacksAndMessages(null);
        } else {
            Log.w(TAG, "LocationManager is null!");
        }
    }

    public boolean isRequesting() {
        return mRequesting;
    }

    public void sendLastLocation(Messenger messenger) {
        if (mBestLocation == null) { // Еще не подключился?
            Log.w(TAG, "sendLastLocation(): best location is null!");
            updateNotification("Location is unknown");
            return;
        }

        Map<String, Object> gps = new HashMap<>();
        gps.put("lat", mBestLocation.getLatitude());
        gps.put("long", mBestLocation.getLongitude());
        gps.put("alt", mBestLocation.getAltitude());
        gps.put("acc", mBestLocation.getAccuracy());
        gps.put("time", mBestLocation.getTime());
        gps.put("bear", mBestLocation.getBearing());

        Map<String, Object> params = new HashMap<>();
        params.put("gps", gps);
        params.put("battery", getBatteryLevel() * 100);

        Map<String, Object> body = new HashMap<>();
        body.put("command", "sensors");
        body.put("params", params);

        Message msg = Message.obtain();
        msg.obj = mGson.toJson(body);
        try {
            messenger.send(msg);
        } catch (RemoteException e) {
            Log.e(TAG, "sendLastLocation() error:", e);
        }
        updateNotification(getString(R.string.location_update_success, new Date().toString()));
    }

    private void updateNotification(String text) {
        // Обновить текст в нотификации на статусбаре
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(GeoService.this);
        Notification notification = getNotification()
                .setContentText(text).build();
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    /** Determines whether one Location reading is better than the current Location fix
     * @param location  The new Location that you want to evaluate
     * @param currentBestLocation  The current Location fix, to which you want to compare the new one
     */
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        return isMoreAccurate ||
                isNewer && !isLessAccurate ||
                isNewer && !isSignificantlyLessAccurate && isFromSameProvider;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public GeoService getService() {
            return GeoService.this;
        }
    }

    private float getBatteryLevel() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent battery = registerReceiver(null, filter);
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            return level / (float) scale;
        } else {
            return -1;
        }
    }
}
