/*
 FailHealthProfileTestCase.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.profile.restful.test;

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.deviceconnect.message.DConnectMessage.ErrorCode;
import org.deviceconnect.profile.AuthorizationProfileConstants;
import org.deviceconnect.profile.HealthProfileConstants;
import org.deviceconnect.utils.URIBuilder;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Healthプロファイルの異常系テスト.
 * @author NTT DOCOMO, INC.
 */
public class FailHealthProfileTestCase extends RESTfulDConnectTestCase 
    implements HealthProfileConstants {

    /**
     * コンストラクタ.
     * 
     * @param tag テストタグ
     */
    public FailHealthProfileTestCase(final String tag) {
        super(tag);
    }

    /**
     * serviceIdを指定せずに心拍数の取得要求を送信するテスト.
     * <pre>
     * 【HTTP通信】
     * Method: GET
     * Path: /health/heartrate
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに1が返ってくること。
     * </pre>
     */
    public void testGetHeartRateNoServiceId() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        try {
            HttpUriRequest request = new HttpGet(builder.toString());
            JSONObject root = sendRequest(request);
            assertResultError(ErrorCode.EMPTY_SERVICE_ID.getCode(), root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }

    /**
     * serviceIdが空状態で心拍数の取得要求を送信するテスト.
     * <pre>
     * 【HTTP通信】
     * Method: GET
     * Path: /health/heartrate?serviceId=
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに1が返ってくること。
     * </pre>
     */
    public void testGetHeartRateEmptyServiceId() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(PARAM_SERVICE_ID, "");
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        try {
            HttpUriRequest request = new HttpGet(builder.toString());
            JSONObject root = sendRequest(request);
            assertResultError(ErrorCode.NOT_FOUND_SERVICE.getCode(), root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }

    /**
     * 存在しないserviceIdで心拍数の取得要求を送信するテスト.
     * <pre>
     * 【HTTP通信】
     * Method: GET
     * Path: /health/heartrate?serviceId=123456789
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに1が返ってくること。
     * </pre>
     */
    public void testGetHeartRateInvalidServiceId() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(PARAM_SERVICE_ID, "123456789");
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        try {
            HttpUriRequest request = new HttpGet(builder.toString());
            JSONObject root = sendRequest(request);
            assertResultError(ErrorCode.NOT_FOUND_SERVICE.getCode(), root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }

    /**
     * serviceIdを2重に指定して心拍数の取得要求を送信するテスト.
     * <pre>
     * 【HTTP通信】
     * Method: GET
     * Path: /health/heartrate?serviceId=123456789&serviceId=xxx
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・先に定義された属性が優先されること。
     * ・resultに1が返ってくること。
     * </pre>
     */
    public void testGetHeartRateDuplicatedServiceId() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(PARAM_SERVICE_ID, "123456789");
        builder.addParameter(PARAM_SERVICE_ID, getServiceId());
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        try {
            HttpUriRequest request = new HttpGet(builder.toString());
            JSONObject root = sendRequest(request);
            assertResultError(ErrorCode.NOT_FOUND_SERVICE.getCode(), root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }

    /**
     * メソッドにPOSTを指定して心拍数の取得要求を送信するテスト.
     * <pre>
     * 【HTTP通信】
     * Method: POST
     * Path: /health/heartrate?serviceId=xxxx
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに1が返ってくること。
     * </pre>
     */
    public void testHeartRateInvalidMethodPost() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(PARAM_SERVICE_ID, getServiceId());
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        try {
            HttpUriRequest request = new HttpPost(builder.toString());
            JSONObject root = sendRequest(request);
            assertResultError(ErrorCode.NOT_SUPPORT_ACTION.getCode(), root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }
    

    /**
     * serviceIdが無い状態でheartrate属性のコールバック登録テストを行う.
     * <pre>
     * 【HTTP通信】
     * Method: PUT
     * Path: /health/heartrate
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに1が返ってくること。
     * </pre>
     */
    public void testPutHeartRate001() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(PARAM_SESSION_KEY, getClientId());
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        try {
            HttpUriRequest request = new HttpPut(builder.toString());
            JSONObject root = sendRequest(request);
            assertResultError(ErrorCode.EMPTY_SERVICE_ID.getCode(), root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }

    /**
     * serviceIdが空状態でheartrate属性のコールバック登録テストを行う.
     * <pre>
     * 【HTTP通信】
     * Method: PUT
     * Path: /health/heartrate?serviceId=
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに1が返ってくること。
     * </pre>
     */
    public void testPutHeartRate002() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(PARAM_SERVICE_ID, "");
        builder.addParameter(PARAM_SESSION_KEY, getClientId());
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        try {
            HttpUriRequest request = new HttpPut(builder.toString());
            JSONObject root = sendRequest(request);
            assertResultError(ErrorCode.NOT_FOUND_SERVICE.getCode(), root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }

    /**
     * 存在しないserviceIdでheartrate属性のコールバック登録テストを行う.
     * <pre>
     * 【HTTP通信】
     * Method: PUT
     * Path: /health/heartrate?serviceId=123456789
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに1が返ってくること。
     * </pre>
     */
    public void testPutHeartRate003() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(PARAM_SERVICE_ID, "123456789");
        builder.addParameter(PARAM_SESSION_KEY, getClientId());
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        try {
            HttpUriRequest request = new HttpPut(builder.toString());
            JSONObject root = sendRequest(request);
            assertResultError(ErrorCode.NOT_FOUND_SERVICE.getCode(), root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }

    /**
     * 定義にない属性を指定してheartrate属性のコールバック登録テストを行う.
     * <pre>
     * 【HTTP通信】
     * Method: PUT
     * Path: /health/heartrate?serviceId=xxxxx&abc=abc
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・定義にない属性は無視されること。
     * ・resultが0で返ってくること。
     * </pre>
     */
    public void testPutHeartRate004() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(PARAM_SERVICE_ID, getServiceId());
        builder.addParameter("abc", "abc");
        builder.addParameter(PARAM_SESSION_KEY, getClientId());
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        try {
            HttpUriRequest request = new HttpPut(builder.toString());
            JSONObject root = sendRequest(request);
            assertResultOK(root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }

    /**
     * serviceIdを2重に指定してheartrate属性のコールバック登録テストを行う.
     * <pre>
     * 【HTTP通信】
     * Method: PUT
     * Path: /health/heartrate?serviceId=123456789&serviceId=xxx
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・先に定義された属性が優先されること。
     * ・resultに1が返ってくること。
     * </pre>
     */
    public void testPutHeartRate005() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(PARAM_SERVICE_ID, "123456789");
        builder.addParameter(PARAM_SERVICE_ID, getServiceId());
        builder.addParameter(PARAM_SESSION_KEY, getClientId());
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        try {
            HttpUriRequest request = new HttpPut(builder.toString());
            JSONObject root = sendRequest(request);
            assertResultError(ErrorCode.NOT_FOUND_SERVICE.getCode(), root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }
    

    /**
     * serviceIdが無い状態でheartrate属性のコールバック解除テストを行う.
     * <pre>
     * 【HTTP通信】
     * Method: DELETE
     * Path: /health/heartrate
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに1が返ってくること。
     * </pre>
     */
    public void testDeleteHeartRate001() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(PARAM_SESSION_KEY, getClientId());
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        try {
            HttpUriRequest request = new HttpDelete(builder.toString());
            JSONObject root = sendRequest(request);
            assertResultError(ErrorCode.EMPTY_SERVICE_ID.getCode(), root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }

    /**
     * serviceIdが空状態でheartrate属性のコールバック解除テストを行う.
     * <pre>
     * 【HTTP通信】
     * Method: DELETE
     * Path: /health/heartrate?serviceId=
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに1が返ってくること。
     * </pre>
     */
    public void testDeleteHeartRate002() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(PARAM_SERVICE_ID, "");
        builder.addParameter(PARAM_SESSION_KEY, getClientId());
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        try {
            HttpUriRequest request = new HttpDelete(builder.toString());
            JSONObject root = sendRequest(request);
            assertResultError(ErrorCode.NOT_FOUND_SERVICE.getCode(), root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }

    /**
     * 存在しないserviceIdでheartrate属性のコールバック解除テストを行う.
     * <pre>
     * 【HTTP通信】
     * Method: DELETE
     * Path: /health/heartrate?serviceId=123456789
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・resultに1が返ってくること。
     * </pre>
     */
    public void testDeleteHeartRate003() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(PARAM_SERVICE_ID, "123456789");
        builder.addParameter(PARAM_SESSION_KEY, getClientId());
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        try {
            HttpUriRequest request = new HttpDelete(builder.toString());
            JSONObject root = sendRequest(request);
            assertResultError(ErrorCode.NOT_FOUND_SERVICE.getCode(), root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }

    /**
     * 定義にない属性を指定してheartrate属性のコールバック解除テストを行う.
     * <pre>
     * 【HTTP通信】
     * Method: DELETE
     * Path: /health/heartrate?serviceId=xxxxx&abc=abc
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・定義にない属性は無視されること。
     * ・resultが0で返ってくること。
     * </pre>
     */
    public void testDeleteHeartRate004() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(PARAM_SERVICE_ID, getServiceId());
        builder.addParameter("abc", "abc");
        builder.addParameter(PARAM_SESSION_KEY, getClientId());
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        try {
            HttpUriRequest request = new HttpDelete(builder.toString());
            JSONObject root = sendRequest(request);
            assertResultOK(root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }

    /**
     * serviceIdを2重に指定してheartrate属性のコールバック解除テストを行う.
     * <pre>
     * 【HTTP通信】
     * Method: DELETE
     * Path: /health/heartrate?serviceId=123456789&serviceId=xxx
     * </pre>
     * <pre>
     * 【期待する動作】
     * ・先に定義された属性が優先されること。
     * ・resultに1が返ってくること。
     * </pre>
     */
    public void testDeleteHeartRate005() {
        URIBuilder builder = TestURIBuilder.createURIBuilder();
        builder.setProfile(PROFILE_NAME);
        builder.setAttribute(ATTRIBUTE_HEART_RATE);
        builder.addParameter(PARAM_SERVICE_ID, "123456789");
        builder.addParameter(PARAM_SERVICE_ID, getServiceId());
        builder.addParameter(PARAM_SESSION_KEY, getClientId());
        builder.addParameter(AuthorizationProfileConstants.PARAM_ACCESS_TOKEN, getAccessToken());
        try {
            HttpUriRequest request = new HttpDelete(builder.toString());
            JSONObject root = sendRequest(request);
            assertResultError(ErrorCode.NOT_FOUND_SERVICE.getCode(), root);
        } catch (JSONException e) {
            fail("Exception in JSONObject." + e.getMessage());
        }
    }
}
