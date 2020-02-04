package org.deviceconnect.android.libsrt.server;

import android.util.Log;

import org.deviceconnect.android.libsrt.SRTSocket;
import org.deviceconnect.android.libsrt.SRTServerSocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.deviceconnect.android.libsrt.BuildConfig.DEBUG;

/**
 * SRTサーバー.
 */
public class SRTServer {

    private static final String TAG = "SRT";

    private static final int DEFAULT_MAX_CLIENT_NUM = 10;

    private final SRTServerSocket mServerSocket;

    private Thread mServerThread;

    /**
     * SRT サーバに接続されているクライアントを確認するスレッドのリスト.
     */
    private final List<SocketThread> mSocketThreads = new ArrayList<>();

    private final List<SRTSocket> mClientSocketList = new ArrayList<>();

    private int mMaxClientNum = DEFAULT_MAX_CLIENT_NUM;

    private boolean mIsStarted;

    /**
     * SRT の処理を行うセッション.
     */
    private SRTSession mSRTSession;

    /**
     * SRT サーバへのイベントを通知するコールバック.
     */
    private Callback mCallback;

    /**
     * コンストラクタ.
     *
     * @param port サーバーのソケットにバインドするローカルのポート番号.
     */
    public SRTServer(final int port) {
        mServerSocket = new SRTServerSocket(port);
    }

    /**
     * 接続しているクライアントのソケット一覧を取得します.
     * @return ソケット一覧
     */
    public List<SRTSocket> getSocketList() {
        synchronized (mClientSocketList) {
            return new ArrayList<>(mClientSocketList);
        }
    }

    /**
     * 同時接続可能なクライアントの上限を設定します.
     *
     * {@link #start()} でサーバーを開始する前に設定してください.
     *
     * @param maxClientNum 同時接続可能なクライアントの最大個数
     */
    public void setMaxClientNum(final int maxClientNum) {
        mMaxClientNum = maxClientNum;
    }

    public void setCallback(final Callback callback) {
        mCallback = callback;
    }

    public String getServerAddress() {
        return mServerSocket.getServerAddress();
    }

    public int getServerPort() {
        return mServerSocket.getServerPort();
    }

    public SRTSession getSRTSession() {
        return mSRTSession;
    }

    public synchronized void start() throws IOException {
        if (mIsStarted) {
            return;
        }

        try {
            mServerSocket.open();
            mIsStarted = true;
        } catch (IOException e) {
            throw e;
        }

        startServerThread();
    }

    private void startServerThread() {
        mServerThread = new Thread(() -> {
            try {
                while (!Thread.interrupted()) {
                    if (DEBUG) {
                        Log.d(TAG, "Waiting for SRT client...");
                    }

                    SRTSocket socket = mServerSocket.accept();

                    if (isMaxClientNum()) {
                        socket.close();
                    } else {
                        new SocketThread(socket).start();
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                stop();
            }
        });
        mServerThread.setName("SRTServerThread");
        mServerThread.start();
    }

    private boolean isMaxClientNum() {
        return mClientSocketList.size() >= mMaxClientNum;
    }

    public synchronized void stop() {
        if (!mIsStarted) {
            return;
        }
        mIsStarted = false;

        mServerSocket.close();
        synchronized (mClientSocketList) {
            for (SRTSocket socket : mClientSocketList) {
                socket.close();
            }
            mClientSocketList.clear();
        }
        synchronized (mSocketThreads) {
            for (SocketThread t : mSocketThreads) {
                t.terminate();
            }
            mSocketThreads.clear();
        }

        mServerThread.interrupt();
        try {
            mServerThread.join(100);
        } catch (InterruptedException e) {
            // ignore
        }
        mServerThread = null;
    }

    /**
     * SRT セッションを作成します.
     */
    private void createSRTSession() {
        if (mSRTSession != null) {
            releaseSRTSession();
        }
        mSRTSession = new SRTSession();
        if (mCallback != null) {
            mCallback.createSession(mSRTSession);
        }
        mSRTSession.start();
    }

    /**
     * SRT セッションを破棄します.
     */
    private void releaseSRTSession() {
        if (mSRTSession == null) {
            return;
        }
        mSRTSession.stop();
        if (mCallback != null) {
            mCallback.releaseSession(mSRTSession);
        }
        mSRTSession = null;
    }

    /**
     * SRT クライアントソケットの生存確認を行うスレッド.
     */
    private class SocketThread extends Thread {
        /**
         * 生存確認を行うソケット.
         */
        private SRTSocket mClientSocket;

        SocketThread(final SRTSocket clientSocket) {
            mClientSocket = clientSocket;
        }

        void terminate() {
            interrupt();

            try {
                join(200);
            } catch (InterruptedException e) {
                // ignore.
            }
        }

        @Override
        public void run() {
            try {

                synchronized (mClientSocketList) {
                    mClientSocketList.add(mClientSocket);
                }

                synchronized (mSocketThreads) {
                    mSocketThreads.add(this);
                    if (mSocketThreads.size() == 1) {
                        createSRTSession();
                    }
                }

                mSRTSession.addSRTClientSocket(mClientSocket);

                while (!isInterrupted()) {
                    if (mClientSocket.isClosed()) {
                        // クライアントのソケットが閉じているので終了します
                        break;
                    }

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            } catch (Exception e) {
                // ignore.
            } finally {
                mSRTSession.removeSRTClientSocket(mClientSocket);

                try {
                    mClientSocket.close();
                } catch (Exception e) {
                    // ignore.
                }

                synchronized (mSocketThreads) {
                    mSocketThreads.remove(this);
                    if (mSocketThreads.isEmpty()) {
                        releaseSRTSession();
                    }
                }

                synchronized (mClientSocketList) {
                    mClientSocketList.remove(mClientSocket);
                }
            }
        }
    }

    public interface Callback {
        /**
         * SRTSession を作成時に呼び出します.
         *
         * <p>
         * この SRTSession にエンコーダを設定します。
         * </p>
         *
         * @param session セッション
         */
        void createSession(SRTSession session);

        /**
         * SRTSession が破棄された時に呼び出します.
         *
         * @param session セッション
         */
        void releaseSession(SRTSession session);
    }
}
