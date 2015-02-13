/*
 TestHealthProfile.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.test.profile;

import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.HealthProfile;
import org.deviceconnect.message.DConnectMessage;

import android.content.Intent;

/**
 * JUnit用テストデバイスプラグイン、Healthプロファイル.
 * 
 * @author NTT DOCOMO, INC.
 */
public class TestHealthProfile extends HealthProfile {

    /**
     * テスト用心拍数の値.
     */
    public static final int TEST_HEART_RATE = 81;

    @Override
    protected boolean onGetHeartRate(final Intent request, final Intent response, final String serviceId) {
        if (!checkServiceId(serviceId)) {
            createNotFoundService(response);
        } else {
            setResult(response, DConnectMessage.RESULT_OK);
            setHeartRate(response, TEST_HEART_RATE);
        }
        return true;
    }

    @Override
    protected boolean onPutOnHeartRate(final Intent request, final Intent response, 
            final String serviceId, final String sessionKey) {
        if (serviceId == null) {
            createEmptyServiceId(response);
        } else if (!checkServiceId(serviceId)) {
            createNotFoundService(response);
        } else if (sessionKey == null) {
            MessageUtils.setInvalidRequestParameterError(response);
        } else {
            setResult(response, DConnectMessage.RESULT_OK);

            Intent intent = MessageUtils.createEventIntent();
            setSessionKey(intent, sessionKey);
            setServiceID(intent, serviceId);
            setProfile(intent, getProfileName());
            setAttribute(intent, ATTRIBUTE_HEART_RATE);
            setHeartRate(intent, TEST_HEART_RATE);

            Util.sendBroadcast(getContext(), intent);
        }
        return true;
    }

    @Override
    protected boolean onDeleteOnHeartRate(final Intent request, final Intent response,
            final String serviceId, final String sessionKey) {
        if (serviceId == null) {
            createEmptyServiceId(response);
        } else if (!checkServiceId(serviceId)) {
            createNotFoundService(response);
        } else if (sessionKey == null) {
            MessageUtils.setInvalidRequestParameterError(response);
        } else {
            setResult(response, DConnectMessage.RESULT_OK);
        }
        return true;
    }

    /**
     * サービスIDをチェックする.
     * 
     * @param serviceId サービスID
     * @return <code>serviceId</code>がテスト用サービスIDに等しい場合はtrue、そうでない場合はfalse
     */
    private boolean checkServiceId(final String serviceId) {
        return TestServiceDiscoveryProfile.SERVICE_ID.equals(serviceId);
    }

    /**
     * サービスIDが空の場合のエラーを作成する.
     * 
     * @param response レスポンスを格納するIntent
     */
    private void createEmptyServiceId(final Intent response) {
        MessageUtils.setEmptyServiceIdError(response, "Service ID is empty.");
    }

    /**
     * デバイスが発見できなかった場合のエラーを作成する.
     * 
     * @param response レスポンスを格納するIntent
     */
    private void createNotFoundService(final Intent response) {
        MessageUtils.setNotFoundServiceError(response, "Service is not found.");
    }
}
