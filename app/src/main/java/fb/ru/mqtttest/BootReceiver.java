package fb.ru.mqtttest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Автозагрузка обновления геолокации. Включается и выключется через PackageManager при старте
 * периодических запросов локации, чтобы поднимать службу локации при старте.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent geoService = new Intent(context, GeoService2.class)
                    .setAction(GeoService2.ACTION_START_UPDATES);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(geoService);
            } else {
                context.startService(geoService);
            }
        }
    }
}
