package org.deviceconnect.android.deviceplugin.host.recorder.camera;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.rtsp.RtspServer;
import net.majorkernelpanic.streaming.rtsp.RtspServerImpl;

import org.deviceconnect.android.deviceplugin.host.recorder.AbstractPreviewServerProvider;
import org.deviceconnect.android.deviceplugin.host.recorder.PreviewServer;
import org.deviceconnect.android.deviceplugin.host.recorder.camera.AbstractRTSPPreviewServer;
import org.deviceconnect.android.streaming.opus.OpusAudioQuality;
import org.deviceconnect.android.streaming.opus.OpusStream;
import org.deviceconnect.opuscodec.BuildConfig;
import org.deviceconnect.opuscodec.OpusEncoder;

import java.net.Socket;

class OpusRtspServer extends AbstractRTSPPreviewServer implements RtspServer.Delegate {
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final String TAG = OpusRtspServer.class.getSimpleName();

    static final String MIME_TYPE = "audio/opus";
    static final int OPUS_RTSP_SERVER_PORT = 25000;

    private static final String SERVER_NAME = "Opus Audio RTSP Server";

    private final Object mLockObj = new Object();
    private Handler mHandler;
    private RtspServer mRtspServer;
    private Context mContext;

    private int mSamplingRate;
    private int mFrameSize;

    /**
     * Callback interface used to receive the result of starting a web server.
     */
    public interface OnWebServerStartCallback {
        /**
         * Called when a web server successfully started.
         *
         * @param uri An ever-updating, static image URI.
         */
        void onStart(String uri);

        /**
         * Called when a web server failed to start.
         */
        void onFail();
    }
    OpusRtspServer(final Context context,
                   final AbstractPreviewServerProvider serverProvider,
                   final int samplingRate, final int frameSize) {
        super(context, serverProvider);
        mContext = context;
        mSamplingRate = samplingRate;
        mFrameSize = frameSize;
    }

    @Override
    public Session generateSession(String uri, String redirectUri, Socket client) {
        SessionBuilder builder = new SessionBuilder();
        builder.setContext(mContext);
        OpusAudioQuality quality = new OpusAudioQuality();
        quality.samplingRate = mSamplingRate;
        quality.frameSize = mFrameSize;
        quality.bitRate = OpusEncoder.BITRATE_MAX;
        quality.application = OpusEncoder.Application.E_AUDIO;
        OpusStream opus = new OpusStream(quality);
        // ミュートを解除する
        // Hostプラグイン側と辻褄を合わせるためにこのようにしている。
        opus.unMute();
        builder.setAudioStream(opus);
//        builder.setAudioDestinationPorts(OPUS_RTSP_SERVER_PORT + 4);

        Session session = builder.build();
        session.setOrigin(client.getLocalAddress().getHostAddress());
        if (session.getDestination() == null) {
            session.setDestination(client.getInetAddress().getHostAddress());
        }
        return session;
    }

    @Override
    public void eraseSession(Session session) {
        stopWebServer();
    }

    public String getMimeType() {
        return MIME_TYPE;
    }

    /**
     * サーバを開始します.
     * @param callback 開始結果を通知するコールバック
     */
    @Override
    public void startWebServer(final PreviewServer.OnWebServerStartCallback callback) {
        synchronized (mLockObj) {
            if (mRtspServer == null) {
                mRtspServer = new RtspServerImpl(SERVER_NAME);
                mRtspServer.setPort(OPUS_RTSP_SERVER_PORT);
                mRtspServer.setDelegate(this);
                Log.d("ABC", "opus1");
                if (!mRtspServer.start()) {
                    Log.d("ABC", "opus2");
                    callback.onFail();
                    return;
                }
            }
            Log.d("ABC", "opus3");
            if (mHandler == null) {
                HandlerThread thread = new HandlerThread("OpusRtspServer");
                thread.start();
                mHandler = new Handler(thread.getLooper());
            }
            String uri = "rtsp://localhost:" + mRtspServer.getPort();
            callback.onStart(uri);
        }
    }

    /**
     * サーバを停止します.
     */
    @Override
    public void stopWebServer() {
        try {
            synchronized (mLockObj) {
                if (mRtspServer != null) {
                    mRtspServer.stop();
                    mRtspServer = null;
                }
            }
        } catch (Throwable e) {
            if (DEBUG) {
                Log.e(TAG, "stopWebServer", e);
            }
            throw e;
        }
    }

    @Override
    public int getQuality() {
        return 0;
    }

    @Override
    public void setQuality(int quality) {

    }

    @Override
    public void onDisplayRotation(int degree) {

    }
}
