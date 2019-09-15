package org.deviceconnect.android.streaming.opus;

import android.util.Log;

import net.majorkernelpanic.streaming.audio.AudioStream;

import java.io.IOException;

public class OpusStream extends AudioStream {
    private String mSessionDescription = null;
    private Thread mThread = null;
    private OpusAudioQuality mQuality;
    private boolean muted = true;

    public OpusStream(OpusAudioQuality quality) {
        mQuality = quality;
    }
    @Override
    public synchronized void start() throws IllegalStateException, IOException {
        if (!mStreaming) {
            configure();
            encodeWithMediaCodec();
        }
    }
    /** Stops the stream. */
    public synchronized void stop() {
        if (mStreaming) {
            if (mMode==MODE_MEDIACODEC_API ) {
                Log.d(TAG, "Interrupting threads...");
                if (mThread != null) {
                    mThread.interrupt();
                }
            }
            mPacketizer.stop();
            mStreaming = false;
        }
    }
    public synchronized void configure() throws IllegalStateException, IOException {
        super.configure();
        mSessionDescription = "m=audio "+getDestinationPorts()[0]+" RTP/AVP 111\r\n" +
                "a=rtpmap:111 opus/"+mQuality.samplingRate+"/1\r\n"+
                "a=fmtp:111 maxplaybackrate=16000; sprop-maxcapturerate=16000; maxaveragebitrate=20000; stereo=1; useinbandfec=1; usedtx=0\r\n";
        mPacketizer = new OpusPacketizer(mQuality);
        mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);

        if (isMuted()) {
            ((OpusPacketizer) mPacketizer).mute();
        } else {
            ((OpusPacketizer) mPacketizer).unMute();
        }
    }
    @Override
    protected void encodeWithMediaCodec() throws IOException {
        mPacketizer.start();
        mStreaming = true;
    }

    @Override
    public String getSessionDescription() throws IllegalStateException {
        if (mSessionDescription == null) throw new IllegalStateException("You need to call configure() first !");
        return mSessionDescription;
    }
    public void mute() {
        muted = true;
        if (mPacketizer != null) {
            ((OpusPacketizer) mPacketizer).mute();
        }
    }
    public void unMute() {
        muted = false;
        if (mPacketizer != null) {
            ((OpusPacketizer) mPacketizer).unMute();
        }
    }
    public boolean isMuted() {
        return muted;

    }

}
