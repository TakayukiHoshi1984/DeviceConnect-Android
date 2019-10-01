package org.deviceconnect.android.streaming.redirect;

import android.util.Log;

import net.majorkernelpanic.streaming.BuildConfig;
import net.majorkernelpanic.streaming.audio.AudioStream;

import org.deviceconnect.android.streaming.opus.OpusPacketizer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class RedirectAudioStream extends AudioStream {
    private byte[] mRtpBuf = new byte[4096];
    private byte[] mRtpRtcpBuf = new byte[4096];
    /** RTSPのリソースデータをGatewayに転送するためのSocket. */
    protected  DatagramSocket mRTPSocket;
    protected  DatagramSocket mRTPRtcpSocket;
    /** RTSPのリソースデータを詰め込むPacket. */
    protected DatagramPacket mRTPPacket;
    protected DatagramPacket mRTPRtcpPacket;

    public RedirectAudioStream() {
        try {
            mRTPSocket = new DatagramSocket();
            mRTPRtcpSocket = new DatagramSocket();
        } catch (SocketException e) {
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

    @Override
    public String getSessionDescription() {
        return "m=audio "+getDestinationPorts()[0]+" RTP/AVP 111\r\n" +
                "a=rtpmap:111 opus/16000/1\r\n"+
                "a=fmtp:111 maxplaybackrate=16000; sprop-maxcapturerate=16000; maxaveragebitrate=20000; stereo=1; useinbandfec=1; usedtx=0\r\n";
    }

    public synchronized void configure() throws IllegalStateException, IOException {
        super.configure();
        mPacketizer = new RedirectPacketizer();
        mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
        encodeWithMediaCodec();
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

    public void sendFrame(byte[] data) {
        if (mRTPSocket == null || mRTPPacket == null || data == null) {
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
    public void sendRtcpFrame(byte[] data) {
        if (mRTPRtcpSocket == null || mRTPRtcpPacket == null || data == null) {
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
