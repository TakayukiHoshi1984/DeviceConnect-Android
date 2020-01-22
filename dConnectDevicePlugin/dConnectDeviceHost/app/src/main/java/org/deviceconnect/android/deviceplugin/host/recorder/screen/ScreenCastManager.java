package org.deviceconnect.android.deviceplugin.host.recorder.screen;


import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.view.Surface;

import org.deviceconnect.android.util.NotificationUtils;

@TargetApi(21)
class ScreenCastManager {

    private static final String RESULT_DATA = "result_data";

    private static final String EXTRA_CALLBACK = "callback";

    private final Context mContext;

    private final MediaProjectionManager mMediaProjectionMgr;

    private MediaProjection mMediaProjection;

    private final Handler mCallbackHandler = new Handler(Looper.getMainLooper());

    /**
     * Notification Id
     */
    private final int NOTIFICATION_ID = 3539;

    /**
     * Notification Content
     */
    private final String NOTIFICATION_CONTENT = "Host Media Streaming Recording Profileからの起動要求";


    ScreenCastManager(final Context context) {
        mContext = context;
        mMediaProjectionMgr = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    synchronized void clean() {
        MediaProjection projection = mMediaProjection;
        if (projection != null) {
            projection.stop();
            mMediaProjection = null;
        }
    }

    public void requestPermission(final PermissionCallback callback) {
        if (mMediaProjection != null) {
            callback.onAllowed();
            return;
        }

        Intent intent = new Intent();
        intent.setClass(mContext, PermissionReceiverActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_CALLBACK, new ResultReceiver(mCallbackHandler) {
            @Override
            protected void onReceiveResult(final int resultCode, final Bundle resultData) {
                if (resultCode == Activity.RESULT_OK) {
                    Intent data = resultData.getParcelable(RESULT_DATA);
                    if (data != null) {
                        mMediaProjection = mMediaProjectionMgr.getMediaProjection(resultCode, data);
                        mMediaProjection.registerCallback(new MediaProjection.Callback() {
                            @Override
                            public void onStop() {
                                clean();
                            }
                        }, new Handler(Looper.getMainLooper()));
                    }
                }

                if (mMediaProjection != null) {
                    callback.onAllowed();
                } else {
                    callback.onDisallowed();
                }
            }
        });
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            mContext.startActivity(intent);
        } else {
            NotificationUtils.createNotificationChannel(mContext);
            NotificationUtils.notify(mContext, NOTIFICATION_ID, 0, intent, NOTIFICATION_CONTENT);
        }
    }

    public SurfaceScreenCast createScreenCast(final Surface outputSurface, int width, int height) {
        if (mMediaProjection == null) {
            throw new IllegalStateException("Media Projection is not allowed.");
        }
        return new SurfaceScreenCast(mContext, mMediaProjection, outputSurface, width, height);
    }

    public ImageScreenCast createScreenCast(final ImageReader imageReader, int width, int height) {
        if (mMediaProjection == null) {
            throw new IllegalStateException("Media Projection is not allowed.");
        }
        return new ImageScreenCast(mContext, mMediaProjection, imageReader, width, height);
    }

    interface PermissionCallback {
        void onAllowed();

        void onDisallowed();
    }
}
