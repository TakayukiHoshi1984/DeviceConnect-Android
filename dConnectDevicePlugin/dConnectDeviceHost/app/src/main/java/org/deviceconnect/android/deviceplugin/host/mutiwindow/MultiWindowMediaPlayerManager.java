package org.deviceconnect.android.deviceplugin.host.mutiwindow;

import android.content.Intent;
import android.net.Uri;

import org.deviceconnect.android.deviceplugin.host.mediaplayer.MediaPlayerManager;
import org.deviceconnect.android.deviceplugin.host.mediaplayer.VideoConst;
import org.deviceconnect.android.deviceplugin.host.mediaplayer.VideoPlayer;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.activity.MultiWindowVideoPlayer;
import org.deviceconnect.android.message.DevicePluginContext;

public class MultiWindowMediaPlayerManager extends MediaPlayerManager {
    /**
     * コンストラクタ.
     *
     * @param pluginContext PluginのContext
     */
    public MultiWindowMediaPlayerManager(DevicePluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    protected String getTargetToVideoPlayerAction() {
        return VideoConst.SEND_MW_TO_VIDEOPLAYER;
    }

    @Override
    protected String getVideoPlayerToTargetAction() {
        return VideoConst.SEND_VIDEOPLAYER_TO_MW;
    }

    @Override
    protected boolean isShowMediaPlayer() {
        return getApplication().getShowActivityAndData(MultiWindowVideoPlayer.class.getName()) != null;
    }

    @Override
    protected void playMediaPlayer() {
        mMediaStatus = MEDIA_PLAYER_PLAY;
        Intent mIntent = new Intent(getTargetToVideoPlayerAction());
        mIntent.setClass(getContext(), MultiWindowVideoPlayer.class);
        Uri data = Uri.parse(mMyCurrentFilePath);
        mIntent.setDataAndType(data, mMyCurrentFileMIMEType);
        mIntent.putExtra(VideoConst.EXTRA_NAME, VideoConst.EXTRA_VALUE_VIDEO_PLAYER_PLAY);
//        mIntent.setFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT | Intent.FLAG_ACTIVITY_NEW_TASK);
        mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getApplication().putShowActivityAndData(MultiWindowVideoPlayer.class.getName(), mIntent);
        getContext().startActivity(mIntent);
        sendOnStatusChangeEvent("play");
    }
}
