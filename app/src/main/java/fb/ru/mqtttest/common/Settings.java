package fb.ru.mqtttest.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

import fb.ru.mqtttest.BuildConfig;
import fb.ru.mqtttest.common.logger.Log;

/**
 * Хранилище настроек.
 *
 * Created by kolyan on 13.03.18.
 */
public class Settings implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "Settings";

    private static final String DEFAULT_MQTT_BROKER = "tcp://176.112.218.148:1883";
    private static final String PATTERN_PUBLISH_TOPIC = "%s/echo/req";
    private static final String PATTERN_SUBSCRIBE_TOPIC = "%s/echo/resp";
    private static final String DEFAULT_TIMEOUT = "1000";

    public static final String PREF_MQTT_BROKER = "PREF_MQTT_BROKER";
    public static final String PREF_PUBLISH_TOPIC = "PREF_PUBLISH_TOPIC";
    public static final String PREF_SUBSCRIBE_TOPIC = "PREF_SUBSCRIBE_TOPIC";
    public static final String PREF_TIMEOUT = "PREF_TIMEOUT";
    public static final String PREF_REST_API_URL = "PREF_REST_API_URL";

    private final SharedPreferences mPrefs;
    private final List<OnSettingsChangedListener> mListeners = new ArrayList<>();

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
        // Да, путь все кто хочет мониторит сессию и по новой подключаются
        mListeners.clear();
        // Подставить имя пользователя в название каналов
        mPrefs.edit()
                .putString(PREF_PUBLISH_TOPIC,
                        String.format(PATTERN_PUBLISH_TOPIC, session.getLogin()))
                .putString(PREF_SUBSCRIBE_TOPIC,
                        String.format(PATTERN_SUBSCRIBE_TOPIC, session.getLogin()))
                .apply();

    }

    public String getBroker() {
        return mPrefs.getString(PREF_MQTT_BROKER, DEFAULT_MQTT_BROKER);
    }

    public String getPublishTopic() {
        return mPrefs.getString(PREF_PUBLISH_TOPIC, "");
    }

    public String getSubscribeTopic() {
        return mPrefs.getString(PREF_SUBSCRIBE_TOPIC, "");
    }

    public long getTimeout() {
        return Long.valueOf(mPrefs.getString(PREF_TIMEOUT, DEFAULT_TIMEOUT));
    }

    public String getRestApiUrl() {
        return mPrefs.getString(PREF_REST_API_URL, BuildConfig.REST_API_URL);
    }

    public void setRestApiUrl(String url) {
        mPrefs.edit().putString(PREF_REST_API_URL, url).apply();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        printSettings();
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
