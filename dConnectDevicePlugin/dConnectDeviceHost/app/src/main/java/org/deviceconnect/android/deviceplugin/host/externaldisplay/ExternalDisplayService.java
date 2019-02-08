/*
 ExternalDisplayService.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.externaldisplay;

import android.content.Context;
import android.content.DialogInterface;
import android.media.MediaRouter;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import org.deviceconnect.android.deviceplugin.host.BuildConfig;
import org.deviceconnect.android.deviceplugin.host.HostDeviceService;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawImageObject;
import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;
import org.deviceconnect.android.deviceplugin.host.externaldisplay.profile.ExternalDisplayCanvasProfile;
import org.deviceconnect.android.deviceplugin.host.profile.HostMediaPlayerProfile;
import org.deviceconnect.android.message.DevicePluginContext;
import org.deviceconnect.android.service.DConnectService;
import org.deviceconnect.android.service.DConnectServiceProvider;

/**
 * 外部ディスプレイのサービス.
 * @author NTT DOCOMO, INC.
 */
public class ExternalDisplayService extends DConnectService {
    /** サービスID. */
    public static final String SERVICE_ID = "external_display";
    /** サービス名. */
    private static final String SERVICE_NAME = "外部ディスプレイ";
    /** デバッグタグ名. */
    private static final String TAG = "ExternalDisplayService";
    /** Canvasの機能を持つPresentation. */
    private ExternalDisplayCanvasPresentation mCanvasPresentation;
    /** MediaPlayerの機能を持つPresentation. */
    private ExternalDisplayMediaPlayerPresentation mMediaPlayerPresentation;
    /** ディスプレイ. */
    private Display mPresentationDisplay;
    /** Presentation投影用のHnadler. */
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    /**
     * Presentationで投影されているものが終了されたときに呼ばれるリスナー.
     */
    private final DialogInterface.OnDismissListener mOnDismissListener = (dialog) -> {
        if (dialog == mCanvasPresentation && dialog == mMediaPlayerPresentation) {
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Presentation was dismissed.");
            }
            DConnectServiceProvider provider = ((HostDeviceService) getContext()).getServiceProvider();
            DConnectService dService = provider.getService(ExternalDisplayService.SERVICE_ID);
            if (dService != null) {
                dService.setOnline(false);
            }
        }
    };

    /**
     * コンストラクタ.
     *
     * @throws NullPointerException idに<code>null</code>が指定された場合
     */
    public ExternalDisplayService(Context context, DevicePluginContext pluginContext) {
        super(SERVICE_ID);
        setName(SERVICE_NAME);
        setNetworkType(NetworkType.WIFI);
        setOnline(false);
        addProfile(new ExternalDisplayCanvasProfile(new HostCanvasSettings(context)));
        addProfile(new HostMediaPlayerProfile(new ExternalDisplayMediaPlayerManager(pluginContext, this)));

    }
    public boolean connect() {
        MediaRouter mediaRouter = (MediaRouter) getContext().getSystemService(Context.MEDIA_ROUTER_SERVICE);
        MediaRouter.RouteInfo route = mediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_VIDEO);
        mPresentationDisplay = route != null ? route.getPresentationDisplay() : null;
        return mPresentationDisplay != null;
    }

    public void showCanvasDisplay(final CanvasDrawImageObject drawImageObject) {
        // Dismiss the current presentation if the display has changed.
        if (mCanvasPresentation != null) {
            mCanvasPresentation.updateCanvasDisplay(drawImageObject);
            return;
        }

        mMainHandler.post(() -> {
            mCanvasPresentation = new ExternalDisplayCanvasPresentation(getContext(), mPresentationDisplay, drawImageObject);
            mCanvasPresentation.setOnDismissListener(mOnDismissListener);
            try {
                mCanvasPresentation.show();
            } catch (WindowManager.InvalidDisplayException ex) {
                Log.w(TAG, "Couldn't show presentation!  Display was removed in "
                        + "the meantime.", ex);
                mCanvasPresentation = null;
            }
        });
    }

    public boolean disconnectCanvasDisplay() {
        if (mCanvasPresentation != null) {
            mCanvasPresentation.dismiss();
            mCanvasPresentation = null;
        }
        return mCanvasPresentation == null;
    }

    public void showMediaPlayerDisplay(final Uri uri) {
        if (mMediaPlayerPresentation != null) {
            mMediaPlayerPresentation.dismiss();
            mMediaPlayerPresentation = null;
        }

        mMainHandler.post(() -> {
            mMediaPlayerPresentation = new ExternalDisplayMediaPlayerPresentation(getContext(), mPresentationDisplay, this, uri);
            mMediaPlayerPresentation.setOnDismissListener(mOnDismissListener);
            try {
                mMediaPlayerPresentation.show();
            } catch (WindowManager.InvalidDisplayException ex) {
                Log.w(TAG, "Couldn't show presentation!  Display was removed in "
                        + "the meantime.", ex);
                mMediaPlayerPresentation = null;
            }
        });
    }

    public boolean isMediaPlayerPresentation() {
        return mMediaPlayerPresentation != null;
    }

    public boolean disconnectMediaPlayerDisplay() {
        if (mMediaPlayerPresentation != null) {
            mMediaPlayerPresentation.dismiss();
            mMediaPlayerPresentation = null;
        }
        return mMediaPlayerPresentation == null;
    }

}
