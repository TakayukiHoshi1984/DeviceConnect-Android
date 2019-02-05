/*
 CanvasImageView.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.canvas.view;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import org.deviceconnect.android.deviceplugin.host.R;
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
    /**
     *  エラーダイアログのタイプ:{@value}.
     */
    private static final String DIALOG_TYPE_OOM = "TYPE_OOM";

    /**
     *  エラーダイアログのタイプ:{@value}.
     */
    private static final String DIALOG_TYPE_NOT_FOUND = "TYPE_NOT_FOUND";
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
        NotFoundResource
    }

    /**
     * リソースダウンロード中のProgressDialog.
     */
    private static DownloadMessageDialogFragment mDialog;

    /**
     * ダウンロード中フラグ.
     */
    private static boolean mDownloadFlag = false;

    /**
     * コンストラクタ.
     * @param activity Activity
     * @param drawObject Canvasに表示するリソース情報を持つオブジェクト
     */
    public CanvasImageView(final Activity activity, final CanvasDrawImageObject drawObject) {
        refreshImage(activity, drawObject);
    }

    /**
     * 画像を表示する.
     * @param activity Activity
     * @param drawObject Canvasに表示するリソース情報を持つオブジェクト
     */
    private void refreshImage(final Activity activity, final CanvasDrawImageObject drawObject) {
        if (drawObject == null) {
            openNotFoundDrawImage(activity);
            return;
        }

        if (mDownloadFlag) {
            return;
        }
        mDownloadFlag = true;
        DownloadTask task = new DownloadTask(activity, drawObject);
        task.execute();
    }
    /**
     * リソースが見つからない場合のエラーダイアログを表示します.
     */
    private static void openNotFoundDrawImage(final Activity activity) {
        ErrorDialogFragment oomDialog = ErrorDialogFragment.create(DIALOG_TYPE_NOT_FOUND,
                activity.getString(R.string.host_canvas_error_title),
                activity.getString(R.string.host_canvas_error_not_found_message),
                activity.getString(R.string.host_ok));
        oomDialog.show(activity.getFragmentManager(), DIALOG_TYPE_NOT_FOUND);
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
        private WeakReference<Activity> mActivityReference;
        /** 表示する画像データ. */
        private Bitmap mBitmap;

        /**
         * コンストラクタ.
         * @param activity Activity
         * @param drawImageObject Canvasに表示するリソース情報を持つオブジェクト
         */
        DownloadTask(final Activity activity, final CanvasDrawImageObject drawImageObject) {
            ImageView canvasView = activity.findViewById(R.id.canvasProfileView);
            canvasView.setKeepScreenOn(true);
            mCanvasView = new WeakReference<>(canvasView);
            mActivityReference = new WeakReference<>(activity);
            mDrawImageObject = drawImageObject;
        }
        @Override
        protected void onPreExecute() {
            showDownloadDialog();
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
                    data = CanvasDrawUtils.getContentData(mActivityReference.get(), uri);
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
            return ResourceResult.Success;
        }

        @Override
        protected void onPostExecute(final ResourceResult result) {
            dismissDownloadDialog();
            switch (result) {
                case Success:
                    showDrawObject();
                    break;
                case OutOfMemory:
                    openOutOfMemory();
                    break;
                case NotFoundResource:
                    openNotFoundDrawImage(mActivityReference.get());
                    break;
            }

            mDownloadFlag = false;
        }
        /**
         * ダウンロードダイアログを非表示.
         */
        private synchronized  void dismissDownloadDialog() {
            if (mDialog != null) {
                mDialog.dismiss();
                mDialog = null;
            }
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
                    BitmapDrawable bd = new BitmapDrawable(mActivityReference.get().getResources(), mBitmap);
                    bd.setTileModeX(Shader.TileMode.REPEAT);
                    bd.setTileModeY(Shader.TileMode.REPEAT);
                    mCanvasView.get().setImageDrawable(bd);
                    mCanvasView.get().setScaleType(ImageView.ScaleType.FIT_XY);
                    mCanvasView.get().setTranslationX((int) mDrawImageObject.getX());
                    mCanvasView.get().setTranslationY((int) mDrawImageObject.getY());
                    break;
            }
        }



        /**
         * メモリ不足エラーダイアログを表示.
         */
        private void openOutOfMemory() {
            ErrorDialogFragment oomDialog = ErrorDialogFragment.create(DIALOG_TYPE_OOM,
                    mActivityReference.get().getString(R.string.host_canvas_error_title),
                    mActivityReference.get().getString(R.string.host_canvas_error_oom_message),
                    mActivityReference.get().getString(R.string.host_ok));
            oomDialog.show(mActivityReference.get().getFragmentManager(), DIALOG_TYPE_OOM);
        }

        /**
         * ダウンロードダイアログを表示.
         */
        private synchronized void showDownloadDialog() {
            if (mDialog != null) {
                mDialog.dismiss();
            }
            mDialog = new DownloadMessageDialogFragment();
            mDialog.show(mActivityReference.get().getFragmentManager(), "dialog");
        }
    };

}
