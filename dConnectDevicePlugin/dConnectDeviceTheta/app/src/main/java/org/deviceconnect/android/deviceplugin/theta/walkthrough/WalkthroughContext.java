package org.deviceconnect.android.deviceplugin.theta.walkthrough;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import org.deviceconnect.android.deviceplugin.theta.opengl.PixelBuffer;
import org.deviceconnect.android.deviceplugin.theta.opengl.SphereRenderer;
import org.deviceconnect.android.deviceplugin.theta.utils.Quaternion;
import org.deviceconnect.android.deviceplugin.theta.utils.Vector3D;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class WalkthroughContext implements SensorEventListener {

    private static final String TAG = "Walk";

    private static final int NUM_PRELOAD = 50;
    private static final float NS2S = 1.0f / 1000000000.0f;

    private Logger mLogger = Logger.getLogger("theta.dplugin");

    private final SensorManager mSensorMgr;
    private long mLastEventTimestamp;
    private final int mDisplayRotation;
    private Quaternion mCurrentRotation = new Quaternion(1, new Vector3D(0, 0, 0));

    private final File[] mAllFiles;
    private final BitmapLoader mBitmapLoader;

    private final long mInterval; // milliseconds
    private Timer mTimer;

    private Overlay mOverlay;
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(1);

    public WalkthroughContext(final Context context, final File omniImageDir,
                              final int width, final int height, final float fps) {
        WindowManager windowMgr = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mDisplayRotation = windowMgr.getDefaultDisplay().getRotation();
        mSensorMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        boolean isDir = omniImageDir.isDirectory();
        if (!isDir) {
            throw new IllegalArgumentException("dir is not directory.");
        }

        File[] files = omniImageDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String filename) {
                return filename.endsWith(".jpg");
            }
        });
        List<File> fileList = new ArrayList<File>();
        for (int i = 0; i < files.length; i++) {
            fileList.add(files[i]);
        }
        Collections.sort(fileList);
        mAllFiles = fileList.toArray(new File[fileList.size()]);

        mOverlay = new Overlay(context);

        mBitmapLoader = new BitmapLoader(mAllFiles);
        mBitmapLoader.setLoaderListener(new BitmapLoaderListener() {
            @Override
            public void onLoad(int pos, Bitmap b) {
                Log.d(TAG, "onLoad: " + pos);
                if (pos == NUM_PRELOAD - 1) {
                    startVideo();
                }
            }

            @Override
            public void onComplete() {
                Log.d(TAG, "onComplete: ");
                stop();
            }

            @Override
            public void onError(int pos, Exception e) {
                e.printStackTrace();
                Log.e("Walk", "Error: " + e.getMessage());
            }
        });

        mInterval = (long) (1000.0f / fps);
    }

    public void destroy() {
        stop();
        mBitmapLoader.reset();
    }

    private boolean startVrMode() {
        // Reset current rotation.
        mCurrentRotation = new Quaternion(1, new Vector3D(0, 0, 0));

        List<Sensor> sensors = mSensorMgr.getSensorList(Sensor.TYPE_ALL);
        if (sensors.size() == 0) {
            mLogger.warning("Failed to start VR mode: any sensor is NOT found.");
            return false;
        }
        for (Sensor sensor : sensors) {
            if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                mLogger.info("Started VR mode: GYROSCOPE sensor is found.");
                mSensorMgr.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
                return true;
            }
        }
        mLogger.warning("Failed to start VR mode: GYROSCOPE sensor is NOT found.");
        return false;
    }

    private void stopVrMode() {
        mSensorMgr.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
        // Nothing to do.
    }

    @Override
    public void onSensorChanged(final SensorEvent event) {
        if (mLastEventTimestamp != 0) {
            float EPSILON = 0.000000001f;
            float[] vGyroscope = new float[3];
            float[] deltaVGyroscope = new float[4];
            Quaternion qGyroscopeDelta;
            float dT = (event.timestamp - mLastEventTimestamp) * NS2S;

            System.arraycopy(event.values, 0, vGyroscope, 0, vGyroscope.length);
            float tmp = vGyroscope[2];
            vGyroscope[2] = vGyroscope[0];
            vGyroscope[0] = tmp;

            float magnitude = (float) Math.sqrt(Math.pow(vGyroscope[0], 2)
                    + Math.pow(vGyroscope[1], 2) + Math.pow(vGyroscope[2], 2));
            if (magnitude > EPSILON) {
                vGyroscope[0] /= magnitude;
                vGyroscope[1] /= magnitude;
                vGyroscope[2] /= magnitude;
            }

            float thetaOverTwo = magnitude * dT / 2.0f;
            float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
            float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);

            deltaVGyroscope[0] = sinThetaOverTwo * vGyroscope[0];
            deltaVGyroscope[1] = sinThetaOverTwo * vGyroscope[1];
            deltaVGyroscope[2] = sinThetaOverTwo * vGyroscope[2];
            deltaVGyroscope[3] = cosThetaOverTwo;

            float[] delta = new float[3];
            switch (mDisplayRotation) {
                case Surface.ROTATION_0:
                    delta[0] = deltaVGyroscope[0];
                    delta[1] = deltaVGyroscope[1];
                    delta[2] = deltaVGyroscope[2];
                    break;
                case Surface.ROTATION_90:
                    delta[0] = deltaVGyroscope[0];
                    delta[1] = deltaVGyroscope[2] * -1;
                    delta[2] = deltaVGyroscope[1];
                    break;
                case Surface.ROTATION_180:
                    delta[0] = deltaVGyroscope[0];
                    delta[1] = deltaVGyroscope[1] * -1;
                    delta[2] = deltaVGyroscope[2];
                    break;
                case Surface.ROTATION_270:
                    delta[0] = deltaVGyroscope[0];
                    delta[1] = deltaVGyroscope[2];
                    delta[2] = deltaVGyroscope[1] * -1;
                    break;
                default:
                    break;
            }

            qGyroscopeDelta = new Quaternion(deltaVGyroscope[3], new Vector3D(delta));

            mCurrentRotation = qGyroscopeDelta.multiply(mCurrentRotation);

            float[] qvOrientation = new float[4];
            qvOrientation[0] = mCurrentRotation.imaginary().x();
            qvOrientation[1] = mCurrentRotation.imaginary().y();
            qvOrientation[2] = mCurrentRotation.imaginary().z();
            qvOrientation[3] = mCurrentRotation.real();

            float[] rmGyroscope = new float[9];
            SensorManager.getRotationMatrixFromVector(rmGyroscope,
                    qvOrientation);

            float[] vOrientation = new float[3];
            SensorManager.getOrientation(rmGyroscope, vOrientation);

            if (mOverlay != null && mOverlay.isShow()) {
                SphereRenderer renderer = mOverlay.getRenderer();
                SphereRenderer.Camera currentCamera = renderer.getCamera();
                SphereRenderer.CameraBuilder newCamera = new SphereRenderer.CameraBuilder(currentCamera);
                newCamera.rotate(mCurrentRotation);
                renderer.setCamera(newCamera.create());
            }
        }
        mLastEventTimestamp = event.timestamp;
    }

    public void start() {
        Log.d(TAG, "Walkthrough.start()");
        startVrMode();

        mBitmapLoader.init(NUM_PRELOAD);

        mOverlay.show(null);
    }

    public void stop() {
        Log.d(TAG, "Walkthrough.stop()");

        mOverlay.hide();

        if (mTimer == null) {
            return;
        }
        mTimer.cancel();
        mTimer = null;

        stopVrMode();
    }

    private void startVideo() {
        Log.d(TAG, "Walkthrough.startVideo()");
        if (mTimer != null) {
            Log.d(TAG, "Already started video.");
            return;
        }
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                render();
            }
        }, 0, mInterval);
    }

    private void render() {
        Log.d(TAG, "Walkthrough.render: interval=" + mInterval);
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                long start;

                try {
                    Bitmap texture;

                    start = System.currentTimeMillis();
                    texture = mBitmapLoader.pull();
                    Log.d("Walk", "pull texture: " + (System.currentTimeMillis() - start) + " msec");

                    if (texture == null) {
                        Log.d("Walk", "no longer bitmap.");
                        return;
                    }

                    Log.i(TAG, "Changing Texure: size=" + texture.getWidth() + " x " + texture.getHeight());

                    mOverlay.setOmnidirectionalImage(texture);
                } catch (InterruptedException e) {
                    Log.d("Walk", "thread is interrupted.");
                } catch (Throwable e) {
                    Log.d(TAG, "ERROR: Executor:", e);
                    e.printStackTrace();
                }
            }
        });
    }

    private static class BitmapLoader {

        private final File[] mFiles;

        private final Bitmap[] mBitmaps;

        private final ExecutorService mExecutor = Executors.newFixedThreadPool(1);

        private BitmapLoaderListener mListener;

        private int mPos;

        public BitmapLoader(final File[] files) {
            for (File file : files) {
                if (file == null) {
                    throw new IllegalArgumentException("files must not have a null object.");
                }
            }
            mFiles = files;
            mBitmaps = new Bitmap[files.length];
        }

        public void setLoaderListener(final BitmapLoaderListener listener) {
            mListener = listener;
        }

        public void init(final int num) {
            Log.d(TAG, "Preloading bitmaps: num=" + num);
            for (int i = 0; i < num; i++) {
                loadBitmap(i);
            }
        }

        public synchronized void reset() {
            mPos = 0;
            for (int i = 0; i < mBitmaps.length; i++) {
                Bitmap bitmap = mBitmaps[i];
                if (bitmap != null) {
                    bitmap.recycle();
                    mBitmaps[i] = null;
                }
            }
        }

        public Bitmap get(final int pos) {
            return mBitmaps[pos];
        }

        public synchronized Bitmap pull() throws InterruptedException {
            if (mPos == mBitmaps.length) {
                return null;
            }

            int pos = mPos++;
            if (pos == mBitmaps.length - 1) {
                if (mListener != null) {
                    mListener.onComplete();
                }
            }
            try {
                File file = mFiles[pos];
                synchronized (file) {
                    Bitmap bitmap = mBitmaps[pos];
                    if (bitmap != null) {
                        Log.d(TAG, "Already loaded: pos=" + pos);
                        return bitmap;
                    }

                    Log.d(TAG, "Now loading... : pos=" + pos);
                    loadBitmap(pos);
                    while ((bitmap = mBitmaps[pos]) == null) {
                        file.wait(10);
                    }
                    Log.d(TAG, "Loaded: pos=" + pos);

                    // Remove pulled bitmap from this buffer.
                    mBitmaps[pos] = null;

                    return bitmap;
                }
            } finally {
                loadBitmap(pos + 1);
            }
        }

        private void loadBitmap(final int pos) {
            if (pos >= mBitmaps.length) {
                return;
            }

            Log.d(TAG, "Loading bitmap: pos=" + pos);

            final File file = mFiles[pos];

            if (mBitmaps[pos] != null) {
                Log.d(TAG, "Already loaded bitmap: pos=" + pos);
                synchronized (file) {
                    file.notifyAll();
                }
                return;
            }

            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    long start;

                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(file);

                        start = System.currentTimeMillis();
                        Bitmap bitmap = BitmapFactory.decodeStream(fis);
                        Log.d("Walk", "decodeStream: " + (System.currentTimeMillis() - start) + " msec");

                        synchronized (file) {
                            mBitmaps[pos] = bitmap;
                            file.notifyAll();
                        }

                        if (mListener != null) {
                            mListener.onLoad(pos, bitmap);
                        }
                    } catch (IOException e) {
                        if (mListener != null) {
                            mListener.onError(pos, e);
                        }
                    } catch (Throwable e) {
                        Log.d(TAG, "ERROR: loadBitmap: ", e);
                        e.printStackTrace();
                    } finally {
                        if (fis != null) {
                            try {
                                fis.close();
                            } catch(IOException e) {
                                // Nothing to do.
                            }
                        }
                    }
                }
            });
        }
    }

    private static interface BitmapLoaderListener {

        void onLoad(int pos, Bitmap b);

        void onComplete();

        void onError(int pos, Exception e);

    }
}
