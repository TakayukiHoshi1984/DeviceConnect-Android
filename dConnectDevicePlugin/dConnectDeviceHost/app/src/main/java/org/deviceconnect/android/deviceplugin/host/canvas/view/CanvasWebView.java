/*
 CanvasWebView.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.canvas.view;

import android.content.Context;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.deviceconnect.android.deviceplugin.host.canvas.CanvasController;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawImageObject;
import org.deviceconnect.android.deviceplugin.host.canvas.ExternalAccessCheckUtils;
import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;

/**
 * CanvasでのWebViewの機能を操作する.
 * @author NTT DOCOMO, INC.
 */
public class CanvasWebView {
    /** WebView. */
    private WebView mCanvasWebView;
    /** Canvasに表示するリソース情報を持つオブジェクト. */
    private CanvasDrawImageObject mDrawImageObject;
    /** Canvasに関する設定項目. */
    private HostCanvasSettings mSettings;
    /** 外部アクセスされたかどうかのフラグ. */
    private boolean mExternalAccessFlag;
    private Context mContext;
    private CanvasController.Presenter mPresenter;
    /**
     * コンストラクタ.
     * @param context コンテキスト
     * @param drawObject Canvasに表示するリソース情報を持つオブジェクト
     * @param settings Canvasの設定項目
     * @param externalAccessFlag 外部起動されていたかどうかのフラグ
     */
    public CanvasWebView(final Context context,
                         final WebView canvasWebView,
                         final CanvasController.Presenter presenter,
                         final CanvasDrawImageObject drawObject,
                         final HostCanvasSettings settings,
                         final boolean externalAccessFlag) {
        mContext = context;
        mCanvasWebView = canvasWebView;
        mCanvasWebView.setKeepScreenOn(true);
        mPresenter = presenter;
        mSettings = settings;
        mDrawImageObject = drawObject;
        mExternalAccessFlag = externalAccessFlag;
    }

    /**
     * Viewを非表示にする.
     */
    public void gone() {
        mCanvasWebView.setVisibility(View.GONE);
    }

    /**
     * Viewを表示する.
     */
    public void visibility() {
        mCanvasWebView.setVisibility(View.VISIBLE);
    }


    /**
     * WebViewを初期化する.
     * @param mimeType 表示するリソースのMIME-Type
     */
    public void initWebView(final String mimeType) {
        WebSettings settings = mCanvasWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        mCanvasWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (mSettings.isCanvasActivityAccessExternalNetworkFlag()
                        && ExternalAccessCheckUtils.isExternalAccessResource(mContext, url)
                        && !mExternalAccessFlag) {
                    mPresenter.showExternalNetworkAccessDialog(new CanvasController.PresenterCallback() {
                        @Override
                        public void onOKCallback() {
                            mCanvasWebView.loadUrl(url);
                            mExternalAccessFlag = true;
                        }

                        @Override
                        public void onCancelCallback() {
                            mPresenter.finishActivity();
                        }
                    });
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }
        });

        if (mimeType.equalsIgnoreCase("video/x-mjpeg")) {
            String tag = "<html><body background='" + mDrawImageObject.getData() + "' style='background-repeat: no-repeat; background-size: cover;'></html>";
            mCanvasWebView.loadDataWithBaseURL("", tag, "text/html", "utf-8", "");
        } else {
            String uri = mDrawImageObject.getData();
            if (!mDrawImageObject.getData().startsWith("http")) {
                uri = "file://" + mDrawImageObject.getData();
            }
            mCanvasWebView.loadUrl(uri);
        }
    }

}
