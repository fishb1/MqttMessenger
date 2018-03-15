package fb.ru.mqtttest.common.logger;

import android.util.Log;

/**
 * Логгер в стандартный лог Android.
 *
 * Created by kolyan on 23.01.18.
 */
public class AndroidLogWrapper implements Logger {

    private Logger mNext;

    public void setNext(Logger logger) {
        mNext = logger;
    }

    @Override
    public final void println(int priority, String tag, String msg, Throwable tr) {
        String useMsg = msg;
        if (useMsg == null) {
            useMsg = "";
        }
        if (tr != null) {
            useMsg += Log.getStackTraceString(tr);
        }
        Log.println(priority, tag, useMsg);
        // Передать сообщение дальше
        if (mNext != null) {
            mNext.println(priority, tag, msg, tr);
        }
    }
}
