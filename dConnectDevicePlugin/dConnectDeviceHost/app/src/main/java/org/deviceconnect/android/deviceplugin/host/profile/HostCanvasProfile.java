/*
 HostCanvasProfile.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */

package org.deviceconnect.android.deviceplugin.host.profile;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

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
    private static final long DEFAULT_EXPIRE = 1000 * 60 * 5;

    /** Canvasプロファイルのファイル名プレフィックス。 */
    private static final String CANVAS_PREFIX = "host_canvas";

    /** ファイルが生存できる有効時間. */
    private long mExpire = DEFAULT_EXPIRE;

    /** Imageを表示するサービス. */
    private ExecutorService mImageService = Executors.newSingleThreadExecutor();

    /** Canvasの設定. */
    private HostCanvasSettings mSettings;

    private final DConnectApi mDrawImageApi = new PostApi() {

        @Override
        public String getAttribute() {
            return ATTRIBUTE_DRAW_IMAGE;
        }

        @Override
        public boolean onRequest(final Intent request, final Intent response) {
            if (!mSettings.isCanvasActivityNeverShowFlag()) {
                MessageUtils.setIllegalServerStateError(response,
                        "The function of Canvas API is turned off.\n" +
                                "Please cancel on the setting screen of Host plug-in.");
                return true;
            }
            String className = getClassnameOfTopActivity();
            // 連続起動のダイアログが表示中かどうか
            if (mSettings.isCanvasMultipleShowFlag()
                    && CanvasProfileActivity.class.getName().equals(className)) {
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
            if (mimeType == null) {
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
            String className = getClassnameOfTopActivity();
            if (CanvasProfileActivity.class.getName().equals(className)) {
                Intent intent = new Intent(CanvasDrawImageObject.ACTION_DELETE_CANVAS);
                LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
                setResult(response, DConnectMessage.RESULT_OK);
            } else {
                MessageUtils.setIllegalDeviceStateError(response, "canvas not display");
            }

            return true;
        }
    };

    /**
     * コンストラクタ.
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
    private void drawImage(Intent response, String uri, CanvasDrawImageObject.Mode enumMode, String mimeType, double x, double y) {
        CanvasDrawImageObject drawObj = new CanvasDrawImageObject(uri, enumMode, mimeType, x, y);

        String className = getClassnameOfTopActivity();
        if (CanvasProfileActivity.class.getName().equals(className)) {
            Intent intent = new Intent(CanvasDrawImageObject.ACTION_DRAW_CANVAS);
            drawObj.setValueToIntent(intent);
            LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
        } else {
            Intent intent = new Intent();
            intent.setClass(getContext(), CanvasProfileActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            drawObj.setValueToIntent(intent);
            getContext().startActivity(intent);
        }

        setResult(response, DConnectMessage.RESULT_OK);
    }

    /**
     * 画面の一番上にでているActivityのクラス名を取得.
     *
     * @return クラス名
     */
    private String getClassnameOfTopActivity() {
        ActivityManager activityMgr = (ActivityManager) getContext().getSystemService(Service.ACTIVITY_SERVICE);
        return activityMgr.getRunningTasks(1).get(0).topActivity.getClassName();
    }

    /**
     * 画像の保存
     * @param data binary
     * @param mimeType MIME-Type
     * @return URI
     * @throws IOException
     * @throws OutOfMemoryError
     */
    private String writeForImage(final byte[] data, final String mimeType) throws IOException, OutOfMemoryError {
        File file = getContext().getCacheDir();
        FileOutputStream out = null;
        checkAndRemove(file);
        String suffix = ".tmp";
        if (mimeType.equals("text/html")) {  //htmlファイルの時は拡張子を変える
            suffix = ".html";
        }
        File dstFile = File.createTempFile(CANVAS_PREFIX + System.currentTimeMillis(), suffix, file);
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
        } else if (file.isFile() && file.getName().startsWith(CANVAS_PREFIX)) {
            long modified = file.lastModified();
            if (System.currentTimeMillis() - modified > mExpire) {
                file.delete();
            }
        }
    }
}
