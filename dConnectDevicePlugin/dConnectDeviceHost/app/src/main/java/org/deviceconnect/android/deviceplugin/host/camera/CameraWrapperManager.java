/*
 CameraWrapperManager.java
 Copyright (c) 2018 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.camera;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;

import org.deviceconnect.android.deviceplugin.host.BuildConfig;
import org.deviceconnect.android.deviceplugin.host.recorder.camera.Camera2Recorder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * カメラ管理クラス.
 *
 * <p>アプリケーションに対してただ1つのインスタンスを作成すること.</p>
 *
 * @author NTT DOCOMO, INC.
 */
public class CameraWrapperManager {

    public interface AvailabilityListener {
        void onCameraAvailable(CameraWrapper camera);
        void onCameraUnavailable(CameraWrapper camera);
    }

    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final String TAG = "CameraWrapperManager";

    /**
     * カメラ操作クラスの一覧.
     */
    private final Map<String, CameraWrapper> mCameras = new LinkedHashMap<>();

    private CameraManager.AvailabilityCallback mAvailabilityCallback = new CameraManager.AvailabilityCallback() {
        @Override
        public void onCameraAvailable(final @NonNull String cameraId) {
            if (DEBUG) {
                Log.d(TAG, "onCameraAvailable: cameraId=" + cameraId);
            }
            synchronized (mCameras) {
                final CameraWrapper camera = new CameraWrapper(mContext, cameraId);
                mCameras.put(cameraId, camera);

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mAvailabilityListeners) {
                            for (final AvailabilityListener l : mAvailabilityListeners) {
                                l.onCameraAvailable(camera);
                            }
                        }
                    }
                });
            }
        }

        @Override
        public void onCameraUnavailable(final @NonNull String cameraId) {
            if (DEBUG) {
                Log.d(TAG, "onCameraUnavailable: cameraId=" + cameraId);
            }
            synchronized (mCameras) {
                final CameraWrapper camera = mCameras.remove(cameraId);
                if (camera != null) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (mAvailabilityListeners) {
                                for (final AvailabilityListener l : mAvailabilityListeners) {
                                    l.onCameraUnavailable(camera);
                                }
                            }
                        }
                    });
                }
            }
        }
    };

    private final Context mContext;

    private final Handler mHandler;

    private final List<AvailabilityListener> mAvailabilityListeners = new ArrayList<>();

    /**
     * コンストラクタ.
     * @param context コンテキスト
     */
    public CameraWrapperManager(final Context context, final Handler handler) {
        mContext = context;
        mHandler = handler;
        try {

            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            cameraManager.registerAvailabilityCallback(mAvailabilityCallback, mHandler);
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraWrapper camera = new CameraWrapper(context, cameraId);
                mCameras.put(cameraId, camera);
            }
        } catch (CameraAccessException e) {
            // No camera is available now.
        }
    }

    public void addAvailabilityListener(final AvailabilityListener listener) {
        synchronized (mAvailabilityListeners) {
            for (AvailabilityListener l : mAvailabilityListeners) {
                if (l == listener) {
                    return;
                }
            }
            mAvailabilityListeners.add(listener);
        }
    }

    public void removeAvailabilityListener(final AvailabilityListener listener) {
        synchronized (mAvailabilityListeners) {
            for (Iterator<AvailabilityListener> it = mAvailabilityListeners.iterator(); it.hasNext(); ) {
                if (it.next() == listener) {
                    it.remove();
                    return;
                }
            }
        }
    }

    /**
     * カメラ操作クラスのリストを取得する.
     * @return カメラ操作クラスのリスト
     */
    public synchronized List<CameraWrapper> getCameraList() {
        return new ArrayList<>(mCameras.values());
    }

    /**
     * カメラ操作クラスを全て破棄する.
     * アプリケーションを終了するときにのみ実行すること.
     */
    public synchronized void destroy() {
        for (CameraWrapper camera : mCameras.values()) {
            camera.destroy();
        }
        mCameras.clear();
    }
}
