package org.deviceconnect.android.deviceplugin.host.util;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;

@SuppressWarnings("deprecation")
public final class HostUtils {

    // コンストラクタ(未使用)
    private HostUtils(){}

    /**
     * Get the class name of the Activity being displayed at the top of the screen.
     *
     * @return class name.
     */
    public static String getClassnameOfTopActivity(final Context context) {
        ActivityManager activityMgr = (ActivityManager) context.getSystemService(Service.ACTIVITY_SERVICE);
        return activityMgr.getRunningTasks(1).get(0).topActivity.getClassName();
    }
}
