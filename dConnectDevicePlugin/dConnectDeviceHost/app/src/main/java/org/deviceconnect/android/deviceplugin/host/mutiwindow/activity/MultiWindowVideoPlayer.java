package org.deviceconnect.android.deviceplugin.host.mutiwindow.activity;

import org.deviceconnect.android.deviceplugin.host.mediaplayer.VideoConst;
import org.deviceconnect.android.deviceplugin.host.mediaplayer.VideoPlayer;

public class MultiWindowVideoPlayer extends VideoPlayer {
    protected String getActionForTargetToPlayer() {
        return VideoConst.SEND_MW_TO_VIDEOPLAYER;
    }

    protected String getActionForPlayerToTarget() {
        return VideoConst.SEND_VIDEOPLAYER_TO_MW;
    }
    @Override
    protected String getActivityName() {
        return MultiWindowVideoPlayer.class.getName();
    }
}
