package org.deviceconnect.android.deviceplugin.host.activity;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Window;

import org.deviceconnect.android.deviceplugin.host.HostDeviceApplication;

public abstract class HostActivity extends Activity {
    /** Application class instance. */
    protected HostDeviceApplication mApp;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        mApp = ((HostDeviceApplication) getApplication());
    }
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        if (!isInMultiWindowMode) {
            mApp.putShowActivityFlagFromAvailabilityService(getActivityName(), false);
        }
    }

    protected abstract String getActivityName();
}
