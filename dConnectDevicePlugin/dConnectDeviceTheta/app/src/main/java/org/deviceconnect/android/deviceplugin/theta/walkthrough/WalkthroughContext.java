package org.deviceconnect.android.deviceplugin.theta.walkthrough;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import org.deviceconnect.android.deviceplugin.theta.opengl.SphereRenderer;
import org.deviceconnect.android.deviceplugin.theta.opengl.SphericalView;
import org.deviceconnect.android.deviceplugin.theta.utils.Quaternion;
import org.deviceconnect.android.deviceplugin.theta.utils.Vector3D;

import java.io.File;
import java.io.IOException;
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
    private static final float EPSILON = 0.000000001f;

    private static final SphereRenderer.Camera DEFAULT_CAMERA = new SphereRenderer.Camera();

    private Logger mLogger = Logger.getLogger("theta.dplugin");

    private final float[] vGyroscope = new float[3];
    private final float[] deltaVGyroscope = new float[4];
    private final SensorManager mSensorMgr;
    private long mLastEventTimestamp;
    private final int mDisplayRotation;
    private float[] mCurrentRotation = new float[] {1, 0, 0, 0};
    private float[] qGyroscopeDelta = new float[4];

    private final File mDir;
    private final Video mVideo;
    private final VideoPlayer mVideoPlayer;
    private boolean mIsAutoPlay;

    private SphericalView mSphericalView;

    private String mUri;
    private String mSegment;

    private final long mInterval; // milliseconds
    private Timer mExpireTimer;
    private EventListener mListener;

    private boolean mIsStopped = true;

    public WalkthroughContext(final Context context, final File omniImageDir, final float fps) throws IOException {
        Log.d(TAG, "WalkthroughContext: dir = " + omniImageDir);
        WindowManager windowMgr = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mDisplayRotation = windowMgr.getDefaultDisplay().getRotation();
        mSensorMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        mDir = omniImageDir;
        mVideo = Video.createVideo(mDir, 1024, 512, fps, 15.0);
        mVideoPlayer = new VideoPlayer(mVideo, 5);
        mVideoPlayer.setDisplay(new VideoPlayer.Display() {

            @Override
            public void onPrepared() {
                if (DEBUG) {
                    Log.d(TAG, "onPrepared");
                }

                if (isAutoPlay()) {
                    mVideoPlayer.playAuto();
                } else {
                    mVideoPlayer.playBy(1);
                }
            }

            @Override
            public void onDraw(final FrameJpeg jpeg) {
                if (DEBUG) {
                    Log.d(TAG, "onDraw: jpeg = " + jpeg.getFrame().getPosition() + ", bitmap = " + jpeg.toBitmap());
                }

                try {
                    mSphericalView.setTexture(jpeg.toBitmap());
                } catch (Throwable e) {
                    Log.e(TAG, "onDraw: ERROR: ", e);
                }
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

        mInterval = (long) (1000.0f / fps);
    }

    public void setFOV(float fov) {
        SphereRenderer renderer = mSphericalView.getRenderer();

        SphereRenderer.Camera camera = renderer.getCamera();
        camera.setFov(fov);
    }

//    public void rotate(final int yaw, final int roll, final int pitch) {
//        if (mSphericalView != null) {
//            mSphericalView.getRenderer().rotateSphere(-1 * yaw, -1 * roll, -1 * pitch);
//        }
//    }

    public void setView(final SphericalView view) {
        mSphericalView = view;

        Param param = new Param();
        param.setImageWidth(view.getWidth());
        param.setImageHeight(view.getHeight());

        SphereRenderer renderer = mSphericalView.getRenderer();
        renderer.setSphereRadius((float) param.getSphereSize());
        renderer.setScreenWidth(param.getImageWidth());
        renderer.setScreenHeight(param.getImageHeight());

        SphereRenderer.Camera camera = renderer.getCamera();
        camera.setPosition(new Vector3D(
            (float) param.getCameraX(),
            (float) param.getCameraY() * -1,
            (float) param.getCameraZ()));
        camera.setFov((float) param.getCameraFov());
    }

    private boolean startVrMode() {
        // Reset current rotation.
        mCurrentRotation = new float[] {1, 0, 0, 0};

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
        synchronized (this) {
            if (mLastEventTimestamp != 0) {
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

                qGyroscopeDelta[0] = deltaVGyroscope[3];
                switch (mDisplayRotation) {
                    case Surface.ROTATION_0:
                        qGyroscopeDelta[1] = deltaVGyroscope[0];
                        qGyroscopeDelta[2] = deltaVGyroscope[1];
                        qGyroscopeDelta[3] = deltaVGyroscope[2];
                        break;
                    case Surface.ROTATION_90:
                        qGyroscopeDelta[1] = deltaVGyroscope[0];
                        qGyroscopeDelta[2] = deltaVGyroscope[2] * -1;
                        qGyroscopeDelta[3] = deltaVGyroscope[1];
                        break;
                    case Surface.ROTATION_180:
                        qGyroscopeDelta[1] = deltaVGyroscope[0];
                        qGyroscopeDelta[2] = deltaVGyroscope[1] * -1;
                        qGyroscopeDelta[3] = deltaVGyroscope[2];
                        break;
                    case Surface.ROTATION_270:
                        qGyroscopeDelta[1] = deltaVGyroscope[0];
                        qGyroscopeDelta[2] = deltaVGyroscope[2];
                        qGyroscopeDelta[3] = deltaVGyroscope[1] * -1;
                        break;
                    default:
                        break;
                }

                Quaternion.multiply(mCurrentRotation, qGyroscopeDelta, mCurrentRotation);

                SphereRenderer renderer = mSphericalView.getRenderer();
                SphereRenderer.Camera currentCamera = renderer.getCamera();
                currentCamera.rotate(DEFAULT_CAMERA, mCurrentRotation);
            }
            mLastEventTimestamp = event.timestamp;
        }
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
        if (mSphericalView == null) {
            return null;
        }
        return mSphericalView.getRoi();
    }

    public boolean isAutoPlay() {
        return mIsAutoPlay;
    }

    public void setAutoPlay(final boolean isAutoPlay) {
        mIsAutoPlay = isAutoPlay;
    }

    public synchronized void start() {
        if (DEBUG) {
            Log.d(TAG, "Walkthrough.start()");
        }

        if (mIsStopped) {
            mIsStopped = false;
            mVideoPlayer.prepare();
            startVrMode();
        }
    }

    public synchronized void stop() {
        if (DEBUG) {
            Log.d(TAG, "Walkthrough.stop()");
        }

        if (!mIsStopped) {
            mIsStopped = true;
            mSphericalView.getRenderer().clearTexture();
            stopExpireTimer();
            stopVrMode();
            mVideoPlayer.destroy();
        }
    }

    public void resetCameraDirection() {
        mSphericalView.resetCamera();
        mCurrentRotation = new float[] {1, 0, 0, 0};
    }

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    public synchronized void seek(final int delta) {
        if (mIsStopped) {
            return;
        }
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mVideoPlayer.playBy(delta);
            }
        });
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

    public synchronized void restartExpireTimer() {
        stopExpireTimer();
        startExpireTimer();
    }

    public void setEventListener(final EventListener listener) {
        mListener = listener;
    }

    public interface EventListener {

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
