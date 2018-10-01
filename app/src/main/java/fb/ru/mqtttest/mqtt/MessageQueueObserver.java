package fb.ru.mqtttest.mqtt;

/**
 * Обозреватель очереди сообщений.
 */
public interface MessageQueueObserver {

    void onNewMessage(MessageStorage queue);
}
