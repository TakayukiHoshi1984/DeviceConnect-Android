/*
 HostDeviceScreenCast.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.screen;


import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;

import org.deviceconnect.android.deviceplugin.host.HostDevicePreviewServer;
import org.deviceconnect.android.deviceplugin.host.HostDeviceRecorder;
import org.deviceconnect.android.deviceplugin.host.camera.MixedReplaceMediaServer;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Host Device Screen Cast.
 *
 * @author NTT DOCOMO, INC.
 */
@TargetApi(21)
public class HostDeviceScreenCast implements HostDeviceRecorder, HostDevicePreviewServer {

    public static final String ACTION_PERMISSION =
        HostDeviceScreenCast.class.getPackage().getName() + ".permission";

    static final String RESULT_CODE = "result_code";

    static final String RESULT_DATA = "result_data";

    private static final String ID = "screen";

    private static final String NAME = "AndroidHost Screen";

    private static final String MIME_TYPE = "video/x-mjpeg";

    private static final double DEFAULT_MAX_FPS = 10.0d;

    private final Context mContext;

    private final Object mLockObj = new Object();

    private final Logger mLogger = Logger.getLogger("host.dplugin");

    private ScreenPreview mScreenPreview;

    private MixedReplaceMediaServer mServer;

    private MediaProjectionManager mManager;

    private MediaProjection mMediaProjection;

    private boolean mIsCasting;

    private Handler mMainHandler;

    private Thread mThread;

    private final List<PictureSize> mSupportedPreviewSizes = new ArrayList<PictureSize>();

    private PictureSize mPreviewSize;

    private BroadcastReceiver mPermissionReceiver;

    private long mFrameInterval;

    private double mMaxFps;

    public HostDeviceScreenCast(final Context context) {
        mContext = context;
        mManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mMainHandler = new Handler(Looper.getMainLooper());

        initSupportedPreviewSizes(context.getResources());
        setPreviewSize(mSupportedPreviewSizes.get(0));

        mMaxFps = DEFAULT_MAX_FPS;
        setPreviewFrameRate(mMaxFps);
    }

    private void initSupportedPreviewSizes(final Resources res) {
        DisplayMetrics metrics = res.getDisplayMetrics();
        PictureSize originalSize;
        switch (getOrientation()) {
            case Configuration.ORIENTATION_PORTRAIT:
                originalSize = new PictureSize(metrics.widthPixels, metrics.heightPixels);
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                originalSize = new PictureSize(metrics.heightPixels, metrics.widthPixels);
                break;
            default:
                return;
        }
        final int num = 4;
        final int w = originalSize.getWidth();
        final int h = originalSize.getHeight();
        mSupportedPreviewSizes.clear();
        for (int i = 1; i <= num; i++) {
            float scale = i / ((float) num);
            PictureSize previewSize = new PictureSize((int) (w * scale), (int) (h * scale));
            mSupportedPreviewSizes.add(previewSize);
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getMimeType() {
        return MIME_TYPE;
    }

    @Override
    public String[] getSupportedMimeTypes() {
        return new String[] {MIME_TYPE};
    }

    @Override
    public RecorderState getState() {
        return mIsCasting ? RecorderState.RECORDING : RecorderState.INACTTIVE;
    }

    @Override
    public void initialize() {
        // Nothing to do.
    }

    @Override
    public boolean mutablePictureSize() {
        return false;
    }

    @Override
    public boolean usesCamera() {
        return false;
    }

    @Override
    public int getCameraId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PictureSize> getSupportedPictureSizes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsPictureSize(int width, int height) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PictureSize getPictureSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPictureSize(final PictureSize size) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void startWebServer(final OnWebServerStartCallback callback) {
        mLogger.info("Starting web server...");
        synchronized (mLockObj) {
            if (mServer == null) {
                mServer = new MixedReplaceMediaServer();
                mServer.setServerName("HostDevicePlugin ScreenCast Server");
                mServer.setContentType("image/jpg");
                final String ip = mServer.start();

                if (mPermissionReceiver != null) {
                    mContext.unregisterReceiver(mPermissionReceiver);
                }
                mPermissionReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(final Context context, final Intent intent) {
                        if (ACTION_PERMISSION.equals(intent.getAction())) {
                            int resultCode = intent.getIntExtra(RESULT_CODE, -1);
                            if (resultCode == Activity.RESULT_OK) {
                                Intent data = intent.getParcelableExtra(RESULT_DATA);
                                setupMediaProjection(resultCode, data);
                                startScreenCast();
                                callback.onStart(ip);
                            } else {
                                callback.onFail();
                            }
                        }
                    }
                };
                IntentFilter filter = new IntentFilter();
                filter.addAction(ACTION_PERMISSION);
                mContext.registerReceiver(mPermissionReceiver, filter);
                requestPermission();
            } else {
                callback.onStart(mServer.getUrl());
            }
        }
        mLogger.info("Started web server.");
    }

    @Override
    public void stopWebServer() {
        mLogger.info("Stopping web server...");
        synchronized (mLockObj) {
            if (mPermissionReceiver != null) {
                mContext.unregisterReceiver(mPermissionReceiver);
                mPermissionReceiver = null;
            }
            stopScreenCast();
            if (mServer != null) {
                mServer.stop();
                mServer = null;
            }
        }
        mLogger.info("Stopped web server.");
    }

    private void requestPermission() {
        Intent intent = new Intent();
        intent.setClass(mContext, PermissionReceiverActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private void setupMediaProjection(int resultCode, Intent data) {
        mMediaProjection = mManager.getMediaProjection(resultCode, data);
    }

    private int getOrientation() {
        return mContext.getResources().getConfiguration().orientation;
    }

    private PictureSize getImageSize() {
        switch (getOrientation()) {
            case Configuration.ORIENTATION_PORTRAIT:
                return mPreviewSize;
            case Configuration.ORIENTATION_LANDSCAPE:
                return new PictureSize(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            default:
                throw new RuntimeException();
        }
    }

    private void startScreenCast() {
        if (mIsCasting) {
            return;
        }
        mIsCasting = true;
        startScreenCastAtMainThread();
    }

    private void startScreenCastAtMainThread() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                PictureSize imageSize = getImageSize();
                mScreenPreview = new ScreenPreview(mContext, imageSize.getWidth(), imageSize.getHeight());

                mThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mLogger.info("Server URL: " + mServer.getUrl());
                        boolean reset = false;

                        cast:
                        while (mIsCasting) {
                            try {
                                long start = System.currentTimeMillis();

                                PictureSize imageSize = getImageSize();
                                if (mScreenPreview == null || !mScreenPreview.isSameSize(imageSize)) {
                                    reset = true;
                                    break;
                                }
                                mServer.offerMedia(mScreenPreview.load());

                                long end = System.currentTimeMillis();
                                long interval = mFrameInterval - (end - start);
                                if (interval > 0) {
                                    Thread.sleep(interval);
                                }
                            } catch (InterruptedException e) {
                                break;
                            }
                        }

                        if (mScreenPreview != null) {
                            mScreenPreview.destroy();
                            mScreenPreview = null;
                        }

                        if (reset) {
                            restartScreenCast();
                        }
                    }
                });
                mThread.start();
            }
        });
    }

    private void stopScreenCast() {
        if (!mIsCasting) {
            return;
        }
        mIsCasting = false;
        if (mThread != null) {
            try {
                mThread.interrupt();
                mThread.join();
            } catch (InterruptedException e) {
                // NOP
            }
            mThread = null;
        }
    }

    private void restartScreenCast() {
        stopScreenCast();
        startScreenCast();
    }

    @Override
    public List<PictureSize> getSupportedPreviewSizes() {
        return mSupportedPreviewSizes;
    }

    @Override
    public boolean supportsPreviewSize(final int width, final int height) {
        if (mSupportedPreviewSizes != null) {
            for (PictureSize size : mSupportedPreviewSizes) {
                if (width == size.getWidth() && height == size.getHeight()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public PictureSize getPreviewSize() {
        return mPreviewSize;
    }

    @Override
    public synchronized void setPreviewSize(final PictureSize size) {
        mPreviewSize = size;
        if (mIsCasting) {
            restartScreenCast();
        }
    }

    @Override
    public double getPreviewMaxFrameRate() {
        return mMaxFps;
    }

    @Override
    public void setPreviewFrameRate(final double max) {
        mMaxFps = max;
        mFrameInterval = (long) (1 / max) * 1000L;
    }

    private class ScreenPreview {

        private final int mWidth;

        private final int mHeight;

        private ImageReader mImageReader;

        private int[] mPixels;

        private Bitmap mBitmap;

        private VirtualDisplay mVirtualDisplay;

        ScreenPreview(final Context context, final PictureSize size) {
            this(context, size.getWidth(), size.getHeight());
        }

        ScreenPreview(final Context context, final int w, final int h) {
            mWidth = w;
            mHeight = h;
            mImageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 10);
            mPixels = new int[w * h];
            DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
            mBitmap = Bitmap.createBitmap(metrics, w, h, Bitmap.Config.RGB_565);

            mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                "Android Host Screen",
                w,
                h,
                context.getResources().getDisplayMetrics().densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(),
                new VirtualDisplay.Callback() {
                    @Override
                    public void onPaused() {
                        mLogger.info("VirtualDisplay.Callback.onPaused");
                        stopScreenCast();
                    }

                    @Override
                    public void onResumed() {
                        mLogger.info("VirtualDisplay.Callback.onResumed");
                        startScreenCast();
                    }

                    @Override
                    public void onStopped() {
                        mLogger.info("VirtualDisplay.Callback.onStopped");
                    }
                }, null);
        }

        public boolean isSameSize(final PictureSize size) {
            return isSameSize(size.getWidth(), size.getHeight());
        }

        public boolean isSameSize(final int w, final int h) {
            return mWidth == w && mHeight == h;
        }

        public void destroy() {
            mVirtualDisplay.release();
            mImageReader.close();
            mBitmap.recycle();
            mPixels = null;
        }

        public byte[] load() {
            Image image = mImageReader.acquireLatestImage();
            if (image != null) {
                decodeToBitmap(image);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            return baos.toByteArray();
        }

        private void decodeToBitmap(final Image img) {
            Image.Plane[] planes = img.getPlanes();
            if (planes[0].getBuffer() == null) {
                return;
            }
            int width = img.getWidth();
            int height = img.getHeight();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;

            int offset = 0;
            ByteBuffer buffer = planes[0].getBuffer();
            for (int i = 0; i < height; ++i) {
                for (int j = 0; j < width; ++j) {
                    int pixel = 0;
                    pixel |= (buffer.get(offset) & 0xff) << 16;     // R
                    pixel |= (buffer.get(offset + 1) & 0xff) << 8;  // G
                    pixel |= (buffer.get(offset + 2) & 0xff);       // B
                    pixel |= (buffer.get(offset + 3) & 0xff) << 24; // A
                    mPixels[j + (width * i)] = pixel;
                    offset += pixelStride;
                }
                offset += rowPadding;
            }
            img.close();
            mBitmap.setPixels(mPixels, 0, width, 0, 0, width, height);
        }
    }
}
