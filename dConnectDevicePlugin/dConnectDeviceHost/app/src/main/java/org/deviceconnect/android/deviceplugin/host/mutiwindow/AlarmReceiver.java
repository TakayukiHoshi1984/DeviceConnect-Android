package org.deviceconnect.android.deviceplugin.host.mutiwindow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.deviceconnect.android.deviceplugin.host.HostDeviceApplication;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String activityName = intent.getStringExtra("EXTRA_ACTIVITY_NAME");
        HostDeviceApplication app = ((HostDeviceApplication)context.getApplicationContext());
        app.putShowActivityFlagFromAvailabilityService(activityName, false);
        app.putActivityResumePauseFlag(activityName, false);
        app.removeShowActivityAndData(activityName);
    }
}
