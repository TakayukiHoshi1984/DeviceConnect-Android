package org.deviceconnect.android.deviceplugin.host.recorder.camera;

import android.media.Image;
import android.util.Log;

import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapper;
import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapperException;
import org.deviceconnect.android.libmedia.streaming.mjpeg.MJPEGEncoder;

class DepthCameraVideoMJPEGEncoder extends MJPEGEncoder {
    private CameraWrapper.OnImageAvailableListener mListener = new CameraWrapper.OnImageAvailableListener() {
        @Override
        public void onSuccess(Image image) {
            postJPEG(mDepthCamera2Recorder.convertJPEG(image));
        }

        @Override
        public void onFailed(String message) {
            Log.e("host.dplugin", message);
        }
    };

    /**
     * Depthカメラ操作クラス.
     */
    private DepthCamera2Recorder mDepthCamera2Recorder;

    DepthCameraVideoMJPEGEncoder(DepthCamera2Recorder recorder) {
        mDepthCamera2Recorder = recorder;
    }

    @Override
    public void start() {
        try {
            mDepthCamera2Recorder.setPreview(mListener);
            if (mDepthCamera2Recorder.isPreview()) {
                mDepthCamera2Recorder.startPreview(true);
            } else {
                mDepthCamera2Recorder.startPreview(false);
            }
        } catch (CameraWrapperException e) {
            // ignore
        }
    }

    @Override
    public void stop() {
        try {
            mDepthCamera2Recorder.removePreview();
            mDepthCamera2Recorder.stopPreview();
        } catch (CameraWrapperException e) {
            // ignore
        }
    }
}
