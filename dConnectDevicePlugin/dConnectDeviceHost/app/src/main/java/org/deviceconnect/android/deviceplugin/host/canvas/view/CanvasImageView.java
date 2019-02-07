/*
 CanvasImageView.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.canvas.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import org.deviceconnect.android.deviceplugin.host.R;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasController;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawImageObject;
import org.deviceconnect.android.deviceplugin.host.canvas.CanvasDrawUtils;
import org.deviceconnect.android.deviceplugin.host.canvas.dialog.DownloadMessageDialogFragment;
import org.deviceconnect.android.deviceplugin.host.canvas.dialog.ErrorDialogFragment;

import java.lang.ref.WeakReference;

/**
 * CanvasでのImageViewの機能を操作する.
 * @author NTT DOCOMO, INC.
 */
public class CanvasImageView {
    private static final int OOM_MEMORY_SIZE = 5000000;
    /**
     *  エラーダイアログのタイプ:{@value}.
     */
    public static final String DIALOG_TYPE_OOM = "TYPE_OOM";

    /**
     *  エラーダイアログのタイプ:{@value}.
     */
    public static final String DIALOG_TYPE_NOT_FOUND = "TYPE_NOT_FOUND";
    /**
     * 画像リソース取得結果
     */
    private enum ResourceResult {
        /**
         * リソースの取得に成功.
         */
        Success,

        /**
         * リソースの取得時にOut Of Memoryが発生.
         */
        OutOfMemory,

        /**
         * リソースの取得に失敗.
         */
        NotFoundResource,


    }


    /**
     * ダウンロード中フラグ.
     */
    private static boolean mDownloadFlag = false;
    /**
     * コンストラクタ.
     * @param drawObject Canvasに表示するリソース情報を持つオブジェクト
     */
    public CanvasImageView(final Context context, final ImageView imageView, final CanvasController.Presenter presenter, final CanvasDrawImageObject drawObject) {
        if (drawObject == null) {
            presenter.showNotFoundDrawImageDialog();
            return;
        }

        if (mDownloadFlag) {
            return;
        }
        mDownloadFlag = true;
        DownloadTask task = new DownloadTask(context, imageView, presenter, drawObject);
        task.execute();
    }


    /**
     * リソースダウンロード用のAsyncTask.
     */
    private static class DownloadTask extends AsyncTask<Void, ResourceResult, ResourceResult> {
        /** 画像を表示するImageView. */
        private WeakReference<ImageView> mCanvasView;
        /** Canvasに表示するリソース情報を持つオブジェクト. */
        private CanvasDrawImageObject mDrawImageObject;
        /** Activity. */
        private WeakReference<Context> mContextReference;
        private CanvasController.Presenter mPresenter;
        /** 表示する画像データ. */
        private Bitmap mBitmap;

        /**
         * コンストラクタ.
         * @param drawImageObject Canvasに表示するリソース情報を持つオブジェクト
         */
        DownloadTask(final Context context, final ImageView canvasView, final CanvasController.Presenter presenter, final CanvasDrawImageObject drawImageObject) {
            canvasView.setKeepScreenOn(true);
            mCanvasView = new WeakReference<>(canvasView);
            mContextReference = new WeakReference<>(context);
            mDrawImageObject = drawImageObject;
            mPresenter = presenter;
        }
        @Override
        protected void onPreExecute() {
            mPresenter.showDownloadDialog();
        }

        @Override
        protected ResourceResult doInBackground(final Void... params) {

            if (mBitmap != null) {
                if (!mBitmap.isRecycled()) {
                    mBitmap.recycle();
                    mBitmap = null;
                }
            }

            String uri = mDrawImageObject.getData();
            byte[] data;
            try {
                if (uri.startsWith("http")) {
                    data = CanvasDrawUtils.getData(uri);
                } else if (uri.startsWith("content")) {
                    data = CanvasDrawUtils.getContentData(mContextReference.get(), uri);
                } else {
                    data = CanvasDrawUtils.getCacheData(uri);
                }
                mBitmap = CanvasDrawUtils.getBitmap(data);
                if (mBitmap == null) {
                    return ResourceResult.NotFoundResource;
                }
            } catch (OutOfMemoryError e) {
                return ResourceResult.OutOfMemory;
            } catch (Exception e) {
                return ResourceResult.NotFoundResource;
            }
            if ((mBitmap.getWidth() * mBitmap.getHeight()) > OOM_MEMORY_SIZE) {
                return ResourceResult.OutOfMemory;
            }
            return ResourceResult.Success;
        }

        @Override
        protected void onPostExecute(final ResourceResult result) {
            mPresenter.dismissDownloadDialog();
            switch (result) {
                case Success:
                    showDrawObject();
                    break;
                case OutOfMemory:
                    mPresenter.showOutOfMemoryDialog();
                    break;
                case NotFoundResource:
                    mPresenter.showNotFoundDrawImageDialog();
                    break;
            }
            mDownloadFlag = false;
        }
        /**
         * 画面を更新.
         */
        private void showDrawObject() {
            switch (mDrawImageObject.getMode()) {
                default:
                case NON_SCALE_MODE:
                    Matrix matrix = new Matrix();
                    matrix.postTranslate((float) mDrawImageObject.getX(), (float) mDrawImageObject.getY());
                    mCanvasView.get().setImageBitmap(mBitmap);
                    mCanvasView.get().setScaleType(ImageView.ScaleType.MATRIX);
                    mCanvasView.get().setImageMatrix(matrix);
                    break;
                case SCALE_MODE:
                    mCanvasView.get().setImageBitmap(mBitmap);
                    mCanvasView.get().setScaleType(ImageView.ScaleType.FIT_CENTER);
                    mCanvasView.get().setTranslationX((int) mDrawImageObject.getX());
                    mCanvasView.get().setTranslationY((int) mDrawImageObject.getY());
                    break;
                case FILL_MODE:
                    BitmapDrawable bd = new BitmapDrawable(mContextReference.get().getResources(), mBitmap);
                    bd.setTileModeX(Shader.TileMode.REPEAT);
                    bd.setTileModeY(Shader.TileMode.REPEAT);
                    mCanvasView.get().setImageDrawable(bd);
                    mCanvasView.get().setScaleType(ImageView.ScaleType.FIT_XY);
                    mCanvasView.get().setTranslationX((int) mDrawImageObject.getX());
                    mCanvasView.get().setTranslationY((int) mDrawImageObject.getY());
                    break;
            }
        }
    };

}
