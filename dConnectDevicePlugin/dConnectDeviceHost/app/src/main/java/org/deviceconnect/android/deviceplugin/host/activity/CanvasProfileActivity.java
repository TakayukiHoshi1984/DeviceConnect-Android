/*
 HostCanvasProfile.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */

package org.deviceconnect.android.deviceplugin.host.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.canvas.ExternalAccessCheckUtils;
import org.deviceconnect.android.deviceplugin.host.canvas.dialog.ErrorDialogFragment;
import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawImageObject;
import org.deviceconnect.android.deviceplugin.host.canvas.dialog.ExternalNetworkWarningDialogFragment;
import org.deviceconnect.android.deviceplugin.host.canvas.dialog.MultipleShowWarningDialogFragment;
import org.deviceconnect.android.deviceplugin.host.canvas.view.CanvasImageView;
import org.deviceconnect.android.deviceplugin.host.canvas.view.CanvasVideoView;
import org.deviceconnect.android.deviceplugin.host.canvas.view.CanvasWebView;

import static org.deviceconnect.android.deviceplugin.host.canvas.dialog.ExternalNetworkWarningDialogFragment.EXTERNAL_SHOW_CANVAS_WARNING_TAG;
import static org.deviceconnect.android.deviceplugin.host.canvas.dialog.MultipleShowWarningDialogFragment.MULTIPLE_SHOW_CANVAS_WARNING_TAG;

/**
 * Canvasプロファイルから受け取った画像・HTML・動画・MJPEGなどを表示する.
 *
 * @author NTT DOCOMO, INC.
 */
public class CanvasProfileActivity extends Activity  {

    /**
     * 受け取ったパラメータを保持するキー値.
     */
    private static final String PARAM_INTENT = "param_intent";

    /**
     * Canvasが連続で起動されたかを判定する時間(ms).
     */
    private static final int DELAY_MILLIS = 10000;

    /**
     * Canvas情報を持つIntent.
     */
    private Intent mIntent;
    /**
     * 表示用オブジェクト.
     */
    private CanvasDrawImageObject mDrawImageObject;
    /**
     * HTML・MJPEGを表示する.
     * バックキーで前のページに戻らせるためにフィールド変数とする.
     */
    private CanvasWebView mCanvasWebView;
    /**
     * CanvasAPIの設定値を保持する.
     */
    private HostCanvasSettings mSettings;
    /**
     * 一度外部アクセスダイアログを出したかどうかのフラグ.
     * 二度目はダイアログを出さないようにする.
     */
    private boolean mExternalAccessFlag = false;

    /**
     * Canvasが表示中に処理を受け付けるBroadcastReceiver.
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            String action = intent.getAction();
            if (CanvasDrawImageObject.ACTION_DRAW_CANVAS.equals(action)) {
                setDrawingArgument(intent);
                mDrawImageObject = CanvasDrawImageObject.create(intent);
                // 更新データが外部リソースかどうかを確認する
                if (mSettings.isCanvasActivityAccessExternalNetworkFlag()
                        && ExternalAccessCheckUtils.isExternalAccessResource(CanvasProfileActivity.this, mDrawImageObject.getData())) {
                    // 外部リソースが指定されているかを確認
                    ExternalNetworkWarningDialogFragment.createDialog(CanvasProfileActivity.this,
                                                    new ErrorDialogFragment.OnWarningDialogListener() {
                        @Override
                        public void onOK() {
                            mExternalAccessFlag = true;
                            // 多重起動が行われたかをチェック
                            if (mSettings.isCanvasMultipleShowFlag()) {
                                MultipleShowWarningDialogFragment
                                        .createDialog(CanvasProfileActivity.this, new ErrorDialogFragment.OnWarningDialogListener() {
                                            @Override
                                            public void onOK() {
                                                showCanvasView();
                                            }

                                            @Override
                                            public void onCancel() {

                                            }
                                        })
                                        .show(getFragmentManager(), MULTIPLE_SHOW_CANVAS_WARNING_TAG);
                            } else {
                                showCanvasView();
                            }
                        }

                        @Override
                        public void onCancel() {
                            finish();
                        }
                    }).show(getFragmentManager(), EXTERNAL_SHOW_CANVAS_WARNING_TAG);
                } else {
                    showCanvasView();
                }
            } else if (CanvasDrawImageObject.ACTION_DELETE_CANVAS.equals(action)) {
                finish();
            }
        }
    };


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

        // 受け取ったリクエストパラメータの設定
        Intent intent = null;
        if (savedInstanceState != null) {
            intent = (Intent) savedInstanceState.get(PARAM_INTENT);
        }
        if (intent == null) {
            intent = getIntent();
        }
        setDrawingArgument(intent);
        mDrawImageObject = CanvasDrawImageObject.create(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Intent-Fileterの設定
        IntentFilter filter = new IntentFilter();
        filter.addAction(CanvasDrawImageObject.ACTION_DRAW_CANVAS);
        filter.addAction(CanvasDrawImageObject.ACTION_DELETE_CANVAS);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filter);
        // 攻撃されているかどうかの確認
        checkForAtack();
    }

    /**
     * 異常な方法でCanvasが起動されたか、おかしな外部サイトにアクセスしていないかをチェック.
     */
    private void checkForAtack() {
        String uri = mDrawImageObject.getData();
        if (mSettings.isCanvasMultipleShowFlag()) {
            // 多重起動が行われたかをチェック
            MultipleShowWarningDialogFragment
                    .createDialog(this, new ErrorDialogFragment.OnWarningDialogListener() {
                        @Override
                        public void onOK() {
                            showCanvasView();
                        }

                        @Override
                        public void onCancel() {

                        }
                    })
                    .show(getFragmentManager(), MULTIPLE_SHOW_CANVAS_WARNING_TAG);
        } else if (mSettings.isCanvasActivityAccessExternalNetworkFlag()
                && ExternalAccessCheckUtils.isExternalAccessResource(this, uri)) {
            // 外部リソースが指定されているかを確認
            ExternalNetworkWarningDialogFragment.createDialog(this, new ErrorDialogFragment.OnWarningDialogListener() {
                @Override
                public void onOK() {
                    mExternalAccessFlag = true;
                    // 多重起動が行われたかをチェック
                    if (mSettings.isCanvasMultipleShowFlag()) {
                        MultipleShowWarningDialogFragment
                                .createDialog(CanvasProfileActivity.this, new ErrorDialogFragment.OnWarningDialogListener() {
                                    @Override
                                    public void onOK() {
                                        showCanvasView();
                                    }

                                    @Override
                                    public void onCancel() {

                                    }
                                })
                                .show(getFragmentManager(), MULTIPLE_SHOW_CANVAS_WARNING_TAG);
                    } else {
                        showCanvasView();
                    }
                }

                @Override
                public void onCancel() {
                    finish();
                }
            }).show(getFragmentManager(), EXTERNAL_SHOW_CANVAS_WARNING_TAG);
        } else {
            showCanvasView();
        }
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        super.onPause();
    }
    @Override
    protected void onDestroy() {
        final HostCanvasSettings settings = new HostCanvasSettings(this);
        settings.setCanvasMultipleShowFlag(true);
        // Canvasが閉じられて10秒間以内に再び起動されたら、悪意のあるスクリプトが実行されたかを確認する。
        new Handler().postDelayed(() -> {
            // 10秒後に連続起動フラグを無効にする
            settings.setCanvasMultipleShowFlag(false);
        }, DELAY_MILLIS);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        if (mIntent != null) {
            outState.putParcelable(PARAM_INTENT, mIntent);
        }
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mCanvasWebView != null && keyCode == KeyEvent.KEYCODE_BACK && mCanvasWebView.canGoBack()) {
            mCanvasWebView.goBack();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * 指定されたCanvas用のViewを表示する.
     *
     */
    private void showCanvasView() {
        mCanvasWebView = new CanvasWebView(this, mDrawImageObject, mSettings, mExternalAccessFlag);
        CanvasVideoView canvasVideoView = new CanvasVideoView(this, mDrawImageObject);
        String mimeType = mDrawImageObject.getMimeType();

        if (mimeType != null && mimeType.contains("video/x-mjpeg")) {
            mCanvasWebView.visibility();
            canvasVideoView.gone();
            mCanvasWebView.initWebView(mimeType);
        } else if (mimeType != null && mimeType.contains("video/")) {
            mCanvasWebView.gone();
            canvasVideoView.visibility();
            canvasVideoView.initVideoView();
        } else if (mimeType != null && mimeType.contains("text/html")) {
            mCanvasWebView.visibility();
            canvasVideoView.gone();
            mCanvasWebView.initWebView(mimeType);
        } else {
            // 画像が来た時あるいは、mimeTypeが指定されていなかった場合
            new CanvasImageView(this, mDrawImageObject);
        }
    }
    /**
     * Set a argument that draw in canvas.
     *
     * @param intent argument
     */
    private void setDrawingArgument(final Intent intent) {
        if (intent != null) {
            mIntent = intent;
        }
    }
}
