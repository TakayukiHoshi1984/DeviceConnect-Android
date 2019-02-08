package org.deviceconnect.android.deviceplugin.host.externaldisplay;

import android.app.Presentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.VideoView;

import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.mediaplayer.VideoConst;
/**
 * 外部ディスプレイにAndroid端末のメディアを再生する.
 *
 * @author NTT DOCOMO, INC.
 */
public class ExternalDisplayMediaPlayerPresentation extends Presentation implements MediaPlayer.OnCompletionListener {
    /** VideoView. */
    private VideoView mVideoView;

    /** URI. */
    private Uri mUri;
    /** 外部ディスプレイサービス. */
    private ExternalDisplayService mEDService;
    /** Ready flag. */
    private Boolean mIsReady = false;

    /**
     * コンストラクタ.
     * @param outerContext Context
     * @param display ディスプレイ
     * @param edService 外部ディスプレイサービス
     * @param uri 再生するメディアファイルのURI
     */
    public ExternalDisplayMediaPlayerPresentation(final Context outerContext,
                                                  final Display display,
                                                  final ExternalDisplayService edService,
                                                  final Uri uri) {
        super(outerContext, display);
        mEDService = edService;
        mUri = uri;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // タイトルを非表示
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.video_player);

        // ステータスバーを消す
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );

        mVideoView = findViewById(R.id.videoView);
        // ReceiverをRegister
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(VideoConst.SEND_ED_TO_VIDEOPLAYER);
        getContext().registerReceiver(mReceiver, mIntentFilter);

        mVideoView.setKeepScreenOn(true);
        mVideoView.setVideoURI(mUri);
        mVideoView.requestFocus();
        mVideoView.setOnCompletionListener(this);
        mVideoView.setOnPreparedListener((mp) -> {
            mVideoView.start();
            mIsReady = true;
        });
    }

    @Override
    protected void onStop() {
        super.onStop();

        // ReceiverをUnregister
        if (mReceiver != null) {
            getContext().unregisterReceiver(mReceiver);
        }
    }
    /**
     * 受信. 受信用のReceiver
     */
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (intent.getAction().equals(VideoConst.SEND_ED_TO_VIDEOPLAYER)) {
                    String mVideoAction = intent.getStringExtra(VideoConst.EXTRA_NAME);
                    if (mVideoAction.equals(VideoConst.EXTRA_VALUE_VIDEO_PLAYER_PLAY)) {
                        mVideoView.resume(); // resume()で動画の最初から再生される
                    } else if (mVideoAction.equals(VideoConst.EXTRA_VALUE_VIDEO_PLAYER_STOP)) {
                        if (mIsReady) {
                            mVideoView.stopPlayback();
                        }
                        getContext().unregisterReceiver(mReceiver);
                        mReceiver = null;
                        mIsReady = false;
                        Intent mIntent = new Intent(VideoConst.SEND_VIDEOPLAYER_TO_ED);
                        mIntent.putExtra(VideoConst.EXTRA_NAME, VideoConst.EXTRA_VALUE_VIDEO_PLAYER_STOP);
                        getContext().sendBroadcast(mIntent);
                        mEDService.disconnectMediaPlayerDisplay();
                    } else if (mVideoAction.equals(VideoConst.EXTRA_VALUE_VIDEO_PLAYER_PAUSE)) {
                        mVideoView.pause();
                    } else if (mVideoAction.equals(VideoConst.EXTRA_VALUE_VIDEO_PLAYER_RESUME)) {
                        mVideoView.start(); // start()でpause()で止めたところから再生される
                    } else if (mVideoAction.equals(VideoConst.EXTRA_VALUE_VIDEO_PLAYER_SEEK)) {
                        int pos = intent.getIntExtra("pos", -1);
                        mVideoView.seekTo(pos);
                    } else if (mVideoAction.equals(VideoConst.EXTRA_VALUE_VIDEO_PLAYER_GET_POS)) {
                        Intent mIntent = new Intent(VideoConst.SEND_VIDEOPLAYER_TO_HOSTDP);
                        mIntent.putExtra(VideoConst.EXTRA_NAME, VideoConst.EXTRA_VALUE_VIDEO_PLAYER_PLAY_POS);
                        mIntent.putExtra("pos", mVideoView.getCurrentPosition());
                        getContext().sendBroadcast(mIntent);
                    }
            }
        }
    };

    @Override
    public void onCompletion(final MediaPlayer mp) {
        mIsReady = false;
        Intent mIntent = new Intent(VideoConst.SEND_VIDEOPLAYER_TO_ED);
        mIntent.putExtra(VideoConst.EXTRA_NAME, VideoConst.EXTRA_VALUE_VIDEO_PLAYER_PLAY_COMPLETION);
        getContext().sendBroadcast(mIntent);
        dismiss();
    }
}
