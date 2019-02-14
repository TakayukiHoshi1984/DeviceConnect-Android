package org.deviceconnect.android.deviceplugin.host.canvas;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.VideoView;

import org.deviceconnect.android.deviceplugin.host.canvas.view.CanvasImageView;
import org.deviceconnect.android.deviceplugin.host.canvas.view.CanvasVideoView;
import org.deviceconnect.android.deviceplugin.host.canvas.view.CanvasWebView;


public class CanvasController {
    private Context mContext;
    private CanvasDrawImageObject mDrawImageObject;

    public interface PresenterCallback {
        void onOKCallback();
        void onCancelCallback();
    }
    public interface Presenter {
        void finishActivity();
        void showNotFoundDrawImageDialog();
        void showDownloadDialog();
        void dismissDownloadDialog();
        void showOutOfMemoryDialog();
        void showExternalNetworkAccessDialog(final PresenterCallback callback);
        void showContinuousAccessConfirmDilaog(final PresenterCallback callback);
        boolean isCanvasContinuousAccess();
    }

    /**
     * 一度外部アクセスダイアログを出したかどうかのフラグ.
     * 二度目はダイアログを出さないようにする.
     */
    private boolean mExternalAccessFlag = false;
    /**
     * CanvasAPIの設定値を保持する.
     */
    private HostCanvasSettings mSettings;

    private String mDrawActionName;
    private String mDeleteActionName;
    private Presenter mPresenter;
    private WebView mCanvasWebView;
    private ImageView mCanvasImageView;
    private VideoView mCanvasVideoView;
    /**
     * Canvasが表示中に処理を受け付けるBroadcastReceiver.
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            String action = intent.getAction();
            if (mDrawActionName.equals(action)) {
                mDrawImageObject = CanvasDrawImageObject.create(intent);
                checkForAtack(true);
            } else if (mDeleteActionName.equals(action)) {
                mPresenter.finishActivity();
            }
        }
    };

    public CanvasController(final Context context,
                            final WebView canvasWebView,
                            final ImageView imageView,
                            final VideoView videoView,
                            final Presenter presenter,
                            final CanvasDrawImageObject drawObject,
                            final HostCanvasSettings settings,
                            final String drawActionName,
                            final String deleteActionName) {
        mContext = context;
        mCanvasWebView = canvasWebView;
        mCanvasVideoView = videoView;
        mCanvasImageView = imageView;
        mPresenter = presenter;
        mDrawImageObject = drawObject;
        mSettings = settings;
        mDrawActionName = drawActionName;
        mDeleteActionName = deleteActionName;
    }

    public void registerReceiver() {
        // Intent-Fileterの設定
        IntentFilter filter = new IntentFilter();
        filter.addAction(mDrawActionName);
        filter.addAction(mDeleteActionName);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mReceiver, filter);
    }

    public void unregisterReceiver() {
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mReceiver);
    }

    public void updateCanvas(final CanvasDrawImageObject drawImageObject) {
        mDrawImageObject = drawImageObject;
        checkForAtack(true);
    }

    /**
     * 異常な方法でCanvasが起動されたか、おかしな外部サイトにアクセスしていないかをチェック.
     */
    public void checkForAtack(final boolean isContinuous) {
        String uri = mDrawImageObject.getData();
        if (!isContinuous && mPresenter.isCanvasContinuousAccess()) {
            mPresenter.showContinuousAccessConfirmDilaog(new PresenterCallback() {
                @Override
                public void onOKCallback() {
                    showCanvasView();
                }

                @Override
                public void onCancelCallback() {

                }
            });
        } else if (mSettings.isCanvasActivityAccessExternalNetworkFlag()
                && ExternalAccessCheckUtils.isExternalAccessResource(mContext, uri)) {
            mPresenter.showExternalNetworkAccessDialog(new PresenterCallback() {
                @Override
                public void onOKCallback() {
                    mExternalAccessFlag = true;
                    // 多重起動が行われたかをチェック
                    if (mPresenter.isCanvasContinuousAccess()) {
                        mPresenter.showContinuousAccessConfirmDilaog(new PresenterCallback() {
                            @Override
                            public void onOKCallback() {
                                showCanvasView();
                            }

                            @Override
                            public void onCancelCallback() {

                            }
                        });
                    } else {
                        showCanvasView();
                    }
                }

                @Override
                public void onCancelCallback() {
                    mPresenter.finishActivity();
                }
            });
        } else {
            showCanvasView();
        }
    }
    /**
     * 指定されたCanvas用のViewを表示する.
     *
     */
    private void showCanvasView() {
        CanvasWebView canvasWebView = new CanvasWebView(mContext, mCanvasWebView, mPresenter, mDrawImageObject, mSettings, mExternalAccessFlag);
        CanvasVideoView canvasVideoView = new CanvasVideoView(mCanvasVideoView, mDrawImageObject);
        String mimeType = mDrawImageObject.getMimeType();

        if (mimeType != null && mimeType.contains("video/x-mjpeg")) {
            canvasWebView.visibility();
            canvasVideoView.gone();
            canvasWebView.initWebView(mimeType);
        } else if (mimeType != null && mimeType.contains("video/")) {
            canvasWebView.gone();
            canvasVideoView.visibility();
            canvasVideoView.initVideoView();
        } else if (mimeType != null && mimeType.contains("text/html")) {
            canvasWebView.visibility();
            canvasVideoView.gone();
            canvasWebView.initWebView(mimeType);
        } else {
            // 画像が来た時あるいは、mimeTypeが指定されていなかった場合
            new CanvasImageView(mContext, mCanvasImageView, mPresenter, mDrawImageObject);
        }
    }
}
