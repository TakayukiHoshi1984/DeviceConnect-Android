/*
 HueManager.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.hue.db;

import android.content.Context;
import android.util.Log;

import com.philips.lighting.hue.sdk.wrapper.HueLog;
import com.philips.lighting.hue.sdk.wrapper.Persistence;
import com.philips.lighting.hue.sdk.wrapper.connection.BridgeConnection;
import com.philips.lighting.hue.sdk.wrapper.connection.BridgeConnectionCallback;
import com.philips.lighting.hue.sdk.wrapper.connection.BridgeConnectionType;
import com.philips.lighting.hue.sdk.wrapper.connection.BridgeStateCacheType;
import com.philips.lighting.hue.sdk.wrapper.connection.BridgeStateUpdatedCallback;
import com.philips.lighting.hue.sdk.wrapper.connection.BridgeStateUpdatedEvent;
import com.philips.lighting.hue.sdk.wrapper.connection.ConnectionEvent;
import com.philips.lighting.hue.sdk.wrapper.connection.FoundDevicesCallback;
import com.philips.lighting.hue.sdk.wrapper.connection.HeartbeatManager;
import com.philips.lighting.hue.sdk.wrapper.discovery.BridgeDiscovery;
import com.philips.lighting.hue.sdk.wrapper.discovery.BridgeDiscoveryCallback;
import com.philips.lighting.hue.sdk.wrapper.discovery.BridgeDiscoveryResult;
import com.philips.lighting.hue.sdk.wrapper.domain.Bridge;
import com.philips.lighting.hue.sdk.wrapper.domain.BridgeBuilder;
import com.philips.lighting.hue.sdk.wrapper.domain.BridgeState;
import com.philips.lighting.hue.sdk.wrapper.domain.HueError;
import com.philips.lighting.hue.sdk.wrapper.domain.ReturnCode;
import com.philips.lighting.hue.sdk.wrapper.domain.device.light.LightPoint;
import org.deviceconnect.android.deviceplugin.hue.HueConstants;
import org.deviceconnect.android.deviceplugin.hue.service.HueLightService;
import org.deviceconnect.android.deviceplugin.hue.service.HueService;
import org.deviceconnect.android.service.DConnectServiceProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Hueの操作をするクラス.
 * @author NTT DOCOMO, INC.
 */
public enum HueManager {

    /**
     * シングルトンなHueManagerのインスタンス.
     */
    INSTANCE;

    static {
        // Load the huesdk native library before calling any SDK method
        System.loadLibrary("huesdk");
    }
    /**
     * Hue接続状態.
     */
    public enum HueState {
        /** 未認証. */
        INIT,
        /** 未接続. */
        NO_CONNECT,
        /** 認証失敗. */
        AUTHENTICATE_FAILED,
        /** 認証済み. */
        AUTHENTICATE_SUCCESS
    }
    private Map<String, Bridge> mBridges;
    private BridgeDiscovery mBridgeDiscovery;

    /**
     * 同一ネットワーク上で発見したブリッジのリストを通知する.
     */
    public interface HueBridgeDiscoveryListener {
        /**
         * 発見したブリッジのリストを返す.
         * @param results ブリッジ
         */
        void onFoundedBridge(final List<BridgeDiscoveryResult> results);
    }

    public interface HueBridgeConnectionListener {
        void onPushlinkingBridge(final String ip);
        void onConnectedBridge(final String ip);
        void onDisconnectedBridge(final String ip);
        void onError(final String ip, List<HueError> list);
    }



    /**
     * HueのBridgeデータを管理ヘルパークラス.
     */
    private HueDBHelper mHueDBHelper;
    /**
     * Hueのライトデータを管理ヘルパークラス.
     */
    private HueLightDBHelper mHueLightDBHelper;

    /**
     * HueManagerのインスタンスを生成する.
     */
    HueManager() {
    }

    /**
     * 初期化を実行する.
     *
     * @param context コンテキストオブジェクト。
     */
    public void init(final Context context) {
        mHueDBHelper = new HueDBHelper(context);
        mHueLightDBHelper = new HueLightDBHelper(context);
        mBridges = Collections.synchronizedMap(new HashMap<String, Bridge>());
        Persistence.setStorageLocation(context.getFilesDir().getAbsolutePath(), HueConstants.APNAME);
        HueLog.setConsoleLogLevel(HueLog.LogLevel.OFF);

    }

    /**
     * HueManagerを破棄する.
     */
    public synchronized void destroy() {
        stopBridgeDiscovery();
        disconnectAllBridges();
    }

    /**
     * ブリッジを検索する.
     */
    public void startBridgeDiscovery(final HueBridgeDiscoveryListener l) {
        if (l == null) {
            return;
        }
        mBridgeDiscovery = new BridgeDiscovery();
        mBridgeDiscovery.search(BridgeDiscovery.BridgeDiscoveryOption.ALL, new BridgeDiscoveryCallback() {
            @Override
            public void onFinished(List<BridgeDiscoveryResult> list, ReturnCode returnCode) {
                if (returnCode == ReturnCode.SUCCESS) {
                    l.onFoundedBridge(list);
                    stopBridgeDiscovery();
                }
            }
        });
    }

    /**
     * ブリッジの検索処理を止める.
     */
    public void stopBridgeDiscovery() {
        if (mBridgeDiscovery != null) {
            mBridgeDiscovery.stop();
            mBridgeDiscovery = null;
        }
    }


    public synchronized void connectToBridge(final String bridgeIp, final HueBridgeConnectionListener l) {
        if (l == null) {
            return;
        }
        stopBridgeDiscovery();
        disconnectFromBridge(bridgeIp);
        Bridge bridge = new BridgeBuilder(HueConstants.APNAME, HueConstants.APNAME)
                .setIpAddress(bridgeIp)
                .setConnectionType(BridgeConnectionType.LOCAL)
                .setBridgeConnectionCallback(new BridgeConnectionCallback() {
                    @Override
                    public void onConnectionEvent(BridgeConnection bridgeConnection, ConnectionEvent connectionEvent) {
                        switch (connectionEvent) {
                            case LINK_BUTTON_NOT_PRESSED:
                                l.onPushlinkingBridge(bridgeIp);
                                break;
                            case CONNECTION_RESTORED:
                                l.onConnectedBridge(bridgeIp);
                                break;
                            case COULD_NOT_CONNECT:
                            case CONNECTION_LOST:
                            case DISCONNECTED:
                                // User-initiated disconnection.
                                l.onDisconnectedBridge(bridgeIp);
                                break;

                            default:
                                break;
                        }
                    }

                    @Override
                    public void onConnectionError(BridgeConnection bridgeConnection, List<HueError> list) {
                        l.onError(bridgeIp, list);
                    }
                })
                .addBridgeStateUpdatedCallback(new BridgeStateUpdatedCallback() {
                    @Override
                    public void onBridgeStateUpdated(Bridge bridge, BridgeStateUpdatedEvent bridgeStateUpdatedEvent) {
                        switch (bridgeStateUpdatedEvent) {
                            case INITIALIZED:
                                l.onConnectedBridge(bridgeIp);
                                final BridgeConnection bridgeConnection = bridge.getBridgeConnection(BridgeConnectionType.LOCAL);
                                if (bridgeConnection == null) {
                                    return;
                                }
                                final HeartbeatManager heartbeatManager = bridgeConnection.getHeartbeatManager();
                                if (heartbeatManager == null) {
                                    return;
                                }
                                heartbeatManager.stopAllHeartbeats();
                                heartbeatManager.startHeartbeat(BridgeStateCacheType.LIGHTS_AND_GROUPS, 1000);
                                break;

                            case LIGHTS_AND_GROUPS:
                                // At least one light was updated.
                                break;

                            default:
                                break;
                        }
                    }
                })
                .build();
        mBridges.put(bridgeIp, bridge);
        bridge.connect();
    }


    public Bridge getBridge(final String ip) {
        return mBridges.get(ip);
    }

    /**
     * ブリッジとの接続を切る.
     */
    public void disconnectFromBridge(final String ip) {
        Bridge removeBridge = mBridges.remove(ip);
        if (removeBridge != null) {
            for (BridgeConnection connection : removeBridge.getBridgeConnections()) {
                HeartbeatManager heartbeatManager = connection.getHeartbeatManager();
                if (heartbeatManager != null) {
                    heartbeatManager.stopAllHeartbeats();
                }
            }
            removeBridge.disconnect();
        }
    }

    public void disconnectAllBridges() {
        for (String key : mBridges.keySet()) {
            disconnectFromBridge(key);
        }
    }




    public synchronized List<LightPoint> getCacheLights(final String ip) {
        Bridge bridge = mBridges.get(ip);

        if (bridge == null) {
            return new ArrayList<>();
        }
        BridgeState bridgeState = bridge.getBridgeState();
        return bridgeState.getLights();
    }


    /**
     * SDKのキャッシュ上のライトリストを返す.
     * @return ライトリスト
     */
    public synchronized Bridge searchLightAutomatic(final String ip, final FoundDevicesCallback c) {
        Bridge bridge = mBridges.get(ip);
        if (bridge == null) {
            return null;
        }
        bridge.findNewDevices(c);
        return bridge;
    }

    public synchronized Bridge searchLightManually(final String ip, final String serialNumber, final FoundDevicesCallback c) {
        Bridge bridge = mBridges.get(ip);
        if (bridge == null) {
            return null;
        }

        List<String> serials = new ArrayList<String>();
        serials.add(serialNumber);
        bridge.findNewDevices(serials, c);
        return bridge;
    }
    /**
     * SDkのキャッシュの中からライトIDにあったPHLightオブジェクトを返す
     * @param ip ライトのServiceId
     * @return PHLightオブジェクト
     */
    public synchronized LightPoint getCacheLight(final String ip, final String lightId) {
        Bridge bridge = mBridges.get(ip);
        if (bridge != null) {
            BridgeState bridgeState = bridge.getBridgeState();
            for (LightPoint light : bridgeState.getLights()) {
                if (light.getIdentifier().equals(lightId)) {
                    return light;
                }
            }
        }
        return null;
    }


    /*
     * アクセスポイントと再接続する.
     * @param listener 再接続処理結果を返すリスナー
     */
    public synchronized void reconnectBridge(final DConnectServiceProvider provider, final HueBridgeConnectionListener l) {
        List<HueService> bridges = mHueDBHelper.getBridgeServices();
        for (HueService bridge : bridges) {
            if (bridge.isOnline()) {
                bridge.setOnline(false);
                connectToBridge(bridge.getId(), l);
            }
            provider.addService(bridge);
        }
    }


    /**
     *　指定されたIPのブリッジに紐づいているライトリストを返す.
     *
     * @param ipAddress BridgeのIP
     */
    public synchronized List<HueLightService> getLightsForIp(final String ipAddress) {
        return mHueLightDBHelper.getLightsForIp(ipAddress);
    }


    /**
     * Hue ブリッジの情報を永続化する.
     * @param service ブリッジ情報
     */
    public synchronized void saveBridgeForDB(final HueService service) {
        if (!mHueDBHelper.hasBridgeService(service.getId())) {
            mHueDBHelper.addHueBridgeService(service);
        } else {
            mHueDBHelper.updateBridge(service);
        }
    }

    /**
     * ライトの情報を永続化する.
     * @param service ライト情報
     */
    public synchronized void saveLightForDB(final HueLightService service) {
        String[] ids = service.getId().split(":");
        String ip = ids[0];
        String lightId = ids[1];
        if (!mHueLightDBHelper.hasLight(ip, lightId)) {
            mHueLightDBHelper.addLight(service);
        } else {
            mHueLightDBHelper.updateLight(service);
        }
    }

    /**
     * DBに保存されている、指定されたIPのブリッジの情報を返す.
     * @param ip IPアドレス
     * @return ブリッジの情報
     */
    public synchronized HueService getHueService(final String ip) {
        return mHueDBHelper.getBridgeServiceByIpAddress(ip);
    }

    /**
     * DBに保存されている、指定されたライトの情報を返す.
     * @param ip ブリッジのIPアドレス
     * @param lightId ライトID
     * @return ライトの情報
     */
    public synchronized HueLightService getHueLightService(final String ip, final String lightId) {
        return mHueLightDBHelper.getLight(ip, lightId);
    }

    /**
     * DBから指定されたIPのブリッジ情報を削除する.
     * @param service 削除するブリッジの情報
     */
    public synchronized void removeBridgeForDB(final HueService service) {
        mHueDBHelper.removeBridgeByIpAddress(service.getId());
    }

    /**
     * DBから指定された情報のライトを削除する。
     * @param service ライト情報
     */
    public synchronized void removeLightForDB(final HueLightService service) {
        String[] ids = service.getId().split(":");
        String ip = ids[0];
        mHueLightDBHelper.removeLightByIpAddress(ip);
    }
}
