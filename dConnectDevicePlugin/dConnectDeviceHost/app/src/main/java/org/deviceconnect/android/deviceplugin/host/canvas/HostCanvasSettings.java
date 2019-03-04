/*
 HostCanvasSettings.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.canvas;

import android.content.Context;
import android.content.SharedPreferences;

import org.deviceconnect.android.deviceplugin.host.R;

/**
 * HostプラグインのCanvasに関する設定を保持するクラス.
 *
 * @author NTT DOCOMO, INC.
 */
public final class HostCanvasSettings {
    /**
     * 情報を共有するプリファレンス.
     */
    private SharedPreferences mPreferences;

    /**
     * コンテキスト.
     */
    private final Context mContext;


    /**
     * コンストラクタ.
     *
     * @param context コンテキスト
     */
    public HostCanvasSettings(final Context context) {
        mContext = context;
        load();
    }

    /**
     * SharedPreferencesのデータを読み込む.
     */
    private void load() {
        mPreferences = mContext.getSharedPreferences("org_deviceconnect_android_deviceplugin_host_preferences",
                Context.MODE_PRIVATE);
    }


    /**
     * CanvasActivityの多重起動フラグを取得する.
     *
     * @return 多重起動されている恐れがある場合はtrue、それ以外はfalse
     */
    public synchronized boolean isCanvasContinuousAccessForHost() {
        return mPreferences.getBoolean(mContext.getString(R.string.settings_host_canvas_multiple_show), isDefaultCanvasContinuousAccessForHost());
    }

    /**
     * デフォルトのCanvasActivityの多重起動フラグを取得する.
     *
     * @return 多重起動されている恐れがある場合はtrue、それ以外はfalse
     */
    public boolean isDefaultCanvasContinuousAccessForHost() {
        return false;
    }

    /**
     * CanvasActivityの多重起動フラグを設定する.
     *
     * @param flag 起動フラグ
     */
    public synchronized void setCanvasContinuousAccessForHost(final boolean flag) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(mContext.getString(R.string.settings_host_canvas_multiple_show), flag);
        editor.apply();
    }

    /**
     * CanvasActivityの多重起動させないためのフラグを取得する.
     *
     * @return CanvasActivityを起動させる場合はtrue、それ以外はfalse
     */
    public boolean isCanvasActivityNeverShowFlag() {
        return mPreferences.getBoolean(mContext.getString(R.string.settings_host_canvas_multiple_show_never), isDefaultCanvasActivityNeverShowFlag());
    }

    /**
     * デフォルトのCanvasActivityを起動するかどうかのフラグを取得する.
     *
     * @return CanvasActivityを起動させる場合はtrue、それ以外はfalse
     */
    public boolean isDefaultCanvasActivityNeverShowFlag() {
        return true;
    }

    /**
     * CanvasActivityの多重起動されたため、これ以上CavnasActivityを起動させえないかを判定するためのフラグを設定する.
     *
     * @param flag 起動させるかどうかのフラグ
     */
    public void setCanvasActivityNeverShowFlag(final boolean flag) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(mContext.getString(R.string.settings_host_canvas_multiple_show_never), flag);
        editor.apply();
    }

    /**
     * CanvasActivityが外部リソースにアクセスするかどうかを判断するフラグを取得する.
     *
     * @return 外部リソースにアクセスすることを確認しないはtrue、それ以外はfalse
     */
    public boolean isCanvasActivityAccessExternalNetworkFlag() {
        return mPreferences.getBoolean(mContext.getString(R.string.settings_host_canvas_access_external_network), isDefaultCanvasActivityAccessExternalNetworkFlag());
    }

    /**
     * デフォルトのCanvasActivity外部リソースにアクセスするかどうかのフラグを取得する.
     *
     * @return 外部リソースにアクセスする場合はtrue、それ以外はfalse
     */
    public boolean isDefaultCanvasActivityAccessExternalNetworkFlag() {
        return true;
    }

    /**
     * CanvasActivity外部リソースにアクセスするかどうかのフラグを設定する.
     *
     * @param flag 外部リソースにアクセスする場合はtrue、それ以外はfalse
     */
    public void setCanvasActivityAccessExternalNetworkFlag(final boolean flag) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(mContext.getString(R.string.settings_host_canvas_access_external_network), flag);
        editor.apply();
    }


    /**
     * CanvasActivityの多重起動フラグを取得する.
     *
     * @return 多重起動されている恐れがある場合はtrue、それ以外はfalse
     */
    public boolean isCanvasContinuousAccessForMultiWindow() {
        return mPreferences.getBoolean(mContext.getString(R.string.settings_host_canvas_multiple_show_for_muti_window), isDefaultCanvasContinuousAccessForMultiWindow());
    }

    /**
     * デフォルトのCanvasActivityの多重起動フラグを取得する.
     *
     * @return 多重起動されている恐れがある場合はtrue、それ以外はfalse
     */
    public boolean isDefaultCanvasContinuousAccessForMultiWindow() {
        return false;
    }

    /**
     * CanvasActivityの多重起動フラグを設定する.
     *
     * @param flag 起動フラグ
     */
    public void setCanvasContinuousAccessForMultiWindow(final boolean flag) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(mContext.getString(R.string.settings_host_canvas_multiple_show_for_muti_window), flag);
        editor.apply();
    }

    /**
     * CanvasActivityの多重起動フラグを取得する.
     *
     * @return 多重起動されている恐れがある場合はtrue、それ以外はfalse
     */
    public boolean isCanvasContinuousAccessForPresentation() {
        return mPreferences.getBoolean(mContext.getString(R.string.settings_host_canvas_multiple_show_for_presentation), isDefaultCanvasContinuousAccessForPresentation());
    }

    /**
     * デフォルトのCanvasActivityの多重起動フラグを取得する.
     *
     * @return 多重起動されている恐れがある場合はtrue、それ以外はfalse
     */
    public boolean isDefaultCanvasContinuousAccessForPresentation() {
        return false;
    }

    /**
     * CanvasActivityの多重起動フラグを設定する.
     *
     * @param flag 起動フラグ
     */
    public void setCanvasContinuousAccessForPresentation(final boolean flag) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(mContext.getString(R.string.settings_host_canvas_multiple_show_for_presentation), flag);
        editor.apply();
    }
    @Override
    public String toString() {
        return "{\n" +
                "multipleShow:" + isCanvasContinuousAccessForHost() + "\n" +
                "neverShow:" + isCanvasActivityNeverShowFlag() + "\n" +
                "externalResourceAccess:" + isCanvasActivityAccessExternalNetworkFlag() + "\n" +
                "}";
    }
}
