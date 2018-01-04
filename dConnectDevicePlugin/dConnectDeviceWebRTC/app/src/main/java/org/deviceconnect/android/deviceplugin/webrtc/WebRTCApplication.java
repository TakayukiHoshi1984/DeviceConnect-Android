/*
 WebRTCApplication.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.webrtc;

import android.app.Application;
import android.util.Log;

import org.deviceconnect.android.deviceplugin.webrtc.core.Peer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application for WebRTC.
 *
 * @author NTT DOCOMO, INC.
 */
public class WebRTCApplication extends Application {

    /**
     * Tag for debugging.
     */
    private static final String TAG = "WEBRTC";

    /**
     * Map that contains the PeerConfig and Peer.
     */
    private final Map<String, Peer> mPeerMap = new HashMap<>();

    /**
     * VideoChatActivity call timestamp.
     */
    private long mCallTimeStamp = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "WebRTCApplication:onCreate");
        }
    }

    @Override
    public void onTerminate() {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "WebRTCApplication:onTerminate");
        }
        destroyPeer();
        super.onTerminate();
    }

    /**
     * Gets the peer corresponding to the config.
     * @param serviceId DevicePlugin's service ID.
     * @param callback Callback to notify the Peer
     */
    public void getPeer(final String serviceId, final OnGetPeerCallback callback) {
        if (serviceId == null) {
            throw new NullPointerException("serviceId is null.");
        }

        if (callback == null) {
            throw new NullPointerException("callback is null.");
        }

        final Peer.OnConnectCallback peerCallback = new Peer.OnConnectCallback() {
            @Override
            public void onConnected(final Peer peer) {
                callback.onGetPeer(peer);
            }
            @Override
            public void onError() {
                callback.onGetPeer(null);
            }
        };

        synchronized (mPeerMap) {
            Peer peer = mPeerMap.get(serviceId);
            if (peer != null) {
                if (peer.isConnected()) {
                    callback.onGetPeer(peer);
                } else {
                    peer.connect(peerCallback);
                }
            } else {
                try {
                    peer = new Peer(getApplicationContext());
                    peer.connect(peerCallback);
                    mPeerMap.put(serviceId, peer);
                } catch (Exception e) {
                    callback.onGetPeer(null);
                }
            }
        }
    }

    /**
     * Destroy the all Peer.
     */
    public void destroyPeer() {
        synchronized (mPeerMap) {
            for (Map.Entry<String, Peer> entry : mPeerMap.entrySet()) {
                entry.getValue().destroy();
                entry.getValue().removePeerEventListener();
            }
            mPeerMap.clear();
        }
    }

    /**
     * Checks whether device has a video chat.
     * @return true if device has a video chat, false otherwise
     */
    public boolean isConnected() {
        synchronized (mPeerMap) {
            for (Map.Entry<String, Peer> entry : mPeerMap.entrySet()) {
                if (entry.getValue().hasConnections()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Destroy the Peer corresponding to the serviceId.
     * @param serviceId serviceId
     */
    public void destroyPeer(final String serviceId) {
        synchronized (mPeerMap) {
            Peer peer = mPeerMap.remove(serviceId);
            if (peer != null) {
                peer.destroy();
            }
        }
    }

    /**
     * Gets the list of ServiceId.
     * @return list
     */
    public List<String> getServiceIds() {
        synchronized (mPeerMap) {
            List<String> list = new ArrayList<>();

            // shadow copy
            Set<String> configs = mPeerMap.keySet();
            for (String config : configs) {
                list.add(config);
            }
            return list;
        }
    }

    /**
     * Gets the peer corresponding to the ServiceId.
     * @param serviceId serviceId
     * @return instance of Peer
     */
    public Peer getPeer(final String serviceId) {
        synchronized (mPeerMap) {
            return mPeerMap.get(serviceId);
        }
    }

    /**
     * This interface is used to implement {@link WebRTCApplication} callbacks.
     */
    public interface OnGetPeerCallback {
        /**
         * Gets the peer.
         * @param peer instance of peer
         */
        void onGetPeer(Peer peer);
    }

    /**
     * Set callTimeStamp.
     * @param timeStamp timestamp.
     */
    public void setCallTimeStamp(final long timeStamp) {
        mCallTimeStamp = timeStamp;
    }

    /**
     * Get callTimeStamp;
     * @return callTimeStamp.
     */
    public long getCallTimeStamp() {
        return mCallTimeStamp;
    }
}
