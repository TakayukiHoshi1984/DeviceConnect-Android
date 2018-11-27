/*
 DeviceOrientationHelper.java
 Copyright (c) 2018 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.profile.utils;

import android.hardware.SensorManager;

/**
 * 加速度センサーと磁束センサーからデバイスの向きを計算するためのヘルパークラス.
 *
 * @author NTT DOCOMO, INC.
 */
public class DeviceOrientationHelper {
    /**
     * ローパスフィルタの平滑化係数.
     * <p>
     * 0.0f < ALPHA < 1.0f
     * </p>
     */
    private static final float ALPHA = 0.8f;

    /**
     * 加速度センサーの閾値を定義します.
     * <p>
     * この値を超えた時に向きの計算を行います.
     * </p>
     */
    private static final float THRESHOLD_ACCELEROMETER = 0.08f;

    /**
     * 磁束センサーの閾値を定義します.
     * <p>
     * この値を超えた時に向きの計算を行います.
     * </p>
     */
    private static final float THRESHOLD_MAGNETIC_FLUX = 200.0f;

    /**
     * マトリクスのサイズを定義します.
     */
    private static final int MATRIX_SIZE = 16;

    /**
     * ラジアンを角度に変換するための係数を定義します.
     */
    private static final double RAD2DEG = 180 / Math.PI;

    /**
     * 回転行列を格納するためのマトリクス.
     */
    private float[] mInR = new float[MATRIX_SIZE];

    /**
     * 回転行列を格納するためのマトリクス.
     */
    private float[] mI = new float[MATRIX_SIZE];

    /**
     * 座標系に合わせて回転行を変換した結果を格納するマトリクス.
     */
    private float[] mOutR = new float[MATRIX_SIZE];

    /**
     * デバイスの向きを格納する配列.
     */
    private float[] mOrientationValues = new float[3];

    /**
     * 一つ前の加速度センサーの値を保持する変数.
     */
    private float[] mLastAccelerometer = new float[3];

    /**
     * 一つ前の現在の加速度センサーの値を計算して補間した値.
     */
    private float[] mAccelerometer = new float[3];

    /**
     * 一つ前の磁束センサーの値を保持する変数.
     */
    private float[] mLastMagneticFlux = new float[3];

    /**
     * 一つ前の現在の磁束センサーの値を計算して補間した値.
     */
    private float[] mMagneticFlux = new float[3];

    /**
     * データ計算が行われる前フラグ.
     */
    private boolean mInitFlag;

    /**
     * 初期化.
     */
    public void init() {
        mInitFlag = false;
    }

    /**
     * デバイスの向きを計算します.
     * <p>
     * 細かく磁束センサーが揺れるので、一定の閾値にならないと向きの計算を行わないようにしています。
     * また、向きの計算はローパスフィルタを用いて、少しだけ補正をかけています。
     * </p>
     * @param accelerometerWithGravity 加速度センサーの値
     * @param magneticFlux 磁束センサーの値
     */
    public void calc(final float[] accelerometerWithGravity, final float[] magneticFlux) {
        if (a(accelerometerWithGravity) > THRESHOLD_ACCELEROMETER || b(magneticFlux) > THRESHOLD_MAGNETIC_FLUX) {
            if (!mInitFlag) {
                mAccelerometer[0] = accelerometerWithGravity[0];
                mAccelerometer[1] = accelerometerWithGravity[1];
                mAccelerometer[2] = accelerometerWithGravity[2];

                mMagneticFlux[0] = magneticFlux[0];
                mMagneticFlux[1] = magneticFlux[1];
                mMagneticFlux[2] = magneticFlux[2];

                mInitFlag = true;
            } else {
                // ローパスフィルタの計算
                mAccelerometer[0] = ALPHA * accelerometerWithGravity[0] + (1.0f - ALPHA) * mLastAccelerometer[0];
                mAccelerometer[1] = ALPHA * accelerometerWithGravity[1] + (1.0f - ALPHA) * mLastAccelerometer[1];
                mAccelerometer[2] = ALPHA * accelerometerWithGravity[2] + (1.0f - ALPHA) * mLastAccelerometer[2];

                mMagneticFlux[0] = ALPHA * magneticFlux[0] + (1.0f - ALPHA) * mLastMagneticFlux[0];
                mMagneticFlux[1] = ALPHA * magneticFlux[1] + (1.0f - ALPHA) * mLastMagneticFlux[1];
                mMagneticFlux[2] = ALPHA * magneticFlux[2] + (1.0f - ALPHA) * mLastMagneticFlux[2];
            }

            calcInternal(mAccelerometer, mMagneticFlux);

            mLastAccelerometer[0] = accelerometerWithGravity[0];
            mLastAccelerometer[1] = accelerometerWithGravity[1];
            mLastAccelerometer[2] = accelerometerWithGravity[2];

            mLastMagneticFlux[0] = magneticFlux[0];
            mLastMagneticFlux[1] = magneticFlux[1];
            mLastMagneticFlux[2] = magneticFlux[2];
        }
    }

    /**
     * デバイスの向きを計算します.
     *
     * @param accelerometer 加速度センサーの値
     * @param magneticFlux 磁束センサーの値
     */
    private void calcInternal(final float[] accelerometer, final float[] magneticFlux) {
        SensorManager.getRotationMatrix(mInR, mI, accelerometer, magneticFlux);
        SensorManager.remapCoordinateSystem(mInR, SensorManager.AXIS_X, SensorManager.AXIS_Z, mOutR);
        SensorManager.getOrientation(mOutR, mOrientationValues);

        mOrientationValues[0] = (float) (mOrientationValues[0] * RAD2DEG);
        mOrientationValues[1] = (float) (mOrientationValues[1] * RAD2DEG);
        mOrientationValues[2] = (float) (mOrientationValues[2] * RAD2DEG);
    }

    /**
     * 加速度センサーの差分を計算します.
     *
     * @param accelerometerWithGravity 加速度センサーの値
     * @return 加速度センサーの差分
     */
    private float a(final float[] accelerometerWithGravity) {
        float dx = mLastAccelerometer[0] - accelerometerWithGravity[0];
        float dy = mLastAccelerometer[1] - accelerometerWithGravity[1];
        float dz = mLastAccelerometer[2] - accelerometerWithGravity[2];
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 磁束センサーの差分を計算します.
     *
     * @param magneticFlux 磁束センサーの値
     * @return 磁束センサーの差分
     */
    private float b(final float[] magneticFlux) {
        float dx = mMagneticFlux[0] - magneticFlux[0];
        float dy = mMagneticFlux[1] - magneticFlux[1];
        float dz = mMagneticFlux[2] - magneticFlux[2];
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * デバイスの向きを取得します.
     *
     * @return デバイスの向き
     */
    public float[] getOrientationValues() {
        return mOrientationValues;
    }
}
