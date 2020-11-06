package org.deviceconnect.android.deviceplugin.host.recorder.screen;

import org.deviceconnect.android.libmedia.streaming.rtsp.session.video.H264VideoStream;
import org.deviceconnect.android.libmedia.streaming.video.VideoEncoder;

public class ScreenCastVideoStream extends H264VideoStream {
    private ScreenCastVideoEncoder mVideoEncoder;

    ScreenCastVideoStream(ScreenCastRecorder recorder, int port) {
        mVideoEncoder = (ScreenCastVideoEncoder) recorder.getVideoEncoder();
        setDestinationPort(port);
    }

    @Override
    public VideoEncoder getVideoEncoder() {
        return mVideoEncoder;
    }
}
