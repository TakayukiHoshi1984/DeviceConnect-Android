/*
 HueDeviceProfile
 Copyright (c) 2018 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.hue.profile;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.philips.lighting.hue.sdk.wrapper.connection.FoundDevicesCallback;
import com.philips.lighting.hue.sdk.wrapper.discovery.BridgeDiscoveryResult;
import com.philips.lighting.hue.sdk.wrapper.domain.Bridge;
import com.philips.lighting.hue.sdk.wrapper.domain.HueError;
import com.philips.lighting.hue.sdk.wrapper.domain.device.Device;

import org.deviceconnect.android.deviceplugin.hue.HueConstants;
import org.deviceconnect.android.deviceplugin.hue.HueDeviceService;
import org.deviceconnect.android.deviceplugin.hue.db.HueManager;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.DConnectProfile;
import org.deviceconnect.android.profile.api.DeleteApi;
import org.deviceconnect.android.profile.api.PostApi;
import org.deviceconnect.message.DConnectMessage;

import java.util.List;

/**
 * スマートデバイスへの接続関連の機能を提供するAPI.
 *
 */

public class HueDeviceProfile extends DConnectProfile {
    /**
     * Search Lightフラグ.
     */
    private boolean mIsSearchBridge;
    /**
     * Hue Bridgeとの接続を行う.
     */
    private PostApi mPostDevicePairing = new PostApi() {
        @Override
        public String getAttribute() {
            return "pairing";
        }
        @Override
        public boolean onRequest(final Intent request, final Intent response) {
            if (!isLocalNetworkEnabled()) {
                MessageUtils.setIllegalDeviceStateError(response, "Please connect to LocalNetwork");
                return true;
            }
            if (!getService().isOnline()) {
                if (mIsSearchBridge) {
                    MessageUtils.setIllegalDeviceStateError(response, "Now connecting for Hue bridge.");
                    return true;
                }
                mIsSearchBridge = true;
                HueManager.INSTANCE.init(getContext());
                HueManager.INSTANCE.startBridgeDiscovery(new HueManager.HueBridgeDiscoveryListener() {
                    @Override
                    public void onFoundedBridge(List<BridgeDiscoveryResult> results) {
                        for (BridgeDiscoveryResult result : results) {
                            connectToBridge(response, result.getIP());
                        }
                    }
                });
                return false;
            } else {
                setResult(response, DConnectMessage.RESULT_OK);
                return true;
            }
        }
    };



    /**
     * Hue Bridgeとの接続を解除する.
     */
    private DeleteApi mDeleteDevicePairing = new DeleteApi() {
        @Override
        public String getAttribute() {
            return "pairing";
        }
        @Override
        public boolean onRequest(Intent request, Intent response) {
            String serviceId = getServiceID(request);
            HueDeviceService deviceService = ((HueDeviceService) getContext());
            deviceService.updateHueBridge(false, serviceId);
            HueManager.INSTANCE.disconnectFromBridge(serviceId);
            setResult(response, DConnectMessage.RESULT_OK);
            return true;
        }
    };


    /**
     * コンストラクタ.
     */
    public HueDeviceProfile() {
        addApi(mPostDevicePairing);
        addApi(mDeleteDevicePairing);
    }


    @Override
    public String getProfileName() {
        return "device";
    }


    /**
     * 指定されたブリッジと接続する.
     * @param response DConnectMessageレスポンス
     * @param ip IPアドレス
     */
    private void connectToBridge(final Intent response, final String ip) {
        final HueDeviceService deviceService = ((HueDeviceService) getContext());
        HueManager.INSTANCE.connectToBridge(ip, new HueManager.HueBridgeConnectionListener() {
            @Override
            public void onPushlinkingBridge(final String ip) {

            }

            @Override
            public void onConnectedBridge(final String ip) {
                setResult(response, DConnectMessage.RESULT_OK);
                sendResponse(response);
                deviceService.updateHueBridge(true, ip);
                // ライトの検索
                HueManager.INSTANCE.searchLightAutomatic(ip, new FoundDevicesCallback() {
                    @Override
                    public void onDevicesFound(Bridge bridge, List<Device> list, List<HueError> errorList) {
                    }

                    @Override
                    public void onDeviceSearchFinished(Bridge bridge, List<HueError> list) {
                        mIsSearchBridge = false;
                        sendConnectedBroadcast();
                    }
                });
            }

            @Override
            public void onDisconnectedBridge(final String ip) {
                deviceService.updateHueBridge(false, ip);
                MessageUtils.setIllegalDeviceStateError(response, "Connection Lost: " + ip);
                sendResponse(response);
                mIsSearchBridge = false;
            }
            @Override
            public void onError(final String ip, final List<HueError> list) {
                deviceService.updateHueBridge(false, ip);
                StringBuilder errorMessage = new StringBuilder();
                for (HueError e : list) {
                    errorMessage.append(e.toString()).append(",");
                }
                MessageUtils.setIllegalDeviceStateError(response, "Illegal Bridge State: " + errorMessage.toString());
                sendResponse(response);
                mIsSearchBridge = false;
            }
        });
    }

    /**
     * 接続されたことを知らせるIntentを送る.
     */
    private void sendConnectedBroadcast() {
        Intent restartBridge = new Intent(HueConstants.ACTION_CONNECTED_BRIDGE);
        getContext().sendBroadcast(restartBridge);
    }
    /**
     * LocalNetwork接続設定の状態を取得します.
     * @return trueの場合は有効、それ以外の場合は無効
     */
    private boolean isLocalNetworkEnabled() {
        ConnectivityManager convManager = (ConnectivityManager) getContext().getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = convManager.getActiveNetworkInfo();
        boolean isEthernet = false;
        if (info != null) {
            return (info.getType() == ConnectivityManager.TYPE_ETHERNET
                    || info.getType() == ConnectivityManager.TYPE_WIFI);
        } else {
            return false;
        }
    }
}
