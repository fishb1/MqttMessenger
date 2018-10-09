package fb.ru.mqtttest.common;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

    public static String md5hash(String string) {
        StringBuilder hexString = new StringBuilder();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(string.getBytes());

            for (byte aHash : hash) {
                String hex;
                if ((0xff & aHash) < 0x10) {
                    hex = "0" + Integer.toHexString((0xFF & aHash));
                } else {
                    hex = Integer.toHexString(0xFF & aHash);
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
