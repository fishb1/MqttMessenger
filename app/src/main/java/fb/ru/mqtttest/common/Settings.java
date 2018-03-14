package fb.ru.mqtttest.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Хранилище настроек.
 *
 * Created by kolyan on 13.03.18.
 */
public class Settings {

    private static final String PREF_ADDRESS = "PREF_ADDRESS";
    private static final String PREF_PUBLISH_TOPIC = "PREF_PUBLISH_TOPIC";
    private static final String PREF_SUBSCRIBE_TOPIC = "PREF_SUBSCRIBE_TOPIC";
    private static final String PREF_TIMEOUT = "PREF_TIMEOUT";

    private final SharedPreferences mPrefs;

    public Settings(Context context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
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
        clear();
        setAddress(getAddress());
        setTimeout(getTimeout());
        setPublishTopic(String.format(getPublishTopic(), session.getLogin()));
        setSubscribeTopic(String.format(getSubscribeTopic(), session.getLogin()));
    }

    public String getAddress() {
        return mPrefs.getString(PREF_ADDRESS, "tcp://176.112.218.148:1883");
    }

    public void setAddress(String value) {
        mPrefs.edit().putString(PREF_ADDRESS, value).apply();
    }

    public String getPublishTopic() {
        return mPrefs.getString(PREF_PUBLISH_TOPIC, "%s/echo/req");
    }

    public void setPublishTopic(String value) {
        mPrefs.edit().putString(PREF_PUBLISH_TOPIC, value).apply();
    }

    public void setSubscribeTopic(String value) {
        mPrefs.edit().putString(PREF_SUBSCRIBE_TOPIC, value).apply();
    }

    public String getSubscribeTopic() {
        return mPrefs.getString(PREF_SUBSCRIBE_TOPIC, "%s/echo/resp");
    }

    public long getTimeout() {
        return Long.valueOf(mPrefs.getString(PREF_TIMEOUT, "1000"));
    }

    public void setTimeout(long value) {
        mPrefs.edit().putString(PREF_TIMEOUT, String.valueOf(value)).apply();
    }

    public void clear() {
        mPrefs.edit().clear().apply();
    }
}
