package org.deviceconnect.android.deviceplugin.host.recorder.camera;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.media.Image;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapper;
import org.deviceconnect.android.deviceplugin.host.camera.CameraWrapperException;
import org.deviceconnect.android.deviceplugin.host.recorder.HostDeviceLiveStreamRecorder;
import org.deviceconnect.android.deviceplugin.host.recorder.HostMediaRecorder;
import org.deviceconnect.android.deviceplugin.host.recorder.PreviewServer;

import java.util.List;

public class DepthCamera2PreviewServerProvider extends Camera2PreviewServerProvider {
    /**
     * プレビュー確認用オーバレイ用アクションを定義.
     */
    protected static final String SHOW_OVERLAY_PREVIEW_ACTION = "org.deviceconnect.android.deviceplugin.host.depth.SHOW_OVERLAY_PREVIEW";

    /**
     * プレビュー確認用オーバレイ用アクションを定義.
     */
    protected static final String HIDE_OVERLAY_PREVIEW_ACTION = "org.deviceconnect.android.deviceplugin.host.depth.HIDE_OVERLAY_PREVIEW";

    /**
     * カメラを操作するレコーダ.
     */
    private DepthCamera2Recorder mDepthRecorder;
    /**
     * コンストラクタ.
     *
     * @param context  コンテキスト
     * @param recorder レコーダ
     * @param num      カメラの番号
     */
    DepthCamera2PreviewServerProvider(Context context, Camera2Recorder recorder, int num) {
        super(context,  recorder, num);
        mDepthRecorder = (DepthCamera2Recorder) mRecorder;
        getServers().clear();
        addServer(new Camera2MJPEGPreviewServer(context, false, recorder, 14000 + num, mOnEventListener));
        addServer(new Camera2MJPEGPreviewServer(context, true, recorder, 14100 + num, mOnEventListener));
        addServer(new Camera2RTSPPreviewServer(context, recorder, 15000 + num, mOnEventListener));
        addServer(new Camera2SRTPreviewServer(context, recorder, 16000 + num, mOnEventListener));
    }
    /**
     * 画面にプレビューを表示するためのアクションを受け取るための BroadcastReceiver を登録します.
     */
    @Override
    public void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(SHOW_OVERLAY_PREVIEW_ACTION);
        filter.addAction(HIDE_OVERLAY_PREVIEW_ACTION);
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * 画面にプレビューを表示するためのアクションを受け取るための BroadcastReceiver を解除します.
     */
    @Override
    public void unregisterBroadcastReceiver() {
        try {
            mContext.unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e) {
            // ignore.
        }
    }
    @Override
    public void onConfigChange() {
        super.onConfigChange();
        if (mOverlayView != null) {
            // 画面が回転したので、オーバーレイのレイアウトも調整
            mOverlayManager.update();
            mOverlayManager.updateView(mOverlayView,
                    0,
                    0,
                    mOverlayManager.getDisplayWidth(),
                    mOverlayManager.getDisplayHeight());

            adjustSurfaceView(mDepthRecorder.isSwappedDimensions());
        }
    }
    /**
     * 画面にプレビューを表示するための PendingIntent を作成します.
     * @param id カメラID
     * @return PendingIntent
     */
    protected PendingIntent createShowActionIntent(String id) {
        Intent intent = new Intent();
        intent.setAction(SHOW_OVERLAY_PREVIEW_ACTION);
        intent.putExtra(EXTRA_CAMERA_ID, id);
        return PendingIntent.getBroadcast(mContext, getNotificationId(), intent, 0);
    }

    /**
     * 画面にプレビューを非表示にするための PendingIntent を作成します.
     *
     * @param id カメラID
     * @return PendingIntent
     */
    protected PendingIntent createHideActionIntent(String id) {
        Intent intent = new Intent();
        intent.setAction(HIDE_OVERLAY_PREVIEW_ACTION);
        intent.putExtra(EXTRA_CAMERA_ID, id);
        return PendingIntent.getBroadcast(mContext, getNotificationId(), intent, 0);
    }
    /**
     * プレビューのサイズを View に収まるように調整します.
     *
     * @param isSwappedDimensions 縦横の幅をスワップする場合はtrue、それ以外はfalse
     */
    private synchronized void adjustSurfaceView(boolean isSwappedDimensions) {
        if (mOverlayView == null) {
            return;
        }

        mHandler.post(() -> {
            HostMediaRecorder.PictureSize previewSize = mRecorder.getPreviewSize();
            int cameraWidth = previewSize.getWidth();
            int cameraHeight = previewSize.getHeight();

            SurfaceView surfaceView = mOverlayView.findViewById(R.id.surface_view);
            Size changeSize;
            Size viewSize = new Size(mOverlayManager.getDisplayWidth(), mOverlayManager.getDisplayHeight());
            if (isSwappedDimensions) {
                changeSize = calculateViewSize(cameraHeight, cameraWidth, viewSize);
            } else {
                changeSize = calculateViewSize(cameraWidth, cameraHeight, viewSize);
            }

            mOverlayManager.updateView(mOverlayView,
                    0,
                    0,
                    changeSize.getWidth(),
                    changeSize.getHeight());

            if (isSwappedDimensions) {
                surfaceView.getHolder().setFixedSize(previewSize.getHeight(), previewSize.getWidth());
            } else {
                surfaceView.getHolder().setFixedSize(previewSize.getWidth(), previewSize.getHeight());
            }
            TextView textView = mOverlayView.findViewById(R.id.text_view);
            textView.setVisibility(mCameraPreviewFlag ? View.VISIBLE : View.GONE);
        });
    }
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || mDepthRecorder == null) {
                return;
            }

            String cameraId = intent.getStringExtra(EXTRA_CAMERA_ID);
            if (!mDepthRecorder.getId().equals(cameraId)) {
                return;
            }

            String action = intent.getAction();
            if (SHOW_OVERLAY_PREVIEW_ACTION.equals(action)) {
                mHandler.post(() -> showPreviewOnOverlay());
            } else if (HIDE_OVERLAY_PREVIEW_ACTION.equals(action)) {
                mHandler.post(() -> hidePreviewOnOverlay());
            }
        }
    };
    /**
     * オーバーレイ上にプレビューを表示します.
     */
    protected synchronized void showPreviewOnOverlay() {
        if (mOverlayView != null) {
            return;
        }

        // Notification を閉じるイベントを送信
        mContext.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        if (!mOverlayManager.isOverlayAllowed()) {
            openOverlayPermissionActivity();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(mContext);

        mOverlayView = inflater.inflate(R.layout.host_preview_overlay, null);

        final SurfaceView surfaceView = mOverlayView.findViewById(R.id.surface_view);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                    mDepthRecorder.setTargetSurface(new CameraWrapper.OnImageAvailableListener() {
                        private Paint mPaint = new Paint();
                        @Override
                        public void onSuccess(Image image) {
                            byte[] jpeg = mRecorder.convertJPEG(image);
                            Canvas canvas = surfaceView.getHolder().lockCanvas();

                            Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                            if (bitmap == null || canvas == null) {
                                return;
                            }
                            canvas.drawBitmap(bitmap, 0, 0, mPaint);

                            surfaceView.getHolder().unlockCanvasAndPost(canvas);

                        }

                        @Override
                        public void onFailed(String message) {
                            Log.e("host.dplugin", message);
                        }
                    });
                if (mCameraPreviewFlag) {
                    // 既にプレビューが配信中の場合は、オーバーレイ用の Surface を追加してから
                    // カメラを再起動させます。
                    restartCamera();
                } else {
                    new Thread(() -> {
                        try {
                            if (mDepthRecorder.isPreview()) {
                                mDepthRecorder.startPreview(true);
                            } else {
                                mDepthRecorder.startPreview(false);
                            }
                        } catch (CameraWrapperException e) {
                            // ignore.
                        }
                    }).start();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            }
        });

        mOverlayManager.addView(mOverlayView,
                0,
                0,
                mOverlayManager.getDisplayWidth(),
                mOverlayManager.getDisplayHeight(),
                "overlay-" + mRecorder.getId());

        adjustSurfaceView(mDepthRecorder.isSwappedDimensions());
    }

    /**
     * オーバーレイ上に表示しているプレビューを削除します.
     */
    protected synchronized void hidePreviewOnOverlay() {
        if (mOverlayView != null) {
            try {
                HostDeviceLiveStreamRecorder liveStreamRecorder = mDepthRecorder;
                if (!liveStreamRecorder.isStreaming()) {
                    mDepthRecorder.removeTargetSurface();
                    mDepthRecorder.stopPreview();
                } else {
                    mDepthRecorder.stopLiveStreaming();
                    mDepthRecorder.startLiveStreaming();
                }
            } catch (CameraWrapperException e) {
                // ignore.
            }
            mOverlayManager.removeAllViews();
            mOverlayView = null;

            // プレビュー配信中は、カメラを再開させます。
            if (mCameraPreviewFlag) {
                restartCamera();
            }
        }
    }


    /**
     * カメラからのイベントを受け取ります.
     */
    private final Camera2PreviewServer.OnEventListener mOnEventListener = new Camera2PreviewServer.OnEventListener() {
        @Override
        public void onCameraStarted() {
            mCameraPreviewFlag = true;
            if (mOverlayView != null) {
                // プレビュー配信が開始されるので、オーバーレイに表示していたカメラを停止します。
                try {
                    mDepthRecorder.removeTargetSurface();
                    mDepthRecorder.stopPreview();
                } catch (CameraWrapperException e) {
                    // ignore.
                }

                final SurfaceView surfaceView = mOverlayView.findViewById(R.id.surface_view);
                mDepthRecorder.setTargetSurface(new CameraWrapper.OnImageAvailableListener() {
                        private Paint mPaint = new Paint();
                        @Override
                        public void onSuccess(Image image) {
                            byte[] jpeg = mRecorder.convertJPEG(image);
                            Canvas canvas = surfaceView.getHolder().lockCanvas();

                            Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                            if (bitmap == null || canvas == null) {
                                return;
                            }
                            canvas.drawBitmap(bitmap, 0, 0, mPaint);

                            surfaceView.getHolder().unlockCanvasAndPost(canvas);

                        }

                        @Override
                        public void onFailed(String message) {
                            Log.e("host.dplugin", message);
                        }
                    });
                adjustSurfaceView(mDepthRecorder.isSwappedDimensions());
            }
        }

        @Override
        public void onCameraStopped() {
            mCameraPreviewFlag = false;

            if (mOverlayView != null) {
                // カメラの停止は、非同期で行われるので、ここでは、カメラの再開処理を 500msec まってから行います。
                mHandler.postDelayed(() -> {
                    if (mOverlayView != null) {
                        SurfaceView surfaceView = mOverlayView.findViewById(R.id.surface_view);
                        new Thread(() -> {
                            try {
                                mDepthRecorder.removeTargetSurface();
                                mDepthRecorder.setTargetSurface(surfaceView.getHolder().getSurface());
                                mDepthRecorder.startPreview(null);
                            } catch (CameraWrapperException e) {
                                // ignore.
                            }
                        }).start();
                        adjustSurfaceView(mDepthRecorder.isSwappedDimensions());
                    }
                }, 500);
            }
        }
    };
}
