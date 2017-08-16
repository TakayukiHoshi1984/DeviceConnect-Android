package com.mydomain.measurement;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import org.deviceconnect.android.message.DConnectMessageService;
import org.deviceconnect.android.profile.SystemProfile;
import org.deviceconnect.android.service.DConnectService;

import com.mydomain.measurement.profiles.MyDeviceOrientationProfile;
import com.mydomain.measurement.profiles.MySystemProfile;
import org.deviceconnect.profile.ServiceDiscoveryProfileConstants.NetworkType;


public class MyMessageService extends DConnectMessageService {

    @Override
    public void onCreate() {
        super.onCreate();
        setUseLocalOAuth(BuildConfig.USES_AUTH);

        DConnectService service = new DConnectService("event-test-included" + (BuildConfig.USES_AUTH ? "-withAuth" : "-withoutAuth"));
        service.setName("included");
        service.setOnline(true);
        service.setNetworkType(NetworkType.UNKNOWN);
        service.addProfile(new MyDeviceOrientationProfile());
        getServiceProvider().addService(service);
    }

    @Override
    protected SystemProfile getSystemProfile() {
        return new MySystemProfile();
    }

    @Override
    protected void onManagerUninstalled() {
        // TODO Device Connect Managerアンインストール時に実行したい処理. 実装は任意.
    }

    @Override
    protected void onManagerTerminated() {
        // TODO Device Connect Manager停止時に実行したい処理. 実装は任意.
    }

    @Override
    protected void onManagerEventTransmitDisconnected(final String origin) {
        // TODO アプリとのWebSocket接続が切断された時に実行したい処理. 実装は任意.
    }

    @Override
    protected void onDevicePluginReset() {
        // TODO Device Connect Managerの設定画面上で「プラグイン再起動」を要求された場合の処理. 実装は任意.
    }
}