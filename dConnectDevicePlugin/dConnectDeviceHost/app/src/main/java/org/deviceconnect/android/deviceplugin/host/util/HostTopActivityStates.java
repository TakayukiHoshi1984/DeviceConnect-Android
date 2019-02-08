/*
 HostTopActivityStates.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.deviceconnect.android.deviceplugin.host.R;

/**
 * 指定のActivityが画面に表示されているかを返す.
 *
 * @author NTT DOCOMO, INC.
 */
public final class HostTopActivityStates {
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
    public HostTopActivityStates(final Context context) {
        mContext = context;
        load();
    }

    /**
     * SharedPreferencesのデータを読み込む.
     */
    private void load() {
        mPreferences = mContext.getSharedPreferences("org_deviceconnect_android_deviceplugin_host_top_activity_preferences",
                Context.MODE_PRIVATE);
    }

    /**
     * 指定されたActivityがトップ画面にきているかどうか.
     * @param name Activity名
     * @return true:トップにきている false:トップにない
     */
    public boolean isTopActivityState(final String name) {
        return mPreferences.getBoolean(name.replace(".", "_"), false);
    }

    /**
     * 指定されたActivityの状態を設定する.
     * @param name Activity名
     * @param state 状態
     */
    public void setTopActivityState(final String name, final boolean state) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(name.replace(".", "_"), state);
        editor.apply();
    }
}
