package fb.ru.mqtttest;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.text.TextUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

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
import org.msgpack.jackson.dataformat.MessagePackFactory;

import fb.ru.mqtttest.common.Settings;
import fb.ru.mqtttest.common.UserSession;
import fb.ru.mqtttest.common.logger.Log;

/**
 * Служба отвечающая за взаимодействие с MQ TT сервером. Бедет поддерживать трэд с автоматическиой
 * отправкой сообщений геолокации и делать разовую отправку по запросу.
 */
public class MessagingService extends Service {

    public static final String TAG = "MessagingService";

    private MqttAndroidClient mClient;
    private boolean mReconnecting; // Флаг переподключения при изменении адреса в настройках
    private String mCurrentTopic; // Топик на который подписаны в данный момент (для переподписания)
    private Settings mSettings;
    private Settings.OnSettingsChangedListener mSettingsListener = new Settings.OnSettingsChangedListener() {

        @Override
        public void onSettingsChanged(String settingName) {
            switch (settingName) {
                case Settings.PREF_MQTT_BROKER: {
                    reconnect();
                } break;
                case Settings.PREF_SUBSCRIBE_TOPIC: {
                    resubscribe();
                } break;
            }
        }
    };

    private UserSession mUserSession;
    private UserSession.Listener mSessionListener = new UserSession.Listener() {
        @Override
        public void onSessionStop(UserSession session) {
            stopSelf();
        }
    };
    private Messenger mMessenger; // Месэйджер, который прнимает сообщение от других компонент приложения и отсылает в MQTT. Юзается компонентами через его биндер

    public MessagingService() {
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Service created");
        super.onCreate();
        mMessenger = new Messenger(new MessageHandler(this));
        mUserSession = ((App) getApplication()).getUserSession();
        mUserSession.addListener(mSessionListener);
        mSettings = ((App) getApplication()).getSettings();
        mSettings.addOnSettingsChangedListener(mSettingsListener);
        connect();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        super.onDestroy();
        mUserSession.removeListener(mSessionListener);
        mSettings.removeOnSettingsChangedListener(mSettingsListener);
        try {
            mClient.disconnect();
        } catch (MqttException e) {
            Log.e(TAG,"Disconnect error: ", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Client connected");
        return mMessenger.getBinder();
    }

    private void connect() {
        // Инициализация MQTT клиента
        mClient = new MqttAndroidClient(getApplicationContext(), mSettings.getBroker(),
                mUserSession.getSid());
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
                if (mReconnecting) { // Переподключение из-за изменения адреса в настройках
                    mReconnecting = false;
                    connect();
                }
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.d(TAG, "Incoming message: " + new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(false);
        options.setCleanSession(false);
        options.setUserName(mUserSession.getLogin());
        options.setPassword(mUserSession.getPassword().toCharArray());
        try {
            Log.d(TAG, "Connecting to " + mSettings.getBroker());
            mClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mClient.setBufferOpts(disconnectedBufferOptions);
                    subscribeToTopic();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(TAG, "Failed to connect to " + mSettings.getBroker());
                }
            });
        } catch (MqttException ex){
            Log.e(TAG, "Connecting error", ex);
        }
    }

    private void reconnect() {
        Log.d(TAG, "Reconnecting...");
        mReconnecting = true;
        try {
            if (mClient.isConnected()) {
                mClient.disconnect();
            } else {
                connect();
            }
        } catch (MqttException e) {
            Log.e(TAG, "Reconnecting error", e);
        }
    }

    private void resubscribe() {
        Log.d(TAG, "Resubscribing...");
        if (!TextUtils.isEmpty(mCurrentTopic)) {
            try {
                mClient.unsubscribe(mCurrentTopic);
            } catch (MqttException e) {
                Log.e(TAG, "Unsubscribe error", e);
            }
            subscribeToTopic();
       }
    }

    /**
     * Опубликовать сообщение.
     *
     * @param message сообщение, которое нужно засериализовать и отправить
     */
    private void publishMessage(Object message) {
        try {
            ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());
            byte[] data = mapper.writeValueAsBytes(new MqMessage(mUserSession.getSid(), message));
            MqttMessage mqMessage = new MqttMessage();
            mqMessage.setPayload(data);
            String topic = mSettings.getPublishTopic();
            mClient.publish(topic, mqMessage);
            Log.d(TAG, "Message published: " + topic + " : " + new String(data));
            if (!mClient.isConnected()) {
                Log.d(TAG, mClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (Throwable e) {
            Log.e(TAG, "Publish error", e);
        }
    }

    private void subscribeToTopic() {
        Log.d(TAG, "Subscribe to topic " + mSettings.getSubscribeTopic());
        final String topic = mSettings.getSubscribeTopic();
        try {
            mClient.subscribe(topic,0, null,
                    new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Subscribed to " + topic);
                    mCurrentTopic = topic;
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(TAG, "Failed to subscribe");
                }
            });
            mClient.subscribe(topic,0, new IMqttMessageListener() {

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    Log.d(TAG, "Message received: " + topic + " : " + new String(message.getPayload()));
                }
            });
        } catch (MqttException ex){
            Log.e(TAG, "Exception whilst subscribing");
        }
    }
    /**
     * Формат сообщения для отправки на сервер.
     */
    @SuppressWarnings("WeakerAccess")
    private static class MqMessage {

        static int sMsgCounter;

        public final String i;
        public final String x;
        public final Object p;

        public MqMessage(String sessionId, Object payload) {
            i = "id" + (++sMsgCounter);
            x = sessionId;
            p = payload;
        }
    }
    /**
     * Обработчик сообщений. Принимает и передает сообщения от клиентов в службу.
     */
    private static class MessageHandler extends Handler {

        private final MessagingService mService;

        MessageHandler(MessagingService service) {
            mService = service;
        }

        @Override
        public void handleMessage(Message msg) {
            mService.publishMessage(msg.obj);
        }
    }
}
