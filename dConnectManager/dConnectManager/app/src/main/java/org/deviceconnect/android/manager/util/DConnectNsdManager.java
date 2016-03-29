package org.deviceconnect.android.manager.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import org.deviceconnect.android.manager.BuildConfig;

@TargetApi(16)
public class DConnectNsdManager {
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String TAG = "Nsd";

    private static final String SERVICE_TYPE = "_http._tcp";
    private static final String SERVICE_NAME = "DeviceConnectManager";

    private NsdManager mNsdManager;
    private Context mContext;

    public DConnectNsdManager(final Context context) {
        mContext = context;
    }

    public void registerService(final int port) {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(createServiceName());
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);

        mNsdManager = (NsdManager) mContext.getSystemService(Context.NSD_SERVICE);
        mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
    }

    public void unregisterService() {
        if (mNsdManager != null) {
            try {
                mNsdManager.unregisterService(mRegistrationListener);
            } catch (IllegalArgumentException e) {
                if (DEBUG) {
                    Log.w(TAG, "Failed to unregister service.");
                }
            }
        }
    }

    private String createServiceName() {
        return SERVICE_NAME + "-(" + android.os.Build.MODEL + ")#(" + DConnectUtil.getIPAddress(mContext) + ")";
    }

    private final NsdManager.RegistrationListener mRegistrationListener = new NsdManager.RegistrationListener() {
        @Override
        public void onRegistrationFailed(final NsdServiceInfo serviceInfo, final int errorCode) {
            if (DEBUG) {
                Log.w(TAG, "Failed to register a service.");
                Log.w(TAG, "Service name=" + serviceInfo.getServiceName());
                Log.w(TAG, "Error code=" + errorCode);
            }
        }

        @Override
        public void onUnregistrationFailed(final NsdServiceInfo serviceInfo, final int errorCode) {
            if (DEBUG) {
                Log.w(TAG, "Failed to unregister a service.");
                Log.w(TAG, "Service name=" + serviceInfo.getServiceName());
                Log.w(TAG, "Error code=" + errorCode);
            }
        }

        @Override
        public void onServiceRegistered(final NsdServiceInfo serviceInfo) {
            if (DEBUG) {
                Log.d(TAG, "Success to register a service.");
                Log.d(TAG, "Service name=" + serviceInfo.getServiceName());
                Log.d(TAG, "Service name=" + serviceInfo);
            }
        }

        @Override
        public void onServiceUnregistered(final NsdServiceInfo serviceInfo) {
            if (DEBUG) {
                Log.d(TAG, "Success to unregister a service.");
                Log.d(TAG, "Service name=" + serviceInfo.getServiceName());
            }
        }
    };
}
