/*
 DConnectApplication.java
 Copyright (c) 2016 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.manager;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.deviceconnect.android.deviceplugin.host.HostDeviceApplication;
import org.deviceconnect.android.logger.AndroidHandler;
import org.deviceconnect.android.manager.core.DConnectConst;
import org.deviceconnect.android.manager.core.DConnectSettings;
import org.deviceconnect.android.manager.core.plugin.DevicePluginManager;
import org.deviceconnect.android.manager.core.util.DConnectUtil;

import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Device Connect Manager Application.
 *
 * @author NTT DOCOMO, INC.
 */
public class DConnectApplication extends HostDeviceApplication implements Application.ActivityLifecycleCallbacks {

    /**
     * Device Connect システム設定.
     */
    private DConnectSettings mSettings;

    /**
     * プラグイン管理クラス.
     */
    private DevicePluginManager mPluginManager;

    /**
     * Managerがフォアグラウンドにいるかどうかのフラグ.
     */
    private boolean mIsForeground;

    @Override
    public void onCreate() {
        super.onCreate();
        setupLogger("dconnect.manager");
        setupLogger("dconnect.server");
        setupLogger("mixed-replace-media");
        setupLogger("org.deviceconnect.dplugin");
        setupLogger("org.deviceconnect.localoauth");
        setupLogger("LocalCA");

        initialize();
    }

    private void setupLogger(final String name) {
        Logger logger = Logger.getLogger(name);
        if (BuildConfig.DEBUG) {
            AndroidHandler handler = new AndroidHandler(logger.getName());
            handler.setFormatter(new SimpleFormatter());
            handler.setLevel(Level.ALL);
            logger.addHandler(handler);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);
        } else {
            logger.setLevel(Level.OFF);
            logger.setFilter((record) -> false);
        }
    }

    private void initialize() {
        SharedPreferences sp = getSharedPreferences(getPackageName() + "_preferences", Context.MODE_PRIVATE);

        String name = sp.getString(getString(R.string.key_settings_dconn_name), null);
        if (name == null) {
            SharedPreferences.Editor editor = sp.edit();
            editor.putString(getString(R.string.key_settings_dconn_name), DConnectUtil.createName());
            editor.apply();
        }

        String uuid = sp.getString(getString(R.string.key_settings_dconn_uuid), null);
        if (uuid == null) {
            SharedPreferences.Editor editor = sp.edit();
            editor.putString(getString(R.string.key_settings_dconn_uuid), DConnectUtil.createUuid());
            editor.apply();
        }

        Context appContext = getApplicationContext();
        mSettings = new DConnectSettings(appContext);
        mPluginManager = new DevicePluginManager(appContext, DConnectConst.LOCALHOST_DCONNECT);
        mIsForeground = false;
        registerActivityLifecycleCallbacks(this);
    }
    @Override
    public void onTerminate() {
        unregisterActivityLifecycleCallbacks(this);
        super.onTerminate();
    }
    public DConnectSettings getSettings() {
        return mSettings;
    }

    public DevicePluginManager getPluginManager() {
        return mPluginManager;
    }

    public boolean isForground() {
        return mIsForeground;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if ("setting.ServiceListActivity".equals(activity.getLocalClassName())) {
            mIsForeground = true;
        }
        super.onActivityStarted(activity);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if ("setting.ServiceListActivity".equals(activity.getLocalClassName())) {
            mIsForeground = true;
        }
        super.onActivityResumed(activity);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if ("setting.ServiceListActivity".equals(activity.getLocalClassName())) {
            mIsForeground = false;
        }
        super.onActivityDestroyed(activity);

    }
}
