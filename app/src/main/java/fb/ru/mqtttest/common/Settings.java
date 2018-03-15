package fb.ru.mqtttest.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import fb.ru.mqtttest.common.logger.Log;

/**
 * Хранилище настроек.
 *
 * Created by kolyan on 13.03.18.
 */
public class Settings implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "Settings";

    private static final String PREF_ADDRESS = "PREF_ADDRESS";
    private static final String PREF_PUBLISH_TOPIC = "PREF_PUBLISH_TOPIC";
    private static final String PREF_SUBSCRIBE_TOPIC = "PREF_SUBSCRIBE_TOPIC";
    private static final String PREF_TIMEOUT = "PREF_TIMEOUT";

    private final SharedPreferences mPrefs;

    public Settings(Context context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }
    /**
     * Инициализация настроек. Сразу после логина, установить значения по-умолчанию, чтобы можно
     * было их показать. Если пользоваться значениями по умолчанию из XML, то они
     * проинициализируются только при первом открытии фрагмента с настройками, а я хочу показать
     * их раньше на основной форме. Вообще, все это делается только для того,чтобы в название
     * топиков подставить имя пользователя.
     *
     * @param session параметры сессии пользователя
     */
    public void init(UserSession session) {
        mPrefs.edit().clear().apply();
        mPrefs.edit().putString(PREF_ADDRESS, getAddress()).apply();
        mPrefs.edit().putString(PREF_TIMEOUT, String.valueOf(getTimeout())).apply();
        mPrefs.edit().putString(PREF_PUBLISH_TOPIC, String.format(
                getPublishTopic(), session.getLogin())).apply();
        mPrefs.edit().putString(PREF_SUBSCRIBE_TOPIC, String.format(
                getSubscribeTopic(), session.getLogin())).apply();
    }

    public String getAddress() {
        return mPrefs.getString(PREF_ADDRESS, "tcp://176.112.218.148:1883");
    }

    public String getPublishTopic() {
        return mPrefs.getString(PREF_PUBLISH_TOPIC, "%s/echo/req");
    }

    public String getSubscribeTopic() {
        return mPrefs.getString(PREF_SUBSCRIBE_TOPIC, "%s/echo/resp");
    }

    public long getTimeout() {
        return Long.valueOf(mPrefs.getString(PREF_TIMEOUT, "1000"));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        printSettings();
    }

    public void printSettings() {
        Log.i(TAG, "==============================");
        Log.i(TAG, "Server: " + getAddress());
        Log.i(TAG, "Publish topic: " + getPublishTopic());
        Log.i(TAG, "Subscribe topic: " + getSubscribeTopic());
        Log.i(TAG, "Timeout: " + getTimeout());
        Log.i(TAG, "==============================");
    }
}
