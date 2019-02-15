package org.deviceconnect.android.deviceplugin.host.mutiwindow.profile;

import android.app.Activity;
import android.content.Intent;

import org.deviceconnect.android.deviceplugin.host.mutiwindow.activity.MultiWindowKeyEventProfileActivity;
import org.deviceconnect.android.deviceplugin.host.profile.HostKeyEventProfile;

public class MultiWindowKeyEventProfile extends HostKeyEventProfile {
    /** Finish key event profile activity action. */
    public static final String ACTION_MV_FINISH_KEYEVENT_ACTIVITY =
            "org.deviceconnect.android.deviceplugin.host.multiwindow.keyevent.FINISH";
    /** Finish key event profile activity action. */
    public static final String ACTION_MV_KEYEVENT =
            "org.deviceconnect.android.deviceplugin.host.multiwindow.keyevent.action.KEY_EVENT";

    public MultiWindowKeyEventProfile() {
        super();
    }
    protected Class<? extends Activity> getKeyEventActivityClass() {
        return MultiWindowKeyEventProfileActivity.class;
    }

    protected String getActionForFinishKeyEventActivity() {
        return ACTION_MV_FINISH_KEYEVENT_ACTIVITY;
    }
    protected String getActionForSendEvent() {
        return ACTION_MV_KEYEVENT;
    }
    protected int getActivityFlag() {
        return Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT | Intent.FLAG_ACTIVITY_NEW_TASK;
    }
}
