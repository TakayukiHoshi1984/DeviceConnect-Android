package org.deviceconnect.android.libmedia.streaming.rtsp.player;

import android.media.Image;
import android.util.Log;
import android.view.Surface;

import org.deviceconnect.android.libmedia.BuildConfig;
import org.deviceconnect.android.libmedia.streaming.rtsp.RtspClient;
import org.deviceconnect.android.libmedia.streaming.rtsp.RtspClientException;
import org.deviceconnect.android.libmedia.streaming.rtsp.RtspResponse;
import org.deviceconnect.android.libmedia.streaming.rtsp.player.decoder.Decoder;
import org.deviceconnect.android.libmedia.streaming.rtsp.player.decoder.DecoderFactory;
import org.deviceconnect.android.libmedia.streaming.rtsp.player.decoder.audio.AACLATMDecoderFactory;
import org.deviceconnect.android.libmedia.streaming.rtsp.player.decoder.audio.AudioDecoder;
import org.deviceconnect.android.libmedia.streaming.rtsp.player.decoder.video.H264DecoderFactory;
import org.deviceconnect.android.libmedia.streaming.rtsp.player.decoder.video.H265DecoderFactory;
import org.deviceconnect.android.libmedia.streaming.rtsp.player.decoder.video.VideoDecoder;
import org.deviceconnect.android.libmedia.streaming.sdp.Attribute;
import org.deviceconnect.android.libmedia.streaming.sdp.MediaDescription;
import org.deviceconnect.android.libmedia.streaming.sdp.SessionDescription;
import org.deviceconnect.android.libmedia.streaming.sdp.attribute.RtpMapAttribute;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RtspPlayer {
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String TAG = "RTSP-PLAYER";

    /**
     * RTSP サーバと通信を行うクライアント.
     */
    private RtspClient mRtspClient;

    /**
     * RTSP サーバからの映像を描画する Surface.
     */
    private Surface mSurface;

    /**
     * RTSP サーバの URL.
     */
    private final String mUrl;

    /**
     * RTSP サーバへの接続リトライ回数.
     */
    private int mRetryCount;

    /**
     * RTSP プレイヤーのイベントを通知するリスナー.
     */
    private OnEventListener mOnEventListener;

    /**
     * ミュート設定.
     */
    private boolean mMute;

    /**
     * デコーダを保持するマップ.
     */
    private final Map<MediaDescription, Decoder> mDecoderMap = new LinkedHashMap<>();

    /**
     * 映像用のデコーダを作成するファクトリークラスを保持するマップ.
     */
    private final Map<String, DecoderFactory> mVideoFactory = new HashMap<>();

    /**
     * 音声用のデコーダを作成するファクトリークラスを保持するマップ.
     */
    private final Map<String, DecoderFactory> mAudioFactory = new HashMap<>();

    /**
     * 指定ポート番号リスト.
     */
    private final List<Integer> mRtpPortList;

    /**
     * 接続のタイムアウト時間を設定.
     */
    private int mConnectionTimeout = 10 * 1000;

    /**
     * コンストラクタ.
     *
     * @param url RTSP サーバへのURL
     */
    public RtspPlayer(String url) {
        this(url, new ArrayList<>());
    }

    /**
     * コンストラクタ.
     *
     * @param url RTSP サーバへのURL
     * @param rtpPortList RTP/RTCP に指定するUDPポート番号一覧
     */
    public RtspPlayer(String url, List<Integer> rtpPortList) {
        if (url == null) {
            throw new IllegalArgumentException("url is null.");
        }

        if (rtpPortList == null) {
            throw new IllegalArgumentException("setPortList is null.");
        }

        for (int port : rtpPortList) {
            if (port <= 1024) {
                throw new IllegalArgumentException("rtpPortList is invalid port number.(Must be greater than 1025.)");
            }
        }

        mUrl = url;
        mRtpPortList = rtpPortList;

        addVideoFactory("H264", new H264DecoderFactory());
        addVideoFactory("H265", new H265DecoderFactory());
        addAudioFactory("mpeg4-generic", new AACLATMDecoderFactory());
    }

    /**
     * RTSP サーバへの接続タイムアウト時間(ミリ秒)を設定します.
     *
     * @param timeout タイムアウト時間(ms)
     */
    public void setConnectionTimeout(int timeout) {
        mConnectionTimeout = timeout;
    }

    /**
     * RTSP サーバへの URL を取得します.
     *
     * @return RTSP サーバへの URL
     */
    public String getUrl() {
        return mUrl;
    }

    /**
     * 受信したデータサイズを取得します.
     *
     * @return 受信したデータサイズ
     */
    public long getReceivedSize() {
        return mRtspClient != null ? mRtspClient.getReceivedSize() : 0;
    }

    /**
     * 受信したデータの BPS を取得します.
     *
     * @return 受信したデータの BPS
     */
    public long getBPS() {
        return mRtspClient != null ? mRtspClient.getBPS() : 0;
    }

    /**
     * ミュート設定を取得します.
     *
     * @return ミュートの場合はtrue、それ以外はfalse
     */
    public boolean isMute() {
        return mMute;
    }

    /**
     * ミュートを設定します.
     *
     * @param mute ミュートの場合はtrue、それ以外はfalse
     */
    public void setMute(boolean mute) {
        mMute = mute;

        for (Decoder decoder : mDecoderMap.values()) {
            if (decoder instanceof AudioDecoder) {
                ((AudioDecoder) decoder).setMute(mute);
            }
        }
    }

    /**
     * イベントを通知するリスナーを設定します.
     *
     * @param listener リスナー
     */
    public void setOnEventListener(OnEventListener listener) {
        mOnEventListener = listener;
    }

    /**
     * 映像を描画する先の Surface を設定します.
     *
     * @param surface 描画を行う Surface
     */
    public void setSurface(Surface surface) {
        mSurface = surface;
    }

    /**
     * 映像用のデコーダーを登録します.
     *
     * @param name デコーダー名
     * @param factory デコーダーを作成するファクトリークラス
     */
    public void addVideoFactory(String name, DecoderFactory factory) {
        mVideoFactory.put(name.toLowerCase(), factory);
    }

    /**
     * 音声用のデコーダーを登録します.
     *
     * @param name デコーダー名
     * @param factory デコーダーを作成するファクトリークラス
     */
    public void addAudioFactory(String name, DecoderFactory factory) {
        mAudioFactory.put(name.toLowerCase(), factory);
    }

    /**
     * RTSP の再生を開始します.
     */
    public synchronized void start() {
        if (mRtspClient != null) {
            if (DEBUG) {
                Log.e(TAG, "RtspPlayer is already running.");
            }
            return;
        }

        mRetryCount = 0;
        mRtspClient = new RtspClient(mUrl, mRtpPortList);
        mRtspClient.setConnectionTimeout(mConnectionTimeout);
        mRtspClient.setOnEventListener(new RtspClient.OnEventListener() {
            @Override
            public void onConnected() {
                postOnConnected();
            }

            @Override
            public void onDisconnected() {
                postOnDisconnected();
            }

            @Override
            public void onError(RtspClientException e) {
                if (DEBUG) {
                    Log.e(TAG, "RtspClient::onError: " + e.getStatus());
                }

                // RTSP サーバ側でのエラーの場合にはリトライを行う
                if (e.getStatus() == RtspResponse.Status.STATUS_INTERNAL_SERVER_ERROR) {
                    mRetryCount++;
                    if (mRetryCount < 3) {
                        new Thread(() -> {
                            stop();

                            try {
                                Thread.sleep(500 * mRetryCount);
                            } catch (InterruptedException e1) {
                                // ignore.
                            }

                            start();
                        }).start();
                    } else {
                        postOnError(e);
                    }
                } else {
                    postOnError(e);
                }
            }

            @Override
            public void onSdpReceived(SessionDescription sdp) {
                if (DEBUG) {
                    Log.d(TAG, sdp.toString());
                }

                for (MediaDescription md : sdp.getMediaDescriptions()) {
                    Decoder decoder = createDecoder(md);
                    if (decoder != null) {
                        mDecoderMap.put(md, decoder);
                    }
                }

                postOnReady();
            }

            @Override
            public void onRtpReceived(MediaDescription md, byte[] data, int dataLength) {
                Decoder decoder = mDecoderMap.get(md);
                if (decoder != null) {
                    decoder.onRtpReceived(md, data, dataLength);
                }
            }

            @Override
            public void onRtcpReceived(MediaDescription md, byte[] data, int dataLength) {
                Decoder decoder = mDecoderMap.get(md);
                if (decoder != null) {
                    decoder.onRtcpReceived(md, data, dataLength);
                }
            }
        });
        mRtspClient.start();
    }

    /**
     * RTSP の再生を停止します.
     */
    public synchronized void stop() {
        if (mRtspClient != null) {
            mRtspClient.stop();
            mRtspClient = null;
        }

        for (Decoder decoder : mDecoderMap.values()) {
            decoder.onRelease();
        }
        mDecoderMap.clear();
    }

    /**
     * デコーダを作成します.
     *
     * @param md MediaDescription
     * @return デコーダ
     */
    private Decoder createDecoder(MediaDescription md) {
        if ("video".equalsIgnoreCase(md.getMedia())) {
            VideoDecoder decoder = createVideoDecoder(md);
            if (decoder != null) {
                decoder.setSurface(mSurface);
                decoder.setErrorCallback(this::postOnError);
                decoder.setEventCallback(new VideoDecoder.EventCallback() {
                    @Override
                    public void onSizeChanged(int width, int height) {
                        postOnSizeChanged(width, height);
                    }

                    @Override
                    public void onData(Image image, long presentationTimeUs) {
                        postOnVideoData(image, presentationTimeUs);
                    }
                });
                decoder.onInit(md);
                return decoder;
            } else {
                if (DEBUG) {
                    Log.w(TAG, "Not supported. format=" + md.toString());
                }
            }
        } else if ("audio".equalsIgnoreCase(md.getMedia())) {
            AudioDecoder decoder = createAudioDecoder(md);
            if (decoder != null) {
                decoder.setErrorCallback(this::postOnError);
                decoder.setEventCallback(new AudioDecoder.EventCallback() {
                    @Override
                    public void onFormatChanged(int sampleRate, int channel) {
                        postOnAudioFormatChanged(sampleRate, channel);
                    }

                    @Override
                    public void onData(ByteBuffer data, int offset, int size, long presentationTimeUs) {
                        postOnAudioData(data, offset, size, presentationTimeUs);
                    }
                });
                decoder.setMute(mMute);
                decoder.onInit(md);
                return decoder;
            } else {
                if (DEBUG) {
                    Log.w(TAG, "Not supported. format=" + md.toString());
                }
            }
        } else {
            if (DEBUG) {
                Log.w(TAG, "Unknown media type. media=" + md.getMedia());
            }
        }
        return null;
    }

    /**
     * メディア情報から映像用のデコーダーを作成します.
     * <p>
     * デコーダーが作成できなかった場合には null を返却します。
     * </p>
     * @param md メディア情報
     * @return デコーダー
     */
    private VideoDecoder createVideoDecoder(MediaDescription md) {
        String name = getEncodingName(md);
        if (name == null) {
            return null;
        }

        DecoderFactory factory = mVideoFactory.get(name.toLowerCase());
        if (factory != null) {
            return (VideoDecoder) factory.createDecoder();
        }
        return null;
    }

    /**
     * メディア情報から音声用のデコーダを作成します.
     * <p>
     * デコーダーが作成できなかった場合には null を返却します。
     * </p>
     * @param md メディア情報
     * @return デコーダー
     */
    private AudioDecoder createAudioDecoder(MediaDescription md) {
        String name = getEncodingName(md);
        if (name == null) {
            return null;
        }

        DecoderFactory factory = mAudioFactory.get(name.toLowerCase());
        if (factory != null) {
            return (AudioDecoder) factory.createDecoder();
        }
        return null;
    }

    /**
     * メディア情報からエンコーダーの名前を取得します.
     *
     * @param md メディア情報
     * @return エンコーダ名
     */
    private String getEncodingName(MediaDescription md) {
        for (Attribute attribute : md.getAttributes()) {
            if (attribute instanceof RtpMapAttribute) {
                RtpMapAttribute rtpMapAttribute = (RtpMapAttribute) attribute;
                return rtpMapAttribute.getEncodingName();
            }
        }
        return null;
    }

    private void postOnConnected() {
        if (mOnEventListener != null) {
            mOnEventListener.onConnected();
        }
    }

    private void postOnDisconnected() {
        if (mOnEventListener != null) {
            mOnEventListener.onDisconnected();
        }
    }

    private void postOnReady() {
        if (mOnEventListener != null) {
            mOnEventListener.onReady();
        }
    }

    private void postOnSizeChanged(int width, int height) {
        if (mOnEventListener != null) {
            mOnEventListener.onSizeChanged(width, height);
        }
    }

    private void postOnVideoData(Image image, long presentationTimeUs) {
        if (mOnEventListener != null) {
            mOnEventListener.onVideoData(image, presentationTimeUs);
        }
    }

    private void postOnAudioFormatChanged(int samplingRate, int channel) {
        if (mOnEventListener != null) {
            mOnEventListener.onAudioFormatChanged(samplingRate, channel);
        }
    }

    private void postOnAudioData(ByteBuffer data, int offset, int size, long presentationTimeUs) {
        if (mOnEventListener != null) {
            mOnEventListener.onAudioData(data, offset, size, presentationTimeUs);
        }
    }

    private void postOnError(Exception e) {
        if (mOnEventListener != null) {
            mOnEventListener.onError(e);
        }
    }

    public interface OnEventListener {
        /**
         * RTSP サーバの接続したことを通知します.
         */
        void onConnected();

        /**
         * RTSP サーバから切断したことを通知します.
         */
        void onDisconnected();

        /**
         * RTSP の受信準備が完了したことを通知します.
         */
        void onReady();

        /**
         * 映像の解像度が変更されたことを通知します.
         *
         * @param width 横幅
         * @param height 縦幅
         */
        void onSizeChanged(int width, int height);

        /**
         * 映像データを通知します.
         *
         * @param image 映像データ
         * @param presentationTimeUs プレゼンテーションタイム
         */
        void onVideoData(Image image, long presentationTimeUs);

        /**
         * 音声データのフォーマットを通知します.
         *
         * @param samplingRate サンプリングレート
         * @param channel チャンネル
         */
        void onAudioFormatChanged(int samplingRate, int channel);

        /**
         * 音声データを通知します.
         *
         * @param data 音声データ
         * @param offset データのオフセット
         * @param size データサイズ
         * @param presentationTimeUs プレゼンテーションタイム
         */
        void onAudioData(ByteBuffer data, int offset, int size, long presentationTimeUs);

        /**
         * RTSP プレイヤーでエラーが発生したことを通知します.
         *
         * @param e エラー原因の例外
         */
        void onError(Exception e);
    }
}
