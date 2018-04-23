/*
HueDeviceService
Copyright (c) 2014 NTT DOCOMO,INC.
Released under the MIT license
http://opensource.org/licenses/mit-license.php
*/

package org.deviceconnect.android.deviceplugin.hue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.philips.lighting.hue.sdk.wrapper.discovery.BridgeDiscoveryResult;
import com.philips.lighting.hue.sdk.wrapper.domain.HueError;
import com.philips.lighting.hue.sdk.wrapper.domain.device.light.LightPoint;

import org.deviceconnect.android.deviceplugin.hue.db.HueManager;
import org.deviceconnect.android.deviceplugin.hue.profile.HueSystemProfile;
import org.deviceconnect.android.deviceplugin.hue.service.HueLightService;
import org.deviceconnect.android.deviceplugin.hue.service.HueService;
import org.deviceconnect.android.message.DConnectMessageService;
import org.deviceconnect.android.profile.SystemProfile;
import org.deviceconnect.android.service.DConnectService;

import java.util.List;
import java.util.logging.Logger;

/**
 * 本デバイスプラグインのプロファイルをDeviceConnectに登録するサービス.
 *
 * @author NTT DOCOMO, INC.
 */
public class HueDeviceService extends DConnectMessageService {
    /**
     * デバッグフラグ.
     */
    private static final boolean DEBUG = BuildConfig.DEBUG;

    /**
     * ロガー.
     */
    private final Logger mLogger = Logger.getLogger("hue.dplugin");

    @Override
    public void onCreate() {
        super.onCreate();
        initHueSDK();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(HueConstants.ACTION_REMOVE_BRIDGE);
        filter.addAction(HueConstants.ACTION_CONNECTED_BRIDGE);
        registerReceiver(mConnectivityReceiver, filter);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mConnectivityReceiver);
        destroyHueSDK();
        super.onDestroy();
    }

    @Override
    protected void onManagerUninstalled() {
        // Managerアンインストール検知時の処理。
        if (BuildConfig.DEBUG) {
            mLogger.info("Plug-in : onManagerUninstalled");
        }
    }

    @Override
    protected void onManagerTerminated() {
        // Manager正常終了通知受信時の処理。
        if (BuildConfig.DEBUG) {
            mLogger.info("Plug-in : onManagerTerminated");
        }
    }

    @Override
    protected void onDevicePluginReset() {
        // Device Plug-inへのReset要求受信時の処理。
        if (BuildConfig.DEBUG) {
            mLogger.info("Plug-in : onDevicePluginReset");
        }
        HueManager.INSTANCE.reconnectBridge(getServiceProvider(), mHBListener);
    }

    @Override
    protected SystemProfile getSystemProfile() {
        return new HueSystemProfile();
    }

    /**
     * HueSDKを初期化します.
     */
    private void initHueSDK() {
        if (DEBUG) {
            mLogger.info("HueDeviceService#initHueSDK");
        }

        HueManager.INSTANCE.init(getApplicationContext());
        HueManager.INSTANCE.startBridgeDiscovery(new HueManager.HueBridgeDiscoveryListener() {
            @Override
            public void onFoundedBridge(List<BridgeDiscoveryResult> results) {
                for (BridgeDiscoveryResult bridge : results) {
                    addHueService(false, bridge.getIP(), bridge.getUniqueID());
                }
            }
        });
        //オンライン状態のものとのみ再接続する
        HueManager.INSTANCE.reconnectBridge(getServiceProvider(), mHBListener);
    }

    /**
     * HueSDKを破棄します.
     */
    private void destroyHueSDK() {
        if (DEBUG) {
            mLogger.info("HueDeviceService#destroyHueSDK");
        }

        // hue SDKの後始末
        HueManager.INSTANCE.destroy();
    }

    /**
     * Hueサービスを追加します.
     *
     * @param isConnected アクセスポイントと接続しているかどうか
     */
    private void addHueService(final boolean isConnected, final String ip, final String uniqueId) {
        HueService service = (HueService) getServiceProvider().getService(ip);
        if (service == null) {
            service = new HueService(ip, uniqueId);
            getServiceProvider().addService(service);
        }
        service.setOnline(isConnected);
        HueManager.INSTANCE.saveBridgeForDB(service);
    }
    /**
     * ライトサービスを追加します.
     *
     * @param isConnected アクセスポイントと接続しているかどうか
     */
    private void addHueLightService(final boolean isConnected, final String ip, final String lightId, final String name) {
        HueLightService service = (HueLightService) getServiceProvider().getService(ip + ":" + lightId);
        if (service == null) {
            service = new HueLightService(ip, lightId, name);
            getServiceProvider().addService(service);
        }
        service.setOnline(isConnected);
        HueManager.INSTANCE.saveLightForDB(service);
    }

    private final HueManager.HueBridgeConnectionListener mHBListener = new HueManager.HueBridgeConnectionListener() {
        @Override
        public void onPushlinkingBridge(final String ip) {
            if (DEBUG) {
                mLogger.info("HueBridgeConnectionListener:#onPushlinkingBridge: bridgeConnection" +
                        "=" + ip);
            }
        }

        @Override
        public void onConnectedBridge(final String ip) {
            if (DEBUG) {
                mLogger.info("HueBridgeConnectionListener:#onConnectedBridge: bridge=" + ip);
            }
            updateHueBridge(true, ip);
        }

        @Override
        public void onDisconnectedBridge(final String ip) {
            if (DEBUG) {
                mLogger.info("HueBridgeConnectionListener:#onDisconnectedBridge: accessPoint="
                        + ip);
            }
            updateHueBridge(false, ip);
        }

        @Override
        public void onError(final String ip, final List<HueError> list) {
            if (DEBUG) {
                for (HueError error : list) {
                    mLogger.info("HueBridgeConnectionListener:#onError: " + error.toString());
                }
            }
            updateHueBridge(false, ip);
        }
    };

    /**
     * Hueプラグインが管理するサービスのステータスを更新する.
     * @param isConnected true:オンライン false:オフライン
     */
    public void updateHueBridge(final boolean isConnected, final String ip) {
        DConnectService service = getServiceProvider().getService(ip);
        if (service != null) {
            service.setOnline(isConnected);
            HueManager.INSTANCE.saveBridgeForDB((HueService) service);
        } else {
            HueService hueService = HueManager.INSTANCE.getHueService(ip);
            if (hueService != null) {
                hueService.setOnline(isConnected);
                HueManager.INSTANCE.saveBridgeForDB(hueService);
            }
        }
        List<LightPoint> lights = HueManager.INSTANCE.getCacheLights(ip);
        for (int i = 0; i < lights.size(); i++) {
            addHueLightService(isConnected, ip,  lights.get(i).getIdentifier(), lights.get(i).getName());
        }
    }
    /**
     * ネットワーク状況が変わった通知を受けるレシーバー.
     */
    private BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            String action = intent.getAction();
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                ConnectivityManager conn = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo networkInfo = conn.getActiveNetworkInfo();
                if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    HueManager.INSTANCE.reconnectBridge(getServiceProvider(), mHBListener);
                } else {
                    HueManager.INSTANCE.disconnectAllBridges();
                }
            } else if (HueConstants.ACTION_REMOVE_BRIDGE.equals(action)) {
                initHueSDK();
            } else if (HueConstants.ACTION_CONNECTED_BRIDGE.equals(action)) {
                HueManager.INSTANCE.reconnectBridge(getServiceProvider(), mHBListener);
            }
        }
    };
}
