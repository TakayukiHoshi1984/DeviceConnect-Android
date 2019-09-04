package org.deviceconnect.android.streaming.opus;

import net.majorkernelpanic.streaming.audio.AudioQuality;

import org.deviceconnect.opuscodec.OpusEncoder;

public class OpusAudioQuality extends AudioQuality {
    public int frameSize;
    public OpusEncoder.Application application;
}
