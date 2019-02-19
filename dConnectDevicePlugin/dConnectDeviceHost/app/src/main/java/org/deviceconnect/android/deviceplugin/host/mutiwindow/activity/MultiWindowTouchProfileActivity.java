package org.deviceconnect.android.deviceplugin.host.mutiwindow.activity;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;

import org.deviceconnect.android.deviceplugin.host.HostDeviceApplication;
import org.deviceconnect.android.deviceplugin.host.activity.TouchProfileActivity;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.profile.MultiWindowTouchProfile;

import static org.deviceconnect.android.deviceplugin.host.mutiwindow.profile.MultiWindowTouchProfile.ACTION_MV_TOUCH;

public class MultiWindowTouchProfileActivity extends TouchProfileActivity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    @Override
    protected void onResume() {
        super.onResume();
        ((HostDeviceApplication) getApplication()).putActivityResumePauseFlag(getActivityName(), true);
    }
    @Override
    protected void onPause() {
        super.onPause();
        ((HostDeviceApplication) getApplication()).putActivityResumePauseFlag(getActivityName(), false);
    }
    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }
    protected String getActivityName() {
        return MultiWindowTouchProfileActivity.class.getName();
    }

    protected String getActionForFinishTouchActivity() {
        return MultiWindowTouchProfile.ACTION_FINISH_MW_TOUCH_ACTIVITY;
    }
    protected String getActionForSendEvent() {
        return ACTION_MV_TOUCH;
    }
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        if (!isInMultiWindowMode) {
            mApp.putShowActivityFlagFromAvailabilityService(getActivityName(), false);
        }
    }
}
