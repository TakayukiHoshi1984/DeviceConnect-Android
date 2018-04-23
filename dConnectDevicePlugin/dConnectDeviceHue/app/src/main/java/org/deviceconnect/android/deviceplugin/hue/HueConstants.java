/*
HueConstants
Copyright (c) 2014 NTT DOCOMO,INC.
Released under the MIT license
http://opensource.org/licenses/mit-license.php
*/

package org.deviceconnect.android.deviceplugin.hue;

/**
 * hueデバイスプラグインで使用する定数.
 */
public interface HueConstants {

    /**
     * アプリケーション名.
     */
    String APNAME = "DConnectDeviceHueAndroid";

    /**
     * ユーザ名.
     */
    String USERNAME = "DConnectDeviceHueAndroid";
    /**
     * サービス確認画面でサービスが削除されたことを知らせるIntent名.
     */
    String ACTION_REMOVE_BRIDGE = "org.deviceconnect.android.deviceplugin.hue.ACTION_REMOVE_BRIDGE";
    /**
     * ブリッジと接続処理が行われたことを通知するIntent名.
     */
    String ACTION_CONNECTED_BRIDGE = "org.deviceconnect.android.deviceplugin.hue.ACTION_CONNECTED_BRIDGE";

}
