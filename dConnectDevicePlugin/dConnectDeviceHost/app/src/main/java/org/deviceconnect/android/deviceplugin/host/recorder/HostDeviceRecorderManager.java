/*
 HostDeviceRecorderManager.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.recorder;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import org.deviceconnect.android.deviceplugin.host.BuildConfig;
import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapper;
import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapperManager;
import org.deviceconnect.android.deviceplugin.host.recorder.audio.HostDeviceAudioRecorder;
import org.deviceconnect.android.deviceplugin.host.recorder.camera.Camera2Recorder;
import org.deviceconnect.android.deviceplugin.host.recorder.screen.HostDeviceScreenCastRecorder;
import org.deviceconnect.android.event.Event;
import org.deviceconnect.android.event.EventManager;
import org.deviceconnect.android.message.DevicePluginContext;
import org.deviceconnect.android.profile.MediaStreamRecordingProfile;
import org.deviceconnect.android.provider.FileManager;
import org.deviceconnect.profile.MediaStreamRecordingProfileConstants;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Host Device Recorder Manager.
 *
 * @author NTT DOCOMO, INC.
 */
public class HostDeviceRecorderManager {

    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final String TAG = "RecorderManager";

    /** List of HostDeviceRecorder. */
    private final List<HostDeviceRecorder> mRecorders = new ArrayList<>();

    /** HostDevicePhotoRecorder. */
    private Camera2Recorder mDefaultPhotoRecorder;

    /** コンテキスト. */
    private final DevicePluginContext mHostDevicePluginContext;

    /** インテントフィルタ. */
    private final IntentFilter mIntentFilter = new IntentFilter();
    {
        mIntentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
    }

    /** ブロードキャストレシーバ. */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (DEBUG) {
                Log.d(TAG, "BroadcastReceiver.onReceive: action=" + intent.getAction());
            }
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            int rotation = windowManager.getDefaultDisplay().getRotation();
            for (HostDeviceRecorder recorder : mRecorders) {
                if (DEBUG) {
                    Log.d(TAG, "BroadcastReceiver.onReceive: recorder=" + recorder.getId());
                }
                recorder.onDisplayRotation(rotation);
            }
        }
    };

    private final FileManager mFileManager;

    public HostDeviceRecorderManager(final DevicePluginContext pluginContext,
                                     final FileManager fileMgr) {
        mHostDevicePluginContext = pluginContext;
        mFileManager = fileMgr;
    }

    public void watchCameraAvailability(final CameraWrapperManager cameraMgr) {
        cameraMgr.addAvailabilityListener(new CameraWrapperManager.AvailabilityListener() {
            @Override
            public void onCameraAvailable(final CameraWrapper camera) {
                if (DEBUG) {
                    Log.d(TAG, "onCameraAvailable: " + camera.getId());
                }
                synchronized (mRecorders) {
                    HostDeviceRecorder found = null;
                    for (HostDeviceRecorder recorder : mRecorders) {
                        if (recorder instanceof Camera2Recorder) {
                            CameraWrapper c = ((Camera2Recorder) recorder).getCameraWrapper();
                            if (c != null && c.getId().equals(camera.getId())) {
                                found = recorder;
                                break;
                            }
                        }
                    }
                    if (found == null) {
                        mRecorders.add(new Camera2Recorder(getContext(), camera, mFileManager));
                    }
                }
            }

            @Override
            public void onCameraUnavailable(final CameraWrapper camera) {
                if (DEBUG) {
                    Log.d(TAG, "onCameraUnavailable: " + camera.getId());
                }
                synchronized (mRecorders) {
                    for (Iterator<HostDeviceRecorder> it = mRecorders.iterator(); it.hasNext(); ) {
                        HostDeviceRecorder recorder = it.next();
                        if (recorder instanceof Camera2Recorder) {
                            CameraWrapper c = ((Camera2Recorder) recorder).getCameraWrapper();
                            if (c != null && c.getId().equals(camera.getId())) {
                                it.remove();
                                break;
                            }
                        }
                    }
                }
            }
        });
    }

    public void createAudioRecorders() {
        mRecorders.add(new HostDeviceAudioRecorder(getContext()));
    }

    public void createScreenCastRecorder(final FileManager fileMgr) {
        mRecorders.add(new HostDeviceScreenCastRecorder(getContext(), fileMgr));
    }

    public void createCameraRecorders(final CameraWrapperManager cameraMgr) {
        List<Camera2Recorder> photoRecorders = new ArrayList<>();
        for (CameraWrapper camera : cameraMgr.getCameraList()) {
            photoRecorders.add(new Camera2Recorder(getContext(), camera, mFileManager));
        }
        mRecorders.addAll(photoRecorders);
        if (!photoRecorders.isEmpty()) {
            mDefaultPhotoRecorder = photoRecorders.get(0);
        }
    }

    public void initialize() {
        for (HostDeviceRecorder recorder : getRecorders()) {
            recorder.initialize();
        }
    }

    public void start() {
        getContext().registerReceiver(mBroadcastReceiver, mIntentFilter);
    }

    public void stop() {
        getContext().unregisterReceiver(mBroadcastReceiver);
    }

    public void clean() {
        for (HostDeviceRecorder recorder : getRecorders()) {
            recorder.clean();
        }
    }

    public synchronized HostDeviceRecorder[] getRecorders() {
        return mRecorders.toArray(new HostDeviceRecorder[mRecorders.size()]);
    }

    public HostDeviceRecorder getRecorder(final String id) {
        if (mRecorders.size() == 0) {
            return null;
        }
        if (id == null) {
            if (mDefaultPhotoRecorder != null) {
                return mDefaultPhotoRecorder;
            }
            return mRecorders.get(0);
        }
        for (HostDeviceRecorder recorder : mRecorders) {
            if (id.equals(recorder.getId())) {
                return recorder;
            }
        }
        return null;
    }

    public HostDevicePhotoRecorder getCameraRecorder(final String id) {
        if (id == null) {
            return mDefaultPhotoRecorder;
        }
        for (HostDeviceRecorder recorder : mRecorders) {
            if (id.equals(recorder.getId()) && recorder instanceof HostDevicePhotoRecorder) {
                return (HostDevicePhotoRecorder) recorder;
            }
        }
        return null;
    }

    public HostDeviceStreamRecorder getStreamRecorder(final String id) {
        if (id == null) {
            return mDefaultPhotoRecorder;
        }
        for (HostDeviceRecorder recorder : mRecorders) {
            if (id.equals(recorder.getId()) && recorder instanceof HostDeviceStreamRecorder) {
                return (HostDeviceStreamRecorder) recorder;
            }
        }
        return null;
    }

    public PreviewServerProvider getPreviewServerProvider(final String id) {
        if (id == null) {
            return mDefaultPhotoRecorder;
        }
        for (HostDeviceRecorder recorder : mRecorders) {
            if (id.equals(recorder.getId()) && recorder instanceof PreviewServerProvider) {
                return (AbstractPreviewServerProvider) recorder;
            }
        }
        return null;
    }

    public void stopWebServer(final String id) {
        if (id == null) {
            return;
        }
        HostDeviceRecorder recorder = getRecorder(id);
        if (recorder instanceof PreviewServerProvider) {
            ((PreviewServerProvider) recorder).stopWebServers();
        }
    }

    @SuppressWarnings("deprecation")
    public void sendEventForRecordingChange(final String serviceId, final HostDeviceRecorder.RecorderState state,
                                             final String uri, final String path,
                                             final String mimeType, final String errorMessage) {
        List<Event> evts = EventManager.INSTANCE.getEventList(serviceId,
                MediaStreamRecordingProfile.PROFILE_NAME, null,
                MediaStreamRecordingProfile.ATTRIBUTE_ON_RECORDING_CHANGE);

        Bundle record = new Bundle();
        switch (state) {
            case RECORDING:
                MediaStreamRecordingProfile.setStatus(record, MediaStreamRecordingProfileConstants.RecordingState.RECORDING);
                break;
            case INACTTIVE:
                MediaStreamRecordingProfile.setStatus(record, MediaStreamRecordingProfileConstants.RecordingState.STOP);
                break;
            case ERROR:
                MediaStreamRecordingProfile.setStatus(record, MediaStreamRecordingProfileConstants.RecordingState.ERROR);
                break;
            default:
                MediaStreamRecordingProfile.setStatus(record, MediaStreamRecordingProfileConstants.RecordingState.UNKNOWN);
                break;
        }
        record.putString(MediaStreamRecordingProfile.PARAM_URI, uri);
        record.putString(MediaStreamRecordingProfile.PARAM_PATH, path);
        record.putString(MediaStreamRecordingProfile.PARAM_MIME_TYPE, mimeType);
        if (errorMessage != null) {
            record.putString(MediaStreamRecordingProfile.PARAM_ERROR_MESSAGE, errorMessage);
        }

        for (Event evt : evts) {
            Intent intent = EventManager.createEventMessage(evt);
            intent.putExtra(MediaStreamRecordingProfile.PARAM_MEDIA, record);
            mHostDevicePluginContext.sendEvent(intent, evt.getAccessToken());
        }
    }

    public static boolean isSupportedMediaProjection() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    private Context getContext() {
        return mHostDevicePluginContext.getContext();
    }

}
