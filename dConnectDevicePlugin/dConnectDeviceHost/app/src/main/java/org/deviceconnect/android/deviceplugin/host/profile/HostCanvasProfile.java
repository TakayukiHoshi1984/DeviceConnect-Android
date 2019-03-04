/*
 HostCanvasProfile.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */

package org.deviceconnect.android.deviceplugin.host.profile;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import org.deviceconnect.android.deviceplugin.host.HostDeviceApplication;
import org.deviceconnect.android.deviceplugin.host.HostDeviceService;
import org.deviceconnect.android.deviceplugin.host.activity.CanvasProfileActivity;
import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawImageObject;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.CanvasProfile;
import org.deviceconnect.android.profile.api.DConnectApi;
import org.deviceconnect.android.profile.api.DeleteApi;
import org.deviceconnect.android.profile.api.PostApi;
import org.deviceconnect.message.DConnectMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Canvas Profile.
 *
 * @author NTT DOCOMO, INC.
 */
public class HostCanvasProfile extends CanvasProfile {
    /** ファイルが生存できる有効時間を定義する. */
    protected static final long DEFAULT_EXPIRE = 1000 * 60 * 5;

    /** Canvasプロファイルのファイル名プレフィックス。 */
    private static final String CANVAS_PREFIX = "host_canvas";

    /** ファイルが生存できる有効時間. */
    protected long mExpire = DEFAULT_EXPIRE;

    /** Imageを表示するサービス. */
    private ExecutorService mImageService = Executors.newSingleThreadExecutor();

    /** Canvasの設定. */
    protected HostCanvasSettings mSettings;

    private final DConnectApi mDrawImageApi = new PostApi() {

        @Override
        public String getAttribute() {
            return ATTRIBUTE_DRAW_IMAGE;
        }

        @Override
        public boolean onRequest(final Intent request, final Intent response) {
            if (!isActivityNeverShow()) {
                MessageUtils.setIllegalServerStateError(response,
                        "The function of Canvas API is turned off.\n" +
                                "Please cancel on the setting screen of Host plug-in.");
                return true;
            }
            // 連続起動のダイアログが表示中かどうか
            if (isCanvasMultipleShowFlag()) {
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
            if (getApp().getShowActivityAndData(getTopOfActivity().getName()) != null) {
                Intent intent = new Intent(getDeleteCanvasActionName());
                LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
                getApp().removeShowActivityAndData(getTopOfActivity().getName());
                getApp().putShowActivityFlagFromAvailabilityService(getTopOfActivity().getName(), false);
                setResult(response, DConnectMessage.RESULT_OK);
            } else {
                MessageUtils.setIllegalDeviceStateError(response, "canvas not display");
            }

            return true;
        }
    };

    /**
     * コンストラクタ.
     * @param settings Canvasに関する設定値を持つクラス
     */
    public HostCanvasProfile(final HostCanvasSettings settings) {
        addApi(mDrawImageApi);
        addApi(mDeleteImageApi);
        mSettings = settings;
    }

    /**
     * Send Image.
     * @param data binary
     * @param response response message
     * @param enumMode image mode
     * @param mimeType MIME-Type
     * @param x position
     * @param y position
     */
    private void sendImage(byte[] data, Intent response, CanvasDrawImageObject.Mode enumMode, String mimeType, double x, double y) {
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
        CanvasDrawImageObject drawObj = new CanvasDrawImageObject(uri, enumMode, mimeType, x, y, false);
        HostDeviceService service = ((HostDeviceService) getContext());

        if (getApp().getShowActivityAndData(getTopOfActivity().getName()) != null) {
            Intent intent = new Intent(getDrawCanvasActionName());
            drawObj.setValueToIntent(intent);
            ((HostDeviceApplication) service.getApplication()).putShowActivityAndData(getTopOfActivity().getName(), intent);
            LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
        } else {
            Intent intent = new Intent();
            intent.setClass(getContext(), getTopOfActivity());
            intent.setFlags(getActivityFlag());
            drawObj.setValueToIntent(intent);
            ((HostDeviceApplication) service.getApplication()).putShowActivityAndData(getTopOfActivity().getName(), intent);
            getContext().startActivity(intent);
        }

        setResult(response, DConnectMessage.RESULT_OK);
    }

    /**
     * 画像の保存
     * @param data binary
     * @param mimeType MIME-Type
     * @return URI
     * @throws IOException
     * @throws OutOfMemoryError
     */
    protected String writeForImage(final byte[] data, final String mimeType) throws IOException, OutOfMemoryError {
        File file = getContext().getCacheDir();
        FileOutputStream out = null;
        checkAndRemove(file);
        String suffix = ".tmp";
        if (mimeType.equals("text/html")) {  //htmlファイルの時は拡張子を変える
            suffix = ".html";
        }
        File dstFile = File.createTempFile(getTempFileName() + System.currentTimeMillis(), suffix, file);
        try {
            out = new FileOutputStream(dstFile);
            out.write(data);
            out.close();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return dstFile.getAbsolutePath();
    }

    /**
     * ファイルをチェックして、中身を削除する.
     *
     * @param file 削除するファイル
     */
    private void checkAndRemove(@NonNull final File file) {
        if (file.isDirectory()) {
            for (File childFile : file.listFiles()) {
                checkAndRemove(childFile);
            }
        } else if (file.isFile() && file.getName().startsWith(getTempFileName())) {
            long modified = file.lastModified();
            if (System.currentTimeMillis() - modified > mExpire) {
                file.delete();
            }
        }
    }
    protected HostDeviceApplication getApp() {
        return (HostDeviceApplication) getContext().getApplicationContext();
    }
    protected boolean isCanvasMultipleShowFlag() {
        return mSettings.isCanvasContinuousAccessForHost()
                && ((HostDeviceApplication) ((HostDeviceService) getContext()).getApplication())
                            .getActivityResumePauseFlag(getTopOfActivity().getName());
    }

    protected String getTempFileName() {
        return CANVAS_PREFIX;
    }

    protected boolean isActivityNeverShow() {
        return mSettings.isCanvasActivityNeverShowFlag();
    }

    protected Class<? extends Activity> getTopOfActivity() {
        return CanvasProfileActivity.class;
    }

    protected String getDrawCanvasActionName() {
        return CanvasDrawImageObject.ACTION_DRAW_CANVAS;
    }

    protected String getDeleteCanvasActionName() {
        return CanvasDrawImageObject.ACTION_DELETE_CANVAS;
    }

    protected int getActivityFlag() {
        return Intent.FLAG_ACTIVITY_NEW_TASK;
    }
}
