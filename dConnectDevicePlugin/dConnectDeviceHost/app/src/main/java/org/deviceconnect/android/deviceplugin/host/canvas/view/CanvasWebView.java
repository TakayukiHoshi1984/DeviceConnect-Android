package org.deviceconnect.android.deviceplugin.host.canvas.view;

import android.app.Activity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawImageObject;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasUtils;
import org.deviceconnect.android.deviceplugin.host.canvas.HostCanvasSettings;
import org.deviceconnect.android.deviceplugin.host.canvas.dialog.ExternalNetworkWarningDialogFragment;

import static org.deviceconnect.android.deviceplugin.host.canvas.dialog.ExternalNetworkWarningDialogFragment.EXTERNAL_SHOW_CANVAS_WARNING_TAG;

public class CanvasWebView {
    /**
     * Canvas view object.
     */
    private WebView mCanvasWebView;
    private Activity mActivity;
    private CanvasDrawImageObject mDrawImageObject;
    private HostCanvasSettings mSettings;
    private boolean mExternalAccessFlag;

    public CanvasWebView(final Activity activity,
                         final CanvasDrawImageObject drawObject,
                         final HostCanvasSettings settings,
                         final boolean externalAccessFlag) {
        mCanvasWebView = activity.findViewById(R.id.canvasProfileWebView);
        mActivity = activity;
        mSettings = settings;
        mDrawImageObject = drawObject;
        mExternalAccessFlag = externalAccessFlag;
    }

    public void gone() {
        mCanvasWebView.setVisibility(View.GONE);
    }

    public void visibility() {
        mCanvasWebView.setVisibility(View.VISIBLE);
    }
    public boolean canGoBack() {
        return mCanvasWebView.canGoBack();
    }

    public void goBack() {
        mCanvasWebView.goBack();
    }


    public void initWebView(final String mimeType) {
        WebSettings settings = mCanvasWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        mCanvasWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (mSettings.isCanvasActivityAccessExternalNetworkFlag()
                        && CanvasUtils.isExternalAccessResource(mActivity, url)
                        && !mExternalAccessFlag) {
                    // WebView内のリンクをクリックした時に、外部リソースが指定されているかを確認
                    ExternalNetworkWarningDialogFragment.createDialog(mActivity,
                            new ExternalNetworkWarningDialogFragment.OnWarningDialogListener() {
                                @Override
                                public void onOK() {
                                    mCanvasWebView.loadUrl(url);
                                    mExternalAccessFlag = true;
                                }

                                @Override
                                public void onCancel() {
                                    mActivity.finish();
                                }
                            }).show(mActivity.getFragmentManager(), EXTERNAL_SHOW_CANVAS_WARNING_TAG);
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
