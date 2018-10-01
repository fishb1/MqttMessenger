package fb.ru.mqtttest.common;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

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
    private final List<Listener> mListeners = new ArrayList<>();

    public UserSession(SharedPreferences prefs) {
        mPrefs = prefs;
    }

    public void start(DeviceConfig config) {
        mPrefs.edit().putString(PREF_LOGIN, config.mqttUser.username)
                .putString(PREF_PASSWORD, config.user.password)
                .putString(PREF_SID, config.mqttUser.client_id)
                .apply();
        notifyOnStart();
    }

    public boolean isStarted() {
        return !getSid().isEmpty();
    }

    public void clear() {
        mPrefs.edit().clear().apply();
        notifyOnStop();
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

    private void notifyOnStart() {
        for (Listener listener : mListeners) {
            listener.onSessionStart(this);
        }
    }

    private void notifyOnStop() {
        for (Listener listener : mListeners) {
            listener.onSessionStop(this);
        }
    }

    public boolean isAutoStartVisited() {
        return mPrefs.getBoolean(PREF_AUTO_START_VISITED, false);
    }

    public boolean setAutoStartVisited(boolean visited) {
        return mPrefs.edit().putBoolean(PREF_AUTO_START_VISITED, visited).commit();
    }

    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    public static class Listener {

        public void onSessionStart(UserSession session) { }

        public void onSessionStop(UserSession session) { }
    }
}
