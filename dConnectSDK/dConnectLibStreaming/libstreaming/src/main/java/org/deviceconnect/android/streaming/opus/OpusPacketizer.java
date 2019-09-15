package org.deviceconnect.android.streaming.opus;

import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import net.majorkernelpanic.streaming.BuildConfig;
import net.majorkernelpanic.streaming.rtcp.SenderReport;
import net.majorkernelpanic.streaming.rtp.AbstractPacketizer;

import org.deviceconnect.opuscodec.MicAudioRecorder;
import org.deviceconnect.opuscodec.NativeInterfaceFailure;
import org.deviceconnect.opuscodec.OpusEncoder;
import org.deviceconnect.opuscodec.OpusUdpSender;


import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static net.majorkernelpanic.streaming.rtp.RtpSocket.MTU;

public class OpusPacketizer extends AbstractPacketizer implements MicAudioRecorder.AudioRecordCallback, Runnable {
    private final static String TAG = "OpusPacketizer";
    private OpusAudioQuality mQuality;
    private long mOldts;
    private MicAudioRecorder mMicAudioRecorder;
    /** Opus Udp Sender. */
    private OpusUdpSender mUdpSender;
    private boolean muted = false;
    private byte[][] mBuffers;

    private SenderReport mReport;

    private Semaphore mBufferRequested;
    private Semaphore mBufferCommitted;
    private Thread mThread;

    private int mTransport;
    private long mCacheSize;
    private int mBufferCount, mBufferIn, mBufferOut;
    private int mCount = 0;

    public OpusPacketizer(final OpusAudioQuality quality) {
        mQuality = quality;
        mCacheSize = 0;
        mBufferCount = 300; // TODO: readjust that when the FIFO is full
        mBuffers = new byte[mBufferCount][];
//        mUdpSender = new OpusUdpSender[mBufferCount];
        mReport = new SenderReport();
        resetFifo();
    }
    private void resetFifo() {
        mCount = 0;
        mBufferIn = 0;
        mBufferOut = 0;
        mBufferRequested = new Semaphore(mBufferCount);
        mBufferCommitted = new Semaphore(0);
        mReport.reset();
    }
    @Override
    public void setDestination(InetAddress dest, int rtpPort, int rtcpPort) {
        try {
            String ip = dest != null ? dest.getHostAddress() : "127.0.0.1";
//            for (int i = 0; i < mBufferCount; i++) {
                mUdpSender = new OpusUdpSender(ip, rtpPort);
//            }
            mReport.setDestination(dest, rtcpPort);
            mReport.setSSRC(new Random().nextInt());

        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "sender init error", e);
            }
        }
    }

    @Override
    public void start() {
        if (isMuted()) {
            return;
        }
        OpusEncoder.SamplingRate samplingRate = convertSamplingRate(mQuality.samplingRate);
        OpusEncoder.FrameSize frameSize = convertFrameSize(mQuality.frameSize);
        try {
            mMicAudioRecorder = new MicAudioRecorder(
                    samplingRate,
                    frameSize,
                    mQuality.bitRate,
                    mQuality.application,
                    this);
            mMicAudioRecorder.start();
        } catch (NativeInterfaceFailure nativeInterfaceFailure) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Opus Rtsp Server init error", nativeInterfaceFailure);
            }
        }
    }

    @Override
    public void stop() {
        if (mMicAudioRecorder != null) {
            mMicAudioRecorder.stop();
        }
    }

    private OpusEncoder.SamplingRate convertSamplingRate(Integer samplingRate) {
        switch(samplingRate) {
            case 8000:
                return OpusEncoder.SamplingRate.E_8K;
            case 12000:
                return OpusEncoder.SamplingRate.E_12K;
            case 16000:
                return OpusEncoder.SamplingRate.E_16K;
            case 24000:
                return OpusEncoder.SamplingRate.E_24K;
            case 48000:
                return OpusEncoder.SamplingRate.E_48K;
            default:
                throw new IllegalArgumentException("sampling rate illegal");
        }
    }


    private OpusEncoder.FrameSize convertFrameSize(Integer frameSize) {
        switch(frameSize) {
            case 25:
                return OpusEncoder.FrameSize.E_2_5_MS;
            case 50:
                return OpusEncoder.FrameSize.E_5_MS;
            case 100:
                return OpusEncoder.FrameSize.E_10_MS;
            case 200:
                return OpusEncoder.FrameSize.E_20_MS;
            case 400:
                return OpusEncoder.FrameSize.E_40_MS;
            default:
                throw new IllegalArgumentException("frame size illegal");
        }
    }

    @Override
    public void onPeriodicNotification(final byte[] opusFrameBuffer, final int opusFrameBufferLength) {
        if (!isMuted()) {
//            try {
//                mBufferRequested.acquire();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//
//            mBuffers[mBufferIn] = Arrays.copyOf(opusFrameBuffer, opusFrameBufferLength);
//            if (++mBufferIn >= mBufferCount) {
//                mBufferIn = 0;
//            }
//            mBufferCommitted.release();
//
//            if (mThread == null) {
//                mThread = new Thread(this);
//                mThread.setPriority(Thread.MAX_PRIORITY);
//                mThread.start();
//            }
            mUdpSender.send(opusFrameBuffer, opusFrameBufferLength);
        }
    }

    @Override
    public void onEncoderError() {
    }

    public void mute() {
        muted = true;
    }
    public void unMute() {
        muted = false;
    }
    public boolean isMuted() {
        return muted;
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE);

        try {
            // Caches mCacheSize milliseconds of the stream in the FIFO.
            if (mCacheSize > 0) {
                Thread.sleep(mCacheSize);
            }

            while (mBufferCommitted.tryAcquire(4, TimeUnit.SECONDS)) {
                final int offset = mBufferOut;
//                final OpusUdpSender packet = mUdpSender[offset];
                final byte[] buffer = mBuffers[mBufferOut];

                mReport.update(MTU, (SystemClock.elapsedRealtime() / 100L) * (16000 / 1000L) / 10000L);

                if (mCount++ > 30) {
//                    packet.send(buffer, buffer.length);
                }
                if (++mBufferOut >= mBufferCount) {
                    mBufferOut = 0;
                }
                mBufferRequested.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        mThread = null;
        resetFifo();
    }
}
