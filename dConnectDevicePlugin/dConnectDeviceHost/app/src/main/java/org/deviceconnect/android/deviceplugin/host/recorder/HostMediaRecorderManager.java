/*
 HostMediaRecorderManager.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.recorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.WindowManager;

import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapper;
import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapperManager;
import org.deviceconnect.android.deviceplugin.host.recorder.audio.HostAudioRecorder;
import org.deviceconnect.android.deviceplugin.host.recorder.camera.Camera2Recorder;
import org.deviceconnect.android.deviceplugin.host.recorder.screen.ScreenCastRecorder;
import org.deviceconnect.android.deviceplugin.host.recorder.util.RecorderSetting;
import org.deviceconnect.android.libmedia.streaming.util.WeakReferenceList;
import org.deviceconnect.android.message.DevicePluginContext;
import org.deviceconnect.android.provider.FileManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Host Device Recorder Manager.
 *
 * @author NTT DOCOMO, INC.
 */
public class HostMediaRecorderManager {
    /**
     * レコーディング停止アクションを定義.
     */
    public static final String ACTION_STOP_RECORDING = "org.deviceconnect.android.deviceplugin.host.STOP_RECORDING";

    /**
     * オーバーレイ削除用アクションを定義.
     */
    public static final String ACTION_STOP_PREVIEW = "org.deviceconnect.android.deviceplugin.host.STOP_PREVIEW";

    /**
     * レコーダのIDを格納するためのキーを定義.
     */
    public static final String KEY_RECORDER_ID = "recorder_id";

    /**
     * List of HostMediaRecorder.
     */
    private final List<HostMediaRecorder> mRecorders = new ArrayList<>();

    /**
     * HostDevicePhotoRecorder.
     */
    private Camera2Recorder mDefaultPhotoRecorder;

    /**
     * コンテキスト.
     */
    private final DevicePluginContext mHostDevicePluginContext;

    /**
     * カメラ管理クラス.
     */
    private CameraWrapperManager mCameraWrapperManager;

    /**
     * ファイル管理クラス.
     */
    private FileManager mFileManager;

    /**
     * 画面の回転イベントを受け取るための BroadcastReceiver.
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
                WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                if (windowManager != null) {
                    int rotation = windowManager.getDefaultDisplay().getRotation();
                    for (HostMediaRecorder recorder : mRecorders) {
                        try {
                            recorder.onDisplayRotation(rotation);
                        } catch (Exception e) {
                            // ignore.
                        }
                    }
                }
            } else if (ACTION_STOP_PREVIEW.equals(action)) {
                stopPreviewServer(intent.getStringExtra(KEY_RECORDER_ID));
            } else if (ACTION_STOP_RECORDING.equals(action)) {
                stopRecording(intent.getStringExtra(KEY_RECORDER_ID));
            }
        }
    };

    public HostMediaRecorderManager(final DevicePluginContext pluginContext, final FileManager fileManager) {
        mHostDevicePluginContext = pluginContext;
        mFileManager = fileManager;
        initRecorders();
    }

    private Context getContext() {
        return mHostDevicePluginContext.getContext();
    }

    /**
     * レコーダの初期化処理を行います.
     *
     * <p>
     * ここで使用できるレコーダの登録を行います。
     * </p>
     */
    private void initRecorders() {
        if (checkCameraHardware()) {
            mCameraWrapperManager = new CameraWrapperManager(getContext());
            createCameraRecorders(mCameraWrapperManager, mFileManager);
        }

        if (checkMicrophone()) {
            createAudioRecorders(mFileManager);
        }

        if (checkMediaProjection()) {
            createScreenCastRecorder(mFileManager);
        }

        for (HostMediaRecorder recorder : mRecorders) {
            recorder.setOnEventListener(new HostMediaRecorder.OnEventListener() {
                @Override
                public void onPreviewStarted(List<PreviewServer> servers) {
                    postOnPreviewStarted(recorder, servers);
                }

                @Override
                public void onPreviewStopped() {
                    postOnPreviewStopped(recorder);
                }

                @Override
                public void onBroadcasterStarted(Broadcaster broadcaster) {
                    postOnBroadcasterStarted(recorder, broadcaster);
                }

                @Override
                public void onBroadcasterStopped() {
                    postOnBroadcasterStopped(recorder);
                }

                @Override
                public void onTakePhoto(String uri, String filePath, String mimeType) {
                    postOnTakePhoto(recorder, uri, filePath, mimeType);
                }

                @Override
                public void onRecordingStarted(String fileName) {
                    postOnRecordingStarted(recorder, fileName);
                }

                @Override
                public void onRecordingPause() {
                    postOnRecordingPause(recorder);
                }

                @Override
                public void onRecordingResume() {
                    postOnRecordingResume(recorder);
                }

                @Override
                public void onRecordingStopped(String fileName) {
                    postOnRecordingStopped(recorder, fileName);

                }
            });
        }

        try {
            initRecorderSetting();
        } catch (Exception e) {
            // TODO レコーダの初期化に失敗した場合の処理
        }
    }

    private void createAudioRecorders(final FileManager fileMgr) {
        mRecorders.add(new HostAudioRecorder(getContext(), fileMgr));
    }

    private void createScreenCastRecorder(final FileManager fileMgr) {
        mRecorders.add(new ScreenCastRecorder(getContext(), fileMgr));
    }

    private void createCameraRecorders(final CameraWrapperManager cameraMgr, final FileManager fileMgr) {
        List<Camera2Recorder> photoRecorders = new ArrayList<>();
        for (CameraWrapper camera : cameraMgr.getCameraList()) {
            photoRecorders.add(new Camera2Recorder(getContext(), camera, fileMgr));
        }
        mRecorders.addAll(photoRecorders);
        if (!photoRecorders.isEmpty()) {
            mDefaultPhotoRecorder = photoRecorders.get(0);
        }
    }

    private void initRecorderSetting() {
        RecorderSetting setting = RecorderSetting.getInstance(getContext().getApplicationContext());
        List<RecorderSetting.Target> targets = setting.getTargets();
        for (HostMediaRecorder recorder : mRecorders) {
            targets.add(new RecorderSetting.Target(recorder.getId(), recorder.getName(), recorder.getMimeType()));
        }
        setting.saveTargets(targets);
    }

    /**
     * 初期化処理を行います.
     */
    public void initialize() {
        for (HostMediaRecorder recorder : getRecorders()) {
            recorder.initialize();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(ACTION_STOP_PREVIEW);
        filter.addAction(ACTION_STOP_RECORDING);
        getContext().registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * レコーダのクリア処理を行います.
     */
    public void clean() {
        for (HostMediaRecorder recorder : getRecorders()) {
            recorder.clean();
        }
    }

    /**
     * レコーダの破棄処理を行います.
     */
    public void destroy() {
        // 登録されていない BroadcastReceiver を解除すると例外が発生するので、try-catchしておく。
        try {
            getContext().unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e) {
            // ignore.
        }

        mOnEventListeners.clear();

        clean();

        for (HostMediaRecorder recorder : getRecorders()) {
            recorder.destroy();
        }
        mCameraWrapperManager.destroy();
    }

    /**
     * レコーダの配列を取得します.
     *
     * @return レコーダの配列
     */
    public synchronized HostMediaRecorder[] getRecorders() {
        return mRecorders.toArray(new HostMediaRecorder[0]);
    }

    /**
     * 指定された ID に対応するレコーダを取得します.
     *
     * <p>
     * id に null が指定された場合には、デフォルトに設定されているレコーダを返却します。
     * </p>
     *
     * @param id レコーダの識別子
     * @return レコーダ
     */
    public HostMediaRecorder getRecorder(final String id) {
        if (mRecorders.size() == 0) {
            return null;
        }
        if (id == null) {
            if (mDefaultPhotoRecorder != null) {
                return mDefaultPhotoRecorder;
            }
            return mRecorders.get(0);
        }
        for (HostMediaRecorder recorder : mRecorders) {
            if (id.equals(recorder.getId())) {
                return recorder;
            }
        }
        return null;
    }

    /**
     * 指定された ID に対応する静止画用のレコーダを取得します.
     *
     * <p>
     * id に null が指定された場合には、デフォルトに設定されているレコーダを返却します。
     * </p>
     *
     * @param id  レコーダの識別子
     * @return レコーダ
     */
    public HostDevicePhotoRecorder getCameraRecorder(final String id) {
        if (id == null) {
            return mDefaultPhotoRecorder;
        }
        for (HostMediaRecorder recorder : mRecorders) {
            if (id.equals(recorder.getId())) {
                return recorder;
            }
        }
        return null;
    }

    /**
     * 指定された ID のレコーダのプレビューを停止します.
     *
     * @param id レコーダの ID
     */
    private void stopPreviewServer(final String id) {
        if (id == null) {
            return;
        }

        HostMediaRecorder recorder = getRecorder(id);
        if (recorder != null) {
            recorder.stopPreview();
        }
    }

    /**
     * 指定された ID のレコードの録音・録画を停止します.
     *
     * @param id レコードの ID
     */
    private void stopRecording(final String id) {
        if (id == null) {
            return;
        }

        HostMediaRecorder recorder = getRecorder(id);
        if (recorder != null) {
            recorder.stopRecording(null);
        }
    }

    /**
     * MediaProjection がサポートされている確認します.
     *
     * @return MediaProjection がサポートされている場合はtrue、それ以外はfalse
     */
    public static boolean isSupportedMediaProjection() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    /**
     * カメラを端末がサポートしているかチェックします.
     *
     * @return カメラをサポートしている場合はtrue、それ以外はfalse
     */
    private boolean checkCameraHardware() {
        return getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    /**
     * マイク入力を端末がサポートしているかチェックします.
     *
     * @return マイク入力をサポートしている場合はtrue、それ以外はfalse
     */
    private boolean checkMicrophone() {
        return getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
    }

    /**
     * MediaProjection APIを端末がサポートしているかチェックします.
     *
     * @return MediaProjection APIをサポートしている場合はtrue、それ以外はfalse
     */
    private boolean checkMediaProjection() {
        return HostMediaRecorderManager.isSupportedMediaProjection();
    }

    private WeakReferenceList<OnEventListener> mOnEventListeners = new WeakReferenceList<>();

    public void addOnEventListener(OnEventListener listener) {
        mOnEventListeners.add(listener);
    }

    public void removeOnEventListener(OnEventListener listener) {
        mOnEventListeners.remove(listener);
    }

    private void postOnPreviewStarted(HostMediaRecorder recorder, List<PreviewServer> servers) {
        for (OnEventListener l : mOnEventListeners) {
            l.onPreviewStarted(recorder, servers);
        }
    }

    private void postOnPreviewStopped(HostMediaRecorder recorder) {
        for (OnEventListener l : mOnEventListeners) {
            l.onPreviewStopped(recorder);
        }
    }

    private void postOnBroadcasterStarted(HostMediaRecorder recorder, Broadcaster broadcaster) {
        for (OnEventListener l : mOnEventListeners) {
            l.onBroadcasterStarted(recorder, broadcaster);
        }
    }

    private void postOnBroadcasterStopped(HostMediaRecorder recorder) {
        for (OnEventListener l : mOnEventListeners) {
            l.onBroadcasterStopped(recorder);
        }
    }

    private void postOnTakePhoto(HostMediaRecorder recorder, String uri, String filePath, String mimeType) {
        for (OnEventListener l : mOnEventListeners) {
            l.onTakePhoto(recorder, uri, filePath, mimeType);
        }
    }

    private void postOnRecordingStarted(HostMediaRecorder recorder, String fileName) {
        for (OnEventListener l : mOnEventListeners) {
            l.onRecordingStarted(recorder, fileName);
        }
    }

    private void postOnRecordingPause(HostMediaRecorder recorder) {
        for (OnEventListener l : mOnEventListeners) {
            l.onRecordingPause(recorder);
        }
    }

    private void postOnRecordingResume(HostMediaRecorder recorder) {
        for (OnEventListener l : mOnEventListeners) {
            l.onRecordingResume(recorder);
        }
    }

    private void postOnRecordingStopped(HostMediaRecorder recorder, String fileName) {
        for (OnEventListener l : mOnEventListeners) {
            l.onRecordingStopped(recorder, fileName);
        }
    }

    public interface OnEventListener {
        void onPreviewStarted(HostMediaRecorder recorder, List<PreviewServer> servers);
        void onPreviewStopped(HostMediaRecorder recorder);
        void onBroadcasterStarted(HostMediaRecorder recorder, Broadcaster broadcaster);
        void onBroadcasterStopped(HostMediaRecorder recorder);

        void onTakePhoto(HostMediaRecorder recorder, String uri, String filePath, String mimeType);

        void onRecordingStarted(HostMediaRecorder recorder, String fileName);
        void onRecordingPause(HostMediaRecorder recorder);
        void onRecordingResume(HostMediaRecorder recorder);
        void onRecordingStopped(HostMediaRecorder recorder, String fileName);
    }
}
