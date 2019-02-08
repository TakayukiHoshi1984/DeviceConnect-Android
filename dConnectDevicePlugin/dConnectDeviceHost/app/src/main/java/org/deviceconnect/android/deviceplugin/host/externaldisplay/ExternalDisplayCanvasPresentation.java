/*
 ExternalDisplayCanvasPresentation.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.externaldisplay;

import android.app.Presentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasController;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawImageObject;
import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;
import org.deviceconnect.message.DConnectMessage;

import static org.deviceconnect.android.deviceplugin.host.activity.CanvasProfileActivity.DELAY_MILLIS;
import static org.deviceconnect.android.deviceplugin.host.externaldisplay.CanvasDialogActivity.ACTION_DISMISS_DOWNLAOD_DIALOG;

/**
 * 外部ディスプレイ用のCanvasの処理を行う.
 * @author NTT DOCOMO, INC.
 */
public class ExternalDisplayCanvasPresentation extends Presentation implements CanvasController.Presenter {
    /**
     * 外部リソースアクセスダイアログの表示命令のレスポンスを受け取るためのAction名.
     */
    public static final String ACTION_EXTERNAL_NETWORK_ACCESS_RECEIVER = "org.deviceconnect.android.deviceplugin.host.canvas.external.network.access.RECEIVER";
    /**
     * 連続アクセス確認ダイアログの表示命令のレスポンスを受け取るためのAction名.
     */
    public static final String ACTION_CONTINUOUS_ACCESS_CONFIRM_RECEIVER = "org.deviceconnect.android.deviceplugin.host.canvas.continuous.access.confirm.RECEIVER";
    /**
     * Canvas上でエラーが発生した時にPresentationを閉じるためのAction名.
     */
    public static final String ACTION_ERROR_RECEIVER = "org.deviceconnect.android.deviceplugin.host.canvas.external.display.error.RECEIVER";

    private CanvasDrawImageObject mDrawImageObject;
    /**
     * CanvasAPIの設定値を保持する.
     */
    private HostCanvasSettings mSettings;
    /**
     * Canvasの操作を行う.
     */
    private CanvasController mController;

    /**
     * コンストラクタ.
     * @param outerContext Context
     * @param display Display
     * @param drawObject 描画するデータの情報を持つオビジェクト
     */
    public ExternalDisplayCanvasPresentation(final Context outerContext,
                                             final Display display,
                                             final CanvasDrawImageObject drawObject) {
        super(outerContext, display);
        mDrawImageObject = drawObject;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_canvas_profile);
        // ステータスバーを消す
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
        findViewById(R.id.buttonClose).setVisibility(View.GONE);
        mSettings = new HostCanvasSettings(getContext());
        mController = new CanvasController(getContext(),
                findViewById(R.id.canvasProfileWebView),
                findViewById(R.id.canvasProfileView),
                findViewById(R.id.canvasProfileVideoView),
                this,
                mDrawImageObject,
                mSettings,
                CanvasDrawImageObject.ACTION_EXTERNAL_DISPLAY_DRAW_CANVAS,
                CanvasDrawImageObject.ACTION_EXTERNAL_DISPLAY_DELETE_CANVAS);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mController.registerReceiver();
        mController.checkForAtack(false);
    }
    @Override
    protected void onStop() {
        mController.unregisterReceiver();
        mSettings.setCanvasMultipleShowFlag(true);
        // Canvasが閉じられて10秒間以内に再び起動されたら、悪意のあるスクリプトが実行されたかを確認する。
        new Handler().postDelayed(() -> {
            // 10秒後に連続起動フラグを無効にする
            Log.d("ABC", "onStop");

            mSettings.setCanvasMultipleShowFlag(false);
        }, DELAY_MILLIS);
        super.onStop();

    }

    @Override
    public void finishActivity() {
        dismiss();
    }

    @Override
    public void showNotFoundDrawImageDialog() {
        setDismissPresentationReceiver();
        showDialogActivity(CanvasDialogActivity.CanvasDialogType.NotFoundResource);
    }

    @Override
    public void showDownloadDialog() {
        showDialogActivity(CanvasDialogActivity.CanvasDialogType.ShowDonwloading);
    }

    @Override
    public void dismissDownloadDialog() {
        Intent response = new Intent(ACTION_DISMISS_DOWNLAOD_DIALOG);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(response);
    }

    @Override
    public void showOutOfMemoryDialog() {
        setDismissPresentationReceiver();
        showDialogActivity(CanvasDialogActivity.CanvasDialogType.OutOfMemory);
    }

    @Override
    public void showExternalNetworkAccessDialog(final CanvasController.PresenterCallback callback) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int result = intent.getIntExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                if (DConnectMessage.RESULT_OK == result) {
                    if (callback != null) {
                        callback.onOKCallback();
                    }
                } else {
                    if (callback != null) {
                        callback.onCancelCallback();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_EXTERNAL_NETWORK_ACCESS_RECEIVER);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver, filter);
        showDialogActivity(CanvasDialogActivity.CanvasDialogType.ExternalNetworkAccess);
    }

    @Override
    public void showContinuousAccessConfirmDilaog(CanvasController.PresenterCallback callback) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int result = intent.getIntExtra(DConnectMessage.EXTRA_RESULT, DConnectMessage.RESULT_ERROR);
                if (DConnectMessage.RESULT_OK == result) {
                    if (callback != null) {
                        callback.onOKCallback();
                    }
                } else {
                    if (callback != null) {
                        callback.onCancelCallback();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CONTINUOUS_ACCESS_CONFIRM_RECEIVER);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver, filter);
        showDialogActivity(CanvasDialogActivity.CanvasDialogType.ContinuousAccessConfirm);
    }

    /**
     * Presentationに投影されているCanvasを更新する.
     * @param drawImageObject 更新するデータを持つオブジェクト
     */
    public void updateCanvasDisplay(final CanvasDrawImageObject drawImageObject) {
        mController.updateCanvas(drawImageObject);
    }

    /**
     * Canvasのエラーに合わせたダイアログを表示するためのActivityを起動する.
     * @param type Canvasのエラータイプ
     */
    private void showDialogActivity(final CanvasDialogActivity.CanvasDialogType type) {
        Intent intent = new Intent();
        intent.setClass(getContext(), CanvasDialogActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(CanvasDialogActivity.CANVAS_DIALOG_TYPE, type.name());
        getContext().startActivity(intent);
    }

    /**
     * ダイアログを表示しているActivityから、Canvasを非表示にするためのタイミングを受け取るレシーバー.
     */
    private void setDismissPresentationReceiver() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                dismiss();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_ERROR_RECEIVER);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver, filter);
    }

}