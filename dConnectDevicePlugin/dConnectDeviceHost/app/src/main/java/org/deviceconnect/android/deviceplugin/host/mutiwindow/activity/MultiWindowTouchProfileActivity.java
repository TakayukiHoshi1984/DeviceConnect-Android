package org.deviceconnect.android.deviceplugin.host.mutiwindow.activity;

import org.deviceconnect.android.deviceplugin.host.activity.TouchProfileActivity;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.profile.MultiWindowTouchProfile;

import static org.deviceconnect.android.deviceplugin.host.mutiwindow.profile.MultiWindowTouchProfile.ACTION_MV_TOUCH;

public class MultiWindowTouchProfileActivity extends TouchProfileActivity {
    protected String getActivityName() {
        return MultiWindowTouchProfileActivity.class.getName();
    }

    protected String getActionForFinishTouchActivity() {
        return MultiWindowTouchProfile.ACTION_FINISH_MW_TOUCH_ACTIVITY;
    }
    protected String getActionForSendEvent() {
        return ACTION_MV_TOUCH;
    }
}
