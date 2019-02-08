package org.deviceconnect.android.deviceplugin.host.mutiwindow;

import android.content.Context;

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
    }
}
