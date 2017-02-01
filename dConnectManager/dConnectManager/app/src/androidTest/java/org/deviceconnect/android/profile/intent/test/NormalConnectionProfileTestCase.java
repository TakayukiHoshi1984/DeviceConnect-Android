/*
 NormalConnectionProfileTestCase.java
 Copyright (c) 2017 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.profile.intent.test;

import android.content.Intent;
import android.support.test.runner.AndroidJUnit4;

import org.deviceconnect.message.DConnectMessage;
import org.deviceconnect.message.intent.message.IntentDConnectMessage;
import org.deviceconnect.profile.ConnectionProfileConstants;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Connectionプロファイルの正常系テスト.
 * @author NTT DOCOMO, INC.
 */
@RunWith(AndroidJUnit4.class)
public class NormalConnectionProfileTestCase extends IntentDConnectTestCase {

    /**
     * WiFi機能有効状態(ON/OFF)取得テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: GET
     * Profile: connection
     * Interface: なし
     * Attribute: wifi
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testGetWifi() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_GET);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());
        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_WIFI);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * WiFi機能有効化テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: PUT
     * Profile: connection
     * Interface: なし
     * Attribute: wifi
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testPutWifi() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_PUT);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());
        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_WIFI);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * WiFi機能無効化テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: PUT
     * Profile: connection
     * Interface: なし
     * Attribute: wifi
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testDeleteWifi() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_DELETE);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());
        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_WIFI);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * WiFi機能有効状態変化イベントのコールバック登録テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: PUT
     * Profile: connection
     * Interface: なし
     * Attribute: onwifichange
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * ・コールバック登録後にイベントが通知されること。
     * </pre>
     */
    @Test
    public void testPutOnWifiChange() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_PUT);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());

        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_ON_WIFI_CHANGE);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * WiFi機能有効状態変化イベントのコールバック解除テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: DELETE
     * Profile: connection
     * Interface: なし
     * Attribute: onwifichange
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testDeleteOnWifiChange() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_DELETE);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());

        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_ON_WIFI_CHANGE);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * Bluetooth機能有効状態(ON/OFF)取得テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: GET
     * Profile: connection
     * Interface: なし
     * Attribute: bluetooth
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testGetBluetooth() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_GET);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());
        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_BLUETOOTH);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * Bluetooth機能有効化テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: PUT
     * Profile: connection
     * Interface: なし
     * Attribute: bluetooth
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testPutBluetooth() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_PUT);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());
        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_BLUETOOTH);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * Bluetooth機能無効化テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: DELETE
     * Profile: connection
     * Interface: なし
     * Attribute: bluetooth
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testDeleteBluetooth() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_DELETE);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());
        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_BLUETOOTH);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * Bluetooth機能有効状態変化イベントのコールバック登録テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: PUT
     * Profile: connection
     * Interface: なし
     * Attribute: onbluetoothchange
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testPutOnBluetoothChange() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_PUT);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());

        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_ON_BLUETOOTH_CHANGE);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * Bluetooth機能有効状態変化イベントのコールバック解除テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: DELETE
     * Profile: connection
     * Interface: なし
     * Attribute: onbluetoothchange
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testDeleteOnBluetoothChange() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_DELETE);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());

        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_ON_BLUETOOTH_CHANGE);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * Bluetooth検索可能状態を有効にするテストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: PUT
     * Profile: connection
     * Interface: bluetooth
     * Attribute: discoverable
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testPutBluetoothDiscoverable() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_PUT);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());
        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_INTERFACE, ConnectionProfileConstants.INTERFACE_BLUETOOTH);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_DISCOVERABLE);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * Bluetooth検索可能状態を無効にするテストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: DELETE
     * Profile: connection
     * Interface: bluetooth
     * Attribute: discoverable
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testDeleteBluetoothDiscoverable() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_DELETE);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());
        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_INTERFACE, ConnectionProfileConstants.INTERFACE_BLUETOOTH);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_DISCOVERABLE);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * NFC機能有効状態(ON/OFF)取得テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: GET
     * Profile: connection
     * Interface: なし
     * Attribute: nfc
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testGetNFC() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_GET);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());
        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_NFC);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * NFC機能有効化テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: PUT
     * Profile: connection
     * Interface: なし
     * Attribute: nfc
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testPutNFC() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_PUT);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());
        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_NFC);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * NFC機能無効化テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: DELETE
     * Profile: connection
     * Interface: なし
     * Attribute: nfc
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testDeleteNFC() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_DELETE);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());
        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_NFC);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * NFC機能有効状態変化イベントのコールバック登録テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: PUT
     * Profile: connection
     * Interface: なし
     * Attribute: onnfcchange
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testPutOnNFCChange() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_PUT);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());

        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_ON_NFC_CHANGE);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * NFC機能有効状態変化イベントのコールバック解除テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: DELETE
     * Profile: connection
     * Interface: なし
     * Attribute: onnfcchange
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testDeleteOnNFCChange() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_DELETE);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());

        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_ON_NFC_CHANGE);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * BLE機能有効状態(ON/OFF)取得テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: GET
     * Profile: connection
     * Interface: なし
     * Attribute: ble
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testGetBLE() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_GET);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());
        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_BLE);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * BLE機能有効化テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: PUT
     * Profile: connection
     * Interface: なし
     * Attribute: ble
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testPutBLE() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_PUT);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());
        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_BLE);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * BLE機能無効化テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: DELETE
     * Profile: connection
     * Interface: なし
     * Attribute: ble
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testDeleteBLE() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_DELETE);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());
        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_BLE);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * BLE機能有効状態変化イベントのコールバック登録テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: PUT
     * Profile: connection
     * Interface: なし
     * Attribute: onblechange
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testPutOnBLEChange() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_PUT);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());

        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_ON_BLE_CHANGE);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

    /**
     * BLE機能有効状態変化イベントのコールバック解除テストを行う.
     * 
     * <pre>
     * 【Intent通信】
     * Action: DELETE
     * Profile: connection
     * Interface: なし
     * Attribute: onblechange
     * </pre>
     * 
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    @Test
    public void testDeleteOnBLEChange() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_DELETE);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());

        request.putExtra(DConnectMessage.EXTRA_PROFILE, ConnectionProfileConstants.PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ConnectionProfileConstants.ATTRIBUTE_ON_BLE_CHANGE);
        Intent response = sendRequest(request);
        assertResultOK(response);
    }

}