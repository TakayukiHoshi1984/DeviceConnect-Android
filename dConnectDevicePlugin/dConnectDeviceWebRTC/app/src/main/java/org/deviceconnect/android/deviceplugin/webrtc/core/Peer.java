/*
 Peer.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.webrtc.core;

import android.content.Context;
import android.util.Log;

import org.deviceconnect.android.deviceplugin.webrtc.BuildConfig;
import org.deviceconnect.android.profile.VideoChatProfileConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.voiceengine.WebRtcAudioManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connection of WebRTC.
 *
 * @author NTT DOCOMO, INC.
 */
public class Peer {
    /**
     * Tag for debugging.
     */
    private static final String TAG = "WebRTC";

    /**
     * Define the parameter of WebRTC.
     */
    private static final String FIELD_TRIAL_AUTOMATIC_RESIZE = "WebRTC-MediaCodecVideoEncoder-AutomaticResize/Enabled/";

    /**
     * Room Name.
     */
    private static final String WEBRTC_ROOM_NAME = "room1";

    /**
     * Instance of PeerConnectionFactory.
     */
    private PeerConnectionFactory mFactory;

    /**
     * Application context.
     */
    private Context mContext;

    /**
     * シグナリングサーバとの通信を行うクラス。
     */
    private Signaling mSignaling;

    /**
     * Peer's id.
     */
    private String mPeerId;

    /**
     * Peerのイベントを通知するリスナー.
     */
    private PeerEventListener mEventListener;

    /**
     * Map that contains the offer message.
     */
    private Map<String, String> mOfferMap = new ConcurrentHashMap<>();

    /**
     * Map that contains the MediaConnection.
     */
    private Map<String, MediaConnection> mConnections = new ConcurrentHashMap<>();

    /**
     * Constructor.
     * @param context context
     */
    public Peer(final Context context) {
        if (context == null) {
            throw new NullPointerException("context is null.");
        }

        mContext = context;
        initPeerConnectionFactory();
    }

    /**
     * Retrieves the id of Peer.
     * @return peer's id
     */
    public String getMyAddressId() {
        return mPeerId;
    }

    /**
     * Destroy a instance of Peer.
     */
    public synchronized void destroy() {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "@@ Peer::destroy");
        }

        if (mSignaling != null) {
            mSignaling.destroy();
            mSignaling = null;
        }

        synchronized (mConnections) {
            for (MediaConnection conn : mConnections.values()) {
                conn.close();
            }
            mConnections.clear();
        }

        if (mFactory != null) {
            mFactory.dispose();
            mFactory = null;
        }
    }

    /**
     * Sets an event listener.
     * @param listener listener
     */
    public void setPeerEventListener(final PeerEventListener listener) {
        mEventListener = listener;
    }

    /**
     * Removes an event listener.
     */
    public void removePeerEventListener() {
        mEventListener = null;
    }

    /**
     * Returns whether the peer has a offer from addressId.
     * @param addressId id of address
     * @return {@code true} if this peer has  a offer from addressId, {@code false} otherwise.
     */
    public boolean hasOffer(final String addressId) {
        return mOfferMap.containsKey(addressId);
    }

    public void setVideoHwAccelerationOptions(EglBase.Context renderEGLContext) {
        mFactory.setVideoHwAccelerationOptions(renderEGLContext, renderEGLContext);
    }

    /**
     * Makes a call to the addressId.
     * @param addressId id of address
     * @param option options for making a call
     * @param listener
     * @return Instance of MediaConnection
     */
    public synchronized MediaConnection call(final String addressId, final PeerOption option,
                                             final MediaConnection.OnMediaEventListener listener) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "@@ Peer::call");
            Log.d(TAG, "@@ address: " + addressId);
        }

        if (addressId == null) {
            throw new NullPointerException("address is null.");
        }

        if (option == null) {
            throw new NullPointerException("option is null.");
        }

        if (mSignaling.isDisconnectFlag()) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "@@ websocket had disconnected.");
            }
            return null;
        } else {
            MediaStream stream = createMediaStream(option);
            if (stream == null) {
                Log.e(TAG, "@@ Failed to create a MediaStream.");
                return null;
            }

            MediaConnection conn = createMediaConnection(addressId, option);
            conn.setOnMediaEventListener(listener);
            conn.setLocalMediaStream(stream);
            conn.createOffer();
            mConnections.put(addressId, conn);
            if (mEventListener != null) {
                Address address = getAddress(addressId);
                mEventListener.onCalling(Peer.this, address);
            }
            return conn;
        }
    }

    /**
     * Takes a phone call to the addressId.
     *
     *
     * @param addressId id of address
     * @param option options for take a call
     * @param listener
     * @return Instance of MediaConnection
     */
    public synchronized MediaConnection answer(final String addressId, final PeerOption option,
                                               final MediaConnection.OnMediaEventListener listener) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "@@ Peer::answer");
            Log.d(TAG, "@@ address: " + addressId);
        }

        if (addressId == null) {
            throw new NullPointerException("address is null.");
        }

        if (option == null) {
            throw new NullPointerException("option is null.");
        }

        String sdp = mOfferMap.remove(addressId);
        if (sdp == null) {
            Log.w(TAG, "@@ do not have an offer.");
            return null;
        }

        if (mSignaling.isDisconnectFlag()) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "@@ websocket had disconnected.");
            }
            return null;
        } else {
            MediaStream stream = createMediaStream(option);
            if (stream == null) {
                Log.e(TAG, "@@ Failed to create a MediaStream.");
                return null;
            }

            MediaConnection conn = createMediaConnection(addressId, option);
            conn.setOnMediaEventListener(listener);
            conn.setLocalMediaStream(stream);
            conn.createAnswer(sdp);
            mConnections.put(addressId, conn);
            return conn;
        }
    }

    /**
     * Hang up a call to the address.
     * @param addressId id of address
     * @return {@code true} if hang up a call, {@code false} otherwise.
     */
    public synchronized boolean hangup(final String addressId) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "@@ Peer::hangup");
        }

        synchronized (mConnections) {
            MediaConnection conn = mConnections.remove(addressId);
            if (conn != null) {
                conn.close();
                return true;
            }
        }
        if (mEventListener != null) {
            Address address = getAddress(addressId);
            mEventListener.onHangup(Peer.this, address);
        }
        return false;
    }

    /**
     * Returns whether this peer has a MediaConnection.
     * @return {@code true} if this peer has a MediaConnection, {@code false} otherwise.
     */
    public boolean hasConnections() {
        return !mConnections.isEmpty();
    }

    /**
     * Returns whether this peer is connected to the server.
     * @return {@code true} if this peer is connected, {@code false} otherwise.
     */
    public boolean isConnected() {
        if (mSignaling != null) {
            return mSignaling.isOpen();
        }
        return false;
    }

    /**
     * Retrieve the list of ids from peer server.
     * @param callback callback of notify the list
     */
    public void getListPeerList(final OnGetAddressCallback callback) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "@@ getListPeerList");
        }

        mSignaling.listAllPeers(new Signaling.OnAllPeersCallback() {
            @Override
            public void onCallback(JSONArray peers) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "@@  Response: " + peers);
                }

                List<Address> addressList = new ArrayList<>();
                for (int i = 0; i < peers.length(); i++) {
                    String strValue;
                    try {
                        strValue = peers.getString(i);
                    } catch (JSONException e) {
                        continue;
                    }
                    // if id is myself, not included int the list
                    if (mPeerId.equalsIgnoreCase(strValue)) {
                        continue;
                    }

                    Address address = new Address();
                    address.setAddressId(strValue);
                    address.setName(strValue);
                    if (mConnections.containsKey(strValue)) {
                        address.setState(VideoChatProfileConstants.State.TALKING);
                    } else if (mOfferMap.containsKey(strValue)) {
                        address.setState(VideoChatProfileConstants.State.INCOMING);
                    } else {
                        address.setState(VideoChatProfileConstants.State.IDLE);
                    }
                    addressList.add(address);
                }

                if (callback != null) {
                    callback.onGetAddresses(addressList);
                }
            }

            @Override
            public void onErrorCallback() {
                if (callback != null) {
                    callback.onGetAddresses(null);
                }
            }
        });
    }

    /**
     * Connect to the server that performs signaling.
     * @param callback callback that notifies the state of connection
     */
    public void connect(final OnConnectCallback callback) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "@@@ connect");
        }

        mSignaling = new SignalingFirebaseClient(WEBRTC_ROOM_NAME);
        mSignaling.setOnSignalingCallback(new Signaling.OnFirebaseSignalingCallback() {

            @Override
            public void onClose() {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "@@@ onClose");
                }
            }

            @Override
            public void onOffer(final String from, final String sdp) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "@@@ onOffer from:" + from + "\n" + sdp);
                }
                mOfferMap.put(from, sdp);
                if (mEventListener != null) {
                    Address address = getAddress(from);
                    mEventListener.onIncoming(Peer.this, address);
                }
            }

            @Override
            public void onAnswer(final String from, final String sdp) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "@@@ onAnswer from:" + from + "\n" + sdp);
                }
                MediaConnection mc = getConnection(from);
                if (mc != null) {
                    mc.handleAnswer(sdp);
                }
            }

            @Override
            public void onCandidate(final String from, final String ice) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "@@@ onCandidate from:" + from + "\n"  + ice);
                }

                MediaConnection mc = getConnection(from);
                if (mc != null) {
                    mc.handleCandidate(ice);
                }
            }

            @Override
            public void onDisconnect(final String from) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "@@@@ onDisconnect");
                }
                hangup(from);
            }

            @Override
            public void onError(final String message) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "@@@ Signaling Server Error: " + message);
                }
                if (callback != null) {
                    callback.onError();
                }
            }
        });
        mPeerId = mSignaling.getId();
        if (callback != null) {
            callback.onConnected(Peer.this);
        }

    }

    /**
     * Create a local media stream.
     * @param option option
     * @return MediaStream
     */
    public MediaStream createMediaStream(final PeerOption option) {
        MediaStream stream = new MediaStream(mFactory, option);
        return stream;
    }

    /**
     * Initializes the PeerConnectionFactory.
     * @throws RuntimeException occurs if failed to create a PeerConnectionFactory
     */
    private void initPeerConnectionFactory() {
        if (mFactory == null) {
            boolean enableAudio = true;
            boolean enableVideo = true;
            boolean enableHWCodec = false;
            if (enableHWCodec) {
                enableHWCodec = PeerUtil.validateHWCodec();
            }

            if (BuildConfig.DEBUG) {
                Log.i(TAG, "initPeerConnectionFactory(enableAudio=" + enableAudio + " enableVideo="
                        + enableVideo + " enableHWCodec=" + enableHWCodec + ")");
            }

            PeerConnectionFactory.initializeFieldTrials(FIELD_TRIAL_AUTOMATIC_RESIZE);
            WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
            boolean result = PeerConnectionFactory.initializeAndroidGlobals(mContext,
                    enableAudio, enableVideo, enableHWCodec);
            if (!result) {
                throw new RuntimeException("PeerConnectionFactory global initialize is failed.");
            }

            try {
                mFactory = new PeerConnectionFactory();
            } catch (Exception e) {
                throw new RuntimeException("PeerConnectionFactory global initialize is failed.", e);
            }
        } else {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "PeerConnectionFactory already exists.");
            }
        }
    }

    /**
     * Retrieve the connection by address.
     * @param address address
     * @return MediaConnection
     */
    private MediaConnection getConnection(final String address) {
        return mConnections.get(address);
    }

    /**
     * Create a media connection.
     * @param addressId address
     * @param option option
     * @return null if failed to create a MediaConnection.
     */
    private MediaConnection createMediaConnection(final String addressId, final PeerOption option) {
        try {
            MediaConnection connection = new MediaConnection(mFactory, option);
            connection.setPeerId(addressId);
            connection.setOnConnectionCallback(mConnectionCallback);
            return connection;
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to create a media connection.", e);
            }
            return null;
        }
    }

    /**
     * Gets a Address by addressId.
     * @param addressId if of address
     * @return Address
     */
    private Address getAddress(final String addressId) {
        Address address = new Address();
        address.setAddressId(addressId);
        address.setName(addressId);
        address.setState(VideoChatProfileConstants.State.INCOMING);
        return address;
    }

    /**
     * Implementation of OnMediaConnectionCallback.
     */
    private MediaConnection.OnMediaConnectionCallback mConnectionCallback = new MediaConnection.OnMediaConnectionCallback() {
        @Override
        public void onLocalDescription(final MediaConnection conn, final SessionDescription sdp) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "@@@ onLocalDescription sdp type:" + sdp.type);
            }

            String type;
            switch (sdp.type) {
                case OFFER:
                    type = "offer";
                    break;
                case ANSWER:
                    type = "answer";
                    break;
                default:
                    type = "";
                    break;
            }

            try {
                JSONObject sdpMsg = new JSONObject();
                sdpMsg.put("sdp", sdp.description);
                sdpMsg.put("type", type);
                sdpMsg.put("from", conn.getPeerId());
                mSignaling.queueMessage(sdpMsg.toString());
            } catch (Exception e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Failed to create a message that send to a signaling server.", e);
                }
            }
        }

        @Override
        public void onIceCandidate(final MediaConnection conn, final IceCandidate candidate) {
            try {
                JSONObject candidateMsg = new JSONObject();
                candidateMsg.put("candidate", candidate.sdp);
                candidateMsg.put("sdpMLineIndex", candidate.sdpMLineIndex);
                candidateMsg.put("sdpMid", candidate.sdpMid);

                JSONObject payload = new JSONObject();
                payload.put("candidate", candidateMsg);
                payload.put("type", "candidate");
                payload.put("from", conn.getPeerId());
                mSignaling.queueMessage(payload.toString());
            } catch (Exception e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Failed to create a message that send to a signaling server.", e);
                }
            }
        }

        @Override
        public void onClose(final MediaConnection conn) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "@@@ onClose: " + conn.getPeerId());
            }
            mConnections.remove(conn.getPeerId());
        }

        @Override
        public void onError(final MediaConnection conn) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "@@@ onError: " + conn.getPeerId());
            }
        }
    };

    /**
     * This interface is used to implement {@link Peer} callbacks.
     */
    public interface PeerEventListener {
        void onIncoming(Peer core, Address address);
        void onHangup(Peer core, Address address);
        void onCalling(Peer core, Address address);
    }

    /**
     * This interface is used to implement {@link Peer} callbacks.
     */
    public interface OnConnectCallback {
        void onConnected(Peer core);
        void onError();
    }

    /**
     * This interface is used to implement {@link Peer} callbacks.
     */
    public interface OnGetAddressCallback {
        void onGetAddresses(List<Address> addresses);
    }
}
