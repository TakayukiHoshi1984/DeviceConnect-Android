package org.deviceconnect.android.streaming.redirect;

import android.util.Log;

import net.majorkernelpanic.streaming.BuildConfig;
import net.majorkernelpanic.streaming.video.VideoStream;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;

public class RedirectVideoStream extends VideoStream {
    private byte[] mRtpBuf = new byte[4096];
    private byte[] mRtpRtcpBuf = new byte[4096];
    /** RTSPのリソースデータをGatewayに転送するためのSocket. */
    protected MulticastSocket mRTPSocket;
    protected  MulticastSocket mRTPRtcpSocket;
    /** RTSPのリソースデータを詰め込むPacket. */
    protected DatagramPacket mRTPPacket;
    protected DatagramPacket mRTPRtcpPacket;

    public RedirectVideoStream() {
        try {
            mRTPSocket = new MulticastSocket();
            mRTPSocket.setTimeToLive(64);
            mRTPSocket.setSoTimeout(5000);

            mRTPRtcpSocket = new MulticastSocket();
            mRTPRtcpSocket.setTimeToLive(64);
            mRTPRtcpSocket.setSoTimeout(5000);

        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "sender init error", e);
            }
        }
        mRTPPacket = new DatagramPacket(mRtpBuf, mRtpBuf.length);
        mRTPRtcpPacket = new DatagramPacket(mRtpRtcpBuf, mRtpRtcpBuf.length);
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
            String ip = mDestination != null ? mDestination.getHostAddress() : "127.0.0.1";
            mRTPPacket.setAddress(InetAddress.getByName(ip));
            mRTPPacket.setPort(mRtpPort);
            mRTPSocket.setReceiveBufferSize(4096);
            mRTPRtcpPacket.setAddress(InetAddress.getByName(ip));
            mRTPRtcpPacket.setPort(mRtcpPort);
            mRTPRtcpSocket.setReceiveBufferSize(4096);
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
            if (mRTPRtcpSocket != null) {
                mRTPRtcpSocket.close();
                mRTPRtcpSocket = null;
            }

            mPacketizer.stop();
            mStreaming = false;
        }
    }

    public synchronized void sendFrame(final byte[] data) {
        if (mRTPSocket == null || mRTPPacket == null || data == null) {
            return;
        }
        if (mRTPSocket.getInetAddress() == null) {
            return;
        }

        mRTPPacket.setData(data);
        mRTPPacket.setLength(data.length);
        try {
            mRTPSocket.send(mRTPPacket);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "UDP wrote packet: error", e);
            }

        }
    }
    public synchronized void sendRtcpFrame(byte[] data) {
        if (mRTPRtcpSocket == null || mRTPRtcpPacket == null || data == null) {
            return;
        }
        if (mRTPSocket.getInetAddress() == null) {
            return;
        }
        mRTPRtcpPacket.setData(data);
        mRTPRtcpPacket.setLength(data.length);
        try {
            mRTPRtcpSocket.send(mRTPRtcpPacket);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "UDP wrote packet: error", e);
            }

        }
    }
}
