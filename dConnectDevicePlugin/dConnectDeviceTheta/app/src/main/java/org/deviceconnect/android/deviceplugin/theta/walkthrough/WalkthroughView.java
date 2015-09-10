package org.deviceconnect.android.deviceplugin.theta.walkthrough;


import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import org.deviceconnect.android.deviceplugin.theta.opengl.SphereRenderer;
import org.deviceconnect.android.deviceplugin.theta.roi.OmnidirectionalImage;

class WalkthroughView extends ViewGroup {

    /** デバック用タグ. */
    public static final String LOG_TAG = "WalkthroughView";

    /**
     * プレビューの横幅の閾値を定義する.
     * <p>
     * これ以上の横幅のプレビューは設定させない。
     */
    private static final int THRESHOLD_WIDTH = 500;

    /**
     * プレビューの縦幅の閾値を定義する.
     * <p>
     * これ以上の縦幅のプレビューは設定させない。
     */
    private static final int THRESHOLD_HEIGHT = 400;

    /** プレビューを表示するSurfaceView. */
    private GLSurfaceView mSurfaceView;

    private final SphereRenderer mRenderer = new SphereRenderer();
    /** SurfaceViewを一時的に保持するホルダー. */
    private SurfaceHolder mHolder;

    /**
     * コンストラクタ.
     *
     * @param context このクラスが属するコンテキスト
     */
    public WalkthroughView(final Context context) {
        super(context);

        mSurfaceView = new GLSurfaceView(context);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setRenderer(mRenderer);
        addView(mSurfaceView);
    }

    /**
     * Preview.
     * @param context context
     * @param attrs attributes
     */
    public WalkthroughView(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        mSurfaceView = new GLSurfaceView(context);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setRenderer(mRenderer);
        addView(mSurfaceView);
    }

    public void setScreenSize(final int width, final int height) {
        mRenderer.setScreenWidth(width);
        mRenderer.setScreenHeight(height);
    }

    public SphereRenderer getRenderer() {
        return mRenderer;
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(final boolean changed, final int l, final int t, final int r, final int b) {
        if (changed && getChildCount() > 0) {
            final View child = getChildAt(0);

            final int width = r - l;
            final int height = b - t;

            int previewWidth = width;
            int previewHeight = height;

            // Center the child SurfaceView within the parent.
            if (width * previewHeight > height * previewWidth) {
                final int scaledChildWidth = previewWidth * height / previewHeight;
                child.layout((width - scaledChildWidth) / 2, 0, (width + scaledChildWidth) / 2, height);
            } else {
                final int scaledChildHeight = previewHeight * width / previewWidth;
                child.layout(0, (height - scaledChildHeight) / 2, width, (height + scaledChildHeight) / 2);
            }
        }
    }

    public void setOmnidirectionalImage(final Bitmap image) {
        mRenderer.setTexture(image);
    }

}
