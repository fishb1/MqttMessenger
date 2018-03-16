package fb.ru.mqtttest;

import android.app.Application;

import fb.ru.mqtttest.common.Settings;
import fb.ru.mqtttest.common.UserSession;

/**
 * Приложение. Нужно чтобы хранить синглтоны.
 *
 * Created by kolyan on 12.03.18.
 */
public class App extends Application {

    Settings mSettings;
    UserSession mUserSession;
    // При логине, проинициализировать настройки (подставить логин в имена топиков)
    UserSession.Listener mUserSessionListener = new UserSession.Listener() {
        @Override
        public void onSessionStart(UserSession session) {
            mSettings.init(session, false);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mSettings = new Settings(this);
        mUserSession = new UserSession(getSharedPreferences(
                getPackageName() + ".session", MODE_PRIVATE));
        mUserSession.addListener(mUserSessionListener);
    }

    public UserSession getUserSession() {
        return mUserSession;
    }

    public Settings getSettings() {
        return mSettings;
    }
}
