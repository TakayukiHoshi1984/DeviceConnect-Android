package org.deviceconnect.android.deviceplugin.host.mutiwindow.activity;

import org.deviceconnect.android.deviceplugin.host.activity.KeyEventProfileActivity;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.profile.MultiWindowKeyEventProfile;

import static org.deviceconnect.android.deviceplugin.host.mutiwindow.profile.MultiWindowKeyEventProfile.ACTION_MV_KEYEVENT;

public class MultiWindowKeyEventProfileActivity extends KeyEventProfileActivity {
    protected String getActivityName() {
        return MultiWindowKeyEventProfileActivity.class.getName();
    }

    protected String getActionForFinishKeyEventActivity() {
        return MultiWindowKeyEventProfile.ACTION_MV_FINISH_KEYEVENT_ACTIVITY;
    }
    protected String getActionForSendEvent() {
        return ACTION_MV_KEYEVENT;
    }
}
