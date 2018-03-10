/*
 HueDeviceProfile
 Copyright (c) 2018 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.hue.profile;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;

import com.philips.lighting.hue.sdk.PHAccessPoint;
import com.philips.lighting.hue.sdk.PHSDKListener;
import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHHueParsingError;
import com.philips.lighting.model.PHLight;

import org.deviceconnect.android.deviceplugin.hue.HueDeviceService;
import org.deviceconnect.android.deviceplugin.hue.db.HueManager;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.DConnectProfile;
import org.deviceconnect.android.profile.api.DeleteApi;
import org.deviceconnect.android.profile.api.PostApi;
import org.deviceconnect.android.service.DConnectService;
import org.deviceconnect.message.DConnectMessage;
import org.json.hue.JSONObject;

import java.util.List;

/**
 * スマートデバイスへの接続関連の機能を提供するAPI.
 *
 */

public class HueDeviceProfile extends DConnectProfile {
    /**
     * hueブリッジのNotificationを受け取るためのリスナー.
     */
    private PHSDKListener mListener;

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
            if (!isWifiEnabled()) {
                MessageUtils.setIllegalDeviceStateError(response, "Please connect to Wifi");
                return true;
            }
            final String serviceId = getServiceID(request);
            mListener = new PHSDKListener() {

                @Override
                public void onAuthenticationRequired(final PHAccessPoint accessPoint) {
                    HueManager.INSTANCE.startPushlinkAuthentication(accessPoint);
                }

                @Override
                public void onAccessPointsFound(final List<PHAccessPoint> accessPoint) {
                    PHAccessPoint point = null;
                    for (int i = 0; i < accessPoint.size(); i++) {
                        PHAccessPoint p = accessPoint.get(i);
                        if (p.getIpAddress().equals(serviceId)) {
                            point = p;
                            break;
                        }
                    }
                    if (point == null) {
                        MessageUtils.setIllegalDeviceStateError(response, "Connection Lost: " + point.getIpAddress());
                        sendResponse(response);
                        return;
                    }
                    HueManager.INSTANCE.startAuthenticate(point, new HueManager.HueConnectionListener() {
                        @Override
                        public void onConnected() {
                            setResult(response, DConnectMessage.RESULT_OK);
                            sendResponse(response);
                        }

                        @Override
                        public void onNotConnected() {
                            // ignore
                        }
                    });
                }

                @Override
                public void onCacheUpdated(final List<Integer> list, final PHBridge bridge) {
                }

                @Override
                public void onBridgeConnected(final PHBridge phBridge, final String userName) {
                    setResult(response, DConnectMessage.RESULT_OK);
                    sendResponse(response);
                }

                @Override
                public void onConnectionLost(final PHAccessPoint point) {
                    MessageUtils.setIllegalDeviceStateError(response, "Connection Lost: " + point.getIpAddress());
                    sendResponse(response);
                }

                @Override
                public void onConnectionResumed(final PHBridge bridge) {
                    setResult(response, DConnectMessage.RESULT_OK);
                    sendResponse(response);
                }

                @Override
                public void onError(final int code, final String message) {
                    MessageUtils.setIllegalDeviceStateError(response, "Illegal Bridge State: " + message);
                    sendResponse(response);
                }

                @Override
                public void onParsingErrors(final List<PHHueParsingError> errors) {
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < errors.size(); i++) {
                        PHHueParsingError error = errors.get(i);
                        builder.append("--").append("\n").append("code: ").append(error.getCode()).append(", ").append(error.getMessage()).append("\n");
                        JSONObject obj = error.getJSONContext();
                        if (obj != null) {
                            builder.append(obj.toString(4)).append("\n");
                        }
                    }
                    MessageUtils.setIllegalDeviceStateError(response, "Illegal Bridge State: " + builder.toString());
                    sendResponse(response);
                }
            };
            if (!getService().isOnline()) {
                HueManager.INSTANCE.init(getContext());
                HueManager.INSTANCE.addSDKListener(mListener);
                HueManager.INSTANCE.searchHueBridge();
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
            if (mListener != null) {
                HueManager.INSTANCE.removeSDKListener(mListener);
            }
            PHBridge bridge = HueManager.INSTANCE.findBridge(serviceId);

            if (bridge != null) {
                List<PHLight> lights = HueManager.INSTANCE.getLightsForIp(serviceId);
                for (int i = 0; i < lights.size(); i++) {
                    PHLight light = lights.get(i);
                    DConnectService s = ((HueDeviceService) getContext()).getServiceProvider().getService(serviceId + ":" + light.getIdentifier());
                    if (s != null) {
                        s.setOnline(false);
                    }
                }
                HueManager.INSTANCE.disconnectAccessPoint(serviceId);
                HueManager.INSTANCE.disconnectHueBridge();
                DConnectService service = getService();
                if (service != null) {
                    service.setOnline(false);
                }
                setResult(response, DConnectMessage.RESULT_OK);
            } else {
                MessageUtils.setIllegalDeviceStateError(response, "Not found bridge " + serviceId);
            }
            return true;
        }
    };


    public HueDeviceProfile() {
        addApi(mPostDevicePairing);
        addApi(mDeleteDevicePairing);
    }


    @Override
    public String getProfileName() {
        return "device";
    }

    /**
     * Wi-Fi接続設定の状態を取得します.
     * @return trueの場合は有効、それ以外の場合は無効
     */
    private boolean isWifiEnabled() {
        WifiManager mgr = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return mgr.isWifiEnabled();
    }
}