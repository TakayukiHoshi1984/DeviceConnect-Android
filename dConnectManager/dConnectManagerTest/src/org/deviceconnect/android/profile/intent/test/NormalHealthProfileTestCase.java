/*
 NormalHealthProfileTestCase.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.profile.intent.test;

import junit.framework.Assert;

import org.deviceconnect.android.test.plugin.profile.TestHealthProfileConstants;
import org.deviceconnect.message.DConnectMessage;
import org.deviceconnect.message.intent.message.IntentDConnectMessage;

import android.content.Intent;

/**
 * Healthプロファイルの正常系テスト.
 * @author NTT DOCOMO, INC.
 */
public class NormalHealthProfileTestCase extends IntentDConnectTestCase
    implements TestHealthProfileConstants {

    /**
     * コンストラクタ.
     * 
     * @param string テストタグ
     */
    public NormalHealthProfileTestCase(final String string) {
        super(string);
    }
    

    /**
     * 心拍数の取得要求を送信するテスト.
     * <pre>
     * 【Intent通信】
     * Action: GET
     * Extra: 
     *     profile=health
     *     attribute=heartrate
     *     serviceId=xxxx
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    public void testGetHeartRate() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_GET);
        request.putExtra(DConnectMessage.EXTRA_PROFILE, PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ATTRIBUTE_HEART_RATE);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());

        Intent response = sendRequest(request);
        assertResultOK(response);
        assertEquals("heartRate is invalid", TEST_HEART_RATE,
                response.getIntExtra(PARAM_HEART_RATE, 0));
    }
    

    /**
     * heartrateイベントのコールバック登録テストを行う.
     * <pre>
     * 【Intent通信】
     * Action: PUT
     * Extra: 
     *     profile=health
     *     attribute=heartrate
     *     sessionKey=xxxx
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * ・コールバック登録後にイベントを受信すること。
     * </pre>
     */
    public void testPutHeartRate() {
        final String serviceId = getServiceId();
        final String clientId = getClientId();
        Intent request = new Intent(IntentDConnectMessage.ACTION_PUT);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, serviceId);
        request.putExtra(DConnectMessage.EXTRA_SESSION_KEY, clientId);
        request.putExtra(DConnectMessage.EXTRA_PROFILE, PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ATTRIBUTE_HEART_RATE);

        Intent response = sendRequest(request);
        Assert.assertTrue(response.hasExtra(IntentDConnectMessage.EXTRA_RESULT));
        Assert.assertEquals(IntentDConnectMessage.RESULT_OK,
                response.getIntExtra(IntentDConnectMessage.EXTRA_RESULT, -1));

        Intent event = waitForEvent();

        Assert.assertEquals(PROFILE_NAME, event.getStringExtra(DConnectMessage.EXTRA_PROFILE));
        Assert.assertEquals(ATTRIBUTE_HEART_RATE, event.getStringExtra(DConnectMessage.EXTRA_ATTRIBUTE));
        Assert.assertEquals(serviceId, event.getStringExtra(DConnectMessage.EXTRA_SERVICE_ID));
        Assert.assertEquals(clientId, event.getStringExtra(DConnectMessage.EXTRA_SESSION_KEY));
        Assert.assertEquals(TEST_HEART_RATE, event.getIntExtra(PARAM_HEART_RATE, 0));
    }
    

    /**
     * heartrateイベントのコールバック解除テストを行う.
     * <pre>
     * 【Intent通信】
     * Action: DELETE
     * Extra: 
     *     profile=heartrate
     *     attribute=heartrate
     *     sessionKey=xxxx
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    public void testDeleteOnDeviceOrientation() {
        Intent request = new Intent(IntentDConnectMessage.ACTION_DELETE);
        request.putExtra(DConnectMessage.EXTRA_SERVICE_ID, getServiceId());
        request.putExtra(DConnectMessage.EXTRA_SESSION_KEY, getClientId());
        request.putExtra(DConnectMessage.EXTRA_PROFILE, PROFILE_NAME);
        request.putExtra(DConnectMessage.EXTRA_ATTRIBUTE, ATTRIBUTE_HEART_RATE);
        Intent response = sendRequest(request);
        Assert.assertTrue(response.hasExtra(IntentDConnectMessage.EXTRA_RESULT));
        Assert.assertEquals(IntentDConnectMessage.RESULT_OK,
                response.getIntExtra(IntentDConnectMessage.EXTRA_RESULT, -1));
    }
}
