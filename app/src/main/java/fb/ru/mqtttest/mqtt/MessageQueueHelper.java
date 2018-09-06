package fb.ru.mqtttest.mqtt;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Вспомогательный класс для управления базой данных очереди сообщений.
 */
public class MessageQueueHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "messagesDb";
    private static final int DB_VERSION = 1;

    private static final String SQL_CREATE_TABLE =
            "CREATE TABLE " + Contact.TABLE_NAME  + " (" +
                    Contact.ID + " INTEGER PRIMARY KEY, " +
                    Contact.TIME + " INTEGER, " +
                    Contact.PAYLOAD + " TEXT" +
            ")";
    private static final String SQL_DROP_TABLE =
            "DROP TABLE IF EXISTS " + Contact.TABLE_NAME;

    MessageQueueHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DROP_TABLE);
        onCreate(db);
    }

    /**
     * Поля и название таблицы очереди сообщений.
     */
    interface Contact {

        String TABLE_NAME = "message_queue";
        String ID = "id";
        String TIME = "time";
        String PAYLOAD = "payload";
    }
}
