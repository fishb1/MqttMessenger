package fb.ru.mqtttest.common;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class VendorUtils {

    private static final String TAG = "VendorUtils";

    private static final String sVendor = android.os.Build.MANUFACTURER;
    private static final Map<String, ComponentName> sAutoStartActivities = new HashMap<>();
    static {
        sAutoStartActivities.put("xiaomi", new ComponentName("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        sAutoStartActivities.put("oppo", new ComponentName("com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
        sAutoStartActivities.put("vivo", new ComponentName("com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
        sAutoStartActivities.put("letv", new ComponentName("com.letv.android.letvsafe",
                "com.letv.android.letvsafe.AutobootManageActivity"));
        sAutoStartActivities.put("honor", new ComponentName("com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"));
    }

    public static void gotoAutoStartSetting(Context context) {
        Intent intent = new Intent();
        intent.setComponent(sAutoStartActivities.get(sVendor.toLowerCase()));
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        } else {
            Log.w(TAG, "Unable to resolve autostart activity, manufacturer=" + sVendor);
        }
    }

    public static boolean hasAutoStart() {
        return sAutoStartActivities.get(sVendor.toLowerCase()) != null;
    }
}
