package org.deviceconnect.android.deviceplugin.host.recorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.deviceconnect.android.deviceplugin.host.BuildConfig;
import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.recorder.util.MediaSharing;
import org.deviceconnect.android.provider.FileManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;

public abstract class AbstractMediaRecorder implements HostMediaRecorder {
    /**
     * ログ出力用タグ.
     */
    private static final String TAG = "host.dplugin";

    /**
     * デバッグフラグ.
     */
    private static final boolean DEBUG = BuildConfig.DEBUG;

    /**
     * コンテキスト.
     */
    private Context mContext;

    /**
     * ファイルマネージャ.
     */
    private FileManager mFileManager;

    /**
     * ファイルを Android 端末内で共有するためのクラス.
     */
    private MediaSharing mMediaSharing = MediaSharing.getInstance();

    /**
     * 通知ID.
     */
    private int mNotificationId;

    /**
     * イベント通知用リスナー.
     */
    private OnEventListener mOnEventListener;

    /**
     * コンストラクタ.
     */
    public AbstractMediaRecorder(Context context, int notificationId, FileManager fileManager) {
        mContext = context;
        mNotificationId = notificationId;
        mFileManager = fileManager;
    }

    // Implements HostMediaRecorder methods.

    @Override
    public void initialize() {
        BroadcasterProvider broadcasterProvider = getBroadcasterProvider();
        if (broadcasterProvider != null) {
            broadcasterProvider.setOnEventListener(new BroadcasterProvider.OnEventListener() {
                @Override
                public void onStarted(Broadcaster broadcaster) {
                    postOnBroadcasterStarted(broadcaster);
                }

                @Override
                public void onStopped(Broadcaster broadcaster) {
                    postOnBroadcasterStopped();
                }

                @Override
                public void onError(Broadcaster broadcaster, Exception e) {
                }
            });
        }

        PreviewServerProvider previewProvider = getServerProvider();
        if (previewProvider != null) {
            previewProvider.setOnEventListener(new PreviewServerProvider.OnEventListener() {
                @Override
                public void onStarted(List<PreviewServer> servers) {
                    postOnPreviewStarted(servers);
                }

                @Override
                public void onStopped() {
                    postOnPreviewStopped();
                }

                @Override
                public void onError(PreviewServer server, Exception e) {

                }
            });
        }
    }

    @Override
    public boolean isPreviewRunning() {
        PreviewServerProvider provider = getServerProvider();
        return provider != null && provider.isRunning();
    }

    @Override
    public List<PreviewServer> startPreview() {
        PreviewServerProvider provider = getServerProvider();
        if (provider == null) {
            return new ArrayList<>();
        }

        List<PreviewServer> servers = provider.startServers();
        if (!servers.isEmpty()) {
            provider.setMute(getSettings().isMute());
        }
        return servers;
    }

    @Override
    public void stopPreview() {
        PreviewServerProvider provider = getServerProvider();
        if (provider != null) {
            provider.stopServers();
        }
    }

    @Override
    public boolean isBroadcasterRunning() {
        BroadcasterProvider provider = getBroadcasterProvider();
        return provider != null && provider.isRunning();
    }

    @Override
    public Broadcaster startBroadcaster(String uri) {
        BroadcasterProvider provider = getBroadcasterProvider();
        if (provider == null) {
            return null;
        }

        Broadcaster broadcaster = provider.startBroadcaster(uri);
        if (broadcaster != null) {
            broadcaster.setMute(getSettings().isMute());
        }
        return broadcaster;
    }

    @Override
    public void stopBroadcaster() {
        BroadcasterProvider provider = getBroadcasterProvider();
        if (provider != null) {
            provider.stopBroadcaster();
        }
    }

    @Override
    public void requestKeyFrame() {
        PreviewServerProvider previewProvider = getServerProvider();
        if (previewProvider != null) {
            previewProvider.requestSyncFrame();
        }
    }

    @Override
    public void setMute(boolean mute) {
        Settings settings = getSettings();
        settings.setMute(mute);

        PreviewServerProvider previewProvider = getServerProvider();
        if (previewProvider != null) {
            previewProvider.setMute(mute);
        }

        BroadcasterProvider broadcasterProvider = getBroadcasterProvider();
        if (broadcasterProvider != null) {
            broadcasterProvider.setMute(mute);
        }
    }

    @Override
    public boolean isMute() {
        return getSettings().isMute();
    }

    @Override
    public void setSSLContext(SSLContext sslContext) {
        PreviewServerProvider previewProvider = getServerProvider();
        if (previewProvider != null) {
            for (PreviewServer server : previewProvider.getServers()) {
                server.setSSLContext(sslContext);
            }
        }
    }

    @Override
    public void setOnEventListener(OnEventListener listener) {
        mOnEventListener = listener;
    }

    /**
     * コンテキストを取得します.
     *
     * @return コンテキスト
     */
    public Context getContext() {
        return mContext;
    }

    /**
     * ファイル管理クラスを取得します.
     *
     * @return ファイル管理クラス
     */
    public FileManager getFileManager() {
        return mFileManager;
    }

    /**
     * NotificationId を取得します.
     *
     * @return NotificationId
     */
    protected int getNotificationId() {
        return mNotificationId;
    }

    /**
     * レコーディング停止用の Notification を削除します.
     *
     * @param id notification を識別する ID
     */
    public void hideNotification(String id) {
        NotificationManager manager = (NotificationManager) mContext
                .getSystemService(Service.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(id, getNotificationId());
        }
    }

    /**
     * レコーディング停止用の Notification を送信します.
     *
     * @param id notification を識別する ID
     * @param name 名前
     */
    public void sendNotificationForStopRecording(String id, String name) {
        PendingIntent contentIntent = createPendingIntentForStopRecording(id);
        Notification notification = createNotificationForStopRecording(contentIntent, null, name);
        NotificationManager manager = (NotificationManager) mContext
                .getSystemService(Service.NOTIFICATION_SERVICE);
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String channelId = mContext.getResources().getString(R.string.overlay_preview_channel_id);
                NotificationChannel channel = new NotificationChannel(
                        channelId,
                        mContext.getResources().getString(R.string.overlay_preview_content_title),
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription(mContext.getResources().getString(R.string.overlay_preview_content_message));
                manager.createNotificationChannel(channel);
                notification = createNotificationForStopRecording(contentIntent, channelId, name);
            }
            manager.notify(id, getNotificationId(), notification);
        }
    }

    /**
     * Notificationを作成する.
     *
     * @param pendingIntent Notificationがクリックされたときに起動する Intent
     * @param channelId チャンネルID
     * @param name 名前
     * @return Notification
     */
    protected Notification createNotificationForStopRecording(final PendingIntent pendingIntent, final String channelId, String name) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext.getApplicationContext());
            builder.setContentIntent(pendingIntent);
            builder.setTicker(mContext.getString(R.string.overlay_preview_ticker));
            builder.setSmallIcon(R.drawable.dconnect_icon);
            builder.setContentTitle(mContext.getString(R.string.overlay_preview_content_title, name));
            builder.setContentText(mContext.getString(R.string.overlay_preview_content_message));
            builder.setWhen(System.currentTimeMillis());
            builder.setAutoCancel(true);
            builder.setOngoing(true);
            return builder.build();
        } else {
            Notification.Builder builder = new Notification.Builder(mContext.getApplicationContext());
            builder.setContentIntent(pendingIntent);
            builder.setTicker(mContext.getString(R.string.overlay_preview_ticker));
            int iconType = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ?
                    R.drawable.dconnect_icon : R.drawable.dconnect_icon_lollipop;
            builder.setSmallIcon(iconType);
            builder.setContentTitle(mContext.getString(R.string.overlay_preview_content_title, name));
            builder.setContentText(mContext.getString(R.string.overlay_preview_content_message));
            builder.setWhen(System.currentTimeMillis());
            builder.setAutoCancel(true);
            builder.setOngoing(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channelId != null) {
                builder.setChannelId(channelId);
            }
            return builder.build();
        }
    }

    /**
     * PendingIntent を作成する.
     *
     * @param id カメラ ID
     *
     * @return PendingIntent
     */
    private PendingIntent createPendingIntentForStopRecording(String id) {
        Intent intent = new Intent();
        intent.setAction(HostMediaRecorderManager.ACTION_STOP_RECORDING);
        intent.putExtra(HostMediaRecorderManager.KEY_RECORDER_ID, id);
        return PendingIntent.getBroadcast(mContext, getNotificationId(), intent, 0);
    }

    /**
     * 写真を保存して、Android OS のメディアに登録します.
     *
     * @param filename ファイル名
     * @param jpeg 写真データ
     * @param listener 保存結果を通知するリスナー
     */
    protected void storePhoto(String filename, byte[] jpeg, OnPhotoEventListener listener) {
        mFileManager.saveFile(filename, jpeg, true, new FileManager.SaveFileCallback() {
            @Override
            public void onSuccess(@NonNull final String uri) {
                if (DEBUG) {
                    Log.d(TAG, "Saved photo: uri=" + uri);
                }

                String photoFilePath = mFileManager.getBasePath().getAbsolutePath() + "/" + uri;
                registerPhoto(new File(mFileManager.getBasePath(), filename));

                if (listener != null) {
                    listener.onTakePhoto(uri, photoFilePath, MIME_TYPE_JPEG);
                }

                postOnTakePhoto(uri, photoFilePath, MIME_TYPE_JPEG);
            }

            @Override
            public void onFail(@NonNull final Throwable e) {
                if (DEBUG) {
                    Log.e(TAG, "Failed to save photo", e);
                }

                if (listener != null) {
                    listener.onFailedTakePhoto(e.getMessage());
                }
            }
        });
    }

    /**
     * 映像ファイルを Android OS に登録します.
     *
     * @param videoFile 映像ファイル
     */
    protected void registerVideo(final File videoFile) {
        Uri uri = mMediaSharing.shareVideo(mContext, videoFile, mFileManager);
        if (DEBUG) {
            String filePath = videoFile.getAbsolutePath();
            if (uri != null) {
                Log.d(TAG, "Registered video: filePath=" + filePath + ", uri=" + uri.getPath());
            } else {
                Log.e(TAG, "Failed to register video: file=" + filePath);
            }
        }
    }

    /**
     * 写真ファイルを Android OS に登録します.
     *
     * @param photoFile 写真ファイル
     */
    protected void registerPhoto(final File photoFile) {
        Uri uri = mMediaSharing.sharePhoto(mContext, photoFile);
        if (DEBUG) {
            if (uri != null) {
                Log.d(TAG, "Registered photo: uri=" + uri.getPath());
            } else {
                Log.e(TAG, "Failed to register photo: file=" + photoFile.getAbsolutePath());
            }
        }
    }

    /**
     * 音声ファイルを Android OS に登録します.
     *
     * @param audioFile 音声ファイル
     */
    protected void registerAudio(final File audioFile) {
        Uri uri = mMediaSharing.shareAudio(mContext, audioFile);
        if (DEBUG) {
            String filePath = audioFile.getAbsolutePath();
            if (uri != null) {
                Log.d(TAG, "Registered audio: filePath=" + filePath + ", uri=" + uri.getPath());
            } else {
                Log.e(TAG, "Failed to register audio: file=" + filePath);
            }
        }
    }

    protected void postOnPreviewStarted(List<PreviewServer> servers) {
        if (mOnEventListener != null) {
            mOnEventListener.onPreviewStarted(servers);
        }
    }

    protected void postOnPreviewStopped() {
        if (mOnEventListener != null) {
            mOnEventListener.onPreviewStopped();
        }
    }

    protected void postOnBroadcasterStarted(Broadcaster broadcaster) {
        if (mOnEventListener != null) {
            mOnEventListener.onBroadcasterStarted(broadcaster);
        }
    }

    protected void postOnBroadcasterStopped() {
        if (mOnEventListener != null) {
            mOnEventListener.onBroadcasterStopped();
        }
    }

    protected void postOnTakePhoto(String uri, String filePath, String mimeType) {
        if (mOnEventListener != null) {
            mOnEventListener.onTakePhoto(uri, filePath, mimeType);
        }
    }

    protected void postOnRecordingStarted(String path) {
        if (mOnEventListener != null) {
            mOnEventListener.onRecordingStarted(path);
        }
    }

    protected void postOnRecordingStopped(String path) {
        if (mOnEventListener != null) {
            mOnEventListener.onRecordingStopped(path);
        }
    }

    protected void postOnRecordingPause() {
        if (mOnEventListener != null) {
            mOnEventListener.onRecordingPause();
        }
    }

    protected void postOnRecordingResume() {
        if (mOnEventListener != null) {
            mOnEventListener.onRecordingResume();
        }
    }
}
