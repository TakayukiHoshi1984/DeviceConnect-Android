package org.deviceconnect.android.deviceplugin.theta.core.sensor;


import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.WindowManager;

import org.deviceconnect.android.deviceplugin.theta.utils.Quaternion;
import org.deviceconnect.android.deviceplugin.theta.utils.Vector3D;

import java.util.logging.Logger;

public class DefaultHeadTracker extends AbstractHeadTracker implements SensorEventListener {

    private static final float NS2S = 1.0f / 1000000000.0f;

    private long mLastEventTimestamp;

    private final SensorManager mSensorMgr;

    private Quaternion mCurrentRotation = new Quaternion(1, new Vector3D(0, 0, 0));

    private float[] mCurrentGyroscope = new float[3];

    private Logger mLogger = Logger.getLogger("theta.dplugin");

    private final WindowManager mWindowMgr;

    private int mAccCnt = 0;

    public DefaultHeadTracker(final Context context) {
        mSensorMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mWindowMgr = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public void start() {
        // Reset current rotation.
        mCurrentRotation = new Quaternion(1, new Vector3D(0, 0, 0));

        Sensor sensor = mSensorMgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (sensor == null) {
            mLogger.warning("Failed to start: Default GYROSCOPE sensor is NOT found.");
            return;
        }
        mSensorMgr.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        for (int i = 0; i < mCurrentGyroscope.length; i++) {
            mCurrentGyroscope[i] = 0.0f;
        }

        sensor = mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensor == null) {
            mSensorMgr.unregisterListener(this);
            mInitAccelSensor = false;
            mLogger.warning("Failed to start: any sensor is NOT found.");
            return;
        }
        mSensorMgr.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);

        sensor = mSensorMgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (sensor == null) {
            mSensorMgr.unregisterListener(this);
            mInitGeoSensor = false;
            mLogger.warning("Failed to start: any sensor is NOT found.");
            return;
        }
        mSensorMgr.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void stop() {
        mSensorMgr.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
        // Nothing to do.
    }

    @Override
    public void onSensorChanged(final SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_GYROSCOPE:
                onGyroSensorChanged(event);
                break;
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_MAGNETIC_FIELD:
                onAccelMagSensorChanged(event);
                break;
            default:
                break;
        }
    }

    void onGyroSensorChanged(final SensorEvent event) {
        if (mLastEventTimestamp != 0) {
            float[] values = new float[3];
            int displayOrientation = mWindowMgr.getDefaultDisplay().getRotation();
            switch (displayOrientation) {
                case Surface.ROTATION_0:
                    values[0] = event.values[0];
                    values[1] = event.values[1];
                    values[2] = event.values[2];
                    break;
                case Surface.ROTATION_90:
                    values[0] = event.values[1] * -1;
                    values[1] = event.values[0];
                    values[2] = event.values[2];
                    break;
                case Surface.ROTATION_180:
                    values[0] = event.values[0] * -1;
                    values[1] = event.values[1] * -1;
                    values[2] = event.values[2];
                    break;
                case Surface.ROTATION_270:
                    values[0] = event.values[1];
                    values[1] = event.values[0] * -1;
                    values[2] = event.values[2];
                    break;
                default:
                    break;
            }

            float epsilon = 0.000000001f;
            float[] vGyroscope = new float[3];
            float[] deltaVGyroscope = new float[4];
            Quaternion qGyroscopeDelta;
            float dT = (event.timestamp - mLastEventTimestamp) * NS2S;

            System.arraycopy(values, 0, vGyroscope, 0, vGyroscope.length);
            float tmp = vGyroscope[2];
            vGyroscope[2] = vGyroscope[0] * -1;
            vGyroscope[0] = tmp;

            float magnitude = (float) Math.sqrt(Math.pow(vGyroscope[0], 2)
                    + Math.pow(vGyroscope[1], 2) + Math.pow(vGyroscope[2], 2));
            if (magnitude > epsilon) {
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

            qGyroscopeDelta = new Quaternion(deltaVGyroscope[3], new Vector3D(deltaVGyroscope));
            mCurrentRotation = qGyroscopeDelta.multiply(mCurrentRotation);
            notifyHeadRotation(mCurrentRotation);
        }
        mLastEventTimestamp = event.timestamp;
    }

    float[] mGeomagnetic = new float[3];
    float[] mGravity = new float[3];
    float[] mCurrentgeomagnetic = new float[3];
    float[] mCurrentgravity = new  float[3];
    float[] mAttitude = new float[3];
    float[] mInR = new float[16];
    float[] mOutR = new float[16];
    float[] mI = new float[16];
    boolean mInitAccelSensor = false;
    boolean mInitGeoSensor = false;
    boolean mInitAccelGeoQuaternion = false;
    Quaternion mAccelGeoQuaternion;

    void onAccelMagSensorChanged(final SensorEvent event) {
        final float alpha = 0.93f;
        switch (event.sensor.getType()) {
            case Sensor.TYPE_MAGNETIC_FIELD:
                if (!mInitGeoSensor) {
                    mCurrentgeomagnetic = event.values.clone();
                    mInitGeoSensor = true;
                }
                mGeomagnetic = event.values.clone();
                break;
            case Sensor.TYPE_ACCELEROMETER:
                if (!mInitAccelSensor) {
                    mCurrentgravity = event.values.clone();
                    mInitAccelSensor = true;
                }
                mGravity = event.values.clone();
                break;
            default:
                return;
        }

        if (mInitAccelSensor && mInitGeoSensor) {

            mCurrentgeomagnetic[0] = alpha * mCurrentgeomagnetic[0] + (1.0f - alpha) * mGeomagnetic[0];
            mCurrentgeomagnetic[1] = alpha * mCurrentgeomagnetic[1] + (1.0f - alpha) * mGeomagnetic[1];
            mCurrentgeomagnetic[2] = alpha * mCurrentgeomagnetic[2] + (1.0f - alpha) * mGeomagnetic[2];
            mCurrentgravity[0] = alpha * mCurrentgravity[0] + (1.0f - alpha) * mGravity[0];
            mCurrentgravity[1] = alpha * mCurrentgravity[1] + (1.0f - alpha) * mGravity[1];
            mCurrentgravity[2] = alpha * mCurrentgravity[2] + (1.0f - alpha) * mGravity[2];

            SensorManager.getRotationMatrix(mInR, mI, mCurrentgravity, mCurrentgeomagnetic);
            SensorManager.remapCoordinateSystem(mInR, SensorManager.AXIS_X, SensorManager.AXIS_Z, mOutR);
            SensorManager.getOrientation(mOutR, mAttitude);

            float[] delta = new float[3];
            delta[0] = mAttitude[2] * -1;
            delta[1] = (mAttitude[0] - (float) (Math.PI / 2f)) * -1;
            delta[2] = mAttitude[1];
            Quaternion qAtitude = new Quaternion(1, new Vector3D(delta));

            if (!mInitAccelGeoQuaternion) {
                mAccelGeoQuaternion = new Quaternion(1, new Vector3D(delta)).conjugate();
                mInitAccelGeoQuaternion = true;
            }
            Quaternion deltaQ = new Quaternion(1, new Vector3D(0, 0, 0));
            float[] tmpVec = new float[3];
            tmpVec[0] = mAccelGeoQuaternion.imaginary().x() + qAtitude.imaginary().x();
            tmpVec[1] = mAccelGeoQuaternion.imaginary().y() + qAtitude.imaginary().y();
            tmpVec[2] = mAccelGeoQuaternion.imaginary().z() + qAtitude.imaginary().z();

            deltaQ = deltaQ.multiply(new Quaternion(1, new Vector3D(tmpVec)));

            if (mAccCnt % 8 == 0) {
                mCurrentRotation = deltaQ;
                notifyHeadRotation(mCurrentRotation);
            }
            mAccCnt++;
        }
    }

    @Override
    public void reset() {
        mCurrentRotation = new Quaternion(1, new Vector3D(0, 0, 0));
        mInitGeoSensor = false;
        mInitAccelSensor = false;
        mInitAccelGeoQuaternion = false;
    }

}
