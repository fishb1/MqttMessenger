package fb.ru.mqtttest;

import android.app.LauncherActivity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.HashMap;
import java.util.Map;

import fb.ru.mqtttest.common.Settings;
import fb.ru.mqtttest.common.UserSession;
import fb.ru.mqtttest.common.logger.Log;

/**
 *
 * Created by kolyan on 20.03.18.
 */

public class LocationUpdatesService extends Service {


    public static final String TAG = LocationUpdatesService.class.getSimpleName();

    private static final int NOTIFICATION_ID = 12345678;
    private static final String CHANNEL_ID = "channel_01";

    private final IBinder mBinder = new LocalBinder();
    private LocationRequest mLocationRequest;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    private Handler mServiceHandler;
    private boolean mChangingConfiguration;
    private boolean mRequestingLocationUpdates;
    private UserSession mSession;
    private UserSession.Listener mSessionListener = new UserSession.Listener() {
        @Override
        public void onSessionStop(UserSession session) {
            if (mRequestingLocationUpdates) {
                removeLocationUpdates();
            }
        }
    };
    private Settings mSettings;
    private Settings.OnSettingsChangedListener mSettingListener = new Settings.OnSettingsChangedListener() {
        @Override
        public void onSettingsChanged(String settingName) {
            if (Settings.PREF_TIMEOUT.equals(settingName)) { // Если изменили таймаут, то переподключиться
                if (mRequestingLocationUpdates) {
                    removeLocationUpdates(); // Отключить обновление
                    createLocationRequest(); // Пересоздать запрос
                    requestLocationUpdates(); // Включить обновление
                }
            }
        }
    };
    private Location mLocation; // The current location
    private Messenger mMessenger;
    private boolean mMessengerServiceBound;
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

    public LocationUpdatesService() {
    }

    @Override
    public void onCreate() {
        mSession = ((App) getApplication()).getUserSession();
        // Remove updates if user logged out
        mSession.addListener(mSessionListener);
        mSettings = ((App) getApplication()).getSettings();
        // Recreate update if interval changed by user
        mSettings.addOnSettingsChangedListener(mSettingListener);
        bindService(new Intent(this, MessagingService.class), mConnection,
                BIND_AUTO_CREATE);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                onNewLocation(locationResult.getLastLocation());
            }
        };
        createLocationRequest();
        getLastLocation();
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mServiceHandler = new Handler(handlerThread.getLooper());
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
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started");
        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mChangingConfiguration = true;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service. The service should cease to be a foreground service
        // when that happens.
        Log.i(TAG, "in onBind()");
        stopForeground(true);
        mChangingConfiguration = false;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        Log.i(TAG, "in onRebind()");
        stopForeground(true);
        mChangingConfiguration = false;
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Last client unbound from service");
        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!mChangingConfiguration && mRequestingLocationUpdates) {
            Log.i(TAG, "Starting foreground service");
            startForeground(NOTIFICATION_ID, getNotification());
        }
        return true; // Ensures onRebind() is called when a client re-binds.
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        mSession.removeListener(mSessionListener);
        mSettings.removeOnSettingsChangedListener(mSettingListener);
        mServiceHandler.removeCallbacksAndMessages(null);
        if (mMessengerServiceBound) {
            unbindService(mConnection);
            mMessengerServiceBound = false;
        }
    }
    /**
     * Makes a request for location updates. Note that in this sample we merely log the
     * {@link SecurityException}.
     */
    public void requestLocationUpdates() {
        Log.i(TAG, "Requesting location updates");
        mRequestingLocationUpdates = true;
        startService(new Intent(getApplicationContext(), LocationUpdatesService.class));
        try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback, Looper.myLooper());
        } catch (SecurityException unlikely) {
            mRequestingLocationUpdates = false;
            Log.e(TAG, "Lost location permission. Could not request updates. " + unlikely);
        }
    }
    /**
     * Removes location updates. Note that in this sample we merely log the
     * {@link SecurityException}.
     */
    public void removeLocationUpdates() {
        Log.i(TAG, "Removing location updates");
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            mRequestingLocationUpdates = false;
            stopSelf();
        } catch (SecurityException unlikely) {
            mRequestingLocationUpdates = true;
            Log.e(TAG, "Lost location permission. Could not remove updates. " + unlikely);
        }
    }

    public boolean isStarted() {
        return mRequestingLocationUpdates;
    }
    /**
     * Returns the {@link NotificationCompat} used as part of the foreground service.
     */
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
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentTitle(getString(R.string.app_name));
        // Set the Channel ID for Android O.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID); // Channel ID
        }
        return builder.build();
    }

    private void getLastLocation() {
        try {
            mFusedLocationClient.getLastLocation()
                    .addOnCompleteListener(new OnCompleteListener<Location>() {
                        @Override
                        public void onComplete(@NonNull Task<Location> task) {
                            if (task.isSuccessful() && task.getResult() != null) {
                                mLocation = task.getResult();
                                Log.d(TAG, "Current location fixed");
                            } else {
                                Log.w(TAG, "Failed to get location.");
                            }
                        }
                    });
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission." + unlikely);
        }
    }

    private void onNewLocation(Location location) {
        Log.i(TAG, "New location: " + location);
        sendLocation(mLocation = location);
    }

    /**
     * Sets the location request parameters.
     */
    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(mSettings.getTimeout());
        mLocationRequest.setFastestInterval(mSettings.getTimeout() / 2);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public LocationUpdatesService getService() {
            return LocationUpdatesService.this;
        }
    }

    public void sendLastLocation() {
        if (mLocation != null) {
            sendLocation(mLocation);
        } else {
            Log.w(TAG, "sendLastLocation: last location is unknown");
        }
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
}