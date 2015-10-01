package org.deviceconnect.android.deviceplugin.theta.walkthrough;

import android.graphics.Bitmap;
import android.util.Log;

import org.deviceconnect.android.deviceplugin.theta.utils.JpegLoader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;


class FrameJpeg {

    private static final String TAG = "FrameJpeg";
    private static final boolean DEBUG = false; // BuildConfig.DEBUG;

    private static final JpegLoader LOADER = new JpegLoader();

    private final Bitmap mBitmap;

    private Frame mFrame;

    public FrameJpeg(final int width, final int height) {
        if (DEBUG) {
            Log.d(TAG, "FrameJpeg(): size = " + width + " x " + height);
        }

        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
    }

    public void destroy() {
        Log.d(TAG, "FrameJpeg.destroy");
        synchronized (mBitmap) {
            mBitmap.recycle();
        }
    }

    public void load(final Frame frame) throws IOException {
        mFrame = frame;
        File file = frame.getSource();
        if (!file.exists()) {
            throw new FileNotFoundException("The specified file is not found: " + file.getAbsolutePath());
        }
        String path = file.getCanonicalPath();
        synchronized (mBitmap) {
            if (!mBitmap.isRecycled()) {
                LOADER.load(path, mBitmap);
            }
        }
    }

    public boolean isLoaded(final Frame frame) {
        return mFrame == frame;
    }

    public Frame getFrame() {
        return mFrame;
    }

    public Bitmap toBitmap() {
        return mBitmap;
    }
}
