package fb.ru.mqtttest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import fb.ru.mqtttest.ui.HomeActivity;
import fb.ru.mqtttest.ui.LauncherActivity;

/**
 * Автозагрузка обновления геолокации. Включается и выключется через PackageManager при старте
 * периодических запросов локации, чтобы поднимать службу локации при старте.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            context.startActivity(new Intent(context, LauncherActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY)
                    .setAction(HomeActivity.ACTION_RESTART));
        }
    }
}
