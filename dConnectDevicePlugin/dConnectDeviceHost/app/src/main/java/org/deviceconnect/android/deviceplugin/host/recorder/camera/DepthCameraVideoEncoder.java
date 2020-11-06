package org.deviceconnect.android.deviceplugin.host.recorder.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.Image;
import android.util.Log;
import android.view.Surface;

import org.deviceconnect.android.deviceplugin.host.camera.Camera2Helper;
import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapper;
import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapperException;
import org.deviceconnect.android.deviceplugin.host.recorder.HostMediaRecorder;
import org.deviceconnect.android.deviceplugin.host.recorder.util.ImageUtil;
import org.deviceconnect.android.libmedia.streaming.video.CameraVideoQuality;
import org.deviceconnect.android.libmedia.streaming.video.SurfaceVideoEncoder;
import org.deviceconnect.android.libmedia.streaming.video.VideoQuality;

import java.util.List;

class DepthCameraVideoEncoder extends SurfaceVideoEncoder {
    /**
     * Depthカメラ操作クラス.
     */
    private DepthCamera2Recorder mDepthCamera2Recorder;
    private CameraWrapper.OnImageAvailableListener mListener = new CameraWrapper.OnImageAvailableListener() {
        @Override
        public void onSuccess(Image image) {
            byte[] jpeg = ImageUtil.convertToJPEG(image);
            jpeg = ImageUtil.rotateJPEG(jpeg, 100, 90);
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            mCanvas = mSurface.lockCanvas(null);

            if (bitmap == null || mCanvas == null) {
                return;
            }
            try {
                mCanvas.drawBitmap(bitmap, 0, 0, mPaint);
            } finally {
                mSurface.unlockCanvasAndPost(mCanvas);
            }

        }

        @Override
        public void onFailed(String message) {
            Log.e("ABC", message);
        }
    };
    private Canvas mCanvas;
    private Paint mPaint = new Paint();
    /**
     * 映像のエンコード設定.
     */
    private CameraVideoQuality mVideoQuality;

    private Surface mSurface;
    DepthCameraVideoEncoder(DepthCamera2Recorder recorder) {
        mDepthCamera2Recorder = recorder;

        mVideoQuality = new CameraVideoQuality("video/avc");
        List<HostMediaRecorder.PictureSize> pSize = mDepthCamera2Recorder.getSupportedPreviewSizes();
        mVideoQuality.setVideoHeight(pSize.get(0).getHeight());
        mVideoQuality.setVideoWidth(pSize.get(0).getWidth());
    }

    // VideoEncoder

    @Override
    public VideoQuality getVideoQuality() {
        return mVideoQuality;
    }

    @Override
    protected int getDisplayRotation() {
        return mDepthCamera2Recorder.getDisplayRotation();
    }

    @Override
    public boolean isSwappedDimensions() {
        return mDepthCamera2Recorder.isSwappedDimensions();
    }

    // SurfaceVideoEncoder

    @Override
    protected void onStartSurfaceDrawing() {
        try {
            mDepthCamera2Recorder.setStreaming(mListener);
            if (mDepthCamera2Recorder.isPreview()) {
                mDepthCamera2Recorder.startPreview(true);
            } else {
                mDepthCamera2Recorder.startPreview(false);
            }
            mSurface = new Surface(getSurfaceTexture());
        } catch (CameraWrapperException e) {
            // ignore
        }
    }

    @Override
    protected void onStopSurfaceDrawing() {
        try {
            mDepthCamera2Recorder.removeStreaming();
            mDepthCamera2Recorder.stopPreview();
            if (mSurface != null) {
                mSurface = null;
            }
        } catch (CameraWrapperException e) {
            // ignore
        }
    }
}
