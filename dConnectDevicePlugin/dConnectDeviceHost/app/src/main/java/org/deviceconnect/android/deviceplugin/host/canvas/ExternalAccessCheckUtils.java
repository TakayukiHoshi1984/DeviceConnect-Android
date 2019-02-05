/*
 ExternalAccessCheckUtils.java
 Copyright (c) 2019 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.canvas;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 外部リソースにアクセスしているかを確認する.
 * @author NTT DOCOMO, INC.
 */
public final class ExternalAccessCheckUtils {
    /** URLの正規表現. */
    private static final String URL_REGEX = "^(http|https)://([\\w-]+\\.)+[\\w-]+(/[\\w-./?%&=]*)?$";
    /** コンストラクタ：未使用. */
    private ExternalAccessCheckUtils() {}

    /**
     * 外部リソースにアクセスしているかどうかを返す.
     * @param context コンテキスト
     * @param uri 判定するURI
     * @return true:外部リソースである　false:外部リソースではない
     */
    public static boolean isExternalAccessResource(final Context context, final String uri) {
        return !uri.startsWith("file://") && !uri.startsWith("http://localhost") && !uri.startsWith("https://localhost")
                && !uri.startsWith("http://127.0.0.1") && !uri.startsWith("https://127.0.0.1")
                && !uri.startsWith("http://" + getIPAddress(context))
                && Pattern.compile(URL_REGEX).matcher(uri).matches();

    }

    /**
     * この端末のIPアドレスを取得する.
     * @param context コンテキスト
     * @return IPアドレス
     */
    private static String getIPAddress(final Context context) {
        Context appContext = context.getApplicationContext();
        WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager cManager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network = cManager.getActiveNetworkInfo();
        String en0Ip = null;
        if (network != null) {
            switch (network.getType()) {
                case ConnectivityManager.TYPE_ETHERNET:
                    try {
                        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                            NetworkInterface intf = en.nextElement();
                            for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                                InetAddress inetAddress = enumIpAddr.nextElement();
                                if (inetAddress instanceof Inet4Address
                                        && !inetAddress.getHostAddress().equals("127.0.0.1")) {
                                    en0Ip = inetAddress.getHostAddress();
                                    break;
                                }
                            }
                        }
                    } catch (SocketException e) {
                        Log.e("CanvasUtil", "Get Ethernet IP Error", e);
                    }
            }
        }

        if (en0Ip != null) {
            return en0Ip;
        } else {
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            return String.format(Locale.getDefault(), "%d.%d.%d.%d",
                    (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
                    (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        }
    }
}
