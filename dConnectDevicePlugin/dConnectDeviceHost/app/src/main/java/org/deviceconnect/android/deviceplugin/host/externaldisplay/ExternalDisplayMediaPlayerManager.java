/*
 ExternalDisplayMediaPlayerManager.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.externaldisplay;

import android.net.Uri;

import org.deviceconnect.android.deviceplugin.host.mediaplayer.MediaPlayerManager;
import org.deviceconnect.android.deviceplugin.host.mediaplayer.VideoConst;
import org.deviceconnect.android.message.DevicePluginContext;

/**
 * Android端末のメディアを管理する.
 * Android端末がキャストしている先のディスプレイにメディアを再生する.
 *
 * @author NTT DOCOMO, INC.
 */
public class ExternalDisplayMediaPlayerManager extends MediaPlayerManager {
    /**
     * 外部ディスプレイサービス.
     */
    private ExternalDisplayService mEDService;

    /**
     * コンストラクタ.
     * @param pluginContext PluginのContext
     * @param edService 外部ディスプレイサービス
     */
    public ExternalDisplayMediaPlayerManager(DevicePluginContext pluginContext, ExternalDisplayService edService) {
        super(pluginContext);
        mEDService = edService;
    }

    @Override
    protected String getTargetToVideoPlayerAction() {
        return VideoConst.SEND_ED_TO_VIDEOPLAYER;
    }

    @Override
    protected String getVideoPlayerToTargetAction() {
        return VideoConst.SEND_VIDEOPLAYER_TO_ED;
    }

    @Override
    protected boolean isShowMediaPlayer() {
        return mEDService.isMediaPlayerPresentation();
    }

    @Override
    protected void playMediaPlayer() {
        mMediaStatus = MEDIA_PLAYER_PLAY;
        Uri data = Uri.parse(mMyCurrentFilePath);
        mEDService.showMediaPlayerDisplay(data);
        sendOnStatusChangeEvent("play");
    }
}
