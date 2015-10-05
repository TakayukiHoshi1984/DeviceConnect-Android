/*
 RoiDeliveryContext.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.theta.roi;


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

import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * ROI Image Delivery Context.
 *
 * @author NTT DOCOMO, INC.
 */
public class RoiDeliveryContext implements SensorEventListener {

    /**
     * The default parameter of ROI Settings API.
     */
    public static final Param DEFAULT_PARAM = new Param();

    private static final boolean DEBUG = false; // BuildConfig.DEBUG;

    private static final String TAG = "Roi";

    private static final float NS2S = 1.0f / 1000000000.0f;

    private static final long EXPIRE_INTERVAL = 10 * 1000;

    private static final float EPSILON = 0.000000001f;

    private static final SphereRenderer.Camera DEFAULT_CAMERA = new SphereRenderer.Camera();

    private long mLastEventTimestamp;

    private float mEventInterval;

    private final float[] vGyroscope = new float[3];

    private final float[] deltaVGyroscope = new float[4];

    private int mDisplayRotation;

    private final OmnidirectionalImage mSource;

    private final SensorManager mSensorMgr;

    private Timer mExpireTimer;

    private Timer mDeliveryTimer;

    private SphericalView mSphericalView;

    private Param mCurrentParam = DEFAULT_PARAM;

    private String mUri;

    private String mSegment;

    private final ExecutorService mExecutor = Executors.newFixedThreadPool(1);

    private OnChangeListener mListener;

    private float[] mCurrentRotation = new float[] {1, 0, 0, 0};

    private float[] qGyroscopeDelta = new float[4];

    private SphereRenderer.Camera mDefaultCamera;

    private Logger mLogger = Logger.getLogger("theta.dplugin");

    /**
     * Constructor.
     *
     * @param context an instance of {@link Context}
     * @param source an instance of {@link OmnidirectionalImage} to create ROI image
     */
    public RoiDeliveryContext(final Context context, final OmnidirectionalImage source) {
        WindowManager windowMgr = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mDisplayRotation = windowMgr.getDefaultDisplay().getRotation();

        mSource = source;
        mSensorMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

    }

    public void setSphericalView(final SphericalView view) {
        mSphericalView = view;
        mSphericalView.setTexture(mSource.getData());

        initCameraRotaion();
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

    public void destroy() {
        mSphericalView.getRenderer().clearTexture();
        if (mSensorMgr != null) {
            mSensorMgr.unregisterListener(RoiDeliveryContext.this);
        }
    }

    public void changeRendererParam(final Param param, final boolean isUserRequest) {
        final SphereRenderer renderer = mSphericalView.getRenderer();

        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (param.isCalibration()) {
                    resetCameraDirection();
                    return; // Ignore all other parameters;
                }

                if (isUserRequest) {
                    if (param.isVrMode()) {
                        startVrMode();
                    } else {
                        stopVrMode();
                    }
                }

                mCurrentParam = param;

                SphereRenderer.Camera camera = renderer.getCamera();
                camera.setPosition(new Vector3D(
                    (float) param.getCameraX(),
                    (float) param.getCameraY() * -1,
                    (float) param.getCameraZ()));
                if (isUserRequest) {
                    camera.rotateByEulerAngle(
                        (float) param.getCameraRoll(),
                        (float) param.getCameraYaw(),
                        (float) param.getCameraPitch() * -1);
                }
                camera.setFov((float) param.getCameraFov());
                renderer.setSphereRadius((float) param.getSphereSize());
                renderer.setScreenWidth(param.getImageWidth());
                renderer.setScreenHeight(param.getImageHeight());
                renderer.setStereoMode(param.isStereoMode());
            }
        });
    }

    private void initCameraRotaion() {
        float yaw = (float) mSource.getYaw();
        float roll = (float) mSource.getRoll();
        float pitch = (float) mSource.getPitch();
        if (DEBUG) {
            Log.d(TAG, "Exif: yaw = " + yaw + ", roll = " + roll + ", pitch = " + pitch);
        }

        mSphericalView.getRenderer().rotateSphere(-1 * yaw, -1 * roll, -1 * pitch);
    }

    private boolean isDisplaySizeChanged(final Param newParam) {
        return newParam.isStereoMode() != mCurrentParam.isStereoMode()
            || newParam.getImageWidth() != mCurrentParam.getImageWidth()
            || newParam.getImageHeight() != mCurrentParam.getImageHeight();
    }

    @Override
    public void onSensorChanged(final SensorEvent event) {
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

    @Override
    public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
        // Nothing to do.
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

    public void resetCameraDirection() {
        mSphericalView.resetCamera();
        mCurrentRotation = new float[] {1, 0, 0, 0};
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
                    mListener.onExpire(RoiDeliveryContext.this);
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

    public void setOnChangeListener(final OnChangeListener listener) {
        mListener = listener;
    }

    public interface OnChangeListener {

        void onExpire(RoiDeliveryContext roiContext);

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

        int mImageWidth = 600;

        int mImageHeight = 400;

        boolean mStereoMode;

        boolean mVrMode;

        boolean mCalibration;

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

        public boolean isCalibration() {
            return mCalibration;
        }

        public void setCalibration(final boolean isCalibration) {
            mCalibration = isCalibration;
        }
    }

}
