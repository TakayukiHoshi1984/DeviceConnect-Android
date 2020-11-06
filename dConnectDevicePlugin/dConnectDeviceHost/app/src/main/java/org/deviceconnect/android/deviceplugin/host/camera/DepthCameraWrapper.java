package org.deviceconnect.android.deviceplugin.host.camera;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DepthCameraWrapper extends CameraWrapper{
    private Image mImage;
    private ImageReader mImageReader;
    private Map<String, OnImageAvailableListener> mListener = new ConcurrentHashMap<>();

    /**
     * 写真撮影の処理を実行するハンドラ.
     */
    private final Handler mPhotoHandler;

    DepthCameraWrapper(@NonNull Context context, @NonNull String cameraId, @NonNull int imageFormat) throws CameraAccessException {
        super(context, cameraId, imageFormat);
        HandlerThread photoThread = new HandlerThread("host-camera-photo");
        photoThread.start();
        mPhotoHandler = new Handler(photoThread.getLooper());
    }

    public void setTargetSurface(final CameraWrapper.OnImageAvailableListener listener) {
        mListener.put("overlay-" + getId(), listener);
    }
    public void removeTargetSurface() {
        mListener.remove("overlay-" + getId());
    }
    public void setPreview(final CameraWrapper.OnImageAvailableListener listener) {
        mListener.put("preview-" + getId(), listener);
    }
    public void removePreview() {
        mListener.remove("preview-" + getId());
    }
    public void setRecording(final CameraWrapper.OnImageAvailableListener listener) {
        mListener.put("recording-" + getId(), listener);
    }
    public void removeRecording() {
        mListener.remove("recording-" + getId());
    }

    public void setStreaming(final CameraWrapper.OnImageAvailableListener listener) {
        mListener.put("streaming-" + getId(), listener);
    }
    public void removeStreaming() {
        mListener.remove("streaming-" + getId());
    }

    public void destroy() {
        super.destroy();
        mPhotoHandler.getLooper().quit();
        close();
    }

    private void close() {
        if (mImage != null) {
            mImage.close();
            mImage = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
    }


    public void startPreview(final boolean isResume) throws CameraWrapperException {
        if (isResume) {
            return;
        }
        if (mImageReader != null) {
            startPreview(mImageReader.getSurface(), isResume);
            return;
        }

        mImageReader = createStillImageReader(mImageFormat);
        mImageReader.setOnImageAvailableListener((image) -> {
            mImage = null;
            try {
                mImage = image.acquireNextImage();
                if (mImage == null) {
                    for (OnImageAvailableListener l : mListener.values()) {
                        l.onFailed("Failed to acquire image.");
                    }
                    return;
                }
                for (OnImageAvailableListener l : mListener.values()) {
                    l.onSuccess(mImage);
                }
            } finally {
                if (mImage != null) {
                    mImage.close();
                }
            }
        }, mPhotoHandler);
        startPreview(mImageReader.getSurface(), isResume);
    }

    public void startRecording(final boolean isResume) throws CameraWrapperException {
        if (isResume) {
            return;
        }
        if (mImageReader != null) {
            startRecording(mImageReader.getSurface(), isResume);
            return;
        }
        mImageReader = createStillImageReader(mImageFormat);
        mImageReader.setOnImageAvailableListener((image) -> {
            mImage = null;
            try {
                mImage = image.acquireNextImage();
                if (mImage == null) {
                    for (OnImageAvailableListener l : mListener.values()) {
                        l.onFailed("Failed to acquire image.");
                    }
                    return;
                }
                for (OnImageAvailableListener l : mListener.values()) {
                    l.onSuccess(mImage);
                }
            } finally {
                if (mImage != null) {
                    mImage.close();
                }
            }
        }, mPhotoHandler);
        startRecording(mImageReader.getSurface(), isResume);
    }

}
