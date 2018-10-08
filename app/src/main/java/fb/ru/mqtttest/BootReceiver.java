package fb.ru.mqtttest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Автозагрузка обновления геолокации. Включается и выключется через PackageManager при старте
 * периодических запросов локации, чтобы поднимать службу локации при старте.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            ((App) context.getApplicationContext()).startGeoService();
        }
    }
}
