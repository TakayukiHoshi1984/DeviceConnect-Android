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
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawImageObject;
import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;
import org.deviceconnect.android.deviceplugin.host.externaldisplay.ExternalDisplayService;
import org.deviceconnect.android.deviceplugin.host.profile.HostCanvasProfile;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.api.DConnectApi;
import org.deviceconnect.android.profile.api.DeleteApi;
import org.deviceconnect.android.service.DConnectServiceProvider;
import org.deviceconnect.message.DConnectMessage;

/**
 * 外部ディスプレイ用のCanvasプロファイルの処理を行う.
 * @author NTT DOCOMO, INC.
 */
public class ExternalDisplayCanvasProfile extends HostCanvasProfile {
    /** Canvasプロファイルのファイル名プレフィックス。 */
    private static final String CANVAS_PREFIX = "presentation_canvas";


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
            Intent intent = new Intent(getDeleteCanvasActionName());
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
        addApi(mDeleteImageApi);
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
        CanvasDrawImageObject drawObj = new CanvasDrawImageObject(uri, enumMode, mimeType, x, y, false);
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

    protected boolean isCanvasMultipleShowFlag() {
        return mSettings.isCanvasContinuousAccessForHost();
    }


    protected String getTempFileName() {
        return CANVAS_PREFIX;
    }

    protected boolean isActivityNeverShow() {
        return mSettings.isCanvasActivityNeverShowFlag();
    }

    protected String getDeleteCanvasActionName() {
        return CanvasDrawImageObject.ACTION_EXTERNAL_DISPLAY_DELETE_CANVAS;
    }
}
