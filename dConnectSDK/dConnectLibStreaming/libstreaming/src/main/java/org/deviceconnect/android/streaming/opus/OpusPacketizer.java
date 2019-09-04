package org.deviceconnect.android.streaming.opus;

import android.util.Log;

import net.majorkernelpanic.streaming.BuildConfig;
import net.majorkernelpanic.streaming.rtp.AbstractPacketizer;

import org.deviceconnect.opuscodec.MicAudioRecorder;
import org.deviceconnect.opuscodec.NativeInterfaceFailure;
import org.deviceconnect.opuscodec.OpusEncoder;
import org.deviceconnect.opuscodec.OpusUdpSender;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class OpusPacketizer extends AbstractPacketizer implements MicAudioRecorder.AudioRecordCallback {
    private final static String TAG = "OpusPacketizer";
    private OpusAudioQuality mQuality;
    private long mOldts;
    private MicAudioRecorder mMicAudioRecorder;
    /** Opus Udp Sender. */
    private OpusUdpSender mUdpSender;
    private boolean muted = true; //default true
    private DatagramSocket mRTPSocket;
    /** RTSPのリソースデータを詰め込むPacket. */
    private DatagramPacket mRTPPacket;
    /** RTSPの映像リソースが詰め込むバイト配列. */
    private byte[] mVideoBuf = new byte[4096];
    /** RTSPの音声リソースが詰め込むバイト配列. */
    private byte[] mAudioBuf = new byte[4096];


    public OpusPacketizer(final OpusAudioQuality quality) {
        mQuality = quality;
    }
    @Override
    public void setDestination(InetAddress dest, int rtpPort, int rtcpPort) {
        try {
            String ip = dest != null ? dest.getHostAddress() : "127.0.0.1";
            mUdpSender = new OpusUdpSender(ip, rtpPort);
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

}
