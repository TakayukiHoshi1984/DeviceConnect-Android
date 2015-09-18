package org.deviceconnect.android.deviceplugin.theta.walkthrough;

import android.graphics.Bitmap;
import android.util.Log;

import org.deviceconnect.android.deviceplugin.theta.utils.JpegLoader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;


class FrameJpeg {

    private static final String TAG = "FrameJpeg";
    private static final boolean DEBUG = true; // BuildConfig.DEBUG;

    private static final JpegLoader LOADER = new JpegLoader();

    private final Bitmap mBitmap;
    private final Buffer mBuffer;

    private Frame mFrame;

    public FrameJpeg(final int width, final int height) {
        if (DEBUG) {
            Log.d(TAG, "FrameJpeg(): size = " + width + " x " + height);
        }

        int stride = 4;
        ByteBuffer buffer = ByteBuffer.allocateDirect(stride * (1024 * 512));
        buffer.order(ByteOrder.nativeOrder());
        mBuffer = buffer.asIntBuffer();

        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    public void destroy() {
        mBitmap.recycle();
    }

    public void load(final Frame frame) throws IOException {
        mFrame = frame;
        File file = frame.getSource();
        if (!file.exists()) {
            throw new FileNotFoundException();
        }
        String path = file.getCanonicalPath();

        if (DEBUG) {
            Log.d(TAG, "FrameJpeg.load: address = " + mBitmap);
        }

        mBuffer.clear();
        LOADER.loadToBuffer(path, mBuffer);
        mBitmap.copyPixelsFromBuffer(mBuffer);
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
