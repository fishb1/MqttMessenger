package fb.ru.mqtttest.common;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import fb.ru.mqtttest.BootReceiver;

public class Utils {

    public static void setGeoServiceAutoBoot(Context context, boolean enable) {
        Log.d("Utils", "setGeoServiceAutoBoot() " + enable);
        ComponentName receiver = new ComponentName(context, BootReceiver.class);
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(receiver, enable ?
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    public static boolean isGeoServiceAutobootEnabled(Context context) {
        ComponentName receiver = new ComponentName(context, BootReceiver.class);
        PackageManager pm = context.getPackageManager();
        return pm.getComponentEnabledSetting(receiver)
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }
}
