/*
 CanvasDialogActivity.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.externaldisplay;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Window;

import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.canvas.dialog.ContinuousAccessConfirmDialogFragment;
import org.deviceconnect.android.deviceplugin.host.canvas.dialog.DownloadMessageDialogFragment;
import org.deviceconnect.android.deviceplugin.host.canvas.dialog.ErrorDialogFragment;
import org.deviceconnect.android.deviceplugin.host.canvas.dialog.ExternalNetworkAccessDialogFragment;
import org.deviceconnect.message.DConnectMessage;

import static org.deviceconnect.android.deviceplugin.host.canvas.dialog.ContinuousAccessConfirmDialogFragment.MULTIPLE_SHOW_CANVAS_WARNING_TAG;
import static org.deviceconnect.android.deviceplugin.host.canvas.view.CanvasImageView.DIALOG_TYPE_NOT_FOUND;
import static org.deviceconnect.android.deviceplugin.host.canvas.view.CanvasImageView.DIALOG_TYPE_OOM;
import static org.deviceconnect.android.deviceplugin.host.externaldisplay.ExternalDisplayCanvasPresentation.ACTION_CONTINUOUS_ACCESS_CONFIRM_RECEIVER;
import static org.deviceconnect.android.deviceplugin.host.externaldisplay.ExternalDisplayCanvasPresentation.ACTION_ERROR_RECEIVER;
import static org.deviceconnect.android.deviceplugin.host.externaldisplay.ExternalDisplayCanvasPresentation.ACTION_EXTERNAL_NETWORK_ACCESS_RECEIVER;

/**
 * Activity以外からCanvasに関するダイアログを表示するためのActivity.
 * @author NTT DOCOMO, INC.
 */
public class CanvasDialogActivity extends Activity {
    /** Canvasのダイアログタイプのキー値. */
    public static final String CANVAS_DIALOG_TYPE = "canvas_dialog_type";
    /** Dismiss Download action名 .*/
    public static final String ACTION_DISMISS_DOWNLAOD_DIALOG = "org.deviceconnect.android.deviceplugin.external.display.download.dismiss.DIALOG";
    /**
     * Canvasで使用するダイアログのタイプ.
     */
    public enum CanvasDialogType {
        /**
         * 指定されていないダイアログタイプ.
         */
        DialogError,
        /**
         * リソースの取得時にOut Of Memoryが発生.
         */
        OutOfMemory,
        /**
         * リソースの取得に失敗.
         */
        NotFoundResource,
        /**
         * リソースダウンロードダイアログの表示.
         */
        ShowDonwloading,
        /**
         * リソースダウンロードダイアログの非表示.
         */
        DismissDownload,
        /**
         * 外部ネットワークアクセス警告ダイアログ.
         */
        ExternalNetworkAccess,
        /**
         * 連続アクセス警告ダイアログ.
         */
        ContinuousAccessConfirm,
    }
    /**
     * リソースダウンロード中のProgressDialog.
     */
    private DownloadMessageDialogFragment mDialog;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Intent intent  = getIntent();
        if (intent == null) {
            finish();
        }
        String dialogType = intent.getStringExtra(CANVAS_DIALOG_TYPE);
        showDialogs(CanvasDialogType.valueOf(dialogType));
    }

    /**
     * タイプに合わせたダイアログを表示する.
     * @param dialogType ダイアログタイプ
     */
    private void showDialogs(final CanvasDialogType dialogType) {
        switch(dialogType) {
            case OutOfMemory:
                ErrorDialogFragment oomDialog = ErrorDialogFragment.create(DIALOG_TYPE_OOM,
                        getString(R.string.host_canvas_error_title),
                        getString(R.string.host_canvas_error_oom_message),
                        getString(R.string.host_ok),  new ErrorDialogFragment.OnWarningDialogListener() {

                            @Override
                            public void onOK() {
                                sendResponse(ACTION_ERROR_RECEIVER, DConnectMessage.RESULT_OK);
                                finish();
                            }

                            @Override
                            public void onCancel() {

                            }
                        });
                oomDialog.show(getFragmentManager(), DIALOG_TYPE_OOM);
                break;
            case NotFoundResource:
                ErrorDialogFragment nfDialog = ErrorDialogFragment.create(DIALOG_TYPE_NOT_FOUND,
                        getString(R.string.host_canvas_error_title),
                        getString(R.string.host_canvas_error_not_found_message),
                        getString(R.string.host_ok),  new ErrorDialogFragment.OnWarningDialogListener() {

                            @Override
                            public void onOK() {
                                sendResponse(ACTION_ERROR_RECEIVER, DConnectMessage.RESULT_OK);
                                finish();
                            }

                            @Override
                            public void onCancel() {

                            }
                        });
                nfDialog.show(getFragmentManager(), DIALOG_TYPE_NOT_FOUND);
                break;
            case ShowDonwloading:
                if (mDialog != null) {
                    mDialog.dismiss();
                }
                mDialog = new DownloadMessageDialogFragment();
                mDialog.show(getFragmentManager(), "dialog");
                BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (mDialog != null) {
                            mDialog.dismiss();
                            mDialog = null;
                        }
                        LocalBroadcastManager.getInstance(CanvasDialogActivity.this).unregisterReceiver(this);
                        finish();
                    }
                };
                IntentFilter filter = new IntentFilter();
                filter.addAction(ACTION_DISMISS_DOWNLAOD_DIALOG);
                LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
                break;
            case DismissDownload:
                break;
            case ExternalNetworkAccess:
                ExternalNetworkAccessDialogFragment
                        .createDialog(this, new ErrorDialogFragment.OnWarningDialogListener() {
                            @Override
                            public void onOK() {
                                sendResponse(ACTION_EXTERNAL_NETWORK_ACCESS_RECEIVER, DConnectMessage.RESULT_OK);
                                finish();
                            }

                            @Override
                            public void onCancel() {
                                sendResponse(ACTION_EXTERNAL_NETWORK_ACCESS_RECEIVER, DConnectMessage.RESULT_ERROR);
                                finish();
                            }
                        })
                        .show(getFragmentManager(), MULTIPLE_SHOW_CANVAS_WARNING_TAG);
                break;
            case ContinuousAccessConfirm:
                ContinuousAccessConfirmDialogFragment
                        .createDialog(this, new ErrorDialogFragment.OnWarningDialogListener() {
                            @Override
                            public void onOK() {
                                sendResponse(ACTION_CONTINUOUS_ACCESS_CONFIRM_RECEIVER, DConnectMessage.RESULT_OK);
                                finish();
                            }

                            @Override
                            public void onCancel() {
                                sendResponse(ACTION_CONTINUOUS_ACCESS_CONFIRM_RECEIVER, DConnectMessage.RESULT_ERROR);
                                finish();
                            }
                        })
                        .show(getFragmentManager(), MULTIPLE_SHOW_CANVAS_WARNING_TAG);
                break;
            default:
                finish();  //想定外のダイアログタイプの場合は終了する
        }
    }

    /**
     * レスポンスを返す.
     * @param action レスポンスを返す場所のアクション名
     * @param result 応答結果
     */
    private void sendResponse(final String action, final int result) {
        Intent response = new Intent(action);
        response.putExtra(DConnectMessage.EXTRA_RESULT, result);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(response);
    }
}
