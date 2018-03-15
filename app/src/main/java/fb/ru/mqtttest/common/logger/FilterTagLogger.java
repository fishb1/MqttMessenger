package fb.ru.mqtttest.common.logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Логгер с фильтром по тэгу. Передает дальше только сообщения с указанными тэгами.
 *
 * Created by kolyan on 15.03.18.
 */
public class FilterTagLogger implements Logger {

    private Logger mNext;
    private Set<String> mTags = new HashSet<>();

    public FilterTagLogger(String... tags) {
        mTags.addAll(Arrays.asList(tags));
    }

    public void setNext(Logger logger) {
        mNext = logger;
    }

    @Override
    public void println(int priority, String tag, String msg, Throwable tr) {
        if (mNext != null && mTags.contains(tag)) {
            mNext.println(priority, tag, msg, tr);
        }
    }
}
