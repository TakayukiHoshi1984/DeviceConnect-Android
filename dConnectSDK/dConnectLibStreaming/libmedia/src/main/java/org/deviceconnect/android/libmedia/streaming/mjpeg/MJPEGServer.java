package org.deviceconnect.android.libmedia.streaming.mjpeg;

import android.util.Log;

import java.net.Socket;

import org.deviceconnect.android.libmedia.BuildConfig;
import org.deviceconnect.android.libmedia.streaming.util.MixedReplaceMediaServer;

public class MJPEGServer {
    /**
     * デバッグフラグ.
     */
    private static final boolean DEBUG = BuildConfig.DEBUG;

    /**
     * デバッグ用タグ.
     */
    private static final String TAG = "MJPEG";

    /**
     * サーバ名.
     */
    private String mServerName = "MJPEG";

    /**
     * サーバのポート番号.
     */
    private int mServerPort = 20000;

    /**
     * サーバのパス.
     */
    private String mServerPath = "mjpeg";

    /**
     * サーバ.
     */
    private MixedReplaceMediaServer mMixedReplaceMediaServer;

    /**
     * MJPEGエンコーダ.
     */
    private MJPEGEncoder mMJPEGEncoder;

    /**
     * MJPEG サーバのイベントを通知するコールバック.
     */
    private Callback mCallback;

    /**
     * MJPEG サーバへのイベントを通知するコールバックを設定します.
     *
     * @param callback コールバック
     */
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    /**
     * サーバのポート番号を設定します.
     *
     * @param serverPort サーバのポート番号
     */
    public void setServerPort(int serverPort) {
        mServerPort = serverPort;
    }

    /**
     * サーバ名を設定します.
     *
     * @param serverName サーバ名
     */
    public void setServerName(String serverName) {
        mServerName = serverName;
    }

    /**
     * サーバのパスを設定します.
     *
     * @param serverPath サーバのパス
     */
    public void setServerPath(String serverPath) {
        mServerPath = serverPath;
    }

    /**
     * MJPEG サーバを開始します.
     */
    public synchronized void start() {
        if (mMixedReplaceMediaServer != null) {
            if (DEBUG) {
                Log.w(TAG, "MixedReplaceMediaServer is already started.");
            }
            return;
        }

        mMixedReplaceMediaServer = new MixedReplaceMediaServer();
        mMixedReplaceMediaServer.setPort(mServerPort);
        mMixedReplaceMediaServer.setServerName(mServerName);
        mMixedReplaceMediaServer.setPath(mServerPath);
        mMixedReplaceMediaServer.setCallback(new MixedReplaceMediaServer.Callback() {
            @Override
            public boolean onAccept(Socket socket) {
                boolean result = false;
                if (mCallback != null) {
                    result = mCallback.onAccept(socket);
                    if (result) {
                        startMJPEGEncoder();
                    }
                }
                return result && mMJPEGEncoder != null;
            }

            @Override
            public void onClosed(Socket socket) {
                if (mCallback != null) {
                    mCallback.onClosed(socket);
                    if (mMixedReplaceMediaServer == null || mMixedReplaceMediaServer.isEmptyConnection()) {
                        stopMJPEGEncoder();
                    }
                }
            }
        });
        mMixedReplaceMediaServer.start();

        if (DEBUG) {
            Log.i(TAG, "MixedReplaceMediaServer is started.");
            Log.i(TAG, "  port: " + mServerPort);
        }
    }

    /**
     * MJPEG サーバを停止します.
     */
    public synchronized void stop() {
        if (mCallback != null) {
            stopMJPEGEncoder();
        }

        if (mMixedReplaceMediaServer != null) {
            mMixedReplaceMediaServer.stop();
            mMixedReplaceMediaServer = null;
        }

        if (DEBUG) {
            Log.i(TAG, "MixedReplaceMediaServer is stopped.");
        }
    }

    private synchronized void startMJPEGEncoder() {
        if (mMJPEGEncoder != null) {
            return;
        }

        mMJPEGEncoder = mCallback.createMJPEGEncoder();
        if (mMJPEGEncoder != null) {
            mMJPEGEncoder.setCallback((byte[] jpeg) ->
                mMixedReplaceMediaServer.offerMedia(jpeg));
            mMJPEGEncoder.start();
        }
    }

    private synchronized void stopMJPEGEncoder() {
        if (mMJPEGEncoder != null) {
            mMJPEGEncoder.stop();
            mCallback.releaseMJPEGEncoder(mMJPEGEncoder);
            mMJPEGEncoder = null;
        }
    }

    /**
     * MJPEG サーバへの接続・切断などのイベントがあったことを通知します.
     */
    public interface Callback {
        /**
         * MJPEG サーバへの接続要求を通知します.
         *
         * @param socket 接続要求してきたソケット
         * @return 接続を許可する場合はtrue、それ以外はfalse
         */
        boolean onAccept(Socket socket);

        /**
         * MJPEGサーバからソケットが切断されたことを通知します.
         *
         * @param socket 切断されたソケット
         */
        void onClosed(Socket socket);

        /**
         * MJPEG エンコーダの初期化を行います.
         *
         * @return MJPEG エンコーダ
         */
        MJPEGEncoder createMJPEGEncoder();

        /**
         * MJPEG エンコーダの後始末を行います.
         *
         * @param encoder 後始末を行う MJPEG エンコーダ
         */
        void releaseMJPEGEncoder(MJPEGEncoder encoder);
    }
}