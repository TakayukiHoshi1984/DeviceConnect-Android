/*
 HostCanvasProfile.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */

package org.deviceconnect.android.deviceplugin.host.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;

import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasController;
import org.deviceconnect.android.deviceplugin.host.canvas.dialog.DownloadMessageDialogFragment;
import org.deviceconnect.android.deviceplugin.host.canvas.dialog.ErrorDialogFragment;
import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawImageObject;
import org.deviceconnect.android.deviceplugin.host.canvas.dialog.ContinuousAccessConfirmDialogFragment;
import org.deviceconnect.android.deviceplugin.host.canvas.dialog.ExternalNetworkAccessDialogFragment;

import static org.deviceconnect.android.deviceplugin.host.canvas.dialog.ContinuousAccessConfirmDialogFragment.MULTIPLE_SHOW_CANVAS_WARNING_TAG;
import static org.deviceconnect.android.deviceplugin.host.canvas.dialog.ExternalNetworkAccessDialogFragment.EXTERNAL_SHOW_CANVAS_WARNING_TAG;
import static org.deviceconnect.android.deviceplugin.host.canvas.view.CanvasImageView.DIALOG_TYPE_NOT_FOUND;
import static org.deviceconnect.android.deviceplugin.host.canvas.view.CanvasImageView.DIALOG_TYPE_OOM;

/**
 * Canvasプロファイルから受け取った画像・HTML・動画・MJPEGなどを表示する.
 *
 * @author NTT DOCOMO, INC.
 */
public class CanvasProfileActivity extends Activity implements CanvasController.Presenter {


    /**
     * Canvasが連続で起動されたかを判定する時間(ms).
     */
    public static final int DELAY_MILLIS = 10000;

    /**
     * CanvasAPIの設定値を保持する.
     */
    private HostCanvasSettings mSettings;
    /**
     * Canvasの操作を行う.
     */
    private CanvasController mController;
    private WebView mCanvasWebView;
    /**
     * リソースダウンロード中のProgressDialog.
     */
    private DownloadMessageDialogFragment mDialog;


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
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

        // Canvasの設定項目の読み込み
        mSettings = new HostCanvasSettings(this);
        // 閉じるボタンの初期化
        Button btn = findViewById(R.id.buttonClose);
        btn.setOnClickListener((v) -> {
            finish();
        });
        mCanvasWebView = findViewById(R.id.canvasProfileWebView);
        // 受け取ったリクエストパラメータの設定
        Intent intent  = getIntent();
        CanvasDrawImageObject drawImageObject = CanvasDrawImageObject.create(intent);
        mController = new CanvasController(this, mCanvasWebView, findViewById(R.id.canvasProfileView), findViewById(R.id.canvasProfileVideoView),
                this, drawImageObject, mSettings, CanvasDrawImageObject.ACTION_DRAW_CANVAS, CanvasDrawImageObject.ACTION_DELETE_CANVAS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mController.registerReceiver();
        mController.checkForAtack(false);
    }

    @Override
    protected void onPause() {
        mController.unregisterReceiver();
        super.onPause();
    }
    @Override
    protected void onDestroy() {
        mSettings.setCanvasMultipleShowFlag(true);
        // Canvasが閉じられて10秒間以内に再び起動されたら、悪意のあるスクリプトが実行されたかを確認する。
        new Handler().postDelayed(() -> {
            // 10秒後に連続起動フラグを無効にする
            mSettings.setCanvasMultipleShowFlag(false);
        }, DELAY_MILLIS);
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mCanvasWebView.canGoBack()) {
            mCanvasWebView.goBack();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void showNotFoundDrawImageDialog() {
        ErrorDialogFragment oomDialog = ErrorDialogFragment.create(DIALOG_TYPE_NOT_FOUND,
                getString(R.string.host_canvas_error_title),
                getString(R.string.host_canvas_error_not_found_message),
                getString(R.string.host_ok),  new ErrorDialogFragment.OnWarningDialogListener() {

                    @Override
                    public void onOK() {
                        finishActivity();
                    }

                    @Override
                    public void onCancel() {

                    }
                });
        oomDialog.show(getFragmentManager(), DIALOG_TYPE_NOT_FOUND);
    }

    @Override
    public void showDownloadDialog() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
        mDialog = new DownloadMessageDialogFragment();
        mDialog.show(getFragmentManager(), "dialog");
    }

    @Override
    public void dismissDownloadDialog() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }

    @Override
    public void showOutOfMemoryDialog() {
        ErrorDialogFragment oomDialog = ErrorDialogFragment.create(DIALOG_TYPE_OOM,
                getString(R.string.host_canvas_error_title),
                getString(R.string.host_canvas_error_oom_message),
                getString(R.string.host_ok),  new ErrorDialogFragment.OnWarningDialogListener() {

                    @Override
                    public void onOK() {
                        finishActivity();
                    }

                    @Override
                    public void onCancel() {

                    }
                });
        oomDialog.show(getFragmentManager(), DIALOG_TYPE_OOM);
    }

    @Override
    public void showExternalNetworkAccessDialog(CanvasController.PresenterCallback callback) {
        // 多重起動が行われたかをチェック
        ExternalNetworkAccessDialogFragment
                .createDialog(this, new ErrorDialogFragment.OnWarningDialogListener() {
                    @Override
                    public void onOK() {
                        if (callback != null) {
                            callback.onOKCallback();
                        }
                    }

                    @Override
                    public void onCancel() {
                        if (callback != null) {
                            callback.onCancelCallback();
                        }
                    }
                })
                .show(getFragmentManager(), EXTERNAL_SHOW_CANVAS_WARNING_TAG);
    }

    @Override
    public void showContinuousAccessConfirmDilaog(CanvasController.PresenterCallback callback) {
        // 多重起動が行われたかをチェック
        ContinuousAccessConfirmDialogFragment
                .createDialog(this, new ErrorDialogFragment.OnWarningDialogListener() {
                    @Override
                    public void onOK() {
                        if (callback != null) {
                            callback.onOKCallback();
                        }
                    }

                    @Override
                    public void onCancel() {
                        if (callback != null) {
                            callback.onCancelCallback();
                        }
                    }
                })
                .show(getFragmentManager(), MULTIPLE_SHOW_CANVAS_WARNING_TAG);
    }

    @Override
    public void finishActivity() {
        finish();
    }

}
