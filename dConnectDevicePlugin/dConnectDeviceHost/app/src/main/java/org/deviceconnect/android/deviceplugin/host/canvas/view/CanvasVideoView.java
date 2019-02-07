/*
 CanvasVideoView.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.canvas.view;

import android.app.Activity;
import android.view.View;
import android.widget.MediaController;
import android.widget.VideoView;

import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawImageObject;
import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;

/**
 * CanvasでのVideoViewの機能を操作する.
 * @author NTT DOCOMO, INC.
 */
public class CanvasVideoView {
    /** VideoView. */
    private VideoView mCanvasVideoView;
    /** Canvasに表示するリソース情報を持つオブジェクト. */
    private CanvasDrawImageObject mDrawImageObject;

    /**
     * コンストラクタ.
     * @param videoView videoView
     * @param drawObject Canvasに表示するリソース情報を持つオブジェクト
     */
    public CanvasVideoView(final VideoView videoView,
                         final CanvasDrawImageObject drawObject) {
        mCanvasVideoView = videoView;
        mDrawImageObject = drawObject;
        mCanvasVideoView.setKeepScreenOn(true);
    }

    /**
     * Viewを非表示にする.
     */
    public void gone() {
        mCanvasVideoView.setVisibility(View.GONE);
    }

    /**
     * Viewを表示する.
     */
    public void visibility() {
        mCanvasVideoView.setVisibility(View.VISIBLE);
    }

    /**
     * VideoViewを初期化する.
     */
    public void initVideoView() {
        mCanvasVideoView.setVideoPath(mDrawImageObject.getData());
        mCanvasVideoView.requestFocus();
        mCanvasVideoView.start();
        // 再生が完了したらループ再生を行う
        mCanvasVideoView.setOnCompletionListener((mp) -> {
            mp.seekTo(0);
            mp.start();
        });
        mCanvasVideoView.setOnErrorListener((mp, what, extra) -> {
            return false;
        });

    }

}
