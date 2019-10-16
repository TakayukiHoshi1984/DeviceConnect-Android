package org.deviceconnect.android.streaming.redirect;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import net.majorkernelpanic.streaming.BuildConfig;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;

public class CalleeRedirectAudioStream extends RedirectAudioStream implements Runnable{
    private String mIp;
    private int mRTPPort;
    private int mRTCPPort;
    /** RTSPの映像リソースを受信するためのSocket. */
    private DatagramSocket mAudioSocket;
    private DatagramSocket mAudioRtcpSocket;
    /** RTSPの映像リソースが詰め込むPacket. */
    private DatagramPacket mAudioPacket;
    private DatagramPacket mAudioRtcpPacket;
    private HandlerThread mAThread;
    private final Handler mAHandler;
    private HandlerThread mACThread;
    private final Handler mACHandler;
    private byte[] mAudioBuf = new byte[4096];
    private byte[] mAudioRtcpBuf = new byte[4096];
    private Thread mThread;
    private volatile static boolean mRedirecdting;

    public CalleeRedirectAudioStream(final String ip, final int rtpPort, final int rtcpPort) {
        super();
        mIp = ip;
        mRtpPort = rtpPort;
        mRtcpPort = rtcpPort;
        mAThread = new HandlerThread("receiver-video-handler-thread");
        mAThread.start();
        mAHandler = new Handler(mAThread.getLooper());
        mACThread = new HandlerThread("receiver-video-rtcp-handler-thread");
        mACThread.start();
        mACHandler = new Handler(mACThread.getLooper());
    }

    @Override
    public synchronized void start()  throws IllegalStateException, IOException {
        super.start();
        if (!mRedirecdting) {
            mRedirecdting = true;
            try {
                mAudioSocket = new DatagramSocket(mRtpPort);
                mAudioRtcpSocket = new DatagramSocket(mRtcpPort);
            } catch (SocketException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "sender init error", e);
                }
                return;
            }
            mAudioPacket = new DatagramPacket(mAudioBuf, mAudioBuf.length);
            mAudioRtcpPacket = new DatagramPacket(mAudioRtcpBuf, mAudioRtcpBuf.length);
            mAudioPacket.setPort(mRtpPort);
            mAudioRtcpPacket.setPort(mRtcpPort);
            mAHandler.post(this);
            mACHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (mRedirecdting) {
                            mAudioRtcpSocket.receive(mAudioRtcpPacket);
                            byte[] data = Arrays.copyOf(mAudioRtcpPacket.getData(), mAudioRtcpPacket.getLength());
                            CalleeRedirectAudioStream.super.sendRtcpFrame(data);
                        }
                    } catch (Exception e) {
                        if (BuildConfig.DEBUG) {
                            Log.e(TAG, "audio port", e);
                        }
                    } finally {
                        if (mAudioRtcpSocket != null) {
                            mAudioRtcpSocket.close();
                            mAudioRtcpSocket = null;
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
//            mAHandler.removeCallbacks(null);
            mAThread.quit();
//            mACHandler.removeCallbacks(null);
            mACThread.quit();
        }

    }

    @Override
    public void run() {
        try {
            while (mRedirecdting) {
                mAudioSocket.receive(mAudioPacket);
                byte[] data = Arrays.copyOf(mAudioPacket.getData(), mAudioPacket.getLength());
                super.sendFrame(data);
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "audio port", e);
            }
        } finally {
            if (mAudioSocket != null) {
                mAudioSocket.close();
                mAudioSocket = null;
            }
        }

    }
}
