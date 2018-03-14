package fb.ru.mqtttest;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

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

/**
 * Служба отвечающая за взаимодействие с MQ TT сервером. Бедет поддерживать трэд с автоматическиой
 * отправкой сообщений геолокации и делать разовую отправку по запросу.
 */
public class MessagingService extends Service {

    private static final String TAG = "MessagingService";

    private MqttAndroidClient mClient;
    private Settings mSettings;
    private UserSession mUserSession;
    private UserSession.Listener mSessionListener = new UserSession.Listener() {
        @Override
        public void onSessionStop(UserSession session) {
            // TODO stop sending messages
        }
    };
    private Messenger mMessenger;

    public MessagingService() {
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        mMessenger = new Messenger(new MessageHandler(this));
        mUserSession = ((App) getApplication()).getUserSession();
        mSettings = ((App) getApplication()).getSettings();
        mUserSession.addListener(mSessionListener);
        connect();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        mUserSession.removeListener(mSessionListener);
        try {
            mClient.disconnect();
        } catch (MqttException e) {
            Log.e(TAG,"Disconnect error: ", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return mMessenger.getBinder();
    }

    private void connect() {
        mClient = new MqttAndroidClient(getApplicationContext(), mSettings.getAddress(),
                mUserSession.getSid());
        mClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    Log.d(TAG, "Reconnected to : " + serverURI);
                    // Because Clean Session is true, we need to re-subscribe
                    subscribeToTopic();
                } else {
                    Log.d(TAG, "Connected to: " + serverURI);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.d(TAG, "The Connection was lost.");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.d(TAG, "Incoming message: " + new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setUserName(mUserSession.getLogin());
        mqttConnectOptions.setPassword(mUserSession.getPassword().toCharArray());

        try {
            Log.d(TAG, "Connecting to ...");
            mClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
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
                    Log.d(TAG, "Failed to connect to: ...");
                }
            });
        } catch (MqttException ex){
            ex.printStackTrace();
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
            Log.e(TAG, "Error", e);
        }
    }

    private void subscribeToTopic() {
        Log.d(TAG, "subscribeToTopic: " + mSettings.getSubscribeTopic());
        try {
            mClient.subscribe(mSettings.getSubscribeTopic(),0, null,
                    new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Subscribed!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(TAG, "Failed to subscribe");
                }
            });
            mClient.subscribe(mSettings.getSubscribeTopic(),0, new IMqttMessageListener() {

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

        public MessageHandler(MessagingService service) {
            mService = service;
        }

        @Override
        public void handleMessage(Message msg) {
            mService.publishMessage(msg.obj);
        }
    }
}
