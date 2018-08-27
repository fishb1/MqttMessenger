package fb.ru.mqtttest;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.text.TextUtils;

import com.google.gson.Gson;

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

import fb.ru.mqtttest.common.Settings;
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
    private Settings mSettings;
    private Settings.OnSettingsChangedListener mSettingsListener = new Settings.OnSettingsChangedListener() {

        @Override
        public void onSettingsChanged(String settingName) {
            switch (settingName) {
                case Settings.PREF_MQTT_BROKER: {
                    reconnect();
                } break;
                case Settings.PREF_SUB_TOPIC: {
                    resubscribe();
                } break;
            }
        }
    };

    private UserSession mUserSession;
    private Messenger mMessenger; // Месэйджер, который прнимает сообщение от других компонент приложения и отсылает в MQTT. Юзается компонентами через его биндер

    public MessagingService() {
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Service created");
        super.onCreate();
        mMessenger = new Messenger(new MessageHandler());
        mUserSession = ((App) getApplication()).getUserSession();
        mSettings = ((App) getApplication()).getSettings();
        mSettings.addOnSettingsChangedListener(mSettingsListener);
        connect();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        super.onDestroy();
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
        mClient = new MqttAndroidClient(getApplicationContext(), HOST,
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
            public void messageArrived(String topic, MqttMessage message)  {
                Log.d(TAG, "Incoming message: " + new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

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
                    DisconnectedBufferOptions bufferOptions = new DisconnectedBufferOptions();
                    bufferOptions.setBufferEnabled(true);
                    bufferOptions.setBufferSize(1000);
                    bufferOptions.setPersistBuffer(true);
                    bufferOptions.setDeleteOldestMessages(false);
                    mClient.setBufferOpts(bufferOptions);
                    subscribeToTopic();
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

    private void reconnect() {
        Log.d(TAG, "Reconnecting...");
        mReconnecting = true;
        try {
            if (mClient.isConnected()) {
                mClient.disconnect();
                mCurrentTopic = null; // Подписка тоже снимется
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
                if (!TextUtils.isEmpty(mCurrentTopic)) {
                    mClient.unsubscribe(mCurrentTopic);
                    mCurrentTopic = null;
                }
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
            Gson gson = new Gson();
            MqttMessage mqMessage = new MqttMessage();
            mqMessage.setPayload(gson.toJson(message).getBytes());
            String topic = String.format(OUT_TOPIC, mUserSession.getLogin());
            mClient.publish(topic, mqMessage);
            Log.d(TAG, "Message published: " + topic + " : " + new String(mqMessage.getPayload()));
            try {
                Log.d(TAG, mClient.getBufferedMessageCount() + " messages in buffer.");
            } catch (Throwable e) {
                Log.e(TAG, "Error!", e);
            }
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
            });
            mClient.subscribe(topic,0, new IMqttMessageListener() {

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
            publishMessage(msg.obj);
        }
    }
}
