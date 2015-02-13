/*
 NormalHealthProfileTestCase.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.profile.restful.test;

import junit.framework.Assert;

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.deviceconnect.android.test.plugin.profile.TestHealthProfileConstants;
import org.deviceconnect.profile.AuthorizationProfileConstants;
import org.deviceconnect.profile.DConnectProfileConstants;
import org.deviceconnect.utils.URIBuilder;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Healthプロファイルの正常系テスト.
 * @author NTT DOCOMO, INC.
 */
public class NormalHealthProfileTestCase extends RESTfulDConnectTestCase
    implements TestHealthProfileConstants {

    /**
     * コンストラクタ.
     * 
     * @param tag テストタグ
     */
    public NormalHealthProfileTestCase(final String tag) {
        super(tag);
    }

    /**
     * 心拍数取得の要求を送信するテスト.
     * <pre>
     * 【HTTP通信】
     * Method: GET
     * Path: /health/heartrate?serviceId=xxxx
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * ・heartRateに値が返ってくること。
     * </pre>
     */
    public void testGetHeartRate() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        builder.addParameter(DConnectProfileConstants.PARAM_SERVICE_ID, getServiceId());
        try {
            HttpUriRequest request = new HttpGet(builder.toString());
            JSONObject response = sendRequest(request);
            assertResultOK(response);
            assertEquals("heartRate is invalid.", TEST_HEART_RATE, response.getInt(PARAM_HEART_RATE));
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }
    /**
     * heartrateイベントのコールバック登録テストを行う.
     * <pre>
     * 【HTTP通信】
     * Method: PUT
     * Path: /health/heartrate?serviceId=xxxx&sessionKey=xxxx
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * ・コールバック登録後にイベントを受信すること。
     * </pre>
     */
    public void testPutHeartRate() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        builder.addParameter(AuthorizationProfileConstants.PARAM_SESSION_KEY, getClientId());
        builder.addParameter(DConnectProfileConstants.PARAM_SERVICE_ID, getServiceId());
        try {
            HttpUriRequest request = new HttpPut(builder.toString());
            JSONObject root = sendRequest(request);
            Assert.assertNotNull("root is null.", root);
            assertResultOK(root);

            JSONObject event = waitForEvent();
            assertEquals("heartRate is invalid", TEST_HEART_RATE, event.getInt(PARAM_HEART_RATE));
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }
    /**
     * heartrateイベントのコールバック登録テストを行う.
     * <pre>
     * 【HTTP通信】
     * Method: DELETE
     * Path: /health/heartrate?serviceId=xxxx&sessionKey=xxxx
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに0が返ってくること。
     * </pre>
     */
    public void testDeleteHeartRate() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        builder.addParameter(AuthorizationProfileConstants.PARAM_SESSION_KEY, getClientId());
        builder.addParameter(DConnectProfileConstants.PARAM_SERVICE_ID, getServiceId());
        try {
            HttpUriRequest request = new HttpDelete(builder.toString());
            JSONObject root = sendRequest(request);
            Assert.assertNotNull("root is null.", root);
            assertResultOK(root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }
}
