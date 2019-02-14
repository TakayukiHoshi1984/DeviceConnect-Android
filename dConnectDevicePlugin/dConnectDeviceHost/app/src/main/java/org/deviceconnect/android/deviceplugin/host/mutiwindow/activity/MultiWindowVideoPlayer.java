package org.deviceconnect.android.deviceplugin.host.mutiwindow.activity;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

import org.deviceconnect.android.deviceplugin.host.mediaplayer.VideoConst;
import org.deviceconnect.android.deviceplugin.host.mediaplayer.VideoPlayer;

public class MultiWindowVideoPlayer extends VideoPlayer {

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    @Override
    public void onResume() {
        super.onResume();
    }
    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }
    protected Class<? extends Activity> getActivityClass() {
        return MultiWindowVideoPlayer.class;
    }

    protected String getActionForTargetToPlayer() {
        return VideoConst.SEND_MW_TO_VIDEOPLAYER;
    }

    protected String getActionForPlayerToTarget() {
        return VideoConst.SEND_VIDEOPLAYER_TO_MW;
    }
}
