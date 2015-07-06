package org.deviceconnect.android.deviceplugin.host.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.deviceconnect.android.deviceplugin.host.camera.CameraOverlay;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.original.profile.LightProfile;
import org.deviceconnect.message.intent.message.IntentDConnectMessage;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * 
 * @author NTT DOCOMO, INC.
 */
public class HostLightProfile extends LightProfile {

    /**
     * バックライト名を定義する.
     */
    private static final String LIGHT_NAME = "LED Light (HOST)";

    /**
     * ライトのIDを定義する.
     */
    private static final String LIGHT_ID = "host_light_id";

    /**
     * カメラのインスタンス.
     */
    private CameraOverlay mCamera;

    /**
     * コンストラクタ.
     * @param camera カメラ操作するためのインスタンス
     */
    public HostLightProfile(final CameraOverlay camera) {
        if (camera == null) {
            throw new NullPointerException("camera is null.");
        }
        mCamera = camera;
    }

    @Override
    protected boolean onGetLight(final Intent request, final Intent response) {
        List<Bundle> lightsParam = new ArrayList<Bundle>();

        Bundle lightParam = new Bundle();
        lightParam.putString(PARAM_NAME, LIGHT_NAME);
        lightParam.putString(PARAM_LIGHT_ID, LIGHT_ID);
        lightParam.putString(PARAM_CONFIG, "");
        lightParam.putBoolean(PARAM_ON, mCamera.isLight());
        lightsParam.add(lightParam);

        response.putExtra(PARAM_LIGHTS, lightsParam.toArray(new Bundle[lightsParam.size()]));
        setResult(response, IntentDConnectMessage.RESULT_OK);
        return true;
    }

    @Override
    protected boolean onPostLight(final Intent request, final Intent response) {
        String serviceId = getServiceID(request);
        String lightId = getLightId(request);
        if (serviceId == null) {
            createEmptyServiceId(response);
        } else if (!checkServiceId(serviceId)) {
            createNotFoundService(response);
        } else if (!checkLightId(lightId)) {
            MessageUtils.setInvalidRequestParameterError(response, 
                    "lightId is invalid.");
        } else {
            mCamera.turnOnLight();
            setResult(response, IntentDConnectMessage.RESULT_OK);
        }
        return true;
    }

    @Override
    protected boolean onDeleteLight(final Intent request, final Intent response) {
        String serviceId = getServiceID(request);
        String lightId = getLightId(request);
        if (serviceId == null) {
            createEmptyServiceId(response);
        } else if (!checkServiceId(serviceId)) {
            createNotFoundService(response);
        } else if (!checkLightId(lightId)) {
            MessageUtils.setInvalidRequestParameterError(response, 
                    "lightId is invalid.");
        } else {
            mCamera.turnOffLight();
            setResult(response, IntentDConnectMessage.RESULT_OK);
        }
        return true;
    }

    /**
     * Check serviceId.
     * 
     * @param serviceId ServiceId
     * @return <code>serviceId</code>がテスト用サービスIDに等しい場合はtrue、そうでない場合はfalse
     */
    private boolean checkServiceId(final String serviceId) {
        String regex = HostServiceDiscoveryProfile.SERVICE_ID;
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(serviceId);
        return m.find();
    }

    /**
     * lightIdをcameraIdに変換する.
     * 対応するcameraIdが存在しない場合には-1を返す。
     * @param lightId ライトID
     * @return cameraId
     */
    private boolean checkLightId(final String lightId) {
        return LIGHT_ID.equals(lightId);
    }

    /**
     * サービスIDが空の場合のエラーを作成する.
     * 
     * @param response レスポンスを格納するIntent
     */
    private void createEmptyServiceId(final Intent response) {
        MessageUtils.setEmptyServiceIdError(response);
    }

    /**
     * デバイスが発見できなかった場合のエラーを作成する.
     * 
     * @param response レスポンスを格納するIntent
     */
    private void createNotFoundService(final Intent response) {
        MessageUtils.setNotFoundServiceError(response);
    }
}
