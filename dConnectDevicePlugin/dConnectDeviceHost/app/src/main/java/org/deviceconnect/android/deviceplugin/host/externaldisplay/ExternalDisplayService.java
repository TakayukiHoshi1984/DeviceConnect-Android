package org.deviceconnect.android.deviceplugin.host.externaldisplay;

import android.content.Context;
import android.content.DialogInterface;
import android.media.MediaRouter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import org.deviceconnect.android.deviceplugin.host.HostDeviceService;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawImageObject;
import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;
import org.deviceconnect.android.deviceplugin.host.externaldisplay.profile.ExternalDisplayCanvasProfile;
import org.deviceconnect.android.service.DConnectService;
import org.deviceconnect.android.service.DConnectServiceProvider;

public class ExternalDisplayService extends DConnectService {
    public static final String SERVICE_ID = "external_display";
    private static final String SERVICE_NAME = "外部ディスプレイ";
    private static final String TAG = "ExternalDisplayService";
    private ExternalDisplayPresentation mPresentation;
    private Display mPresentationDisplay;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    /**
     * Listens for when presentations are dismissed.
     */
    private final DialogInterface.OnDismissListener mOnDismissListener =
            new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (dialog == mPresentation) {
//                        if (BuildConfig.DEBUG) {
                        Log.i("ABC", "Presentation was dismissed.");
//                        }
                        DConnectServiceProvider provider = ((HostDeviceService) getContext()).getServiceProvider();
                        DConnectService dService = provider.getService(ExternalDisplayService.SERVICE_ID);
                        if (dService != null && mPresentation != null) {
                            dService.setOnline(false);
                        }
                        mPresentation = null;
                    }
                }
            };

    /**
     * コンストラクタ.
     *
     * @throws NullPointerException idに<code>null</code>が指定された場合
     */
    public ExternalDisplayService(Context context) {
        super(SERVICE_ID);
        setName(SERVICE_NAME);
        setNetworkType(NetworkType.WIFI);
        setOnline(false);
        addProfile(new ExternalDisplayCanvasProfile(new HostCanvasSettings(context)));
    }
    public boolean connect() {
        MediaRouter mediaRouter = (MediaRouter) getContext().getSystemService(Context.MEDIA_ROUTER_SERVICE);
        MediaRouter.RouteInfo route = mediaRouter.getSelectedRoute(
                MediaRouter.ROUTE_TYPE_LIVE_VIDEO);
        mPresentationDisplay = route != null ? route.getPresentationDisplay() : null;
        return mPresentationDisplay != null;
    }

    public void showDisplay(final CanvasDrawImageObject drawImageObject) {
        // Dismiss the current presentation if the display has changed.
        if (mPresentation != null) {
            Log.i(TAG, "Dismissing presentation because the current route no longer "
                    + "has a presentation display.");
            mPresentation.updateCanvasDisplay(drawImageObject);
            return;
        }

        mMainHandler.post(() -> {
            mPresentation = new ExternalDisplayPresentation(getContext(), mPresentationDisplay, drawImageObject);
            mPresentation.setOnDismissListener(mOnDismissListener);
            try {
                mPresentation.show();
            } catch (WindowManager.InvalidDisplayException ex) {
                Log.w(TAG, "Couldn't show presentation!  Display was removed in "
                        + "the meantime.", ex);
                mPresentation = null;
            }
        });
    }

    public boolean disconnect() {
        if (mPresentation != null) {
            mPresentation.dismiss();
            mPresentation = null;
        }
        return mPresentation == null;
    }
}
