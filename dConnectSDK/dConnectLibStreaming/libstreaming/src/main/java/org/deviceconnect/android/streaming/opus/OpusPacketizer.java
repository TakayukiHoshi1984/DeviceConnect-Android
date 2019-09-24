package org.deviceconnect.android.streaming.opus;

import android.util.Log;

import net.majorkernelpanic.streaming.BuildConfig;
import net.majorkernelpanic.streaming.rtcp.SenderReport;
import net.majorkernelpanic.streaming.rtp.AbstractPacketizer;

import org.deviceconnect.opuscodec.MicAudioRecorder;
import org.deviceconnect.opuscodec.NativeInterfaceFailure;
import org.deviceconnect.opuscodec.OpusEncoder;
import org.deviceconnect.opuscodec.OpusUdpSender;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class OpusPacketizer extends AbstractPacketizer implements MicAudioRecorder.AudioRecordCallback, Runnable {
    private final static String TAG = "OpusPacketizer";
    private OpusAudioQuality mQuality;
    private MicAudioRecorder mMicAudioRecorder;
    /** Opus Udp Sender. */
    private OpusUdpSender mUdpSender;
    private boolean muted = false;
    private Thread mThread;
    private DatagramPacket[] mPackets;
    private byte[] mBuf = new byte[4096];

    private SenderReport mReport;
    private Semaphore mBufferRequested;
    private Semaphore mBufferCommitted;

    private int mBufferCount, mBufferIn, mBufferOut;
    private int mCount = 0;

    public OpusPacketizer(final OpusAudioQuality quality) {
        mQuality = quality;
        mReport = new SenderReport();

        mBufferCount = 300; // TODO: readjust that when the FIFO is full
        mPackets = new DatagramPacket[mBufferCount];
        for (int i = 0; i < mBufferCount; i++) {
            mPackets[i] = new DatagramPacket(mBuf, mBuf.length);
        }
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
            mUdpSender = new OpusUdpSender(ip, rtpPort);
            mReport.setDestination(dest, rtcpPort);
            mReport.setSSRC(mUdpSender.getSSRC());

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
            try {
                mBufferRequested.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (++mBufferIn >= mBufferCount) {
                mBufferIn = 0;
            }
            mBufferCommitted.release();
            mPackets[mBufferIn].setData(opusFrameBuffer);
            mPackets[mBufferIn].setLength(opusFrameBufferLength);
            if (mThread == null) {
                mThread = new Thread(this);
                mThread.setPriority(Thread.MAX_PRIORITY);
                mThread.start();
            }

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
        try {
            while (mBufferCommitted.tryAcquire(4, TimeUnit.SECONDS)) {
                final DatagramPacket packet = mPackets[mBufferOut];

                if (mCount++ > 30) {
                    mUdpSender.send(packet.getData(), packet.getLength());
                    mReport.update(packet.getLength(), mUdpSender.getTimeStamp());
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
