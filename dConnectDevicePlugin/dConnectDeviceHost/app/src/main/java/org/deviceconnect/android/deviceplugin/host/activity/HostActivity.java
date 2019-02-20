package org.deviceconnect.android.deviceplugin.host.activity;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Window;

import org.deviceconnect.android.deviceplugin.host.HostDeviceApplication;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.AlarmReceiver;

import java.util.Calendar;

public abstract class HostActivity extends Activity {
    private AlarmManager mAlarmManager;
    /** Application class instance. */
    protected HostDeviceApplication mApp;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        mApp = ((HostDeviceApplication) getApplication());
        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (getActivityName().contains("MultiWindow")) {
            stopResumePauseCheckerTimer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (getActivityName().contains("MultiWindow")) {
            startResumePauseCheckerTimer();
        }
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        if (!isInMultiWindowMode) {
            mApp.putShowActivityFlagFromAvailabilityService(getActivityName(), false);
        }
    }

    protected abstract String getActivityName();


    private void startResumePauseCheckerTimer() {
        Intent timer = new Intent(getApplicationContext(), AlarmReceiver.class);
        timer.putExtra("EXTRA_ACTIVITY_NAME", getActivityName());
        timer.setType(getActivityName());
        PendingIntent pendingTimer = PendingIntent.getBroadcast(getApplicationContext(), 0, timer, 0);
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(System.currentTimeMillis());
        time.add(Calendar.SECOND, 2);
        mAlarmManager.set(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(), pendingTimer);
    }

    private void stopResumePauseCheckerTimer() {
        Intent timer = new Intent(getApplicationContext(), AlarmReceiver.class);
        timer.setType(getActivityName());
        PendingIntent pendingTimer = PendingIntent.getBroadcast(getApplicationContext(), 0, timer, 0);
        pendingTimer.cancel();
        mAlarmManager.cancel(pendingTimer);
    }

}
