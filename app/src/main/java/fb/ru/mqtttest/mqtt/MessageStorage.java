package fb.ru.mqtttest.mqtt;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.Observable;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Очередь сообщений.
 */
public class MessageStorage {

    private static MessageStorage sInstance;

    private final MessageQueueHelper mHelper;
    private final MessageQueueObservable mObservable = new MessageQueueObservable();

    private MessageStorage(MessageQueueHelper helper) {
        mHelper = helper;
    }

    public static synchronized MessageStorage getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new MessageStorage(new MessageQueueHelper(context.getApplicationContext()));
        }
        return sInstance;
    }

    /**
     * Положить сообщение.
     *
     * @param message JSON-объект в виде строки
     */
    public void putMessage(String message) {
        try (SQLiteDatabase db = mHelper.getWritableDatabase()) {
            ContentValues val = new ContentValues();
            val.put(MessageQueueHelper.Contact.TIME, System.currentTimeMillis());
            val.put(MessageQueueHelper.Contact.PAYLOAD, message);
            db.insert(MessageQueueHelper.Contact.TABLE_NAME, null, val);
            mObservable.notifyNewMessage(this);
        }
    }

    /**
     * Получить список сообщений в очереди.
     *
     * @return список сообщений
     */
    public List<StoredMessage> getMessages() {
        List<StoredMessage> messages = new ArrayList<>();
        try (SQLiteDatabase db = mHelper.getReadableDatabase();
            Cursor cursor = db.query(MessageQueueHelper.Contact.TABLE_NAME,
                    null, null, null, null, null,
                    MessageQueueHelper.Contact.TIME)) {
            int columnId = cursor.getColumnIndex(MessageQueueHelper.Contact.ID);
            int columnDate = cursor.getColumnIndex(MessageQueueHelper.Contact.TIME);
            int columnMsg = cursor.getColumnIndex(MessageQueueHelper.Contact.PAYLOAD);
            while (cursor.moveToNext()) {
                StoredMessage message = new StoredMessage();
                message.setId(cursor.getLong(columnId));
                message.setDate(new Date(cursor.getLong(columnDate)));
                message.setPayload(cursor.getString(columnMsg));
                messages.add(message);
            }
        }
        return messages;
    }

    /**
     * Удалить сообщение из очереди.
     *
     * @param id идентификатор сообщения
     */
    public void deleteMessage(long id) {
        try (SQLiteDatabase db = mHelper.getWritableDatabase()) {
            db.delete(MessageQueueHelper.Contact.TABLE_NAME,
                    MessageQueueHelper.Contact.ID + " = ?",
                    new String[] { String.valueOf(id) });
        }
    }

    /**
     * Bye.
     */
    public void clear() {
        try(SQLiteDatabase db = mHelper.getWritableDatabase()) {
            db.delete(MessageQueueHelper.Contact.TABLE_NAME, null, null);
        }
    }

    /**
     * Подучить поток событий очереди сообщений.
     */
    public Observable<MessageQueueObserver> getObservable() {
        return mObservable;
    }

    /**
     * Оповещение о добавлении нового сообщения.
     */
    private static class MessageQueueObservable extends Observable<MessageQueueObserver> {

        private void notifyNewMessage(MessageStorage queue) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onNewMessage(queue);
            }
        }
    }
}
