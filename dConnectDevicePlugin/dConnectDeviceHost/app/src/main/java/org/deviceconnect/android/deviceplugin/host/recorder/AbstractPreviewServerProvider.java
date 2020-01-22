/*
 HostDevicePreviewServer.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.recorder;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;

import org.deviceconnect.android.deviceplugin.host.R;

/**
 * Host Device Preview Server.
 *
 * @author NTT DOCOMO, INC.
 */
public abstract class AbstractPreviewServerProvider implements PreviewServerProvider, HostDeviceRecorder {

    /**
     * コンテキスト.
     */
    private Context mContext;

    /**
     * 通知ID.
     */
    private int mNotificationId;

    /**
     * コンストラクタ.
     * @param context コンテキスト
     * @param notificationId 通知ID
     */
    public AbstractPreviewServerProvider(final Context context, final int notificationId) {
        mContext = context;
        mNotificationId = notificationId;
    }

    @Override
    public void destroy() {
        // Nothing to do.
    }

    @Override
    public void stopWebServers() {
        for (PreviewServer server : getServers()) {
            server.stopWebServer();
        }
    }

    /**
     * NotificationIdを取得します.
     * @return NotificationId
     */
    private int getNotificationId() {
        return mNotificationId;
    }

    /**
     * Notificationを削除する.
     */
    public void hideNotification() {
        NotificationManager manager = (NotificationManager) mContext
                .getSystemService(Service.NOTIFICATION_SERVICE);
        manager.cancel(getId(), getNotificationId());
    }

    /**
     * Notificationを送信する.
     */
    public void sendNotification() {
        PendingIntent contentIntent = createPreviewStopPendingIntent();
        Notification notification = createNotification(contentIntent, null);
        NotificationManager manager = (NotificationManager) mContext
                .getSystemService(Service.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = mContext.getResources().getString(R.string.overlay_preview_channel_id);
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    mContext.getResources().getString(R.string.overlay_preview_content_title),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(mContext.getResources().getString(R.string.overlay_preview_content_message));
            manager.createNotificationChannel(channel);
            notification = createNotification(contentIntent, channelId);
        }
        manager.notify(getId(), getNotificationId(), notification);
    }

    /**
     * Notificationを作成する.
     * @param pendingIntent Notificationがクリックされたときに起動するIntent
     * @return Notification
     */
    private Notification createNotification(final PendingIntent pendingIntent, final String channelId) {
        int iconType = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ?
                R.drawable.dconnect_icon : R.drawable.dconnect_icon_lollipop;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext.getApplicationContext(), channelId);
        NotificationCompat.Action actionStop = new NotificationCompat.Action(iconType, mContext.getString(R.string.overlay_preview_content_button_finish), createPreviewStopPendingIntent());
        NotificationCompat.Action actionShowOverlay = new NotificationCompat.Action(iconType, mContext.getString(R.string.overlay_preview_content_button_show), createPreviewOverlayShowPendingIntent());
        builder.setTicker(mContext.getString(R.string.overlay_preview_ticker));
        builder.setSmallIcon(iconType);
        builder.setContentTitle(mContext.getString(R.string.overlay_preview_content_title) + " (" + getName() + ")");
        builder.setWhen(System.currentTimeMillis());
        builder.setAutoCancel(true);
        builder.setOngoing(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.addAction(actionStop);
            builder.addAction(actionShowOverlay);
            builder.setContentText(mContext.getString(R.string.overlay_preview_content_message));
        } else {
            builder.setContentIntent(pendingIntent);
            builder.setContentText(mContext.getString(R.string.overlay_preview_content_message_lollipop));
        }

         return builder.build();
    }

    /**
     * Previewを削除するPendingIntentを作成する.
     * @return PendingIntent
     */
    private PendingIntent createPreviewStopPendingIntent() {
        Intent intent = new Intent();
        intent.setAction(STOP_PREVIEW_ACTION);
        intent.putExtra(EXTRA_CAMERA_ID, getId());
        return PendingIntent.getBroadcast(mContext, getNotificationId(), intent, 0);
    }

    /**
     * Previewを削除するPendingIntentを作成する.
     * @return PendingIntent
     */
    private PendingIntent createPreviewOverlayShowPendingIntent() {
        Intent intent = new Intent();
        intent.setAction(DELETE_PREVIEW_ACTION);
        intent.putExtra(EXTRA_CAMERA_ID, getId());
        return PendingIntent.getBroadcast(mContext, getNotificationId(), intent, 0);
    }


    public Context getContext() {
        return mContext;
    }
    public void setPreviewQuality(final PreviewServer server, final int quality) {
        server.setQuality(quality);
        storePreviewQuality(server, quality);
    }

    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    protected void storePreviewQuality(final PreviewServer server, int quality) {
        getSharedPreferences().edit().putInt(getPreviewQualityKey(server), quality).apply();
    }

    protected int readPreviewQuality(final PreviewServer server) {
        return getSharedPreferences().getInt(getPreviewQualityKey(server), getDefaultPreviewQuality(server.getMimeType()));
    }

    protected abstract int getDefaultPreviewQuality(final String mimeType);

    private String getPreviewQualityKey(final PreviewServer server) {
        return getId() + "-" + server.getMimeType() + "-preview-quality";
    }

    @Override
    public void mute() {

    }

    @Override
    public void unMute() {

    }

    @Override
    public boolean isMuted() {
        return false;
    }
}
