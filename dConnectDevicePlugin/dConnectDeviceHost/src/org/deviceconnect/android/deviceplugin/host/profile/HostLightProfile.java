package org.deviceconnect.android.deviceplugin.host.profile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.deviceconnect.android.deviceplugin.host.BuildConfig;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.original.profile.LightProfile;
import org.deviceconnect.message.intent.message.IntentDConnectMessage;

import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

/**
 * 
 * @author NTT DOCOMO, INC.
 */
@SuppressWarnings("deprecation")
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
    private Camera mCamera;

    @Override
    protected boolean onGetLight(final Intent request, final Intent response) {
        List<Bundle> lightsParam = new ArrayList<Bundle>();

        Bundle lightParam = new Bundle();
        lightParam.putString(PARAM_NAME, LIGHT_NAME);
        lightParam.putString(PARAM_LIGHT_ID, LIGHT_ID);
        lightParam.putString(PARAM_CONFIG, "");
        lightParam.putBoolean(PARAM_ON, mCamera != null);
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
            turnOnLight();
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
            turnOffLight();
            setResult(response, IntentDConnectMessage.RESULT_OK);
        }
        return true;
    }

    /**
     * 端末のLEDライトをONにする.
     */
    private synchronized void turnOnLight() {
        if (mCamera == null) {
            mCamera = Camera.open();

            Parameters p = mCamera.getParameters();
            p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            mCamera.setParameters(p);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                SurfaceTexture preview = new SurfaceTexture(0);
                try {
                    mCamera.setPreviewTexture(preview);
                } catch (IOException e) {
                    // Do nothing
                    if (BuildConfig.DEBUG) {
                        Log.w("HOST", "", e);
                    }
                }
            }
            mCamera.startPreview();
        }
    }

    /**
     * 端末のLEDライトをOFFにする.
     */
    private synchronized void turnOffLight() {
        if (mCamera != null) {
            mCamera.stopPreview();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    mCamera.setPreviewTexture(null);
                } catch (IOException e) {
                    // Do nothing
                    if (BuildConfig.DEBUG) {
                        Log.w("HOST", "", e);
                    }
                }
            }
            mCamera.release();
            mCamera = null;
        }
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
