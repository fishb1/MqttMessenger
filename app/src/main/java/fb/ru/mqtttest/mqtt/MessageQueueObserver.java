package fb.ru.mqtttest.mqtt;

/**
 * Обозреватель очереди сообщений.
 */
public interface MessageQueueObserver {

    void onNewMessage(MessageQueue queue);
}
