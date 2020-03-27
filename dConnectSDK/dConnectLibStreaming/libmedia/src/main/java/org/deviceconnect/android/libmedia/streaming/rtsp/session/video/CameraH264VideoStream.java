package org.deviceconnect.android.libmedia.streaming.rtsp.session.video;

import android.content.Context;
import android.view.Surface;

import org.deviceconnect.android.libmedia.streaming.video.CameraSurfaceVideoEncoder;
import org.deviceconnect.android.libmedia.streaming.video.VideoEncoder;

public class CameraH264VideoStream extends H264VideoStream {
    /**
     * 映像用エンコーダ.
     */
    private CameraSurfaceVideoEncoder mVideoEncoder;

    /**
     * コンストラクタ.
     *
     * @param context コンテキスト
     */
    public CameraH264VideoStream(Context context) {
        super();
        mVideoEncoder = new CameraSurfaceVideoEncoder(context);
    }

    @Override
    public VideoEncoder getVideoEncoder() {
        return mVideoEncoder;
    }

    /**
     * カメラの映像を描画する Surface を追加します.
     *
     * @param surface Surface
     */
    public void addSurface(Surface surface) {
        mVideoEncoder.addSurface(surface);
    }

    /**
     * カメラの映像を描画する Surface を削除します.
     *
     * @param surface Surface
     */
    public void removeSurface(Surface surface) {
        mVideoEncoder.removeSurface(surface);
    }
}
