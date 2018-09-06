package fb.ru.mqtttest.mqtt;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fb.ru.mqtttest.App;
import fb.ru.mqtttest.common.UserSession;
import fb.ru.mqtttest.common.logger.Log;

/**
 * Служба отвечающая за взаимодействие с MQ TT сервером. Бедет поддерживать трэд с автоматическиой
 * отправкой сообщений геолокации и делать разовую отправку по запросу.
 */
public class MessagingService extends Service {

    public static final String TAG = "MessagingService";

    public static final String HOST = "ws://dev.wbrush.ru:8000/mqtt";
    public static final String IN_TOPIC = "mv1/%s/toDevice";
    public static final String OUT_TOPIC = "mv1/%s/toServer";

    private MqttAndroidClient mClient;
    private boolean mReconnecting; // Флаг переподключения при изменении адреса в настройках
    private String mCurrentTopic; // Топик на который подписаны в данный момент (для переподписания)
    private UserSession mUserSession;
    private Messenger mMessenger; // Месэйджер, который прнимает сообщение от других компонент приложения и отсылает в MQTT. Юзается компонентами через его биндер
    private MessageQueueObserver mMessageQueueObserver = new MessageQueueObserver() {
        @Override
        public void onNewMessage(MessageQueue queue) {
            connect();
        }
    };
    private Map<IMqttDeliveryToken, Long> mMessagesInProgress = new HashMap<>();

    public MessagingService() {
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Service created");
        mMessenger = new Messenger(new MessageHandler());
        mUserSession = ((App) getApplication()).getUserSession();
        // Подписываемся на новые сообщения в очереди
        MessageQueue.getInstance(this).getObservable().registerObserver(mMessageQueueObserver);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        disconnect();
        MessageQueue.getInstance(this).getObservable().unregisterObserver(mMessageQueueObserver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Client connected");
        return mMessenger.getBinder();
    }

    private void connect() {
        // Инициализация MQTT клиента
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
                Long msgId = mMessagesInProgress.get(token);
                Log.d(TAG, "Message published: " + msgId);
                if (msgId != null) {
                    MessageQueue.getInstance(MessagingService.this).deleteMessage(msgId);
                }
                if (mMessagesInProgress.isEmpty()) {
                    disconnect();
                }
            }
        });
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        options.setUserName(mUserSession.getLogin());
        options.setPassword(mUserSession.getPassword().toCharArray());
        try {
            Log.d(TAG, "Connecting to " + HOST);
            mClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    mClient.setBufferOpts(new DisconnectedBufferOptions());
                    subscribeToTopic();
                    publishMessages();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(TAG, "Failed to connect to " + HOST);
                }
            });
        } catch (MqttException ex){
            Log.e(TAG, "Connecting error", ex);
        }
    }

    private void disconnect() {
        try {
            mClient.disconnect();
        } catch (MqttException e) {
            Log.e(TAG,"Disconnect error: ", e);
        }
    }

    private void publishMessages() {
        List<MessagePojo> messages = MessageQueue.getInstance(this).getMessages();
        for (MessagePojo msg: messages) {
            publishMessage(msg);
        }
    }

    /**
     * Опубликовать сообщение.
     *
     * @param message сообщение, которое нужно засериализовать и отправить
     */
    private void publishMessage(MessagePojo message) {
        try {
            MqttMessage mqMessage = new MqttMessage();
            mqMessage.setPayload(message.getPayload().getBytes());
            String topic = String.format(OUT_TOPIC, mUserSession.getLogin());
            IMqttDeliveryToken token = mClient.publish(topic, mqMessage);
            mMessagesInProgress.put(token, message.getId());
            Log.d(TAG, "Attempt to publish message: " + topic + ": id=" + message.getId() + ", payload=" + new String(mqMessage.getPayload()));
        } catch (Throwable e) {
            Log.e(TAG, "Publish error", e);
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
                    mCurrentTopic = topic;
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
            MessageQueue.getInstance(MessagingService.this).putMessage((String) msg.obj);
        }
    }
}
