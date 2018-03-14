package fb.ru.mqtttest.common;

import android.content.SharedPreferences;
import android.util.Log;

import java.lang.ref.WeakReference;
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
    private final List<WeakReference<Listener>> mListeners = new ArrayList<>(); // TODO: переделать. прохая была идея со слабой ссылкой, т.к. надо держать ссылку на стороне слушателя + еще не понятно в каком треде оповещать слушателей.

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
        List<WeakReference> garbage = new ArrayList<>();
        for (WeakReference<Listener> ref : mListeners) {
            Listener listener = ref.get();
            if (listener == null) {
                Log.e("notifyOnStart", "ref: " + ref);
                garbage.add(ref);
            } else {
                Log.e("notifyOnStart", "listener: " + listener);
                listener.onSessionStart(this);
            }
        }
        mListeners.removeAll(garbage);
    }

    private void notifyOnStop() {
        List<WeakReference> garbage = new ArrayList<>();
        for (WeakReference<Listener> ref : mListeners) {
            Listener listener = ref.get();
            if (listener == null) {
                garbage.add(ref);
            } else {
                listener.onSessionStop(this);
            }
        }
        mListeners.removeAll(garbage);
    }

    public void addListener(Listener listener) {
        mListeners.add(new WeakReference<>(listener));
    }

    public void removeListener(Listener listener) {
        mListeners.remove(new WeakReference<>(listener));
    }

    public static class Listener {

        public void onSessionStart(UserSession session) { }

        public void onSessionStop(UserSession session) { }
    }
}
