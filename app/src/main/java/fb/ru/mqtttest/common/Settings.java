package fb.ru.mqtttest.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

import fb.ru.mqtttest.common.logger.Log;

/**
 * Хранилище настроек.
 *
 * Created by kolyan on 13.03.18.
 */
public class Settings implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "Settings";

    public static final String DEFAULT_REST_API_URL = "http://dev.wbrush.ru:3000";
    public static final String PATTERN_PUB_TOPIC = "%s/echo/req";
    public static final String PATTERN_SUB_TOPIC = "%s/echo/resp";
    public static final long DEFAULT_TIMEOUT = 1000;

    public static final String PREF_MQTT_BROKER = "PREF_MQTT_BROKER";
    public static final String PREF_PUB_TOPIC = "PREF_PUB_TOPIC";
    public static final String PREF_SUB_TOPIC = "PREF_SUB_TOPIC";
    public static final String PREF_TIMEOUT = "PREF_TIMEOUT";
    public static final String PREF_REST_API_URL = "PREF_REST_API_URL";

    private final SharedPreferences mPrefs;
    private final List<OnSettingsChangedListener> mListeners = new ArrayList<>();
    private boolean mInitInProgress;

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
    @SuppressLint("ApplySharedPref")
    public void init(UserSession session, boolean clear) {
        mInitInProgress = true;
        // Подставить имя пользователя в название каналов
        SharedPreferences.Editor editor = mPrefs.edit();
        if (clear) {
            editor.clear().commit();
        }
        editor.putString(PREF_REST_API_URL, DEFAULT_REST_API_URL);
        if (getTimeout() < 0) {
            editor.putString(PREF_TIMEOUT, String.valueOf(DEFAULT_TIMEOUT));
        }
        editor.putString(PREF_PUB_TOPIC, String.format(PATTERN_PUB_TOPIC, session.getLogin()));
        editor.putString(PREF_SUB_TOPIC, String.format(PATTERN_SUB_TOPIC, session.getLogin()));
        editor.apply();
        mInitInProgress = false;
        printSettings();
    }

    private String getBroker() {
        return mPrefs.getString(PREF_MQTT_BROKER, "");
    }

    public String getPublishTopic() {
        return mPrefs.getString(PREF_PUB_TOPIC, "");
    }

    public String getSubscribeTopic() {
        return mPrefs.getString(PREF_SUB_TOPIC, "");
    }

    public long getTimeout() {
        return Long.valueOf(mPrefs.getString(PREF_TIMEOUT, "-1"));
    }

    public String getRestApiUrl() {
        return mPrefs.getString(PREF_REST_API_URL, "");
    }

    public void setRestApiUrl(String url) {
        mPrefs.edit().putString(PREF_REST_API_URL, url).apply();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (!mInitInProgress) {
            printSettings();
        }
        notifyListeners(key);
    }

    private void printSettings() {
        Log.i(TAG, "==============================");
        Log.i(TAG, "Rest API URL: " + getRestApiUrl());
        Log.i(TAG, "MQTT Broker: " + getBroker());
        Log.i(TAG, "Publish Topic: " + getPublishTopic());
        Log.i(TAG, "Subscribe Topic: " + getSubscribeTopic());
        Log.i(TAG, "Location update timeout: " + getTimeout());
        Log.i(TAG, "==============================");
    }

    /**
     * Добавить слушателя изменения настроек. Хранится жесткая ссылка, так что удаление
     * обязательно!
     *
     * @param listener слушатель
     */
    public void addOnSettingsChangedListener(OnSettingsChangedListener listener) {
        mListeners.add(listener);
    }
    /**
     * Удалить слушатель измеения настроек.
     *
     * @param listener слушатель
     */
    public void removeOnSettingsChangedListener(OnSettingsChangedListener listener) {
        mListeners.remove(listener);
    }
    /**
     * Уведомить слушателей об изменении настроек.
     *
     * @param key поле настроек
     */
    private void notifyListeners(String key) {
        for (OnSettingsChangedListener listener : mListeners) {
            listener.onSettingsChanged(key);
        }
    }
    /**
     * Интерфейс слушателя изменения настроек.
     */
    public interface OnSettingsChangedListener {

        void onSettingsChanged(String settingName);
    }
}
