package fb.ru.mqtttest.common;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Хранилище нараметров сессии.
 *
 * Created by kolyan on 12.03.18.
 */
public class UserSession {

    private static String PREF_LOGIN = "PREF_LOGIN";
    private static String PREF_PASSWORD = "PREF_PASSWORD";
    private static String PREF_SID = "PREF_SID";

    private final SharedPreferences mPrefs;
    private final List<Listener> mListeners = new ArrayList<>();

    public UserSession(SharedPreferences prefs) {
        mPrefs = prefs;
    }

    public void start(String user, String password, String sid) {
        mPrefs.edit().putString(PREF_LOGIN, user)
                .putString(PREF_PASSWORD, password)
                .putString(PREF_SID, sid)
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
