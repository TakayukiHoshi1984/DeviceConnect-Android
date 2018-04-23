/*
 HueLightService
 Copyright (c) 2018 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.hue.service;

import com.philips.lighting.hue.sdk.wrapper.domain.device.light.LightPoint;

import org.deviceconnect.android.deviceplugin.hue.profile.HueLightProfile;
import org.deviceconnect.android.service.DConnectService;

/**
 * Hue Lightのサービス.
 *
 * @author NTT DOCOMO, INC.
 */
public class HueLightService extends DConnectService {
    /**
     * コンストラクタ.
     * @param ip ブリッジのIP
     * @param light ブリッジに付随するLightオブジェクト
     */
    public HueLightService(final String ip, final LightPoint light) {
        //LightのServiceIdは、IPアドレスとライトIDを「:」で区切る
        super(ip + ":" + light.getIdentifier());
        setName(light.getName());
        setNetworkType(NetworkType.WIFI);
        addProfile(new HueLightProfile());
    }

    /**
     * コンストラクタ.
     * @param ip ブリッジのIP
     * @param id ライトID
     * @param name ライト名
     */
    public HueLightService(final String ip, final String id, final String name) {
        //LightのServiceIdは、IPアドレスとライトIDを「:」で区切る
        super(ip + ":" + id);
        setName(name);
        setNetworkType(NetworkType.WIFI);
        addProfile(new HueLightProfile());
    }
}
