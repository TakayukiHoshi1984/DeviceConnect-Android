/*
 CapabilityUtil.java
 Copyright (c) 2016 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.recorder.util;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import org.deviceconnect.android.activity.IntentHandlerActivity;
import org.deviceconnect.android.activity.PermissionUtility;
import org.deviceconnect.android.deviceplugin.host.R;

import java.util.List;

public final class CapabilityUtil {
    private CapabilityUtil() {
    }

    public static void requestPermissions(final Context context, final Handler handler, final PermissionUtility.PermissionRequestCallback callback) {
        PermissionUtility.requestPermissions(context, handler, new String[]{Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, callback);
    }

    public static void requestPermissions(final Context context, final PermissionUtility.PermissionRequestCallback callback) {
        requestPermissions(context, new Handler(Looper.getMainLooper()), callback);
    }

    public static void checkCapability(final Context context, final Handler handler, final Callback callback) {
        final ResultReceiver cameraCapabilityCallback = new ResultReceiver(handler) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                try {
                    if (resultCode == Activity.RESULT_OK) {
                        callback.onSuccess();
                    } else {
                        callback.onFail();
                    }
                } catch (Throwable throwable) {
                    callback.onFail();
                }
            }
        };
        final ResultReceiver overlayDrawingCapabilityCallback = new ResultReceiver(handler) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                try {
                    if (resultCode == Activity.RESULT_OK) {
                        CapabilityUtil.checkCameraCapability(context, cameraCapabilityCallback);
                    } else {
                        callback.onFail();
                    }
                } catch (Throwable throwable) {
                    callback.onFail();
                }
            }
        };
        CapabilityUtil.checkOverlayDrawingCapability(context, handler, overlayDrawingCapabilityCallback);
    }

    /**
     * オーバーレイ表示のパーミッションを確認します.
     *
     * @param context コンテキスト
     * @param handler ハンドラー
     * @param resultReceiver 確認を受けるレシーバ
     */
    @TargetApi(23)
    private static void checkOverlayDrawingCapability(final Context context, final Handler handler, final ResultReceiver resultReceiver) {
        if (Settings.canDrawOverlays(context)) {
            resultReceiver.send(Activity.RESULT_OK, null);
        } else {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.getPackageName()));
            IntentHandlerActivity.startActivityForResult(context, intent, new ResultReceiver(handler) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    if (Settings.canDrawOverlays(context)) {
                        resultReceiver.send(Activity.RESULT_OK, null);
                    } else {
                        resultReceiver.send(Activity.RESULT_CANCELED, null);
                    }
                }
            });
        }
    }

    /**
     * カメラのパーミッションを確認します.
     *
     * @param context コンテキスト
     * @param resultReceiver 確認を受けるレシーバ
     */
    private static void checkCameraCapability(final Context context, final ResultReceiver resultReceiver) {
        PermissionUtility.requestPermissions(context, new Handler(), new String[]{Manifest.permission.CAMERA},
                new PermissionUtility.PermissionRequestCallback() {
                    @Override
                    public void onSuccess() {
                        resultReceiver.send(Activity.RESULT_OK, null);
                    }

                    @Override
                    public void onFail(final String deniedPermission) {
                        resultReceiver.send(Activity.RESULT_CANCELED, null);
                    }
                });
    }

    public static void checkMutiWindowCapability(final Context context, final Handler handler, final Callback callback) {
        final ResultReceiver overlayDrawingCapabilityCallback = new ResultReceiver(handler) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                try {
                    if (resultCode == Activity.RESULT_OK) {
                        callback.onSuccess();
                    } else {
                        callback.onFail();
                    }
                } catch (Throwable throwable) {
                    callback.onFail();
                }
            }
        };
        CapabilityUtil.checkAvailabilityServiceCapability(context, handler, overlayDrawingCapabilityCallback);
    }
    /**
     * AvailabilityServiceのパーミッションを確認します.
     *
     * @param context コンテキスト
     * @param handler ハンドラー
     * @param resultReceiver 確認を受けるレシーバ
     */
    private static void checkAvailabilityServiceCapability(final Context context, final Handler handler, final ResultReceiver resultReceiver) {
        if (isAccessibilityEnabled(context)) {
            resultReceiver.send(Activity.RESULT_OK, null);
        } else {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            handler.post(() -> {
                Toast.makeText(context, R.string.mutiwindow_accessibility_setting_message, Toast.LENGTH_LONG).show();
            });
            IntentHandlerActivity.startActivityForResult(context, intent, new ResultReceiver(handler) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    if (isAccessibilityEnabled(context)) {
                        resultReceiver.send(Activity.RESULT_OK, null);
                    } else {
                        resultReceiver.send(Activity.RESULT_CANCELED, null);
                    }
                }
            });
        }
    }

    /**
     * このプラグインに関して、ユーザ補助が許可されているかどうかを返す.
     * @param context コンテキスト
     * @return true:許可されている false:許可されていない
     */
    private static boolean isAccessibilityEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context
                .getSystemService(Context.ACCESSIBILITY_SERVICE);

        List<AccessibilityServiceInfo> runningServices = am
                .getEnabledAccessibilityServiceList(AccessibilityEvent.TYPES_ALL_MASK);
        for (AccessibilityServiceInfo service : runningServices) {
            if (service.getId().contains("org.deviceconnect.android.manager/org.deviceconnect.android.deviceplugin.host.mutiwindow.MutiWindowAccessibilityService")) {
                return true;
            }
        }

        return false;
    }
    /**
     * Overlayの表示結果を通知するコールバック.
     */
    public interface Callback {
        /**
         * 表示できたことを通知します.
         */
        void onSuccess();

        /**
         * 表示できなかったことを通知します.
         */
        void onFail();
    }
}
