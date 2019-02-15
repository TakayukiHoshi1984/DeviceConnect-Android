package org.deviceconnect.android.deviceplugin.host.mutiwindow.profile;

import android.app.Activity;
import android.content.Intent;

import org.deviceconnect.android.deviceplugin.host.mutiwindow.activity.MultiWindowTouchProfileActivity;
import org.deviceconnect.android.deviceplugin.host.profile.HostTouchProfile;

public class MultiWindowTouchProfile extends HostTouchProfile {
    /** Finish touch profile activity action. */
    public static final String ACTION_FINISH_MW_TOUCH_ACTIVITY =
            "org.deviceconnect.android.deviceplugin.host.multiwindow.touch.FINISH";
    /** Finish touch event profile activity action. */
    public static final String ACTION_MV_TOUCH =
            "org.deviceconnect.android.deviceplugin.host.multiwindow.touch.action.KEY_EVENT";

    public MultiWindowTouchProfile() {
        super();
    }
    protected Class<? extends Activity> getTouchActivityClass() {
        return MultiWindowTouchProfileActivity.class;
    }
    protected String getActionForFinishTouchActivity() {
        return ACTION_FINISH_MW_TOUCH_ACTIVITY;
    }
    protected String getActionForSendEvent() {
        return ACTION_MV_TOUCH;
    }
    protected int getActivityFlag() {
        return Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT | Intent.FLAG_ACTIVITY_NEW_TASK;
    }
}
