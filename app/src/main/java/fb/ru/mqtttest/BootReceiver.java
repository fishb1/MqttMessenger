package fb.ru.mqtttest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Автозагрузка обновления геолокации. Включается и выключется через PackageManager при старте
 * периодических запросов локации, чтобы поднимать службу локации при старте.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("BootReceiver", "onReceive() intent=" + intent);
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent geoService = new Intent(context, GeoService.class)
                    .setAction(GeoService.ACTION_START_UPDATES);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(geoService);
            } else {
                Log.d("BootReceiver", "Starting service: " + geoService);
                context.startService(geoService);
            }
        }
    }
}
