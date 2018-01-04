/*
 SignalingFirebaseClient.java
 Copyright (c) 2018 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.webrtc.core;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import org.deviceconnect.android.deviceplugin.webrtc.BuildConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * シグナリングサーバーと通信を行うクライアント.
 * FirebaseのRealtimedatabaseがその役割を持つ.
 * @author NTT DOCOMO, INC.
 */
public final class SignalingFirebaseClient implements Signaling {

    /**
     * Tag for debugging.
     */
    private static final String TAG = "FIREBASE";

    /**
     * Realtime Database Root Path.
     */
    private static final String DATABASE_ROOT = "deviceconnect/multi/";

    /**
     * Peer Id.
     */
    private String mClientId;

    /**
     * Room Name.
     */
    private String mRoomName;

    /**
     * Disconnect flag.
     */
    private boolean mDisconnectFlag;

    /**
     * Queue that stores messages that send to the server.
     */
    private List<String> mQueueMessage = new ArrayList<>();

    /**
     * Handler.
     */
    private Handler mHandler = new Handler(Looper.getMainLooper());

    /**
     * Instance of Runnable that will send message to the server.
     */
    private Runnable mSendingRun;

    /**
     * Callbacks of SignalingClient.
     */
    private OnFirebaseSignalingCallback mSignalingCallback;

    /**
     * Peer List waiting for connection.
     */
    private DatabaseReference mJoin;
    /**
     * List of IDs for which WebRTC is waiting for acceptance.
     */
    private DatabaseReference mBroadcast;
    /**
     * List under direct connection establishment.
     */
    private DatabaseReference mDirect;
    /**
     * Instance of FirebaseDatabase.
     */
    private FirebaseDatabase mDatabase;


    /**
     * Constructor.
     * @param roomName Name of room to prepare for communication
     */
    SignalingFirebaseClient(final String roomName) {
        mDatabase = FirebaseDatabase.getInstance();

        mRoomName = roomName;
        // Register your ID
        mJoin = mDatabase.getReference(DATABASE_ROOT + "/" + mRoomName + "/_join_/");
        DatabaseReference newJoin = mJoin.push();
        Map<String, Object> joinUser = new HashMap<String, Object>();
        joinUser.put("joined", "unknown");
        newJoin.setValue(joinUser);
        String key = newJoin.getKey();
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "client key:" + key);
        }
        mClientId = key;
        joinUser.put("joined", mClientId);
        mDatabase.getReference(DATABASE_ROOT + "/" + mRoomName + "/_join_/" + key).updateChildren(joinUser);

        // List of IDs for which WebRTC is waiting for acceptance
        mBroadcast = mDatabase.getReference(DATABASE_ROOT + "/" + mRoomName + "/_broadcast_/");
        mBroadcast.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                Message message = dataSnapshot.getValue(Message.class);
                String from = message.from;
                String type = message.type;
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "broadcast message from:" + from  +" type:" + type);
                }
                if (from.equals(mClientId)) {
                    return;
                }
                if (type.equals("call me")) {
                    // TODO Many-to-many connection
                } else if (type.equals("bye")) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "bye-bye:" + from);
                    }
                    if (mSignalingCallback != null) {
                        mSignalingCallback.onDisconnect(from);
                    }
                }
                DatabaseReference messageRef = mDatabase.getReference(DATABASE_ROOT + "/" + mRoomName + "/_broadcast_/");
                messageRef.removeValue();
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {}

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {}

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        });
        // List under direct connection establishment
        mDirect = mDatabase.getReference(DATABASE_ROOT + "/" + roomName + "/_direct_/" + mClientId);
        mDirect.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {

                Message message = dataSnapshot.getValue(Message.class);
                String from = message.from;
                String type = message.type;
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "======direct message from:" + from  +" type:" + type);
                }

                if (type.equalsIgnoreCase("offer")) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "OFFER======direct message from:" + from  +" type:" + type);
                    }
                    if (mSignalingCallback != null) {
                        mSignalingCallback.onOffer(from, message.sdp);
                    }
                } else if (type.equalsIgnoreCase("answer")) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "ANSWER======direct message from:" + from  +" type:" + type);
                    }
                    if (mSignalingCallback != null) {
                        mSignalingCallback.onAnswer(from, message.sdp);
                    }
                } else if (type.equalsIgnoreCase("candidate")) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "CANDIDATE======direct message from:" + from  +" type:" + type);
                    }
                    if (mSignalingCallback != null) {
                        mSignalingCallback.onCandidate(from, message.ice);
                    }
                }
                DatabaseReference messageRef = mDatabase.getReference(DATABASE_ROOT + "/" + roomName + "/_direct_/" + mClientId + "/" + dataSnapshot.getKey());
                messageRef.removeValue();
             }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        mSendingRun = new Runnable() {
            @Override
            public void run() {
                sendMessage();
            }
        };
        mDisconnectFlag = false;
    }


    @Override
    public void disconnect() {
        if (!mDisconnectFlag) {
            mDatabase.getReference(DATABASE_ROOT + "/" + mRoomName + "/_join_/" + mClientId).removeValue();
            mDisconnectFlag = true;
            mBroadcast = null;
            mJoin = null;
            mDirect = null;

            if (mSignalingCallback != null) {
                mSignalingCallback.onDisconnect(mClientId);
            }
        }
    }

    @Override
    public void destroy() {
        disconnect();
    }

    @Override
    public boolean isDisconnectFlag() {
        return mDisconnectFlag;
    }

    @Override
    public boolean isOpen() {
        return mDirect != null;
    }

    @Override
    public String getId() {
        return mClientId;
    }

    @Override
    public void queueMessage(final String message) {
        boolean firstTime = false;
        synchronized (mQueueMessage) {
            if (mQueueMessage.isEmpty()) {
                firstTime = true;
            }
            mQueueMessage.add(message);
        }

        if (firstTime && mSendingRun != null) {
            mHandler.postDelayed(mSendingRun, 100L);
        }
    }

    @Override
    public void setOnSignalingCallback(final OnFirebaseSignalingCallback callback) {
        mSignalingCallback = callback;
    }

    @Override
    public void listAllPeers(final OnAllPeersCallback callback) {
        Query query = mJoin.orderByKey();
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                JSONArray jsons = new JSONArray();
                for (DataSnapshot snapshot: dataSnapshot.getChildren()) {
                    String key = snapshot.getKey();
                    jsons.put(key);
                }
                if (jsons.length() > 0) {
                    callback.onCallback(jsons);
                } else {
                    callback.onErrorCallback();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                callback.onErrorCallback();
            }
        });
    }



    private void emitRoom(final String type) {
        Message message = new Message(mClientId, type, null, null);
        mBroadcast.push().setValue(message);
    }

    private void emitTo(final String id, final String type, final String sdp, final String ice) {
        Message message = new Message(mClientId, type, sdp, ice);
        mDatabase.getReference(DATABASE_ROOT + "/" + mRoomName + "/_direct_/" + id).push().setValue(message);
    }


    /**
     * Sends a message to the server.
     */
    private void sendMessage() {
        if (!mQueueMessage.isEmpty()) {
            boolean continues = false;
            String msg = null;

            synchronized (mQueueMessage) {
                if (!mQueueMessage.isEmpty()) {
                    msg = mQueueMessage.remove(0);
                }
            }

            if (msg != null) {
                try {
                    JSONObject jsonObj = new JSONObject(msg);
                    String sdp = null;
                    String ice = null;
                    if (!jsonObj.isNull("sdp")) {
                        sdp = jsonObj.getString("sdp");
                    }
                    if (!jsonObj.isNull("candidate")) {
                        ice = jsonObj.getString("candidate");
                    }
                    emitTo(jsonObj.getString("from"),
                            jsonObj.getString("type"),
                            sdp,
                            ice);
                } catch (JSONException e) {
                    Log.e(TAG, "JSON parse error", e);
                }
                synchronized (mQueueMessage) {
                    continues = !mQueueMessage.isEmpty();
                }
            }

            if (continues && mSendingRun != null) {
                mHandler.postDelayed(mSendingRun, 100L);
            }
        }
    }
}
