package org.deviceconnect.android.deviceplugin.host.mutiwindow;

import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.profile.MultiWindowCanvasProfile;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.profile.MultiWindowKeyEventProfile;
import org.deviceconnect.android.deviceplugin.host.mutiwindow.profile.MultiWindowTouchProfile;
import org.deviceconnect.android.deviceplugin.host.profile.HostMediaPlayerProfile;
import org.deviceconnect.android.message.DevicePluginContext;
import org.deviceconnect.android.service.DConnectService;

public class MultiWindowService extends DConnectService {
    /** サービスID. */
    public static final String SERVICE_ID = "multi_window";
    /** サービス名. */
    private static final String SERVICE_NAME = "マルチウィンドウ";
    /** デバッグタグ名. */
    private static final String TAG = "MultiWindowService";

    /**
     * コンストラクタ.
     *
     */
    public MultiWindowService(DevicePluginContext pluginContext) {
        super(SERVICE_ID);
        setName(SERVICE_NAME);
        setNetworkType(NetworkType.UNKNOWN);
        setOnline(false);
        addProfile(new MultiWindowCanvasProfile(new HostCanvasSettings(pluginContext.getContext())));
        addProfile(new MultiWindowKeyEventProfile());
        addProfile(new MultiWindowTouchProfile());
        addProfile(new HostMediaPlayerProfile(new MultiWindowMediaPlayerManager(pluginContext)));
    }
}
