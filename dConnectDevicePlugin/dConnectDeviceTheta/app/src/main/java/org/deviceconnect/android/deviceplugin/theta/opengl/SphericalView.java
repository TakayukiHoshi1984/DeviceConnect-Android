package org.deviceconnect.android.deviceplugin.theta.opengl;


import android.graphics.Bitmap;
import android.util.Log;

import java.io.ByteArrayOutputStream;

public class SphericalView {

    private static final boolean DEBUG = false;

    private static final String TAG = "SphericalView";

    private int mWidth;

    private int mHeight;

    private Thread mGLThread;

    private final SphereRenderer mRenderer = new SphereRenderer();

    private PixelBuffer mPixelBuffer;

    private ByteArrayOutputStream mBaos;

    private String mKey;

    private byte[] mRoi;

    private boolean mIsCreatedScreen;

    private boolean mIsDestroyed;

    private final Object mLockScreen = new Object();

    private EventListener mListener;

    private long mInterval;

    public SphericalView() {
        start();
    }

    public void setFrameRate(double fps) {
        mInterval = (long) (1000.0 / fps);
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public boolean isDestroyed() {
        return mIsDestroyed;
    }

    public synchronized void destroy() {
        mIsDestroyed = true;
        mGLThread.interrupt();
        mRenderer.destroy();
        mPixelBuffer.destroy();
    }

    public boolean hasScreenSize(final int width, final int height) {
        return width == mWidth && height == mHeight;
    }

    public void createScreen(final int width, final int height) {
        synchronized (mLockScreen) {
            if (!mIsCreatedScreen) {
                mWidth = width;
                mHeight = height;
                mIsCreatedScreen = true;
            }

            mLockScreen.notifyAll();
        }
    }

    public void resetCamera() {
        mRenderer.resetCamera();
        mRenderer.rotateSphere(0, 0, 0);
    }

    private void start() {
        if (DEBUG) {
            Log.d(TAG, "SphericalView.start()");
        }

        mGLThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (mLockScreen) {
                        while (!mIsCreatedScreen) {
                            mLockScreen.wait();
                        }

                        mPixelBuffer = new PixelBuffer(mWidth, mHeight, false);
                        mPixelBuffer.setRenderer(mRenderer);
                        mBaos = new ByteArrayOutputStream(mWidth * mHeight);
                    }

                    long start;
                    long end;
                    long delay;
                    while (!mIsDestroyed) {
                        start = System.currentTimeMillis();
                        draw();
                        end = System.currentTimeMillis();

                        delay = mInterval - (end - start);

                        if (mListener != null) {
                            mListener.onUpdate(mKey, mRoi);
                        } else {
                            synchronized (mLockScreen) {
                                while (mListener == null) {
                                    mLockScreen.wait();
                                }
                            }
                        }

                        if (delay > 0) {
                            Thread.sleep(delay);
                        }
                    }
                } catch (InterruptedException e) {
                    // Nothing to do.
                }

                if (DEBUG) {
                    Log.i(TAG, "Finished GL Thread.");
                }
            }
        });
        mGLThread.start();
    }

    private void draw() {
        mPixelBuffer.render();
        Bitmap result = mPixelBuffer.convertToBitmap();
        if (result == null) {
            return;
        }
        mBaos.reset();
        result.compress(Bitmap.CompressFormat.JPEG, 80, mBaos);
        mRoi = mBaos.toByteArray();
    }

    public void setTexture(final Bitmap texture) {
        mRenderer.setTexture(texture);
    }

    public void setEventListener(final EventListener listener) {
        synchronized (mLockScreen) {
            mListener = listener;
            mLockScreen.notifyAll();
        }
    }

    public byte[] getRoi() {
        return mRoi;
    }

    public SphereRenderer getRenderer() {
        return mRenderer;
    }

    public String getKey() {
        return mKey;
    }

    public void setKey(final String key) {
        mKey = key;
    }

    public interface EventListener {
        void onUpdate(String key, byte[] roi);
    }

}
