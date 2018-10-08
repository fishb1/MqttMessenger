package fb.ru.mqtttest.common;

import android.content.SharedPreferences;
import android.database.Observable;

import fb.ru.mqtttest.rest.DeviceConfig;

/**
 * Хранилище нараметров сессии.
 *
 * Created by kolyan on 12.03.18.
 */
public class UserSession {

    private static String PREF_LOGIN = "PREF_LOGIN";
    private static String PREF_PASSWORD = "PREF_PASSWORD";
    private static String PREF_SID = "PREF_SID";
    private static String PREF_AUTO_START_VISITED = "PREF_AUTO_START_VISITED";

    private final SharedPreferences mPrefs;
    private final SessionObservable mObservable = new SessionObservable();

    public UserSession(SharedPreferences prefs) {
        mPrefs = prefs;
    }

    public void start(DeviceConfig config) {
        mPrefs.edit().putString(PREF_LOGIN, config.mqttUser.username)
                .putString(PREF_PASSWORD, config.user.password)
                .putString(PREF_SID, config.mqttUser.client_id)
                .apply();
        mObservable.notifyOnSessionStart(this);
    }

    public boolean isStarted() {
        return !getSid().isEmpty();
    }

    public void clear() {
        mPrefs.edit().clear().apply();
        mObservable.notifyOnSessionStop(this);
    }

    public String getLogin() {
        return mPrefs.getString(PREF_LOGIN, "");
    }

    public String getPassword() {
        return mPrefs.getString(PREF_PASSWORD, "");
    }

    public String getSid() {
        return mPrefs.getString(PREF_SID, "");
    }

    public Observable<Listener> getObservable() {
        return mObservable;
    }

    public boolean isAutoStartVisited() {
        return mPrefs.getBoolean(PREF_AUTO_START_VISITED, false);
    }

    public boolean setAutoStartVisited(boolean visited) {
        return mPrefs.edit().putBoolean(PREF_AUTO_START_VISITED, visited).commit();
    }

    public static class Listener {

        public void onSessionStart(UserSession session) { }

        public void onSessionStop(UserSession session) { }
    }

    private static class SessionObservable extends Observable<Listener> {

        private void notifyOnSessionStart(UserSession session) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onSessionStart(session);
            }
        }

        private void notifyOnSessionStop(UserSession session) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onSessionStop(session);
            }
        }
    }
}
