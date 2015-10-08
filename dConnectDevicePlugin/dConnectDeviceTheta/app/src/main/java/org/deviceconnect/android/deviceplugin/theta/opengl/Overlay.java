package org.deviceconnect.android.deviceplugin.theta.opengl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import org.deviceconnect.android.deviceplugin.theta.R;

public class Overlay {

    /** コンテキスト. */
    private Context mContext;

    /** ウィンドウ管理クラス. */
    private WindowManager mWinMgr;

    /** プレビュー画面. */
    private WalkthroughView mPreview;

    private Button mZoomInBtn;

    private Button mZoomOutBtn;

    /**
     * 画面回転のイベントを受け付けるレシーバー.
     */
    private BroadcastReceiver mOrientReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (mPreview == null) {
                return;
            }
            String action = intent.getAction();
            if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
                updatePosition(mPreview);
            }
        }
    };

    /**
     * コンストラクタ.
     * @param context コンテキスト
     */
    public Overlay(final Context context) {
        mContext = context;
        mWinMgr = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    /**
     * カメラのオーバーレイが表示されているかを確認する.
     * @return 表示されている場合はtrue、それ以外はfalse
     */
    public synchronized boolean isShow() {
        return mIsAttachedView;
    }

    public SphereRenderer getRenderer() {
        if (mPreview == null) {
            return null;
        }
        return mPreview.getRenderer();
    }

    public synchronized void setOmnidirectionalImage(final Bitmap image) {
        if (mPreview != null) {
            mPreview.setOmnidirectionalImage(image);
        }
    }

    public void resetCamera() {
        if (mPreview != null) {
            mPreview.getRenderer().resetCamera();
        }
    }

    private boolean mIsAttachedView;

    /**
     * Overlayを表示する.
     */
    public synchronized void show(final Bitmap image) {
        Log.d("Walk", "Overlay.show");
        if (isShow()) {
            return;
        }

        Point size = getDisplaySize();

        mPreview = new WalkthroughView(mContext);
        if (image != null) {
            mPreview.setOmnidirectionalImage(image);
        }
        mPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (mEventListener != null) {
                    mEventListener.onClick();
                }
            }
        });
        mPreview.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(final View v) {
                if (mEventListener != null) {
                    mEventListener.onClose();
                }
                hide();
                return true;
            }
        });
        mPreview.setScreenSize(size.x, size.y);
        int pt = (int) (5 * getScaledDensity());
        final WindowManager.LayoutParams l = new WindowManager.LayoutParams(
            size.x, //pt,
            size.y, //pt,
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT);
        l.x = -size.x / 2;
        l.y = -size.y / 2;
        mWinMgr.addView(mPreview, l);

        mZoomOutBtn = new Button(mContext);
        mZoomOutBtn.setBackgroundResource(R.drawable.button_zoom_out);
        mZoomOutBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                SphereRenderer.Camera camera = mPreview.getRenderer().getCamera();
                double fov = camera.getFov();

                double nextFov = fov + 5.0;
                if (nextFov <= 130.0) {
                    camera.setFov(nextFov);
                }
            }
        });
        final WindowManager.LayoutParams l4 = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        l4.x = size.x / 2 - 50;
        l4.y = size.y / 2;
        mWinMgr.addView(mZoomOutBtn, l4);

        mZoomInBtn = new Button(mContext);
        mZoomInBtn.setBackgroundResource(R.drawable.button_zoom_in);
        mZoomInBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                SphereRenderer.Camera camera = mPreview.getRenderer().getCamera();
                double fov = camera.getFov();

                double nextFov = fov - 5.0;
                if (nextFov >= 40.0) {
                    camera.setFov(nextFov);
                }
            }
        });
        final WindowManager.LayoutParams l3 = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        l3.x = size.x / 2 - 50;
        l3.y = (size.y / 2) - 280;
        mWinMgr.addView(mZoomInBtn, l3);

        mIsAttachedView = true;

//        IntentFilter filter = new IntentFilter();
//        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
//        mContext.registerReceiver(mOrientReceiver, filter);
    }

    /**
     * Overlayを非表示にする.
     */
    public synchronized void hide() {
        if (mPreview != null) {
            mWinMgr.removeView(mPreview);
            mPreview = null;
        }
        if (mZoomInBtn != null) {
            mWinMgr.removeView(mZoomInBtn);
            mZoomInBtn = null;
        }
        if (mZoomOutBtn != null) {
            mWinMgr.removeView(mZoomOutBtn);
            mZoomOutBtn = null;
        }
        mIsAttachedView = false;
//        mContext.unregisterReceiver(mOrientReceiver);
    }

    /**
     * Displayの密度を取得する.
     * @return 密度
     */
    private float getScaledDensity() {
        DisplayMetrics metrics = new DisplayMetrics();
        mWinMgr.getDefaultDisplay().getMetrics(metrics);
        return metrics.scaledDensity;
    }

    /**
     * Displayのサイズを取得する.
     * @return サイズ
     */
    private Point getDisplaySize() {
        Display disp = mWinMgr.getDefaultDisplay();
        Point size = new Point();
        disp.getSize(size);
        return size;
    }

    /**
     * Viewの座標を画面の左上に移動する.
     * @param view 座標を移動するView
     */
    private void updatePosition(final View view) {
        if (view == null) {
            return;
        }
        Point size = getDisplaySize();
        WindowManager.LayoutParams lp =
            (WindowManager.LayoutParams) view.getLayoutParams();
        lp.x = -size.x / 2;
        lp.y = -size.y / 2;
        mWinMgr.updateViewLayout(view, lp);
    }

    public interface EventListener {

        void onClose();

        void onClick();
    }

    private EventListener mEventListener;

    public void setEventListener(EventListener l) {
        mEventListener = l;
    }
}
