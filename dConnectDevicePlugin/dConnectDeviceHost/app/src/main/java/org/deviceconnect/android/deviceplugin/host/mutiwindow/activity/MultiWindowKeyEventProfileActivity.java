package org.deviceconnect.android.deviceplugin.host.mutiwindow.activity;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import org.deviceconnect.android.deviceplugin.host.activity.KeyEventProfileActivity;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.profile.MultiWindowKeyEventProfile;

import static org.deviceconnect.android.deviceplugin.host.mutiwindow.profile.MultiWindowKeyEventProfile.ACTION_MV_KEYEVENT;

public class MultiWindowKeyEventProfileActivity extends KeyEventProfileActivity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }
    protected String getActivityName() {
        return MultiWindowKeyEventProfileActivity.class.getName();
    }

    protected String getActionForFinishKeyEventActivity() {
        return MultiWindowKeyEventProfile.ACTION_MV_FINISH_KEYEVENT_ACTIVITY;
    }
    protected String getActionForSendEvent() {
        return ACTION_MV_KEYEVENT;
    }
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        if (!isInMultiWindowMode) {
            mApp.putShowActivityFlag(getActivityName(), false);
        }
    }
}
