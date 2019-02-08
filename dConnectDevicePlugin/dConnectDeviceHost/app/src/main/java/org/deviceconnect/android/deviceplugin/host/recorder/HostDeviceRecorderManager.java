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
import org.deviceconnect.android.deviceplugin.host.HostDevicePlugin;
import org.deviceconnect.android.deviceplugin.host.HostDeviceService;
import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapper;
import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapperManager;
import org.deviceconnect.android.deviceplugin.host.mediaplayer.VideoConst;
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
    private final HostDeviceService mHostDeviceService;

    /** インテントフィルタ. */
    private final IntentFilter mIntentFilter = new IntentFilter();
    {
        mIntentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        mIntentFilter.addAction(VideoConst.SEND_VIDEO_TO_HOSTDP);
    }

    /** ブロードキャストレシーバ. */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (DEBUG) {
                Log.d(TAG, "BroadcastReceiver.onReceive: action=" + intent.getAction());
            }
            if (VideoConst.SEND_VIDEO_TO_HOSTDP.equals(intent.getAction())) {
                String target = intent.getStringExtra(VideoConst.EXTRA_RECORDER_ID);
                HostDeviceRecorder.RecorderState state =
                        (HostDeviceRecorder.RecorderState) intent.getSerializableExtra(VideoConst.EXTRA_VIDEO_RECORDER_STATE);
                String serviceId = intent.getStringExtra(VideoConst.EXTRA_SERVICE_ID);
                String fileName = intent.getStringExtra(VideoConst.EXTRA_FILE_NAME);
                String uri = "";
                if (fileName != null) {
                    FileManager mgr = mHostDeviceService.getFileManager();
                    uri = mgr.getContentUri() + "/" + fileName;
                    fileName = "/" + fileName;
                } else {
                    fileName = "";
                }
                if (target != null && state != null) {
                    HostDeviceStreamRecorder streamer = getStreamRecorder(target);
                    if (state == HostDeviceRecorder.RecorderState.INACTTIVE) {
                        streamer.clean();
                    }
                    sendEventForRecordingChange(serviceId, state, uri,
                            fileName, streamer.getMimeType(), null);
                }


            } else if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())){
                WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                int rotation = windowManager.getDefaultDisplay().getRotation();
                for (HostDeviceRecorder recorder : mRecorders) {
                    if (DEBUG) {
                        Log.d(TAG, "BroadcastReceiver.onReceive: recorder=" + recorder.getId());
                    }
                    recorder.onDisplayRotation(rotation);
                }
            }
        }
    };

    public HostDeviceRecorderManager(final HostDeviceService hostService) {
        mHostDeviceService = hostService;
    }

    public void createAudioRecorders() {
        mRecorders.add(new HostDeviceAudioRecorder(getContext()));
    }

    public void createScreenCastRecorder(final FileManager fileMgr) {
        mRecorders.add(new HostDeviceScreenCastRecorder(getContext(), fileMgr));
    }

    public void createCameraRecorders(final CameraWrapperManager cameraMgr, final FileManager fileMgr) {
        List<Camera2Recorder> photoRecorders = new ArrayList<>();
        for (CameraWrapper camera : cameraMgr.getCameraList()) {
            photoRecorders.add(new Camera2Recorder(getContext(), camera, fileMgr));
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
            mHostDeviceService.sendEvent(intent, evt.getAccessToken());
        }
    }

    public static boolean isSupportedMediaProjection() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    private Context getContext() {
        return mHostDeviceService;
    }

}
