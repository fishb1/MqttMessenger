package fb.ru.mqtttest.mqtt;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.List;

import fb.ru.mqtttest.App;
import fb.ru.mqtttest.common.UserSession;

/**
 * Служба отвечающая за взаимодействие с MQ TT сервером.
 *
 * Принцип: Messenger принимает сообщения от других компонент и помещает в очередь.
 * Сама служба подписана на очередь сообщений, и как только приходит новое сообщение
 * подключаемся и пытамся отправить все сообщения которые есть в очереди. Если сообщение отправлено
 * успешно, то удаляем его из очереди. Если после отправки сообещнения очередь пустая,
 * то дисконнект.
 *
 * TODO: Во время сеанса подключении, заодно, будем обрабатывать downstream сообщения.
 */
public class MessagingService extends Service {

    public static final String TAG = "MessagingService";

    public static final String HOST = "ws://dev.wbrush.ru:8000";
    public static final String IN_TOPIC = "mv1/%s/toDevice";
    public static final String OUT_TOPIC = "mv1/%s/toServer";

    private MqttAndroidClient mClient; // Истранс клиента MQTT
    private MqttConnectOptions mOptions; // Настройки подключения
    private UserSession mUserSession; // Имя пользователя пароль и т.д.
    private Messenger mMessenger; // Месэйджер, который принимает сообщение от других компонент приложения и отсылает в MQTT. Юзается компонентами через его биндер
    private MessageQueueObserver mMessageQueueObserver = new MessageQueueObserver() {
        @Override
        public void onNewMessage(MessageStorage queue) {
            Log.d(TAG, "New messages, count=" + queue.getMessages().size());
            processMessages();
        }
    };

    public MessagingService() {
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Service created");
        mMessenger = new Messenger(new MessageHandler());
        mUserSession = ((App) getApplication()).getUserSession();
        // Создание и настройка MQTT-клиента
        createClient();
        // Подписываемся на новые сообщения в очереди
        MessageStorage.getInstance(this).getObservable().registerObserver(mMessageQueueObserver);
        // Если сообещние есть в очереди, то сразу попытаться их отправить
        processMessages();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        MessageStorage.getInstance(this).getObservable().unregisterObserver(mMessageQueueObserver);
        disconnect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return mMessenger.getBinder();
    }

    /**
     * Инициализация MQTT клиента.
     */
    private void createClient() {
        mClient = new MqttAndroidClient(getApplicationContext(), HOST, mUserSession.getSid());
        mClient.setCallback(new MqttCallbackExtended() {

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    Log.d(TAG, "Reconnected to : " + serverURI);
                    subscribeToTopic(); // Because Clean Session is true, we need to re-subscribe
                } else {
                    Log.d(TAG, "Connected to: " + serverURI);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.d(TAG, "The Connection was lost.");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message)  {
                Log.d(TAG, "Incoming data: " + new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Log.d(TAG, "Delivery complete");
            }
        });
        mOptions = new MqttConnectOptions();
        mOptions.setAutomaticReconnect(true);
        mOptions.setCleanSession(true);
        mOptions.setUserName(mUserSession.getLogin());
        mOptions.setPassword(mUserSession.getPassword().toCharArray());
    }

    private void connect(final Runnable callback) {
        // Инициализация MQTT клиента
        try {
            Log.d(TAG, "Connecting to " + HOST);
            mClient.connect(mOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    callback.run();
                    subscribeToTopic();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "Failed to connect to " + HOST, exception);
                }
            });
        } catch (Throwable ex){
            Log.e(TAG, "Connecting error", ex);
        }
    }

    /**
     * Проверка подключения. isConnected() может кидать анчекед исключения, поэтому пришлось
     * завернуть вызов.
     *
     * @return подключено
     */
    private boolean isConnected() {
        try {
            return mClient.isConnected();
        } catch (Throwable e) {
            return false;
        }
    }

    private void disconnect() {
        try {
            mClient.disconnect();
        } catch (Throwable e) {
            Log.e(TAG,"Disconnect error: ", e);
        }
    }

    /**
     * Опубликовать сообщение.
     */
    private void processMessages() {
        // Connect if need
        if (!isConnected()) {
            connect(new Runnable() {
                @Override
                public void run() {
                    processMessages();
                }
            });
            return;
        }
        // Если с очереди есть сообщения то влять первое и попытаться отправить
        List<StoredMessage> messages = MessageStorage.getInstance(this).getMessages();
        if(!messages.isEmpty()) {
            final StoredMessage message = messages.get(0);
            Log.d(TAG, "Publish next message id=" + message.getId() + " [in queue: " + (messages.size() - 1) + "]");
            MqttMessage mqMessage = new MqttMessage(message.getPayload().getBytes());
            String topic = String.format(OUT_TOPIC, mUserSession.getLogin());
            try {
                mClient.publish(topic, mqMessage, null,
                        new IMqttActionListener() {
                            @Override
                            public void onSuccess(IMqttToken token) {
                                Log.d(TAG, "Message published: " + message.getId());
                                MessageStorage.getInstance(MessagingService.this).deleteMessage(message.getId());
                                // Process next message
                                processMessages();
                            }

                            @Override
                            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                Log.e(TAG, "Fail to publish massage: id=" + message.getId());
                            }
                        });
                Log.d(TAG, "Attempt to publish message: " + topic + ": id=" + message.getId() + ", payload=" + new String(mqMessage.getPayload()));
            } catch (Throwable e) {
                Log.e(TAG, "Publish error", e);
            }
        }
    }

    private void subscribeToTopic() {
        final String topic = String.format(IN_TOPIC, mUserSession.getLogin());
        Log.d(TAG, "Subscribing to topic " + topic);
        try {
            mClient.subscribe(topic,0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Subscribed to " + topic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(TAG, "Failed to subscribe");
                }
            }, new IMqttMessageListener() {

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    Log.d(TAG, "Message received: " + topic + " : " + new String(message.getPayload()));
                }
            });
        } catch (MqttException ex){
            Log.e(TAG, "Exception whilst subscribing");
        }
    }

    /**
     * Обработчик сообщений. Принимает и передает сообщения от клиентов в службу.
     */
    @SuppressLint("HandlerLeak") // It'll never leaks, trust me!
    private class MessageHandler extends Handler {

        MessageHandler() {
        }

        @Override
        public void handleMessage(Message msg) {
            // put data to queue and fire event
            MessageStorage.getInstance(MessagingService.this).putMessage((String) msg.obj);
        }
    }
}
