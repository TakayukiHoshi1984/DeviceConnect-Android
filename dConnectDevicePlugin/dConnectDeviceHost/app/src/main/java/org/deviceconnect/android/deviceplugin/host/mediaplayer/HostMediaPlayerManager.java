/*
 HostMediaPlayerManager.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.mediaplayer;


import android.content.Intent;
import android.net.Uri;

import org.deviceconnect.android.deviceplugin.host.mutiwindow.activity.MultiWindowVideoPlayer;
import org.deviceconnect.android.message.DevicePluginContext;
/**
 * Android端末のメディアを管理する.
 *
 * @author NTT DOCOMO, INC.
 */
public class HostMediaPlayerManager extends MediaPlayerManager {
    /**
     * コンストラクタ.
     * @param pluginContext PluginのContext
     */
    public HostMediaPlayerManager(DevicePluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    protected String getTargetToVideoPlayerAction() {
        return VideoConst.SEND_HOSTDP_TO_VIDEOPLAYER;
    }

    @Override
    protected String getVideoPlayerToTargetAction() {
        return VideoConst.SEND_VIDEOPLAYER_TO_HOSTDP;
    }

    @Override
    protected boolean isShowMediaPlayer() {
        return getApplication().getShowActivityAndData(VideoPlayer.class.getName()) != null;
    }

    @Override
    protected void playMediaPlayer() {
        mMediaStatus = MEDIA_PLAYER_PLAY;
        Intent mIntent = new Intent(getTargetToVideoPlayerAction());
        mIntent.setClass(getContext(), VideoPlayer.class);
        Uri data = Uri.parse(mMyCurrentFilePath);
        mIntent.setDataAndType(data, mMyCurrentFileMIMEType);
        mIntent.putExtra(VideoConst.EXTRA_NAME, VideoConst.EXTRA_VALUE_VIDEO_PLAYER_PLAY);
//        mIntent.setFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT | Intent.FLAG_ACTIVITY_NEW_TASK);
        mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        getApplication().putShowActivityAndData(VideoPlayer.class.getName(), mIntent);
        getContext().startActivity(mIntent);
        sendOnStatusChangeEvent("play");
    }

}
