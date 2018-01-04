/*
 Message.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.webrtc.core;

/**
 * シグナリングサーバーと通信を行う際に受け渡す情報を保持する.
 *
 * @author NTT DOCOMO, INC.
 */
class Message {
    /**
     * 宛先.
     */
    public String from;
    /**
     * メッセージのタイプ.
     */
    public String type;
    /**
     * SDP Message.
     */
    public String sdp;
    /**
     * ICE Message.
     */
    public String ice;

    /**
     * DataSnapshot.getValue(Message.class)用.
     */
    public Message() {

    }

    /**
     * コンストラクタ.
     * @param from 宛先
     * @param type メッセージタイプ
     * @param sdp SDP
     * @param ice ICE
     */
    public Message(final String from, final String type, final String sdp, final String ice) {
        this.from = from;
        this.type = type;
        this.sdp = sdp;
        this.ice = ice;
    }
}
