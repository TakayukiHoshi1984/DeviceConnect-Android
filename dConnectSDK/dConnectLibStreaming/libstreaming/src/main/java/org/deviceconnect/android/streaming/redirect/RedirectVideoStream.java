package org.deviceconnect.android.streaming.redirect;

import android.util.Log;

import net.majorkernelpanic.streaming.BuildConfig;
import net.majorkernelpanic.streaming.video.VideoStream;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class RedirectVideoStream extends VideoStream {
    /** RTSPの音声リソースが詰め込むバイト配列. */
    private byte[] mRtpBuf = new byte[4096];
    /** RTSPのリソースデータをGatewayに転送するためのSocket. */
    private  DatagramSocket mRTPSocket;
    /** RTSPのリソースデータを詰め込むPacket. */
    private  DatagramPacket mRTPPacket;

    public RedirectVideoStream() {
    }

    @Override
    protected void encodeWithMediaCodec() throws IOException {


    }
    public synchronized void configure() throws IllegalStateException, IOException {
        super.configure();
        mPacketizer = new RedirectPacketizer();
        mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
        encodeWithMediaCodec();
    }

    @Override
    public String getSessionDescription() throws IllegalStateException {
        return "m=video " + String.valueOf(getDestinationPorts()[0]) + " RTP/AVP 96\r\n" +
                "a=rtpmap:96 H264/90000\r\n" +
                "a=fmtp:96 packetization-mode=1;profile-level-id=42c029;sprop-parameter-sets=Z0LAKY1oHgUaAeEQjUA=,aM4BqDXI\r\n";

    }

    @Override
    public synchronized void start() throws IllegalStateException, IOException {
        if (!mStreaming) {
            try {
                mRTPSocket = new DatagramSocket();
                mRTPPacket = new DatagramPacket(mRtpBuf, mRtpBuf.length);
                String ip = mDestination != null ? mDestination.getHostAddress() : "127.0.0.1";
                mRTPPacket.setAddress(InetAddress.getByName(ip));
                mRTPPacket.setPort(mRtpPort);
                mRTPSocket.setReceiveBufferSize(mRTPSocket.getReceiveBufferSize() * 5000);

            } catch (Exception e) {
//            if (BuildConfig.DEBUG) {
                Log.e("ABC", "sender init error", e);
//            }
            }
        }
    }
    /** Stops the stream. */
    @Override
    public synchronized void stop() {
        if (mStreaming) {
            if (mRTPSocket != null) {
                mRTPSocket.close();
                mRTPSocket = null;
            }
            mPacketizer.stop();
            mStreaming = false;
        }
    }

    public void sendFrame(final byte[] data) {
        if (mRTPSocket == null || mRTPPacket == null || data == null) {
            return;
        }

        mRTPPacket.setData(data);
        mRTPPacket.setLength(data.length);
        try {
            mRTPSocket.send(mRTPPacket);
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e("ABC", "UDP wrote packet: error", e);
            }

        }
    }
}
