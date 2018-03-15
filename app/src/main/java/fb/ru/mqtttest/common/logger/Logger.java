package fb.ru.mqtttest.common.logger;

/**
 * Интерфейс логгера.
 *
 * Created by kolyan on 23.01.18.
 */
public interface Logger {

    void println(int priority, String tag, String msg, Throwable tr);
}
