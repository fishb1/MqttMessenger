package fb.ru.mqtttest;

import android.app.Application;
import android.content.Intent;

import fb.ru.mqtttest.common.Settings;
import fb.ru.mqtttest.common.UserSession;
import fb.ru.mqtttest.mqtt.MessageStorage;

/**
 * Приложение. Нужно чтобы хранить синглтоны.
 *
 * Created by kolyan on 12.03.18.
 */
public class App extends Application {

    Settings mSettings;
    UserSession mUserSession;

    @Override
    public void onCreate() {
        super.onCreate();
        mSettings = new Settings(this);
        mUserSession = new UserSession(getSharedPreferences(
                getPackageName() + ".session", MODE_PRIVATE));
        mUserSession.getObservable().registerObserver(new UserSession.Listener() {
            @Override
            public void onSessionStart(UserSession session) {
                // При логине, проинициализировать настройки (подставить логин в имена топиков)
                mSettings.init(session, false);
            }

            @Override
            public void onSessionStop(UserSession session) {
                // При разлогине остановить службы и отключить автозагрузку
                startService(new Intent(App.this, GeoService.class)
                        .setAction(GeoService.ACTION_STOP_UPDATES));
                // Сообщения еще почистить
                MessageStorage.getInstance(App.this).clear();
            }
        });
    }

    public UserSession getUserSession() {
        return mUserSession;
    }

    public Settings getSettings() {
        return mSettings;
    }
}
