/*
 Signaling.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.webrtc.core;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * シグナリングサーバーと通信を行うインタフェース.
 *
 * @author NTT DOCOMO, INC.
 */
interface Signaling {


    /**
     * This interface is used to implement {@link Signaling} callbacks.
     */
    interface OnAllPeersCallback {
        void onCallback(JSONArray result);
        void onErrorCallback();
    }

    /**
     * This interface is used to implement {@link Signaling} callbacks.
     */
    interface OnFirebaseSignalingCallback {
        void onClose();
        void onOffer(String from, String sdp);
        void onAnswer(String from, String sdp);
        void onCandidate(String from, String ice);
        void onDisconnect(final String from);
        void onError(String message);
    }

    /**
     * Disconnect from peer server.
     */
    void disconnect();
    /**
     * Destroys this instance.
     */
    void destroy();
    /**
     * Returns true if disconnected to the server.
     * @return true if disconnected to the server, false otherwise
     */
    boolean isDisconnectFlag();
    /**
     * Returns true if connected to the server.
     * @return true if connected to the server, false otherwise
     */
    boolean isOpen();
    /**
     * Gets my id.
     * @return id
     */
    String getId();
    /**
     * Inserts the message into the queue.
     * @param message message
     */

    void queueMessage(final String message);
    /**
     * Sets a callback.
     * @param callback callback of SignalingClient
     */
    void setOnSignalingCallback(final OnFirebaseSignalingCallback callback);
    /**
     * Retrieves the list of peer that can be connected.
     * @param callback Callback to return the list of peer
     */
    void listAllPeers(final OnAllPeersCallback callback);
}
