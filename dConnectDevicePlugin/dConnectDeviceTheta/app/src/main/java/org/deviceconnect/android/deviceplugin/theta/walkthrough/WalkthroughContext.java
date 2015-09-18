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
import org.deviceconnect.android.deviceplugin.theta.utils.JpegLoader;
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
    private static final boolean DEBUG = false; // BuildConfig.DEBUG;

    private static final long EXPIRE_INTERVAL = 10 * 1000;
    private static final float NS2S = 1.0f / 1000000000.0f;

    private Logger mLogger = Logger.getLogger("theta.dplugin");

    private final SensorManager mSensorMgr;
    private long mLastEventTimestamp;
    private final int mDisplayRotation;
    private Quaternion mCurrentRotation = new Quaternion(1, new Vector3D(0, 0, 0));

    private final File mDir;
    private final VideoPlayer mVideoPlayer;

    private byte[] mRoi;
    private String mUri;
    private String mSegment;


    private final long mInterval; // milliseconds
    private Timer mExpireTimer;
    private EventListener mListener;

    private final ExecutorService mPlayerThread = Executors.newFixedThreadPool(1);
    private PixelBuffer mPixelBuffer;
    private final SphereRenderer mRenderer = new SphereRenderer();
    private ByteArrayOutputStream mBaos;
    private boolean mIsStopped = true;

    private final Runnable mRendererTask = new Runnable() {
        @Override
        public void run() {
            try {
                mPixelBuffer.render();
                Bitmap result = mPixelBuffer.convertToBitmap();
                if (result == null) {
                    return;
                }
                mBaos.reset();
                result.compress(Bitmap.CompressFormat.JPEG, 100, mBaos);
                mRoi = mBaos.toByteArray();

                if (mListener != null) {
                    mListener.onUpdate(WalkthroughContext.this, mRoi);
                }
            } catch (Throwable e) {
                if (DEBUG) {
                    Log.d(TAG, "ERROR: Executor:", e);
                }
            }
        }
    };

    public WalkthroughContext(final Context context, final File omniImageDir,
                              final int width, final int height, final float fps) throws IOException {
        WindowManager windowMgr = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mDisplayRotation = windowMgr.getDefaultDisplay().getRotation();
        mSensorMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        mDir = omniImageDir;
        Video video = Video.createVideo(mDir, 1024, 512, fps);
        mVideoPlayer = new VideoPlayer(video, 20);
        mVideoPlayer.setDisplay(new VideoPlayer.Display() {

            @Override
            public void onPrepared() {
                if (DEBUG) {
                    Log.d(TAG, "onPrepared");
                }
                mVideoPlayer.playBy(1);
            }

            @Override
            public void onDraw(final FrameJpeg jpeg) {
                if (DEBUG) {
                    Log.d(TAG, "onDraw");
                }
                mRenderer.setTexture(jpeg.toBitmap());
                startRendering();
            }

            @Override
            public void onFinish() {
                if (DEBUG) {
                    Log.d(TAG, "onFinish");
                }
            }

            @Override
            public void onError(Throwable e) {
                if (DEBUG) {
                    Log.e(TAG, "onError: ", e);
                }
            }
        });
        mVideoPlayer.prepare();

        mInterval = (long) (1000.0f / fps);

        mBaos = new ByteArrayOutputStream(width * height);
        mPlayerThread.execute(new Runnable() {
            @Override
            public void run() {
                mPixelBuffer = new PixelBuffer(width, height, false);
                mPixelBuffer.setRenderer(mRenderer);
            }
        });

        Param param = new Param();
        param.setImageWidth(width);
        param.setImageHeight(height);
        initRendererParam(param);
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
            vGyroscope[2] = vGyroscope[0] * -1;
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

            SphereRenderer.Camera currentCamera = mRenderer.getCamera();
            SphereRenderer.CameraBuilder newCamera = new SphereRenderer.CameraBuilder(currentCamera);
            newCamera.rotate(mCurrentRotation);
            mRenderer.setCamera(newCamera.create());
        }
        mLastEventTimestamp = event.timestamp;
    }

    public void setUri(final String uriString) {
        mUri = uriString;
        mSegment = Uri.parse(uriString).getLastPathSegment();
    }

    public String getUri() {
        return mUri;
    }

    public String getSegment() {
        return mSegment;
    }

    public File getOmnidirectionalImageDirectory() {
        return mDir;
    }

    public byte[] getMedia() {
        return mRoi;
    }

    public synchronized void start() {
        if (DEBUG) {
            Log.d(TAG, "Walkthrough.start()");
        }

        if (mIsStopped) {
            mIsStopped = false;
            startVrMode();
            //seek(1);
        }
    }

    public synchronized void stop() {
        if (DEBUG) {
            Log.d(TAG, "Walkthrough.stop()");
        }

        if (!mIsStopped) {
            stopRendering();
            stopVrMode();
            mPixelBuffer.destroy();
            mPlayerThread.shutdownNow();
            mIsStopped = true;
        }
    }

    private Thread mRendererThread;

    private void startRendering() {
        if (mRendererThread == null) {
            mRendererThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (!mIsStopped) {
                            long start = System.currentTimeMillis();
                            render();
                            long end = System.currentTimeMillis();
                            long delay = mInterval - (end - start);
                            if (delay > 0) {
                                Thread.sleep(delay);
                            }
                        }
                    } catch (InterruptedException e) {
                        // Nothing to do.
                    }
                }
            });
            mRendererThread.start();
        }
    }

    private void stopRendering() {
        if (mRendererThread != null) {
            mRendererThread.interrupt();
            mRendererThread = null;
        }
    }

    public synchronized void seek(final int delta) {
        if (mIsStopped) {
            return;
        }
        mVideoPlayer.playBy(delta);
    }

    private void render() {
        if (DEBUG) {
            Log.d(TAG, "Walkthrough.render()");
        }

        if (mIsStopped) {
            if (DEBUG) {
                Log.w(TAG, "Walkthrough is stopped");
            }
            return;
        }
        mPlayerThread.execute(mRendererTask);
    }

    private void initRendererParam(final Param param) {
        SphereRenderer.CameraBuilder builder = new SphereRenderer.CameraBuilder();
        builder.setPosition(new Vector3D(
            (float) param.getCameraX(),
            (float) param.getCameraY() * -1,
            (float) param.getCameraZ()));
        builder.setFov((float) param.getCameraFov());
        mRenderer.setCamera(builder.create());
        mRenderer.setSphereRadius((float) param.getSphereSize());
        mRenderer.setScreenWidth(param.getImageWidth());
        mRenderer.setScreenHeight(param.getImageHeight());
    }

    public void startExpireTimer() {
        if (mExpireTimer != null) {
            return;
        }
        long now = System.currentTimeMillis();
        Date expireTime = new Date(now + EXPIRE_INTERVAL);
        mExpireTimer = new Timer();
        mExpireTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mExpireTimer.cancel();
                mExpireTimer = null;
                if (mListener != null) {
                    mListener.onExpire(WalkthroughContext.this);
                }
            }
        }, expireTime);
    }

    public void stopExpireTimer() {
        if (mExpireTimer != null) {
            mExpireTimer.cancel();
            mExpireTimer = null;
        }
    }

    public void restartExpireTimer() {
        stopExpireTimer();
        startExpireTimer();
    }

    public void setEventListener(final EventListener listener) {
        mListener = listener;
    }

    public interface EventListener {
        void onUpdate(WalkthroughContext context, byte[] roi);

        void onComplete(WalkthroughContext context);

        void onExpire(WalkthroughContext roiContext);
    }

    public static class Param {

        double mCameraX;

        double mCameraY;

        double mCameraZ;

        double mCameraYaw;

        double mCameraRoll;

        double mCameraPitch;

        double mCameraFov = 90.0d;

        double mSphereSize = 1.0d;

        int mImageWidth = 480;

        int mImageHeight = 270;

        boolean mStereoMode;

        boolean mVrMode;

        public double getCameraX() {
            return mCameraX;
        }

        public void setCameraX(final double x) {
            mCameraX = x;
        }

        public double getCameraY() {
            return mCameraY;
        }

        public void setCameraY(final double y) {
            mCameraY = y;
        }

        public double getCameraZ() {
            return mCameraZ;
        }

        public void setCameraZ(final double z) {
            mCameraZ = z;
        }

        public double getCameraYaw() {
            return mCameraYaw;
        }

        public void setCameraYaw(final double yaw) {
            mCameraYaw = yaw;
        }

        public double getCameraRoll() {
            return mCameraRoll;
        }

        public void setCameraRoll(final double roll) {
            mCameraRoll = roll;
        }

        public double getCameraPitch() {
            return mCameraPitch;
        }

        public void setCameraPitch(final double pitch) {
            mCameraPitch = pitch;
        }

        public double getCameraFov() {
            return mCameraFov;
        }

        public void setCameraFov(final double fov) {
            mCameraFov = fov;
        }

        public double getSphereSize() {
            return mSphereSize;
        }

        public void setSphereSize(final double size) {
            mSphereSize = size;
        }

        public int getImageWidth() {
            return mImageWidth;
        }

        public void setImageWidth(final int width) {
            mImageWidth = width;
        }

        public int getImageHeight() {
            return mImageHeight;
        }

        public void setImageHeight(final int height) {
            mImageHeight = height;
        }

        public boolean isStereoMode() {
            return mStereoMode;
        }

        public void setStereoMode(final boolean isStereo) {
            mStereoMode = isStereo;
        }

        public boolean isVrMode() {
            return mVrMode;
        }

        public void setVrMode(final boolean isVr) {
            mVrMode = isVr;
        }
    }
}
