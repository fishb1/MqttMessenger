package fb.ru.mqtttest;

import android.app.IntentService;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.util.Log;


/**
 * IntentService пытается прибить себя после обработки Intent, а мне нужно чтобы оснавная служба
 * с таймером жила как можно дольше. Чтобы долго не разбираться вынес прием и диспетчеризацию
 * локации в отдельный класс.
 */
public class GeoReceiverService extends IntentService {

    public GeoReceiverService() {
        super("GeoReceiverService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.d("GeoReceiverService", "onHandleIntent(), intent=" + intent);
        Intent intent1 = new Intent(GeoService.ACTION_NEW_LOCATION);
        intent1.putExtras(intent);
        sendBroadcast(intent1);
    }
}
