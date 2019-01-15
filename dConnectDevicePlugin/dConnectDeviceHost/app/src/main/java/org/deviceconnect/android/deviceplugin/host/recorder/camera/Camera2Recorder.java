/*
 Camera2Recorder.java
 Copyright (c) 2018 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.recorder.camera;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import org.deviceconnect.android.activity.PermissionUtility;
import org.deviceconnect.android.deviceplugin.host.BuildConfig;
import org.deviceconnect.android.deviceplugin.host.camera.Camera2Helper;
import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapperException;
import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapper;
import org.deviceconnect.android.deviceplugin.host.recorder.HostDevicePhotoRecorder;
import org.deviceconnect.android.deviceplugin.host.recorder.HostDeviceStreamRecorder;
import org.deviceconnect.android.deviceplugin.host.recorder.PreviewServer;
import org.deviceconnect.android.deviceplugin.host.recorder.util.CapabilityUtil;
import org.deviceconnect.android.provider.FileManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Camera2Recorder extends AbstractCamera2Recorder implements HostDevicePhotoRecorder, HostDeviceStreamRecorder {

    /**
     * ログ出力用タグ.
     */
    private static final String TAG = "host.dplugin";

    /**
     * デバッグフラグ.
     */
    private static final boolean DEBUG = BuildConfig.DEBUG;

    /**
     * カメラターゲットIDの定義.
     */
    private static final String ID_BASE = "camera";

    /**
     * カメラ名の定義.
     */
    private static final String NAME_BASE = "Camera";

    /**
     * ファイル名に付けるプレフィックス.
     */
    private static final String FILENAME_PREFIX = "android_camera_";

    /**
     * ファイルの拡張子.
     */
    private static final String FILE_EXTENSION = ".jpg";

    /**
     * 日付のフォーマット.
     */
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_kkmmss", Locale.JAPAN);

    /**
     * 画面の向きを格納するリスト.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * マイムタイプ一覧.
     */
    private final List<String> mMimeTypes = new ArrayList<String>() {
        {
            add("image/jpg");
            add("video/x-mjpeg");
            add("video/x-rtp");
            add("video/mp4");
        }
    };

    /**
     * ファイルマネージャ.
     */
    private final FileManager mFileManager;

    /**
     * プレビュー配信サーバーのリスト.
     */
    private final List<PreviewServer> mPreviewServers = new ArrayList<>();

    /**
     * MotionJPEG サーバー.
     */
    private final Camera2MJPEGPreviewServer mMjpegServer;

    /**
     * {@link SurfaceRecorder} のインスタンス.
     */
    private SurfaceRecorder mSurfaceRecorder;

    private final CameraWrapper mCameraWrapper;

    private HandlerThread mPreviewThread = new HandlerThread("preview");

    private final HandlerThread mPhotoThread = new HandlerThread("photo");

    /**
     * コンストラクタ.
     *
     * @param context コンテキスト
     * @param camera カメラ
     * @param fileManager ファイルマネージャ
     */
    public Camera2Recorder(final @NonNull Context context,
                           final @NonNull CameraWrapper camera,
                           final @NonNull FileManager fileManager) {
        super(context, camera.getId());
        mCameraWrapper = camera;
        mPreviewThread.start();
        mPhotoThread.start();
        mFileManager = fileManager;

        mMjpegServer = new Camera2MJPEGPreviewServer(this);
        mMjpegServer.setQuality(readPreviewQuality(mMjpegServer));
        Camera2RTSPPreviewServer rtspServer = new Camera2RTSPPreviewServer(getContext(), this, this);
        mPreviewServers.add(mMjpegServer);
        mPreviewServers.add(rtspServer);
    }

    CameraWrapper getCameraWrapper() {
        return mCameraWrapper;
    }

    @Override
    public void takePhoto(final @NonNull OnPhotoEventListener listener) {
        try {
            final CameraWrapper camera = getCameraWrapper();
            final ImageReader stillImageReader = camera.createStillImageReader(ImageFormat.YUV_420_888);
            stillImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(final ImageReader reader) {
                    Image photo = reader.acquireNextImage();
                    if (photo == null) {
                        listener.onFailedTakePhoto("Failed to acquire image.");
                        return;
                    }

                    storePhoto(photo, listener);
                    photo.close();
                }
            }, new Handler(mPhotoThread.getLooper()));

            camera.takeStillImage(stillImageReader.getSurface());
        } catch (CameraWrapperException e) {
            Log.e(TAG, "Failed to take photo.", e);
            listener.onFailedTakePhoto("Failed to take photo.");
        }
    }

    private void storePhoto(final Image image, final OnPhotoEventListener listener) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] jpeg = NV21toJPEG(YUV420toNV21(image), width, height, 80);
        byte[] rotated = rotateJPEG(jpeg, width, height, 80);

        // ファイル保存
        mFileManager.saveFile(createNewFileName(), rotated, true, new FileManager.SaveFileCallback() {
            @Override
            public void onSuccess(@NonNull final String uri) {
                if (DEBUG) {
                    Log.d(TAG, "Saved photo: uri=" + uri);
                }

                String filePath = mFileManager.getBasePath().getAbsolutePath() + "/" + uri;
                listener.onTakePhoto(uri, filePath);
            }

            @Override
            public void onFail(@NonNull final Throwable e) {
                if (DEBUG) {
                    Log.e(TAG, "Failed to save photo", e);
                }

                listener.onFailedTakePhoto(e.getMessage());
            }
        });
    }

    byte[] rotateJPEG(final byte[] jpeg, int width, int height, int quality) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);

        //Log.d(TAG, "bitmap=" + bitmap.getWidth() + "x" + bitmap.getHeight() + " width=" + width + " height=" + height);

        int orientation = Camera2Helper.getSensorOrientation(mCameraManager, mCameraId);
        int degrees;
        Bitmap rotated;
        Matrix m = new Matrix();
        if (mFacing == CameraFacing.FRONT || mFacing == CameraFacing.BACK) {
            degrees = orientation;
        } else {
            degrees = 0;
        }
        m.postRotate(degrees);
        rotated = Bitmap.createBitmap(bitmap, 0, 0, width, height, m, true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        rotated.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        byte[] result = baos.toByteArray();
        bitmap.recycle();
        rotated.recycle();
        return result;
    }

    private static byte[] NV21toJPEG(byte[] nv21, int width, int height, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        yuv.compressToJpeg(new Rect(0, 0, width, height), quality, out);
        return out.toByteArray();
    }

    private static byte[] YUV420toNV21(Image image) {
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    channelOffset = width * height + 1;
                    outputStride = 2;
                    break;
                case 2:
                    channelOffset = width * height;
                    outputStride = 2;
                    break;
            }

            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();

            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }
        return data;
    }

    /**
     * 新規のファイル名を作成する.
     *
     * @return ファイル名
     */
    private String createNewFileName() {
        return FILENAME_PREFIX + DATE_FORMAT.format(new Date()) + FILE_EXTENSION;
    }

    void startPreview(final Surface previewSurface) throws CameraWrapperException {
        CameraWrapper camera = getCameraWrapper();
        camera.startPreview(previewSurface, false);
    }

    void stopPreview() throws CameraWrapperException {
        CameraWrapper camera = getCameraWrapper();
        camera.stopPreview();
    }

    @Override
    public boolean canPauseRecording() {
        return false;
    }

    @Override
    public synchronized void startRecording(final String serviceId, final RecordingListener listener) {
        if (mSurfaceRecorder != null) {
            listener.onFailed(this, "Recording has started already.");
            return;
        }
        try {
            CameraWrapper camera = getCameraWrapper();
            mSurfaceRecorder = new SurfaceRecorder(camera.getOptions().getPictureSize());
            mSurfaceRecorder.initMuxer(mFileManager.getBasePath());
            mSurfaceRecorder.start();
            camera.startRecording(mSurfaceRecorder.getInputSurface(), false);
            listener.onRecorded(this, mSurfaceRecorder.getOutputFile().getAbsolutePath());
        } catch (IOException e) {
            listener.onFailed(this, "Failed to initialize surface recorder: " + e.getMessage());
        } catch (RecorderException e) {
            listener.onFailed(this, "Failed to start recording because of recorder problem: " + e.getMessage());
        } catch (CameraWrapperException e) {
            listener.onFailed(this, "Failed to start recording because of camera problem: " + e.getMessage());
        }
    }

    @Override
    public synchronized void stopRecording(final StoppingListener listener) {
        if (mSurfaceRecorder == null) {
            listener.onFailed(this, "Recording has stopped already.");
            return;
        }
        try {
            CameraWrapper camera = getCameraWrapper();
            camera.stopRecording();

            mSurfaceRecorder.stop();
            File videoFile = mSurfaceRecorder.getOutputFile();
            registerVideo(videoFile);
            mSurfaceRecorder = null;
            listener.onStopped(this, videoFile.getAbsolutePath());
        } catch (CameraWrapperException e) {
            if (DEBUG) {
                Log.w(TAG, "Failed to stop recording.", e);
            }
            listener.onFailed(this, "Failed to stop recording: " + e.getMessage());
        } catch (Throwable e) {
            Log.e(TAG, "Failed to stop recording for unexpected error.", e);
            listener.onFailed(this, "Failed to stop recording for unexpected error: " + e.getMessage());
        }
    }

    private void registerVideo(final File videoFile) {
        if (checkVideoFile(videoFile)) {
            // Content Providerに登録する.
            MediaMetadataRetriever mediaMeta = new MediaMetadataRetriever();
            mediaMeta.setDataSource(videoFile.toString());
            ContentResolver resolver = getContext().getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.TITLE, videoFile.getName());
            values.put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.getName());
            values.put(MediaStore.Video.Media.ARTIST, "DeviceConnect");
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/avc");
            values.put(MediaStore.Video.Media.DATA, videoFile.toString());
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        }
    }

    private boolean checkVideoFile(final @NonNull File file) {
        return file.exists() && file.length() > 0;
    }

    @Override
    public void pauseRecording() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resumeRecording() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isBack() {
        return mFacing == CameraFacing.BACK;
    }

    @Override
    public void turnOnFlashLight() {
        try {
            CameraWrapper camera = getCameraWrapper();
            camera.turnOnTorch();
        } catch (CameraWrapperException e) {
            Log.e(TAG, "Failed to turn on flash light.", e);
        }
    }

    @Override
    public void turnOffFlashLight() {
        CameraWrapper camera = getCameraWrapper();
        camera.turnOffTorch();
    }

    @Override
    public boolean isFlashLightState() {
        return mCameraWrapper.isTorchOn();
    }

    @Override
    public boolean isUseFlashLight() {
        return mCameraWrapper.isUseTorch();
    }

    @Override
    public synchronized void initialize() {
    }

    @Override
    public synchronized void clean() {
        for (PreviewServer server : getServers()) {
            server.stopWebServer();
        }
    }

    @Override
    public String getId() {
        return ID_BASE + "_" + mCameraId;
    }

    @Override
    public String getName() {
        return mFacing.getName() + " " + NAME_BASE;
    }

    @Override
    public String getMimeType() {
        return mMimeTypes.get(0);
    }

    @Override
    public RecorderState getState() {
        if (mCameraWrapper.isRecording() || mCameraWrapper.isTakingStillImage()) {
            return RecorderState.RECORDING;
        }
        return RecorderState.INACTTIVE;
    }

    @Override
    public PictureSize getPictureSize() {
        return new PictureSize(getCameraWrapper().getOptions().getPictureSize());
    }

    @Override
    public void setPictureSize(final PictureSize size) {
        Size newSize = new Size(size.getWidth(), size.getHeight());
        mCameraWrapper.getOptions().setPictureSize(newSize);
    }

    @Override
    public PictureSize getPreviewSize() {
        return new PictureSize(mCameraWrapper.getOptions().getPreviewSize());
    }

    @Override
    public void setPreviewSize(final PictureSize size) {
        Size newSize = new Size(size.getWidth(), size.getHeight());
        mCameraWrapper.getOptions().setPreviewSize(newSize);
    }

    @Override
    public double getMaxFrameRate() {
        return mCameraWrapper.getOptions().getPreviewMaxFrameRate();
    }

    @Override
    public void setMaxFrameRate(final double frameRate) {
        mCameraWrapper.getOptions().setPreviewMaxFrameRate(frameRate);
    }

    @Override
    public int getPreviewBitRate() {
        return mCameraWrapper.getOptions().getPreviewBitRate();
    }

    @Override
    public void setPreviewBitRate(final int bitRate) {
        mCameraWrapper.getOptions().setPreviewBitRate(bitRate);
    }

    @Override
    public List<PictureSize> getSupportedPictureSizes() {
        List<PictureSize> result = new ArrayList<>();
        for (Size size : mCameraWrapper.getOptions().getSupportedPictureSizeList()) {
            result.add(new PictureSize(size));
        }
        return result;
    }

    @Override
    public List<PictureSize> getSupportedPreviewSizes() {
        List<PictureSize> result = new ArrayList<>();
        for (Size size : mCameraWrapper.getOptions().getSupportedPreviewSizeList()) {
            result.add(new PictureSize(size));
        }
        return result;
    }

    @Override
    public List<String> getSupportedMimeTypes() {
        return mMimeTypes;
    }

    @Override
    public boolean isSupportedPictureSize(final int width, final int height) {
        for (PictureSize size : getSupportedPictureSizes()) {
            if (size.getWidth() == width && size.getHeight() == height) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isSupportedPreviewSize(final int width, final int height) {
        for (PictureSize size : getSupportedPreviewSizes()) {
            if (size.getWidth() == width && size.getHeight() == height) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void requestPermission(final PermissionCallback callback) {
        CapabilityUtil.requestPermissions(getContext(), new PermissionUtility.PermissionRequestCallback() {
            @Override
            public void onSuccess() {
                callback.onAllowed();
            }

            @Override
            public void onFail(final String deniedPermission) {
                callback.onDisallowed();
            }
        });
    }

    @Override
    public List<PreviewServer> getServers() {
        return mPreviewServers;
    }

    @Override
    public PreviewServer getServerForMimeType(final String mimeType) {
        for (PreviewServer server : getServers()) {
            if (server.getMimeType().equals(mimeType)) {
                return server;
            }
        }
        return null;
    }

    @Override
    protected int getDefaultPreviewQuality(final String mimeType) {
        return 80;
    }
}