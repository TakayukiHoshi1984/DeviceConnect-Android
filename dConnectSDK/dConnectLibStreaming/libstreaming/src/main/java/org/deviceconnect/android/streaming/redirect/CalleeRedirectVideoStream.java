package org.deviceconnect.android.streaming.redirect;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import net.majorkernelpanic.streaming.BuildConfig;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.Arrays;

public class CalleeRedirectVideoStream extends RedirectVideoStream implements Runnable{
    private String mIp;
    private int mRTPPort;
    private int mRTCPPort;
    private HandlerThread mVThread;
    private final Handler mVHandler;
    private HandlerThread mVCThread;
    private final Handler mVCHandler;
    /** RTSPの映像リソースを受信するためのSocket. */
    private DatagramSocket mVideoSocket;
    private DatagramSocket mVideoRtcpSocket;
    /** RTSPの映像リソースが詰め込むPacket. */
    private DatagramPacket mVideoPacket;
    private DatagramPacket mVideoRtcpPacket;
    private byte[] mVideoBuf = new byte[4096];
    private byte[] mVideoRtcpBuf = new byte[4096];
    private Thread mThread;
    private volatile static boolean mRedirecdting;



    public CalleeRedirectVideoStream(final String ip, final int rtpPort, final int rtcpPort) {
        super();
        mIp = ip;
        mRTPPort = rtpPort;
        mRTCPPort = rtcpPort;
        mVThread = new HandlerThread("receiver-video-handler-thread");
        mVThread.start();
        mVHandler = new Handler(mVThread.getLooper());
        mVCThread = new HandlerThread("receiver-video-rtcp-handler-thread");
        mVCThread.start();
        mVCHandler = new Handler(mVCThread.getLooper());

    }
    @Override
    public String getSessionDescription() throws IllegalStateException {
        return "m=video " + String.valueOf(getDestinationPorts()[0]) + " RTP/AVP 96\r\n" +
                "a=rtpmap:96 H264/90000\r\n" +
                "a=fmtp:96 packetization-mode=1;profile-level-id=42c029;sprop-parameter-sets=Z0LAKY1oHgUaAeEQjUA=,aM4BqDXI\r\n";

    }

    @Override
    public synchronized void start()  throws IllegalStateException, IOException {
        super.start();
        if (!mRedirecdting) {
            mRedirecdting = true;
            try {
                mVideoSocket = new DatagramSocket(mRTPPort);
                mVideoRtcpSocket = new DatagramSocket(mRTCPPort);
            } catch (SocketException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "sender init error", e);
                }
                return;
            }
            mVideoPacket = new DatagramPacket(mVideoBuf, mVideoBuf.length);
            mVideoRtcpPacket = new DatagramPacket(mVideoRtcpBuf, mVideoRtcpBuf.length);

            mVideoPacket.setPort(mRTPPort);
            mVideoRtcpPacket.setPort(mRTCPPort);
            mVHandler.post(this);
            mVCHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (mRedirecdting) {
                            mVideoRtcpSocket.receive(mVideoRtcpPacket);
                            byte[] data = Arrays.copyOf(mVideoRtcpPacket.getData(), mVideoRtcpPacket.getLength());
                            CalleeRedirectVideoStream.super.sendRtcpFrame(data);
                        }

                    } catch (Exception e) {
                        if (BuildConfig.DEBUG) {
                            Log.e(TAG, "video port", e);
                        }
                    } finally {
                        if (mVideoRtcpSocket != null) {
                            mVideoRtcpSocket.close();
                            mVideoRtcpSocket = null;
                        }
                    }
                }
            });
        }
    }

    @Override
    public synchronized void stop() {
        super.stop();
        if (mRedirecdting) {
            mRedirecdting = false;
//            mVHandler.removeCallbacks(null);
            mVThread.quit();
//            mVCHandler.removeCallbacks(null);
            mVCThread.quit();
        }
    }

    @Override
    public void run() {
        try {
            while (mRedirecdting) {
                mVideoSocket.receive(mVideoPacket);
                byte[] data = Arrays.copyOf(mVideoPacket.getData(), mVideoPacket.getLength());
                super.sendFrame(data);
            }

        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "video port", e);
            }
        } finally {
            if (mVideoSocket != null) {
                mVideoSocket.close();
                mVideoSocket = null;
            }
        }
    }
}
