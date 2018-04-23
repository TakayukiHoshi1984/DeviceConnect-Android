/*
 HueLightProfile
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.hue.profile;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.philips.lighting.hue.sdk.wrapper.connection.BridgeConnectionType;
import com.philips.lighting.hue.sdk.wrapper.connection.BridgeResponseCallback;
import com.philips.lighting.hue.sdk.wrapper.domain.Bridge;
import com.philips.lighting.hue.sdk.wrapper.domain.HueError;
import com.philips.lighting.hue.sdk.wrapper.domain.ReturnCode;
import com.philips.lighting.hue.sdk.wrapper.domain.clip.ClipResponse;
import com.philips.lighting.hue.sdk.wrapper.domain.device.DeviceConfiguration;
import com.philips.lighting.hue.sdk.wrapper.domain.device.light.LightPoint;
import com.philips.lighting.hue.sdk.wrapper.domain.device.light.LightState;
import com.philips.lighting.hue.sdk.wrapper.utilities.HueColor;


import org.deviceconnect.android.deviceplugin.hue.HueDeviceService;
import org.deviceconnect.android.deviceplugin.hue.db.HueManager;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.LightProfile;
import org.deviceconnect.android.profile.api.DeleteApi;
import org.deviceconnect.android.profile.api.GetApi;
import org.deviceconnect.android.profile.api.PostApi;
import org.deviceconnect.android.profile.api.PutApi;
import org.deviceconnect.android.service.DConnectService;
import org.deviceconnect.message.DConnectMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 親クラスで振り分けられたメソッドに対して、Hueのlight attribute処理を呼び出す.
 *
 * @author NTT DOCOMO, INC.
 */
public class HueLightProfile extends LightProfile {

    /**
     * hue minimum brightness value.
     */
    private static final int HUE_BRIGHTNESS_MIN_VALUE = 1;

    /**
     * hue maximum brightness value.
     */
    private static final int HUE_BRIGHTNESS_MAX_VALUE = 255;

    /**
     * hue SDK maximum brightness value.
     */
    private static final int HUE_BRIGHTNESS_TUNED_MAX_VALUE = 254;

    /**
     * ライトフラッシング管理マップ.
     */
    private final Map<String, FlashingExecutor> mFlashingMap = new HashMap<String, FlashingExecutor>();

    public HueLightProfile() {
        addApi(new GetApi() {
            @Override
            public boolean onRequest(final Intent request, final Intent response) {
                String serviceId = getServiceID(request);
                String lightId = null;
                if (serviceId.contains(":")) {
                    String[] ids = serviceId.split(":");
                    serviceId = ids[0];
                    lightId = ids[1];
                }
                Bridge bridge = HueManager.INSTANCE.getBridge(serviceId);
                if (bridge == null) {
                    MessageUtils.setNotFoundServiceError(response, "Not found bridge: " + serviceId);
                    return true;
                }
                List<Bundle> lightList = new ArrayList<Bundle>();
                if (lightId == null) {
                    for (LightPoint lightPoint : bridge.getBridgeState().getLights()) {
                        LightState lightState = lightPoint.getLightState();
                        Bundle light = new Bundle();
                        setLightId(light, lightPoint.getIdentifier());
                        setName(light, lightPoint.getName());
                        setOn(light, lightState != null ? lightState.isOn() : false);
                        setConfig(light, "");
                        lightList.add(light);
                    }
                } else {
                    // Lightである場合は自分自身の情報のみ返す
                    LightPoint lightPoint = HueManager.INSTANCE.getCacheLight(serviceId, lightId);
                    LightState lightState = lightPoint.getLightState();
                    Bundle light = new Bundle();
                    setLightId(light, lightPoint.getIdentifier());
                    setName(light, lightPoint.getName());
                    setOn(light, lightState != null ? lightState.isOn() : false);
                    setConfig(light, "");
                    lightList.add(light);
                }
                setLights(response, lightList);
                sendResultOK(response);
                return true;
            }
        });

        addApi(new PostApi() {
            @Override
            public boolean onRequest(final Intent request, final Intent response) {
                String serviceId = getServiceID(request);
                String lightId = getLightId(request);
                Integer color = getColor(request);
                Double brightness = getBrightness(request);
                long[] flashing = getFlashing(request);
                if (serviceId.contains(":")) {
                    String[] ids = serviceId.split(":");
                    serviceId = ids[0];
                    lightId = ids[1];
                }
                final Bridge bridge = HueManager.INSTANCE.getBridge(serviceId);
                if (bridge == null) {
                    MessageUtils.setNotFoundServiceError(response, "Not found bridge: " + serviceId);
                    return true;
                }

                final LightPoint light = HueManager.INSTANCE.getCacheLight(serviceId, lightId);
                if (light == null) {
                    MessageUtils.setInvalidRequestParameterError(response, "Not found light: " + lightId + "@" + serviceId);
                    return true;
                }
                final LightState lightState = makeLightState(bridge, color, brightness, flashing);
                if (flashing != null) {
                    flashing(lightId, lightState, bridge, light, flashing);
                    sendResultOK(response);//do not check result of flashing
                    return true;
                } else {
                    light.updateState(lightState, BridgeConnectionType.LOCAL, new BridgeResponseCallback() {
                        @Override
                        public void handleCallback(Bridge bridge, ReturnCode returnCode, List<ClipResponse> list, List<HueError> errorList) {
                            if (returnCode == ReturnCode.SUCCESS) {
                                sendResultOK(response);
                            } else {
                                StringBuilder errorMessage = new StringBuilder();
                                for (HueError error : errorList) {
                                    errorMessage.append(error.toString()).append(",");
                                }
                                MessageUtils.setUnknownError(response, errorMessage.toString());
                                sendResultERR(response);
                            }
                        }
                    });
                    return false;
                }
            }
        });

        addApi(new DeleteApi() {
            @Override
            public boolean onRequest(final Intent request, final Intent response) {
                String serviceId = getServiceID(request);
                String lightId = getLightId(request);
                if (serviceId.contains(":")) {
                    String[] ids = serviceId.split(":");
                    serviceId = ids[0];
                    lightId = ids[1];
                }

                final Bridge bridge = HueManager.INSTANCE.getBridge(serviceId);
                if (bridge == null) {
                    MessageUtils.setNotFoundServiceError(response, "Not found bridge: " + serviceId);
                    return true;
                }

                LightPoint light = HueManager.INSTANCE.getCacheLight(serviceId, lightId);
                if (light == null) {
                    MessageUtils.setInvalidRequestParameterError(response, "Not found light: " + lightId + "@" + serviceId);
                    return true;
                }

                LightState lightState = new LightState();
                lightState.setOn(false);

                light.updateState(lightState, BridgeConnectionType.LOCAL, new BridgeResponseCallback() {
                    @Override
                    public void handleCallback(Bridge bridge, ReturnCode returnCode, List<ClipResponse> list, List<HueError> errorList) {
                        if (returnCode == ReturnCode.SUCCESS) {
                            sendResultOK(response);
                        } else {
                            StringBuilder errorMessage = new StringBuilder();
                            for (HueError error : errorList) {
                                errorMessage.append(error.toString()).append(",");
                            }
                            MessageUtils.setUnknownError(response, errorMessage.toString());
                            sendResultERR(response);
                        }
                    }
                });
                return false;
            }
        });

        addApi(new PutApi() {
            @Override
            public boolean onRequest(final Intent request, final Intent response) {
                final String[] serviceId = new String[1];
                serviceId[0] = getServiceID(request);
                final String[] lightId = new String[1];
                lightId[0] = getLightId(request);
                Integer color = getColor(request);
                Double brightness = getBrightness(request);
                long[] flashing = getFlashing(request);
                final String name = getName(request);
                if (serviceId[0].contains(":")) {
                    String[] ids = serviceId[0].split(":");
                    serviceId[0] = ids[0];
                    lightId[0] = ids[1];
                }

                final Bridge bridge = HueManager.INSTANCE.getBridge(serviceId[0]);
                if (bridge == null) {
                    MessageUtils.setNotFoundServiceError(response, "Not found bridge: " + serviceId[0]);
                    return true;
                }

                final LightPoint light = HueManager.INSTANCE.getCacheLight(serviceId[0], lightId[0]);
                if (light == null) {
                    MessageUtils.setInvalidRequestParameterError(response, "Not found light: " + lightId[0] + "@" + serviceId[0]);
                    return true;
                }

                if (name == null || name.length() == 0) {
                    MessageUtils.setInvalidRequestParameterError(response, "name is invalid.");
                    return true;
                }

                //wait for change name and status
                final CountDownLatch countDownLatch = new CountDownLatch(2);
                sendResponseAfterAwait(response, countDownLatch);

                DeviceConfiguration conf = light.getConfiguration();
                conf.setName(name);
                light.updateConfiguration(conf, new BridgeResponseCallback() {
                    private boolean mErrorFlag = false;
                    @Override
                    public void handleCallback(Bridge bridge, ReturnCode returnCode, List<ClipResponse> list, List<HueError> errorList) {
                        if (returnCode == ReturnCode.SUCCESS) {
                            DConnectService service = ((HueDeviceService) getContext()).getServiceProvider().getService(serviceId[0] + ":" + lightId[0]);
                            if (service != null) {
                                service.setName(name);
                            }
                            countDown();
                        } else {
                            StringBuilder errorMessage = new StringBuilder();
                            for (HueError error : errorList) {
                                errorMessage.append(error.toString()).append(",");
                            }
                            mErrorFlag = true;
                            countDown();
                            MessageUtils.setUnknownError(response, errorMessage.toString());
                            sendResultERR(response);
                        }
                    }
                    private void countDown() {
                        if (!mErrorFlag) {
                            setResult(response, DConnectMessage.RESULT_OK);
                        }
                        countDownLatch.countDown();
                    }
                });


                final LightState lightState = makeLightState(bridge, color, brightness, flashing);
                if (flashing != null) {
                    flashing(lightId[0], lightState, bridge, light, flashing);
                    countDownLatch.countDown();//do not check result of flashing
                } else {
                    light.updateState(lightState, BridgeConnectionType.LOCAL, new BridgeResponseCallback() {
                        private boolean mErrorFlag = false;
                        @Override
                        public void handleCallback(Bridge bridge, ReturnCode returnCode, List<ClipResponse> list, List<HueError> errorList) {
                            if (returnCode == ReturnCode.SUCCESS) {
                                countDown();
                            } else {
                                StringBuilder errorMessage = new StringBuilder();
                                for (HueError error : errorList) {
                                    errorMessage.append(error.toString()).append(",");
                                }
                                mErrorFlag = true;
                                countDown();
                                MessageUtils.setUnknownError(response, errorMessage.toString());
                                sendResultERR(response);
                            }
                        }
                        private void countDown() {
                            if (!mErrorFlag) {
                                setResult(response, DConnectMessage.RESULT_OK);
                            }
                            countDownLatch.countDown();
                        }
                    });
                }
                return false;
            }
        });
    }

    private LightState makeLightState(Bridge bridge, Integer color, Double brightness, long[] flashing) {
        int[] colors = convertColor(color);

        // Brightness magnification conversion
        calcColorParam(colors, brightness);

        // Calculation of brightness.
        int calcBrightness = calcBrightnessParam(colors);

        LightState lightState = new LightState();
        lightState.setOn(true);

        Color hueColor = new Color(color,bridge);
        lightState.setXY(hueColor.mX, hueColor.mY);
        lightState.setBrightness(calcBrightness);
        if (flashing != null) {
            lightState.setTransitionTime(1);
        }
        return lightState;
    }

    private void flashing(String lightId, final LightState lightState, final Bridge bridge, final LightPoint light, long[] flashing) {
        FlashingExecutor exe = mFlashingMap.get(lightId);
        if (exe == null) {
            exe = new FlashingExecutor();
            mFlashingMap.put(lightId, exe);
        }
        exe.setLightControllable(new FlashingExecutor.LightControllable() {
            @Override
            public void changeLight(final boolean isOn, final FlashingExecutor.CompleteListener listener) {
                lightState.setOn(isOn);
                light.updateState(lightState, BridgeConnectionType.LOCAL, new BridgeResponseCallback() {
                    @Override
                    public void handleCallback(Bridge bridge, ReturnCode returnCode, List<ClipResponse> list, List<HueError> list1) {
                        listener.onComplete();
                    }
                });
            }
        });
        exe.start(flashing);
    }

    private void sendResponseAfterAwait(final Intent response, final CountDownLatch latch) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!latch.await(30, TimeUnit.SECONDS)) {
                        MessageUtils.setTimeoutError(response);
                    }
                } catch (InterruptedException e) {
                    MessageUtils.setTimeoutError(response);
                }
                sendResponse(response);
            }
        }).start();
    }

    /**
     * Convert Integer to int[].
     *
     * @param color color
     * @return int[]
     */
    private int[] convertColor(final Integer color) {
        int[] colors = new int[3];
        if (color != null) {
            colors[0] = android.graphics.Color.red(color);
            colors[1] = android.graphics.Color.green(color);
            colors[2] = android.graphics.Color.blue(color);
        } else {
            colors[0] = 0xFF;
            colors[1] = 0xFF;
            colors[2] = 0xFF;
        }
        return colors;
    }



    /**
     * 成功レスポンス送信.
     *
     * @param response response
     */
    private void sendResultOK(final Intent response) {
        setResult(response, DConnectMessage.RESULT_OK);
        sendResponse(response);
    }

    /**
     * エラーレスポンスを送信する.
     *
     * @param response エラーレスポンス
     */
    private void sendResultERR(final Intent response) {
        setResult(response, DConnectMessage.RESULT_ERROR);
        sendResponse(response);
    }

    /**
     * Calculate color parameter.
     *
     * @param color      Color parameters.
     * @param brightness Brightness parameter.
     */
    private void calcColorParam(final int[] color, final Double brightness) {
        if (brightness != null) {
            color[0] = (int) Math.round(color[0] * brightness);
            color[1] = (int) Math.round(color[1] * brightness);
            color[2] = (int) Math.round(color[2] * brightness);
        }
    }

    /**
     * Calculate brightness parameter.
     *
     * @param color Color parameters.
     * @return brightness Brightness parameter.
     */
    private int calcBrightnessParam(final int[] color) {
        int brightness = Math.max(color[0], color[1]);
        brightness = Math.max(brightness, color[2]);
        if (brightness < HUE_BRIGHTNESS_MIN_VALUE) {
            brightness = HUE_BRIGHTNESS_MIN_VALUE;
        } else if (brightness >= HUE_BRIGHTNESS_MAX_VALUE) {
            brightness = HUE_BRIGHTNESS_TUNED_MAX_VALUE;
        }
        return brightness;
    }

    /**
     * Hueの色指定.
     *
     * @author NTT DOCOMO, INC.
     */
    private static class Color {

        /**
         * R.
         */
        final int mR;

        /**
         * G.
         */
        final int mG;

        /**
         * B.
         */
        final int mB;

        /**
         * 色相のX座標.
         */
        final double mX;

        /**
         * 色相のY座標.
         */
        final double mY;

        /**
         * コンストラクタ.
         *
         * @param rgb RGB
         */
        Color(final int rgb, final Bridge bridge) {
            mR = android.graphics.Color.red(rgb);
            mG = android.graphics.Color.green(rgb);
            mB = android.graphics.Color.blue(rgb);
            HueColor.RGB rgb2 = new HueColor.RGB(mR, mG, mB);
            HueColor color = new HueColor(rgb2, bridge.getBridgeConfiguration().getName(), bridge.getBridgeConfiguration().getSwVersion());
            HueColor.XY xy = color.getXY();
            mX = xy.x;
            mY = xy.y;
        }
    }


}
