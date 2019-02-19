/*
 VideoPlayer.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */

package org.deviceconnect.android.deviceplugin.host.mediaplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.VideoView;

import org.deviceconnect.android.deviceplugin.host.HostDeviceApplication;
import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.activity.HostActivity;

/**
 * Video Player.
 * 
 * @author NTT DOCOMO, INC.
 */
public class VideoPlayer extends HostActivity implements OnCompletionListener {

    /** VideoView. */
    private VideoView mVideoView;

    /** URI. */
    private Uri mUri;

    /** Ready flag. */
    private Boolean mIsReady = false;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_player);

        // ステータスバーを消す
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );

        mVideoView = findViewById(R.id.videoView);

        // 再生するVideoのURI
        Intent mIntent = this.getIntent();
        mUri = mIntent.getData();
        mApp.putShowActivityAndData(getActivityName(), mIntent);

    }

    @Override
    public void onResume() {
        super.onResume();
        // ReceiverをRegister
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(getActionForTargetToPlayer());
        registerReceiver(mReceiver, mIntentFilter);

        MediaController mMediaController = new MediaController(this);
        mMediaController.setVisibility(View.GONE);
        mMediaController.setAnchorView(mVideoView);

        mVideoView.setMediaController(mMediaController);
        mVideoView.setKeepScreenOn(true);
        mVideoView.setVideoURI(mUri);
        mVideoView.requestFocus();
        mVideoView.setOnCompletionListener(this);
        mVideoView.setOnPreparedListener((mp) -> {
            mVideoView.start();
            mIsReady = true;
        });
        mApp.putActivityResumePauseFlag(getActivityName(), true);
    }
    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // ReceiverをUnregister
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        mApp.removeShowActivityAndData(getActivityName());
        mApp.putActivityResumePauseFlag(getActivityName(), false);

        super.onDestroy();
    }

    /**
     * 受信. 受信用のReceiver
     */
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (intent.getAction().equals(getActionForTargetToPlayer())) {
                String mVideoAction = intent.getStringExtra(VideoConst.EXTRA_NAME);
                if (mVideoAction.equals(VideoConst.EXTRA_VALUE_VIDEO_PLAYER_PLAY)) {
                    mVideoView.resume(); // resume()で動画の最初から再生される
                } else if (mVideoAction.equals(VideoConst.EXTRA_VALUE_VIDEO_PLAYER_STOP)) {
                    if (mIsReady) {
                        mVideoView.stopPlayback();
                    }
                    unregisterReceiver(mReceiver);
                    mReceiver = null;
                    mIsReady = false;
                    Intent mIntent = new Intent(getActionForPlayerToTarget());
                    mIntent.putExtra(VideoConst.EXTRA_NAME, VideoConst.EXTRA_VALUE_VIDEO_PLAYER_STOP);
                    sendBroadcast(mIntent);
                    finish();
                } else if (mVideoAction.equals(VideoConst.EXTRA_VALUE_VIDEO_PLAYER_PAUSE)) {
                    mVideoView.pause();
                } else if (mVideoAction.equals(VideoConst.EXTRA_VALUE_VIDEO_PLAYER_RESUME)) {
                    mVideoView.start(); // start()でpause()で止めたところから再生される
                } else if (mVideoAction.equals(VideoConst.EXTRA_VALUE_VIDEO_PLAYER_SEEK)) {
                    int pos = intent.getIntExtra("pos", -1);
                    mVideoView.seekTo(pos);
                } else if (mVideoAction.equals(VideoConst.EXTRA_VALUE_VIDEO_PLAYER_GET_POS)) {
                    Intent mIntent = new Intent(getActionForPlayerToTarget());
                    mIntent.putExtra(VideoConst.EXTRA_NAME, VideoConst.EXTRA_VALUE_VIDEO_PLAYER_PLAY_POS);
                    mIntent.putExtra("pos", mVideoView.getCurrentPosition());
                    sendBroadcast(mIntent);
                }
            }
        }
    };

    @Override
    public void onCompletion(final MediaPlayer mp) {
        mIsReady = false;
        Intent mIntent = new Intent(getActionForPlayerToTarget());
        mIntent.putExtra(VideoConst.EXTRA_NAME, VideoConst.EXTRA_VALUE_VIDEO_PLAYER_PLAY_COMPLETION);
        sendBroadcast(mIntent);
        finish();
    }


    protected String getActionForTargetToPlayer() {
        return VideoConst.SEND_HOSTDP_TO_VIDEOPLAYER;
    }

    protected String getActionForPlayerToTarget() {
        return VideoConst.SEND_VIDEOPLAYER_TO_HOSTDP;
    }

    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        if (!isInMultiWindowMode) {
            HostDeviceApplication app = (HostDeviceApplication) getApplication();
            app.putShowActivityFlagFromAvailabilityService(getActivityName(), false);
        }
    }

    @Override
    protected String getActivityName() {
        return VideoPlayer.class.getName();
    }
}
