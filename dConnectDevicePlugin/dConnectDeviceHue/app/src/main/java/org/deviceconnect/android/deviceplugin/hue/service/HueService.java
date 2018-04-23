/*
 HueService
 Copyright (c) 2018 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.hue.service;


import com.philips.lighting.hue.sdk.wrapper.discovery.BridgeDiscoveryResult;

import org.deviceconnect.android.deviceplugin.hue.profile.HueDeviceProfile;
import org.deviceconnect.android.deviceplugin.hue.profile.HueLightProfile;
import org.deviceconnect.android.service.DConnectService;
/**
 * Hue Bridgeのサービス.
 *
 * @author NTT DOCOMO, INC.
 */
public class HueService extends DConnectService {

    /**
     * サービス名のプレフィックス.
     */
    private static final String NAME_PREFIX = "hue ";

    /**
     * コンストラクタ.
     * @param bridge ブリッジ
     */
    public HueService(final BridgeDiscoveryResult bridge) {
        super(bridge.getIP());
        setName(NAME_PREFIX + bridge.getUniqueID());
        setNetworkType(NetworkType.WIFI);
        addProfile(new HueDeviceProfile());
        addProfile(new HueLightProfile());
    }

    /**
     * コンストラクタ.
     * @param ip ブリッジのIP
     * @param id ブリッジのユニークID
     */
    public HueService(final String ip, final String id) {
        super(ip);
        if (!id.startsWith(NAME_PREFIX)) {
            setName(NAME_PREFIX + id);
        } else {
            setName(id);
        }
        setNetworkType(NetworkType.WIFI);
        addProfile(new HueDeviceProfile());
        addProfile(new HueLightProfile());
    }
}
