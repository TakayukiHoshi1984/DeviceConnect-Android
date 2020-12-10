/*
 LocalOAuthSetting.java
 Copyright (c) 2020 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.linking;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * プラグインの設定を管理するクラス.
 *
 * @author NTT DOCOMO, INC.
 */
public class LocalOAuthSetting {

    /**
     * 設定項目を保存するファイルの名前を定義します.
     */
    private static final String FILE_NAME = "_linking_settings.dat";

    /**
     * プラグイン認可の設定を保存するキー名を定義します.
     */
    private static final String KEY_ENABLE_OAUTH = "key_oauth";

    /**
     * 設定を保存するためのクラス.
     */
    private SharedPreferences mSharedPreferences;

    /**
     * コンストラクタ.
     *
     * @param context コンテキスト
     */
    LocalOAuthSetting(final Context context) {
        mSharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * プラグイン認可の有効・無効を保存します.
     *
     * @param enabled 有効の場合はtrue、無効の場合はfalse
     */
    public void setEnabledOAuth(final boolean enabled) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putBoolean(KEY_ENABLE_OAUTH, enabled);
        editor.apply();
    }

    /**
     * プラグイン認可の有効・無効を取得します.
     * <p>
     * デフォルトでは false にしておきます。
     * </p>
     * @return 有効の場合はtrue、無効の場合はfalse
     */
    public boolean isEnabledOAuth() {
        return mSharedPreferences.getBoolean(KEY_ENABLE_OAUTH, false);
    }
}
