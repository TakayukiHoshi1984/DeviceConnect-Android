/*
 NsdHelper.java
 Copyright (c) 2017 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.manager.util;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.util.Log;

import org.deviceconnect.android.manager.BuildConfig;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * NsdManagerを使用するためのヘルパークラス.
 */
public class NsdHelper {
    /**
     * デバッグフラグ.
     */
    private static final boolean DEBUG = BuildConfig.DEBUG;

    /**
     * デバッグタグ.
     */
    private static final String TAG = "DeviceConnect";

    /**
     * NsdManagerのインスタンス.
     */
    private NsdManager mNsdManager;

    /**
     * コンストラクタ.
     * @param context コンテキスト
     */
    public NsdHelper(final Context context) {
        if (Build.VERSION.SDK_INT >= 16) {
            mNsdManager = (NsdManager) context.getApplicationContext().getSystemService(Context.NSD_SERVICE);
        } else {
            if (DEBUG) {
                Log.w(TAG, "Not support the NsdManager.");
            }
        }
    }

    /**
     * サービスを登録して、Managerを検索できるようにします.
     * @param port ポート番号
     */
    public void registerService(final int port) {
        if (Build.VERSION.SDK_INT >= 16) {
            InetAddress addr = getIpAddress();
            if (addr != null) {
                NsdServiceInfo serviceInfo = new NsdServiceInfo();
                serviceInfo.setServiceName("DC:" + addr);
                serviceInfo.setServiceType("_http._tcp.");
                serviceInfo.setPort(port);
                serviceInfo.setHost(addr);
                mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
            }
        }
    }

    /**
     * サービスを解除します.
     */
    public void unregisterService() {
        if (Build.VERSION.SDK_INT >= 16) {
            mNsdManager.unregisterService(mRegistrationListener);
        }
    }

    /**
     * IPアドレスを取得します.
     * @return IPアドレス
     */
    private static InetAddress getIpAddress() {
        Enumeration<NetworkInterface> enumeration;
        try {
            enumeration = NetworkInterface.getNetworkInterfaces();
            while(enumeration.hasMoreElements()) {
                NetworkInterface netIf = enumeration.nextElement();
                Enumeration<InetAddress> ipAddrs = netIf.getInetAddresses();

                while(ipAddrs.hasMoreElements()) {
                    InetAddress inetAddress = ipAddrs.nextElement();
                    if(!inetAddress.isLoopbackAddress() && netIf.isUp()) {
                        String networkInterfaceName = netIf.getName();
                        if (networkInterfaceName.contains("wlan0")) {
                            return inetAddress;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * NsdManagerへの登録状態の通知を受け取るリスナー.
     */
    private final NsdManager.RegistrationListener mRegistrationListener = new NsdManager.RegistrationListener() {
        @Override
        public void onServiceRegistered(final NsdServiceInfo serviceInfo) {
            if (DEBUG) {
                Log.i(TAG, "onServiceRegistered: " + serviceInfo);
            }
        }

        @Override
        public void onRegistrationFailed(final NsdServiceInfo serviceInfo, final int errorCode) {
            if (DEBUG) {
                Log.i(TAG, "onServiceRegistered: " + serviceInfo);
            }
        }

        @Override
        public void onServiceUnregistered(final NsdServiceInfo arg0) {
        }

        @Override
        public void onUnregistrationFailed(final NsdServiceInfo serviceInfo, final int errorCode) {
        }
    };
}
