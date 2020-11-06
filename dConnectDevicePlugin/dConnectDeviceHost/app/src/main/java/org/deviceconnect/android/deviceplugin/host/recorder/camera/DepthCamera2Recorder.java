package org.deviceconnect.android.deviceplugin.host.recorder.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.Image;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;

import org.deviceconnect.android.deviceplugin.host.BuildConfig;
import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapper;
import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapperException;
import org.deviceconnect.android.deviceplugin.host.camera.DepthCameraWrapper;
import org.deviceconnect.android.deviceplugin.host.recorder.PreviewServer;
import org.deviceconnect.android.deviceplugin.host.recorder.PreviewServerProvider;
import org.deviceconnect.android.deviceplugin.host.recorder.util.DefaultSurfaceRecorder;
import org.deviceconnect.android.deviceplugin.host.recorder.util.ImageUtil;
import org.deviceconnect.android.deviceplugin.host.recorder.util.SurfaceRecorder;
import org.deviceconnect.android.libmedia.streaming.mjpeg.MJPEGEncoder;
import org.deviceconnect.android.libmedia.streaming.video.VideoEncoder;
import org.deviceconnect.android.provider.FileManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DepthCamera2Recorder extends Camera2Recorder {
    /**
     * ログ出力用タグ.
     */
    private static final String TAG = "depth.camera";

    /**
     * デバッグフラグ.
     */
    private static final boolean DEBUG = BuildConfig.DEBUG;


    private Canvas mCanvas;
    private Surface mRecordingSurface;
    /**
     * プレビュー配信サーバを管理するクラス.
     */
    private DepthCamera2PreviewServerProvider mDepthCamera2PreviewServerProvider;

    /**
     * コンストラクタ.
     *
     * @param context     コンテキスト
     * @param camera      カメラ
     * @param fileManager ファイルマネージャ
     */
    public DepthCamera2Recorder(@NonNull Context context, @NonNull CameraWrapper camera, @NonNull FileManager fileManager) {
        super(context, camera, fileManager);
        mDepthCamera2PreviewServerProvider = new DepthCamera2PreviewServerProvider(mContext,
                this, mFacing.getValue());

    }
    @Override
    public synchronized void initialize() {
    }
    @Override
    public synchronized void clean() {
        mDepthCamera2PreviewServerProvider.stopServers();
    }

    @Override
    public RecorderState getState() {
        if (mCameraWrapper.isRecording() || mCameraWrapper.isTakingStillImage()) {
            return RecorderState.RECORDING;
        }
        // Preview用のNotificationが表示されている場合は、カメラをPreviewで占有しているものと判断する。
        if (mDepthCamera2PreviewServerProvider.isShownCameraNotification()) {
            return RecorderState.PREVIEW;
        }
        return RecorderState.INACTIVE;
    }

    @Override
    public List<String> getSupportedMimeTypes() {
        List<String> mimeTypes = mDepthCamera2PreviewServerProvider.getSupportedMimeType();
        mimeTypes.add(0, MIME_TYPE_JPEG);
        return mimeTypes;
    }
    @Override
    public PreviewServerProvider getServerProvider() {
        return mDepthCamera2PreviewServerProvider;
    }

    @Override
    public List<PreviewServer> startPreviews() {
        return mDepthCamera2PreviewServerProvider.startServers();
    }

    @Override
    public void stopPreviews() {
        mDepthCamera2PreviewServerProvider.stopServers();
    }
    @Override
    public void onDisplayRotation(final int degree) {
        mCurrentRotation = degree;
        mDepthCamera2PreviewServerProvider.onConfigChange();
    }

    @Override
    public MJPEGEncoder getMJPEGEncorder() {
        return new DepthCameraVideoMJPEGEncoder(this);
    }

    @Override
    public VideoEncoder getVideoEncoder() {
        return new DepthCameraVideoEncoder(this);
    }

    /**
     * プレビュー確認用の Surface を設定します.
     *
     */
    void setTargetSurface(final CameraWrapper.OnImageAvailableListener listener) {
        DepthCameraWrapper wrapper = (DepthCameraWrapper) mCameraWrapper;
        wrapper.setTargetSurface(listener);
    }
    void removeTargetSurface() {
        DepthCameraWrapper wrapper = (DepthCameraWrapper) mCameraWrapper;
        wrapper.removeTargetSurface();
    }
    void setPreview(final CameraWrapper.OnImageAvailableListener listener) {
        DepthCameraWrapper wrapper = (DepthCameraWrapper) mCameraWrapper;
        wrapper.setPreview(listener);
    }
    void removePreview() {
        DepthCameraWrapper wrapper = (DepthCameraWrapper) mCameraWrapper;
        wrapper.removePreview();
    }
    void setStreaming(final CameraWrapper.OnImageAvailableListener listener) {
        DepthCameraWrapper wrapper = (DepthCameraWrapper) mCameraWrapper;
        wrapper.setStreaming(listener);
    }
    void removeStreaming() {
        DepthCameraWrapper wrapper = (DepthCameraWrapper) mCameraWrapper;
        wrapper.removeStreaming();
    }

    /**
     * プレビューを開始します.
     *
     * @throws CameraWrapperException カメラの操作に失敗した場合に発生
     */
    void startPreview(final boolean isResume) throws CameraWrapperException {
        DepthCameraWrapper wrapper = (DepthCameraWrapper) mCameraWrapper;
        wrapper.startPreview(isResume);
    }

    /**
     * 録画を行います.
     *
     * @param listener 録画開始結果を通知するリスナー
     */
    protected void startRecordingInternal(final RecordingListener listener) {
        if (mSurfaceRecorder != null) {
            listener.onFailed(this, "Recording has started already.");
            return;
        }

        try {
            mSurfaceRecorder = new DefaultSurfaceRecorder(
                    mContext,
                    mFacing,
                    mCameraWrapper.getSensorOrientation(),
                    mCameraWrapper.getOptions().getPictureSize(),
                    mFileManager.getBasePath());
            final DepthCameraWrapper wrapper = (DepthCameraWrapper) mCameraWrapper;
            mSurfaceRecorder.start(new SurfaceRecorder.OnRecordingStartListener() {
                @Override
                public void onRecordingStart() {
                    try {
                        mRecordingSurface = mSurfaceRecorder.getInputSurface();

                        wrapper.setRecording(new CameraWrapper.OnImageAvailableListener() {
                            private Paint mPaint = new Paint();
                            @Override
                            public void onSuccess(Image image) {
                                byte[] jpeg = ImageUtil.convertToJPEG(image);
                                Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                                mCanvas = mRecordingSurface.lockCanvas(null);

                                if (bitmap == null || mCanvas == null) {
                                    return;
                                }
                                try {
                                    mCanvas.drawBitmap(bitmap, 0, 0, mPaint);
                                } finally {
                                    mRecordingSurface.unlockCanvasAndPost(mCanvas);
                                }
                            }

                            @Override
                            public void onFailed(String message) {
                                listener.onFailed(DepthCamera2Recorder.this,
                                        "Failed to start recording because of camera problem: " + message);

                            }
                        });
                        wrapper.startRecording(false);
                        listener.onRecorded(DepthCamera2Recorder.this, mSurfaceRecorder.getOutputFile().getAbsolutePath());
                    } catch (CameraWrapperException e) {
                        listener.onFailed(DepthCamera2Recorder.this,
                                "Failed to start recording because of camera problem: " + e.getMessage());
                    }
                }

                @Override
                public void onRecordingStartError(final Throwable e) {
                    if (DEBUG) {
                        Log.e(TAG, "Failed to start recording for unexpected problem: ", e);
                    }
                    listener.onFailed(DepthCamera2Recorder.this,
                            "Failed to start recording for unexpected problem: " + e.getMessage());
                }
            });
        } catch (Throwable e) {
            if (DEBUG) {
                Log.e(TAG, "Failed to start recording for unexpected problem: ", e);
            }
            listener.onFailed(this, "Failed to start recording for unexpected problem: " + e.getMessage());
        }
    }
    /**
     * 録画停止を行います.
     *
     * @param listener 録画停止結果を通知するリスナー
     */
    protected void stopRecordingInternal(final StoppingListener listener) {
        final DepthCameraWrapper wrapper = (DepthCameraWrapper) mCameraWrapper;
        wrapper.removeRecording();
        super.stopRecordingInternal(listener);
    }
}
