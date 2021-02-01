package org.deviceconnect.android.deviceplugin.host.recorder.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;

import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapper;
import org.deviceconnect.android.deviceplugin.host.recorder.HostMediaRecorder;
import org.deviceconnect.android.libmedia.streaming.gles.EGLSurfaceDrawingThread;

public class CameraSurfaceDrawingThread extends EGLSurfaceDrawingThread {
    /**
     * レコーダ.
     */
    private final Camera2Recorder mRecorder;

    public CameraSurfaceDrawingThread(Camera2Recorder recorder) {
        if (recorder == null) {
            throw new IllegalArgumentException("recorder is null.");
        }
        mRecorder = recorder;
    }

    // EGLSurfaceDrawingThread

    @Override
    public int getDisplayRotation() {
        WindowManager wm = (WindowManager) mRecorder.getContext().getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            throw new RuntimeException("WindowManager is not supported.");
        }
        return wm.getDefaultDisplay().getRotation();
    }

    @Override
    public boolean isSwappedDimensions() {
        int sensorOrientation = mRecorder.getCameraWrapper().getSensorOrientation();
        switch (getDisplayRotation()) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    return true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    protected void onStarted() {
        startCamera(getSurfaceTexture());
    }

    @Override
    protected void onStopped() {
        stopCamera();
    }

    @Override
    public void start() {
        HostMediaRecorder.Settings settings = mRecorder.getSettings();
        Size previewSize = settings.getPreviewSize();
        int width = isSwappedDimensions() ? previewSize.getHeight() : previewSize.getWidth();
        int height = isSwappedDimensions() ? previewSize.getWidth() : previewSize.getHeight();
        setSize(width, height);
        setDrawingRange(settings.getDrawingRange());
        super.start();
    }

    private void startCamera(SurfaceTexture surfaceTexture) {
        try {
            HostMediaRecorder.Settings settings = mRecorder.getSettings();
            CameraWrapper cameraWrapper = mRecorder.getCameraWrapper();
            cameraWrapper.getOptions().setPictureSize(settings.getPictureSize());
            cameraWrapper.getOptions().setPreviewSize(settings.getPreviewSize());
            cameraWrapper.getOptions().setFps(settings.getPreviewMaxFrameRate());
            cameraWrapper.getOptions().setAutoFocusMode(settings.getPreviewAutoFocusMode());
            cameraWrapper.getOptions().setAutoWhiteBalanceMode(settings.getPreviewWhiteBalance());
            cameraWrapper.getOptions().setAutoExposureMode(settings.getAutoExposureMode());
            cameraWrapper.getOptions().setSensorExposureTime(settings.getSensorExposureTime());
            cameraWrapper.getOptions().setSensorSensitivity(settings.getSensorSensitivity());
            cameraWrapper.getOptions().setSensorFrameDuration(settings.getSensorFrameDuration());
            cameraWrapper.getOptions().setStabilizationMode(settings.getStabilizationMode());
            cameraWrapper.getOptions().setOpticalStabilizationMode(settings.getOpticalStabilizationMode());
            cameraWrapper.getOptions().setDigitalZoom(settings.getDigitalZoom());
            cameraWrapper.getOptions().setNoiseReductionMode(settings.getNoiseReduction());
            cameraWrapper.getOptions().setFocalLength(settings.getFocalLength());
            cameraWrapper.startPreview(new Surface(surfaceTexture));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void stopCamera() {
        try {
            mRecorder.getCameraWrapper().stopPreview();
        } catch (Exception e) {
            // ignore.
        }
    }
}
