/*
 ExternalDisplayCanvasProfile.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.externaldisplay.profile;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import org.deviceconnect.android.deviceplugin.host.HostDeviceService;
import org.deviceconnect.android.deviceplugin.host.activity.CanvasProfileActivity;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawImageObject;
import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;
import org.deviceconnect.android.deviceplugin.host.externaldisplay.ExternalDisplayService;
import org.deviceconnect.android.deviceplugin.host.profile.HostCanvasProfile;
import org.deviceconnect.android.deviceplugin.host.util.HostUtils;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.api.DConnectApi;
import org.deviceconnect.android.profile.api.DeleteApi;
import org.deviceconnect.android.profile.api.PostApi;
import org.deviceconnect.android.service.DConnectServiceProvider;
import org.deviceconnect.message.DConnectMessage;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 外部ディスプレイ用のCanvasプロファイルの処理を行う.
 * @author NTT DOCOMO, INC.
 */
public class ExternalDisplayCanvasProfile extends HostCanvasProfile {
    /** Canvasプロファイルのファイル名プレフィックス。 */
    private static final String CANVAS_PREFIX = "presentation_canvas";
    /** Imageを表示するサービス. */
    private ExecutorService mImageService = Executors.newSingleThreadExecutor();


    private final DConnectApi mDrawImageApi = new PostApi() {

        @Override
        public String getAttribute() {
            return ATTRIBUTE_DRAW_IMAGE;
        }

        @Override
        public boolean onRequest(final Intent request, final Intent response) {
            DConnectServiceProvider provider = ((HostDeviceService) getContext()).getServiceProvider();
            ExternalDisplayService dService = (ExternalDisplayService)
                    provider.getService(ExternalDisplayService.SERVICE_ID);
            if (dService == null) {
                MessageUtils.setIllegalServerStateError(response, "External Display Service NotFound");
                return true;
            }

            if (!mSettings.isCanvasActivityNeverShowFlag()) {
                MessageUtils.setIllegalServerStateError(response,
                        "The function of Canvas API is turned off.\n" +
                                "Please cancel on the setting screen of Host plug-in.");
                return true;
            }
            // 連続起動のダイアログが表示中かどうか
            if (mSettings.isCanvasMultipleShowFlag()) {
                MessageUtils.setIllegalServerStateError(response,
                        "Canvas API may be executed continuously.");
                return true;
            }
            String mode = getMode(request);
            String mimeType = getMIMEType(request);
            final CanvasDrawImageObject.Mode enumMode = CanvasDrawImageObject.convertMode(mode);
            if (enumMode == null) {
                MessageUtils.setInvalidRequestParameterError(response);
                return true;
            }
            // MIME-Typeが設定されていない場合は、画像として扱う.
            if (mimeType == null || mimeType.length() == 0) {
                mimeType = "image/jpeg";
            }
            // サポートしているMIME-Typeをチェック
            if ((!mimeType.contains("image")
                    && !mimeType.contains("video")
                    && !mimeType.contains("text/html"))) {
                MessageUtils.setInvalidRequestParameterError(response,
                        "Unsupported mimeType: " + mimeType);
                return true;
            }

            final byte[] data = getData(request);
            final String uri = getURI(request);
            final double x = getX(request);
            final double y = getY(request);

            if (data == null) {
                if (uri != null) {
                    if (uri.startsWith("http") || uri.startsWith("rtsp")) {
                        drawImage(response, uri, enumMode, mimeType, x, y);
                    } else {
                        MessageUtils.setInvalidRequestParameterError(response, "Invalid uri.");
                    }
                } else {
                    MessageUtils.setInvalidRequestParameterError(response, "Uri and data is null.");
                }
                return true;
            } else {
                // CanvasActivityの更新
                final String type = mimeType;
                mImageService.execute(() -> {
                    sendImage(data, response, enumMode, type, x, y);
                });
                return false;
            }
        }
    };

    private final DConnectApi mDeleteImageApi = new DeleteApi() {

        @Override
        public String getAttribute() {
            return ATTRIBUTE_DRAW_IMAGE;
        }

        @Override
        public boolean onRequest(final Intent request, final Intent response) {
            DConnectServiceProvider provider = ((HostDeviceService) getContext()).getServiceProvider();
            ExternalDisplayService dService = (ExternalDisplayService)
                    provider.getService(ExternalDisplayService.SERVICE_ID);
            if (dService == null) {
                MessageUtils.setIllegalServerStateError(response, "External Display Service NotFound");
                return true;
            }
            Intent intent = new Intent(CanvasDrawImageObject.ACTION_EXTERNAL_DISPLAY_DELETE_CANVAS);
            LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
            setResult(response, DConnectMessage.RESULT_OK);
            return true;
        }
    };
    /**
     * コンストラクタ.
     *
     * @param settings Canvasの設定
     */
    public ExternalDisplayCanvasProfile(HostCanvasSettings settings) {
        super(settings);
        addApi(mDrawImageApi);
        addApi(mDeleteImageApi);
    }

    protected void sendImage(byte[] data, Intent response, CanvasDrawImageObject.Mode enumMode, String mimeType, double x, double y) {
        try {
            drawImage(response, writeForImage(data, mimeType), enumMode, mimeType, x, y);
        } catch (OutOfMemoryError e) {
            MessageUtils.setIllegalDeviceStateError(response, e.getMessage());
        } catch (IOException e) {
            MessageUtils.setIllegalDeviceStateError(response, e.getMessage());
        }
        sendResponse(response);
    }

    /**
     * Start Canvas Activity.
     * @param response response message
     * @param uri image url
     * @param enumMode image mode
     * @param mimeType MIME-Type
     * @param x position
     * @param y position
     */
    protected void drawImage(Intent response, String uri, CanvasDrawImageObject.Mode enumMode, String mimeType, double x, double y) {
        CanvasDrawImageObject drawObj = new CanvasDrawImageObject(uri, enumMode, mimeType, x, y);
        DConnectServiceProvider provider = ((HostDeviceService) getContext()).getServiceProvider();
        ExternalDisplayService dService = (ExternalDisplayService)
                provider.getService(ExternalDisplayService.SERVICE_ID);
        if (dService != null) {
            dService.showCanvasDisplay(drawObj);  //新規表示と更新は、このメソッド内で行う
            setResult(response, DConnectMessage.RESULT_OK);
        } else {
            MessageUtils.setIllegalServerStateError(response, "External Display Service NotFound");
        }
    }
}
